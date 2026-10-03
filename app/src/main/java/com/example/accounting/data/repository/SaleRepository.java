package com.example.accounting.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.ProductDao;
import com.example.accounting.data.db.dao.SaleDao;
import com.example.accounting.data.db.dao.SaleWithItems;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.data.db.dao.StatisticsDao;
import com.example.accounting.data.db.dao.StockMovementDao;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.db.entity.SaleItem;
import com.example.accounting.data.db.entity.StockMovement;
import com.example.accounting.data.model.SaleCartLine;
import com.example.accounting.util.SaleCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 销售相关数据的唯一入口。
 *
 * recordSale / voidSale 是全 App 最关键的两个事务（ARCHITECTURE.md 第 3 节）：
 * 单据主表、明细、商品库存、库存台账四类写入必须同生同死——
 * 中途崩溃时整体回滚，绝不允许"账记了但库存没扣"的中间状态。
 */
public class SaleRepository {

    public static final long NO_DISCOUNT = 0;

    private final AppDatabase database;
    private final SaleDao saleDao;
    private final ProductDao productDao;
    private final StockMovementDao stockMovementDao;
    private final StatisticsDao statisticsDao;
    private final ExecutorService writeExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SaleRepository(AppDatabase database, ExecutorService writeExecutor) {
        this.database = database;
        this.saleDao = database.saleDao();
        this.productDao = database.productDao();
        this.stockMovementDao = database.stockMovementDao();
        this.statisticsDao = database.statisticsDao();
        this.writeExecutor = writeExecutor;
    }

    // ---------------- 查询 ----------------

    /**
     * 流水列表（带商品摘要）：含已作废单的显示由 showVoided 控制。
     * Room 的 LiveData 换查询参数会自动重新执行并推送。
     */
    public LiveData<List<SaleWithSummary>> observeSaleSummaries(long fromMillis, long toMillis,
                                                                 boolean showVoided) {
        return saleDao.observeSummariesBetween(fromMillis, toMillis, showVoided);
    }

    public LiveData<Integer> observeCountBetween(long fromMillis, long toMillis) {
        return saleDao.observeCountBetween(fromMillis, toMillis);
    }

    public LiveData<Long> observeTotalBetween(long fromMillis, long toMillis) {
        return saleDao.observeTotalBetween(fromMillis, toMillis);
    }

    /** 毛利估算（首页/统计用） */
    public LiveData<Long> observeGrossProfitBetween(long fromMillis, long toMillis) {
        return statisticsDao.observeGrossProfitBetween(fromMillis, toMillis);
    }

    /** 销售详情（主表 + 明细），点击列表项时一次性加载 */
    public void loadSaleDetail(String saleId, DetailCallback callback) {
        writeExecutor.execute(() -> {
            SaleWithItems detail = saleDao.findWithItems(saleId);
            mainHandler.post(() -> callback.onLoaded(detail));
        });
    }

    /** 详情加载回调 */
    public interface DetailCallback {
        void onLoaded(SaleWithItems detail);
    }

    // ---------------- 写入 ----------------

