package com.example.accounting.ui.settings;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.repository.CategoryRepository;
import com.example.accounting.data.repository.backup.BackupManager;

/**
 * 设置页状态：把 BackupManager 的四个操作委托出来，外加"上次备份时间"。
 * 备份属于低频操作，用回调比 LiveData 简单，不必为此引入更多状态。
 */
public class SettingsViewModel extends AndroidViewModel {

    private final BackupManager backupManager;

    /** 上次本机备份时间（毫秒），null = 从未备份过 */
    private final MutableLiveData<Long> lastBackupTime = new MutableLiveData<>();
    private final MutableLiveData<String> backupSummary = new MutableLiveData<>();

    public SettingsViewModel(Application app) {
        super(app);
        backupManager = ((AccountingApp) app).getBackupManager();
    }

    public MutableLiveData<String> getBackupSummary() {
        return backupSummary;
    }

    public MutableLiveData<Long> getLastBackupTime() {
        return lastBackupTime;
    }

    /** 每次进设置页都重新读一次（可能刚被 Worker 备份过） */
    public void reloadLastBackupTime() {
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            Long time = backupManager.getLastBackupTime();
            lastBackupTime.postValue(time);
        });
    }

    public void reloadBackupSummary() {
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() ->
                backupSummary.postValue(backupManager.getLocalBackupSummary()));
    }

    /** 立即备份：委托 BackupManager（回调已在主线程），成功后刷新"上次备份"时间 */
    public void backupNow(BackupManager.BackupCallback callback) {
        backupManager.backupNow(new BackupManager.BackupCallback() {
            @Override
            public void onDone(String message) {
                lastBackupTime.setValue(System.currentTimeMillis());
                reloadBackupSummary();
                callback.onDone(message);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void exportBackup(Uri destination, BackupManager.BackupCallback callback) {
        backupManager.exportBackupTo(destination, callback);
    }

    public void restoreBackup(Uri source, BackupManager.BackupCallback callback) {
        backupManager.importBackupFrom(source, callback);
    }

    public void exportSalesCsv(Uri destination, BackupManager.BackupCallback callback) {
        backupManager.exportSalesCsvTo(destination, callback);
    }

    /** 导出利润表：后台执行（区间由界面按用户所选决定），完成后记录"上次导出"时间 */
    public void exportProfitReport(Uri destination, long fromMillis, String rangeLabel,
                                   BackupManager.BackupCallback callback) {
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            try {
                int rows = com.example.accounting.data.repository.report.ProfitReportExporter
                        .export(getDatabase(), destination, getApplication(),
                                fromMillis, rangeLabel);
                com.example.accounting.data.sync.SyncEngine.saveConfig(
                        getApplication(), KEY_LAST_EXPORT, String.valueOf(System.currentTimeMillis()));
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onDone("共 " + rows + " 天"));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "导出失败" : e.getMessage();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onError(message));
            }
        });
    }

    /** 导出商品库存表（盘点用） */
    public void exportInventory(Uri destination, BackupManager.BackupCallback callback) {
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            try {
                int count = com.example.accounting.data.repository.report.InventoryExporter
                        .export(getDatabase(), destination, getApplication());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onDone("共 " + count + " 种商品"));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "导出失败" : e.getMessage();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onError(message));
            }
        });
    }

    public long getLastExportTime() {
        String v = com.example.accounting.data.sync.SyncEngine
                .getConfigStatic(getApplication(), KEY_LAST_EXPORT);
        if (v == null) return 0;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------- 分类管理 ----------------

    private CategoryRepository getCategoryRepository() {
        return ((AccountingApp) getApplication()).getCategoryRepository();
    }

    /** 分类列表（ LiveData，增删后自动刷新） */
    public androidx.lifecycle.LiveData<java.util.List<com.example.accounting.data.db.entity.Category>>
    getCategories() {
        return getCategoryRepository().observeCategories();
    }

    public void addCategory(String name, com.example.accounting.data.repository.SaveCallback callback) {
        getCategoryRepository().addCategory(name, callback);
    }

    public void disableCategory(String categoryId,
                                com.example.accounting.data.repository.SaveCallback callback) {
        getCategoryRepository().disableCategory(categoryId, callback);
    }

    private com.example.accounting.data.db.AppDatabase getDatabase() {
        return ((AccountingApp) getApplication()).getDatabase();
    }

    // ---------------- 云同步 ----------------

    private static final String KEY_URL = "server_url";
    private static final String KEY_KEY = "server_key";
    private static final String KEY_LAST_EXPORT = "last_export_at";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_LAST_SYNC_ERROR = "last_sync_error";

    /** 待上行行数（8 张表 dirty 之和），后台线程查询后 post */
    private final MutableLiveData<Integer> pendingCount = new MutableLiveData<>();

    /** 上次同步时间 / 错误 */
    private final MutableLiveData<Long> lastSyncAt = new MutableLiveData<>();
    private final MutableLiveData<String> lastSyncError = new MutableLiveData<>();

    public androidx.lifecycle.LiveData<Integer> getPendingCount() {
        return pendingCount;
    }

    public androidx.lifecycle.LiveData<Long> getLastSyncAt() {
        return lastSyncAt;
    }

    public androidx.lifecycle.LiveData<String> getLastSyncError() {
        return lastSyncError;
    }

    /** 已保存的同步配置（回填输入框用） */
    private final MutableLiveData<String> configUrl = new MutableLiveData<>();
    private final MutableLiveData<String> configKey = new MutableLiveData<>();

    public androidx.lifecycle.LiveData<String> getConfigUrl() {
        return configUrl;
    }

    public androidx.lifecycle.LiveData<String> getConfigKey() {
        return configKey;
    }

    /** 读出同步配置与状态（进设置页时调用一次） */
    public void loadSyncConfig(android.content.Context context) {
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            String url = com.example.accounting.data.sync.SyncEngine
                    .getConfigStatic(context, KEY_URL);
            String key = com.example.accounting.data.sync.SyncEngine
                    .getConfigStatic(context, KEY_KEY);
            configUrl.postValue(url == null ? "" : url);
            configKey.postValue(key == null ? "" : key);
        });
        refreshSyncStatus(context);
    }

    public void saveSyncConfig(Context context, String url, String key) {
        // Room 禁止主线程写库：配置写入排到写线程（主线程写库是实测踩过的崩溃）
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            com.example.accounting.data.sync.SyncEngine.saveConfig(context, KEY_URL, url);
            com.example.accounting.data.sync.SyncEngine.saveConfig(context, KEY_KEY, key);
        });
    }

    public void syncNow(Context context,
                        com.example.accounting.data.sync.SyncEngine.Callback callback) {
        com.example.accounting.data.sync.SyncEngine.syncNow(context, new com.example.accounting.data.sync.SyncEngine.Callback() {
            @Override
            public void onDone(String message) {
                refreshSyncStatus(context);
                callback.onDone(message);
            }

            @Override
            public void onError(String message) {
                lastSyncError.postValue(message);
                refreshSyncStatus(context);
                callback.onError(message);
            }
        });
    }

    /** 各表待同步行数明细（同步状态详情弹窗用），后台查询后 post */
    public void loadPendingDetail(android.content.Context context) {
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            com.example.accounting.data.db.AppDatabase db =
                    ((AccountingApp) getApplication()).getDatabase();
            StringBuilder detail = new StringBuilder();
            addRow(detail, "商品", db.productDao().listDirtyForSync(Integer.MAX_VALUE).size());
            addRow(detail, "分类", db.categoryDao().listDirtyForSync(Integer.MAX_VALUE).size());
            addRow(detail, "销售单", db.saleDao().listDirtyForSync(Integer.MAX_VALUE).size());
            addRow(detail, "销售明细", db.saleDao().listDirtyItemsForSync(Integer.MAX_VALUE).size());
            addRow(detail, "进货单", db.purchaseDao().listDirtyForSync(Integer.MAX_VALUE).size());
            addRow(detail, "进货明细", db.purchaseDao().listDirtyItemsForSync(Integer.MAX_VALUE).size());
            addRow(detail, "支出", db.expenseDao().listDirtyForSync(Integer.MAX_VALUE).size());
            addRow(detail, "库存台账", db.stockMovementDao().listDirtyForSync(Integer.MAX_VALUE).size());
            addRow(detail, "订单事件", db.orderEventDao().listDirtyForSync(Integer.MAX_VALUE).size());
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    pendingDetail.postValue(detail.toString()));
        });
    }

    private void addRow(StringBuilder sb, String name, int count) {
        sb.append(name).append("：").append(count).append(" 行\n");
    }

    public androidx.lifecycle.LiveData<String> getPendingDetail() {
        return pendingDetail;
    }

    private final androidx.lifecycle.MutableLiveData<String> pendingDetail =
            new androidx.lifecycle.MutableLiveData<>("");

    /** 汇总同步状态：待上行行数 + 上次同步时间/错误 */
    public void refreshSyncStatus(android.content.Context context) {
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            com.example.accounting.data.db.AppDatabase db =
                    ((AccountingApp) getApplication()).getDatabase();
            int pending = db.categoryDao().listDirtyForSync(Integer.MAX_VALUE).size()
                    + db.productDao().listDirtyForSync(Integer.MAX_VALUE).size()
                    + db.saleDao().listDirtyForSync(Integer.MAX_VALUE).size()
                    + db.saleDao().listDirtyItemsForSync(Integer.MAX_VALUE).size()
                    + db.purchaseDao().listDirtyForSync(Integer.MAX_VALUE).size()
                    + db.purchaseDao().listDirtyItemsForSync(Integer.MAX_VALUE).size()
                    + db.expenseDao().listDirtyForSync(Integer.MAX_VALUE).size()
                    + db.stockMovementDao().listDirtyForSync(Integer.MAX_VALUE).size()
                    + db.orderEventDao().listDirtyForSync(Integer.MAX_VALUE).size();
            pendingCount.postValue(pending);

            String lastAt = com.example.accounting.data.sync.SyncEngine
                    .getConfigStatic(context, KEY_LAST_SYNC_AT);
            if (lastAt != null) {
                try {
                    lastSyncAt.postValue(Long.parseLong(lastAt));
                } catch (NumberFormatException ignored) {
                }
            }
            lastSyncError.postValue(com.example.accounting.data.sync.SyncEngine
                    .getConfigStatic(context, KEY_LAST_SYNC_ERROR));
        });
    }
}




