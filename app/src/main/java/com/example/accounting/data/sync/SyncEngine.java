package com.example.accounting.data.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.StockMovementDao;
import com.example.accounting.data.db.entity.Category;
import com.example.accounting.data.db.entity.Expense;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.db.entity.Purchase;
import com.example.accounting.data.db.entity.PurchaseItem;
import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.db.entity.SaleItem;
import com.example.accounting.data.db.entity.OrderEvent;
import com.example.accounting.data.db.entity.StockMovement;
import com.example.accounting.data.db.entity.SyncEntity;
import com.example.accounting.data.db.entity.SyncState;
import com.example.accounting.data.sync.dto.SyncModels;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 同步引擎：上行 push / 下行 pull / 冲突合并（ARCHITECTURE.md 第 4 节）。
 *
 * 状态机（简化说明）：本地新建或修改 → PENDING；推送成功 → SYNCED（回填
 * lastSyncedAt=服务器时间）；推送失败 → FAILED（retryCount+1，原因入库）。
 * v1 不再中途打 SYNCING：App 在上传中被杀时行仍是 PENDING，下次重推，
 * 而服务端按 UUID 幂等 upsert，重复推送不会产生第二条记录。
 *
 * 冲突规则 v1：待上行的本地数据胜出——下行合并时，本地行只要不是 SYNCED
 * （即还有未上云的修改），就跳过远端版本；否则远端较新数据覆盖本地。
 *
 * 每次 push 前必须先配置服务器地址（设置页），deviceId 首次同步时自动生成。
 */
public class SyncEngine {

    private static final String TAG = "SyncEngine";

    /** 下行拉取游标向回重叠的窗口：容忍"客户端时钟略快于服务器"造成的漏单 */
    private static final long PULL_OVERLAP_MILLIS = 5 * 60 * 1000L;

    /** 每批上下行条数上限 */
    private static final int BATCH_SIZE = 100;

    /** 表名常量（与实体表一一对应，也是服务端存储的分区键之一） */
    public static final String TABLE_CATEGORIES = "categories";
    public static final String TABLE_PRODUCTS = "products";
    public static final String TABLE_SALES = "sales";
    public static final String TABLE_SALE_ITEMS = "sale_items";
    public static final String TABLE_PURCHASES = "purchases";
    public static final String TABLE_PURCHASE_ITEMS = "purchase_items";
    public static final String TABLE_EXPENSES = "expenses";
    public static final String TABLE_STOCK_MOVEMENTS = "stock_movements";
    public static final String TABLE_ORDER_EVENTS = "order_events";

    // ---- sync_state 键 ----
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_SERVER_KEY = "server_key";
    private static final String KEY_LAST_PULL_AT = "last_pull_at";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_LAST_SYNC_ERROR = "last_sync_error";

    public interface Callback {
        void onDone(String message);

        void onError(String message);
    }

    private static final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Context appContext;
    private final AppDatabase db;
    private final SyncApi api = new SyncApi();
    private final Gson gson = new Gson();