    /**
     * 记一笔销售。事务内的写入顺序（对应 ARCHITECTURE.md 第 3 节）：
     * 1. 销售主表（合计金额在此算定，之后永不重算）
     * 2. 各行明细（价格/名称/成本快照）
     * 3. 各商品库存扣减
     * 4. 库存台账（SALE 类型，负数）
     *
     * recordTimeMillis：业务发生时间。默认是当下；补录昨天的单时传过去的时间
     * （统计按它算），不允许未来——超过当前时间会被钳制到现在。
     */
    public void recordSale(List<SaleCartLine> lines, long discountCents,
                           int payMethod, String note, long recordTimeMillis,
                           SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() ->
                        insertSaleLocked(lines, discountCents, payMethod, note, recordTimeMillis));
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 修改一笔已保存的销售。
     *
     * 设计：账本不可涂改——已保存的单据内容永不 UPDATE。
     * "修改"= 同一个事务里【作废旧单 + 按新内容重开一张新单】：
     * 旧单在流水里留下"已作废"的完整轨迹，新单的快照/合计/库存台账
     * 全部重新生成，"当前库存 = 期初 + Σ台账变动"的恒等式依然成立。
     */
    public void editSale(String saleId, List<SaleCartLine> lines, long discountCents,
                         int payMethod, long recordTimeMillis, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    voidSaleLocked(saleId);
                    insertSaleLocked(lines, discountCents, payMethod, null, recordTimeMillis);
                });
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 作废销售：账本不可涂改，错单的处理方式是"作废"而不是"修改"。
     * 事务内：单据与明细打软删标记（墓碑）+ 库存冲回 + 台账记 VOID_SALE。
     */
    public void voidSale(String saleId, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> voidSaleLocked(saleId));
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    // ---------------- 事务内部步骤（必须在 runInTransaction 里调用） ----------------

    /** 插入一张销售单及其全部影响。调用方必须已处于事务中 */
    private void insertSaleLocked(List<SaleCartLine> lines, long discountCents,
                                  int payMethod, String note, long recordTimeMillis) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("本单没有任何商品");
        }
        // 不允许把账记到未来：手滑选了明天也按现在算
        long safeRecordTime = Math.min(recordTimeMillis, System.currentTimeMillis());

        long sumLineTotals = 0;
        for (SaleCartLine line : lines) {
            if (line.quantityMilli <= 0) {
                throw new IllegalArgumentException(
                        "「" + line.productName + "」数量必须大于 0");
            }
            sumLineTotals += line.lineTotalCents();
        }

        long now = System.currentTimeMillis();

        // 1. 主表
        Sale sale = new Sale();
        sale.id = UUID.randomUUID().toString();
        sale.initTimestamps();
        sale.saleTime = safeRecordTime;
        sale.discountCents = Math.max(discountCents, 0);
        sale.totalAmountCents =
                SaleCalculator.orderTotalCents(sumLineTotals, sale.discountCents);
        sale.payMethod = payMethod;
        sale.note = normalizeNote(note);
        saleDao.insert(sale);

        List<SaleItem> saleItems = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>();

        for (SaleCartLine line : lines) {
            Product product = productDao.findById(line.productId);
            if (product == null) {
                throw new IllegalStateException(
                        "商品不存在或已停用：" + line.productName);
            }

            // 2. 明细（全部用快照，不回读商品表）
            SaleItem item = new SaleItem();
            item.id = UUID.randomUUID().toString();
            item.initTimestamps();
            item.saleId = sale.id;
            item.productId = product.id;
            item.productName = line.productName;
            item.unitPriceCents = line.unitPriceCents;
            item.unitCostCents = product.purchasePriceCents;
            item.quantityMilli = line.quantityMilli;
            item.lineTotalCents = line.lineTotalCents();
            saleItems.add(item);

            // 3. 扣库存
            product.stockQuantityMilli -= line.quantityMilli;
            product.markPending();
            productDao.update(product);

            // 4. 记台账
            StockMovement movement = new StockMovement();
            movement.id = UUID.randomUUID().toString();
            movement.initTimestamps();
            movement.productId = product.id;
            movement.changeType = StockMovement.TYPE_SALE;
            movement.changeQuantityMilli = -line.quantityMilli;
            movement.relatedSaleId = sale.id;
            movement.movementTime = now;
            movements.add(movement);
        }

        saleDao.insertItems(saleItems);
        stockMovementDao.insertAll(movements);
    }

    /** 作废一张销售单及其全部库存影响。调用方必须已处于事务中 */
    private void voidSaleLocked(String saleId) {
        Sale sale = saleDao.findById(saleId);
        if (sale == null) {
            throw new IllegalStateException("销售单不存在");
        }
        if (sale.isDeleted) {
            throw new IllegalStateException("该单已作废，不能重复操作");
        }

        long now = System.currentTimeMillis();
        sale.markDeleted();
        saleDao.update(sale);

        List<SaleItem> items = saleDao.listItems(saleId);
        List<StockMovement> movements = new ArrayList<>();
        for (SaleItem item : items) {
            item.markDeleted();
            saleDao.updateItem(item);

            // 商品可能已被停用，但 findById 不过滤软删除，仍能冲回库存
            Product product = productDao.findById(item.productId);
            if (product != null) {
                product.stockQuantityMilli += item.quantityMilli;
                product.markPending();
                productDao.update(product);

                StockMovement movement = new StockMovement();
                movement.id = UUID.randomUUID().toString();
                movement.initTimestamps();
                movement.productId = product.id;
                movement.changeType = StockMovement.TYPE_VOID_SALE;
                movement.changeQuantityMilli = item.quantityMilli;
                movement.relatedSaleId = sale.id;
                movement.movementTime = now;
                movement.note = "作废销售冲回";
                movements.add(movement);
            }
        }
        stockMovementDao.insertAll(movements);
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void notifySuccess(SaveCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(SaveCallback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }
}