    private SyncEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AppDatabase.getInstance(appContext);
    }

    // ---------------- 对外入口 ----------------

    /** 设置页"立即同步"：后台执行，回调切回主线程 */
    public static void syncNow(Context context, Callback callback) {
        syncExecutor.execute(() -> {
            try {
                String message = new SyncEngine(context).run();
                mainHandler.post(() -> callback.onDone(message));
            } catch (Exception e) {
                Log.w(TAG, "同步失败", e);
                String message = e.getMessage() == null ? "同步失败" : e.getMessage();
                mainHandler.post(() -> callback.onError(message));
            }
        });
    }

    /** WorkManager 调用：在 Worker 自己的后台线程阻塞执行 */
    public static void syncBlocking(Context context) throws IOException {
        new SyncEngine(context).run();
    }

    // ---------------- 主流程 ----------------

    private String run() throws IOException {
        String baseUrl = getConfig(KEY_SERVER_URL);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IOException("尚未配置服务器地址（设置 → 云同步）");
        }
        String key = getConfig(KEY_SERVER_KEY);
        if (key == null) {
            key = "";
        }
        String deviceId = ensureDeviceId();

        // 先上行、后下行：刚改的数据先上云，下行才不会把旧数据合并进来
        int pushed = pushAll(baseUrl, key, deviceId);
        long pulled = pullAll(baseUrl, key, deviceId);

        long now = System.currentTimeMillis();
        putConfig(KEY_LAST_SYNC_AT, String.valueOf(now));
        putConfig(KEY_LAST_SYNC_ERROR, null);
        return "同步完成：上行 " + pushed + " 行，下行 " + pulled + " 行";
    }

    // ---------------- 上行 push ----------------

    private int pushAll(String baseUrl, String key, String deviceId) throws IOException {
        int total = 0;
        for (TableHandler<?> handler : handlers().values()) {
            total += pushOneTable(baseUrl, key, deviceId, handler);
        }
        return total;
    }

    private <T extends SyncEntity> int pushOneTable(String baseUrl, String key,
                                                    String deviceId, TableHandler<T> handler)
            throws IOException {
        List<T> dirty = handler.dirty(BATCH_SIZE);
        if (dirty.isEmpty()) {
            return 0;
        }

        List<String> ids = new ArrayList<>();
        List<SyncModels.Change> changes = new ArrayList<>();
        for (T entity : dirty) {
            ids.add(idOf(entity));
            SyncModels.Change change = new SyncModels.Change();
            change.table = handler.table();
            change.rowId = idOf(entity);
            change.updatedAt = entity.updatedAt;
            change.isDeleted = entity.isDeleted;
            change.payload = entity; // Gson 会把整行实体序列化为 payload
            changes.add(change);
        }

        try {
            SyncModels.PushRequest request = new SyncModels.PushRequest();
            request.deviceId = deviceId;
            request.changes = changes;
            SyncModels.PushResponse response = api.push(baseUrl, key, request);
            handler.mark(ids, SyncStatus.SYNCED, response.serverTime, null, 0);
            Log.i(TAG, "上行 " + handler.table() + " " + ids.size() + " 行");
            return ids.size();
        } catch (IOException e) {
            // 本批标 FAILED 留待重试；其余表下一轮继续
            handler.mark(ids, SyncStatus.FAILED, null, e.getMessage(), 1);
            throw e;
        }
    }

    // ---------------- 下行 pull ----------------

    private long pullAll(String baseUrl, String key, String deviceId) throws IOException {
        long lastPullAt = 0;
        String stored = getConfig(KEY_LAST_PULL_AT);
        if (stored != null) {
            try {
                lastPullAt = Long.parseLong(stored);
            } catch (NumberFormatException ignored) {
            }
        }
        long since = Math.max(lastPullAt - PULL_OVERLAP_MILLIS, 0);

        long serverTime = System.currentTimeMillis();
        long applied = 0;
        while (true) {
            SyncModels.PullRequest request = new SyncModels.PullRequest();
            request.deviceId = deviceId;
            request.since = since;
            request.limit = BATCH_SIZE;
            SyncModels.PullResponse response = api.pull(baseUrl, key, request);
            serverTime = response.serverTime;

            if (response.changes == null || response.changes.isEmpty()) {
                break;
            }
            for (SyncModels.Change change : response.changes) {
                applied += applyChange(change, serverTime);
            }
            if (response.changes.size() < BATCH_SIZE) {
                break;
            }
            // 还有整页数据：以本页最大 updatedAt 继续翻页
            since = response.changes.get(response.changes.size() - 1).updatedAt;
        }

        putConfig(KEY_LAST_PULL_AT, String.valueOf(serverTime));
        return applied;
    }

    /**
     * 合并一条远端变更。
     * 冲突规则：本地行不是 SYNCED（还有未上云的修改）→ 本地胜出，跳过远端；
     * 否则远端数据覆盖本地，并标记 SYNCED。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int applyChange(SyncModels.Change change, long serverTime) {
        TableHandler handler = handlers().get(change.table);
        if (handler == null || change.payload == null) {
            return 0;
        }
        SyncEntity existing = handler.findById(change.rowId);
        if (existing != null && existing.syncStatus != SyncStatus.SYNCED) {
            return 0; // 本地待上行的修改胜出
        }
        // change.payload 被 Gson 反序列化成 LinkedTreeMap（声明为 Object），
        // 先转回 JSON 树再按目标实体类型解析
        SyncEntity remote = (SyncEntity) gson.fromJson(
                gson.toJsonTree(change.payload), handler.type());
        remote.syncStatus = SyncStatus.SYNCED;
        remote.lastSyncedAt = serverTime;
        remote.retryCount = 0;
        remote.lastSyncError = null;
        handler.upsertRemote(remote);
        return 1;
    }

    // ---------------- 配置与设备标识 ----------------

    public static String getConfigStatic(Context context, String key) {
        return AppDatabase.getInstance(context).syncStateDao().getValue(key);
    }

    private String getConfig(String key) {
        return db.syncStateDao().getValue(key);
    }

    private void putConfig(String key, String value) {
        SyncState state = new SyncState();
        state.key = key;
        state.value = value;
        state.updatedAt = System.currentTimeMillis();
        db.syncStateDao().put(state);
    }

    /** 首次同步生成设备标识（此后不变，服务端以 (deviceId, rowId) 幂等分区） */
    private String ensureDeviceId() {
        String deviceId = getConfig(KEY_DEVICE_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            putConfig(KEY_DEVICE_ID, deviceId);
        }
        return deviceId;
    }

    // ---------------- 8 张表的同步适配器 ----------------

    /**
     * 每张表接入同步需要的 5 个动作完全同构，用 TableHandler 统一表达，
     * push/pull 的循环只写一遍。新增表 = 在 map 里注册一个 handler。
     */
    private interface TableHandler<T extends SyncEntity> {
        String table();

        List<T> dirty(int limit);

        Class<T> type();

        SyncEntity findById(String id);

        void mark(List<String> ids, int status, Long syncedAt, String error, int incRetry);

        void upsertRemote(T entity);
    }

    /** 供 push 构造 Change 用：取实体主键 */
    private static String idOf(SyncEntity entity) {
        return entity instanceof Category ? ((Category) entity).id
                : entity instanceof Product ? ((Product) entity).id
                : entity instanceof Sale ? ((Sale) entity).id
                : entity instanceof SaleItem ? ((SaleItem) entity).id
                : entity instanceof Purchase ? ((Purchase) entity).id
                : entity instanceof PurchaseItem ? ((PurchaseItem) entity).id
                : entity instanceof Expense ? ((Expense) entity).id
                : entity instanceof OrderEvent ? ((OrderEvent) entity).id
                : ((StockMovement) entity).id;
    }

    @SuppressWarnings("rawtypes")
    private Map<String, TableHandler> handlers() {
        Map<String, TableHandler> map = new LinkedHashMap<>();

        map.put(TABLE_CATEGORIES, new TableHandler<Category>() {
            @Override public String table() { return TABLE_CATEGORIES; }
            @Override public List<Category> dirty(int limit) {
                return db.categoryDao().listDirtyForSync(limit); }
            @Override public Class<Category> type() { return Category.class; }
            @Override public SyncEntity findById(String id) {
                return db.categoryDao().findById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.categoryDao().markSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(Category entity) {
                db.categoryDao().upsertFromRemote(entity); }
        });

        map.put(TABLE_PRODUCTS, new TableHandler<Product>() {
            @Override public String table() { return TABLE_PRODUCTS; }
            @Override public List<Product> dirty(int limit) {
                return db.productDao().listDirtyForSync(limit); }
            @Override public Class<Product> type() { return Product.class; }
            @Override public SyncEntity findById(String id) {
                return db.productDao().findById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.productDao().markSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(Product entity) {
                db.productDao().upsertFromRemote(entity); }
        });

        map.put(TABLE_SALES, new TableHandler<Sale>() {
            @Override public String table() { return TABLE_SALES; }
            @Override public List<Sale> dirty(int limit) {
                return db.saleDao().listDirtyForSync(limit); }
            @Override public Class<Sale> type() { return Sale.class; }
            @Override public SyncEntity findById(String id) {
                return db.saleDao().findById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.saleDao().markSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(Sale entity) {
                db.saleDao().upsertFromRemote(entity); }
        });

        map.put(TABLE_SALE_ITEMS, new TableHandler<SaleItem>() {
            @Override public String table() { return TABLE_SALE_ITEMS; }
            @Override public List<SaleItem> dirty(int limit) {
                return db.saleDao().listDirtyItemsForSync(limit); }
            @Override public Class<SaleItem> type() { return SaleItem.class; }
            @Override public SyncEntity findById(String id) {
                return db.saleDao().findItemById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.saleDao().markItemsSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(SaleItem entity) {
                db.saleDao().upsertItemFromRemote(entity); }
        });

        map.put(TABLE_PURCHASES, new TableHandler<Purchase>() {
            @Override public String table() { return TABLE_PURCHASES; }
            @Override public List<Purchase> dirty(int limit) {
                return db.purchaseDao().listDirtyForSync(limit); }
            @Override public Class<Purchase> type() { return Purchase.class; }
            @Override public SyncEntity findById(String id) {
                return db.purchaseDao().findById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.purchaseDao().markSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(Purchase entity) {
                db.purchaseDao().upsertFromRemote(entity); }
        });

        map.put(TABLE_PURCHASE_ITEMS, new TableHandler<PurchaseItem>() {
            @Override public String table() { return TABLE_PURCHASE_ITEMS; }
            @Override public List<PurchaseItem> dirty(int limit) {
                return db.purchaseDao().listDirtyItemsForSync(limit); }
            @Override public Class<PurchaseItem> type() { return PurchaseItem.class; }
            @Override public SyncEntity findById(String id) {
                return db.purchaseDao().findItemById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.purchaseDao().markItemsSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(PurchaseItem entity) {
                db.purchaseDao().upsertItemFromRemote(entity); }
        });

        map.put(TABLE_EXPENSES, new TableHandler<Expense>() {
            @Override public String table() { return TABLE_EXPENSES; }
            @Override public List<Expense> dirty(int limit) {
                return db.expenseDao().listDirtyForSync(limit); }
            @Override public Class<Expense> type() { return Expense.class; }
            @Override public SyncEntity findById(String id) {
                return db.expenseDao().findById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.expenseDao().markSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(Expense entity) {
                db.expenseDao().upsertFromRemote(entity); }
        });

        map.put(TABLE_STOCK_MOVEMENTS, new TableHandler<StockMovement>() {
            @Override public String table() { return TABLE_STOCK_MOVEMENTS; }
            @Override public List<StockMovement> dirty(int limit) {
                return ((StockMovementDao) db.stockMovementDao()).listDirtyForSync(limit); }
            @Override public Class<StockMovement> type() { return StockMovement.class; }
            @Override public SyncEntity findById(String id) {
                return db.stockMovementDao().findById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.stockMovementDao().markSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(StockMovement entity) {
                db.stockMovementDao().upsertFromRemote(entity); }
        });

        map.put(TABLE_ORDER_EVENTS, new TableHandler<OrderEvent>() {
            @Override public String table() { return TABLE_ORDER_EVENTS; }
            @Override public List<OrderEvent> dirty(int limit) {
                return db.orderEventDao().listDirtyForSync(limit); }
            @Override public Class<OrderEvent> type() { return OrderEvent.class; }
            @Override public SyncEntity findById(String id) {
                return db.orderEventDao().findById(id); }
            @Override public void mark(List<String> ids, int status, Long syncedAt,
                                       String error, int incRetry) {
                db.orderEventDao().markSyncStatus(ids, status, syncedAt, error, incRetry); }
            @Override public void upsertRemote(OrderEvent entity) {
                db.orderEventDao().upsertFromRemote(entity); }
        });

        return map;
    }

    // ---------------- 给设置页读取状态用的键名 ----------------

    public static String keyServerUrl() {
        return KEY_SERVER_URL;
    }

    public static String keyServerKey() {
        return KEY_SERVER_KEY;
    }

    public static String keyLastSyncAt() {
        return KEY_LAST_SYNC_AT;
    }

    public static String keyLastSyncError() {
        return KEY_LAST_SYNC_ERROR;
    }

    /** 保存同步配置（设置页调用；value 可为 null 表示清除） */
    public static void saveConfig(Context context, String key, String value) {
        SyncState state = new SyncState();
        state.key = key;
        state.value = value;
        state.updatedAt = System.currentTimeMillis();
        AppDatabase.getInstance(context).syncStateDao().put(state);
    }
}











