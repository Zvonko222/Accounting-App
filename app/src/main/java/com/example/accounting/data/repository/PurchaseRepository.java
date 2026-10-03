package com.example.accounting.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.ProductDao;
import com.example.accounting.data.db.dao.PurchaseDao;
import com.example.accounting.data.db.dao.PurchaseWithItems;
import com.example.accounting.data.db.dao.PurchaseWithSummary;
import com.example.accounting.data.db.dao.StockMovementDao;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.db.entity.Purchase;
import com.example.accounting.data.db.entity.PurchaseItem;
import com.example.accounting.data.db.entity.StockMovement;
import com.example.accounting.data.model.PurchaseCartLine;
import com.example.accounting.util.SaleCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 进货相关数据的唯一入口。与 SaleRepository 结构对称：
 * 进货单 + 明细 + 库存增加 + 台账，四类写入同一个事务。
 * 另外进货会把商品的"最近进价"更新为本单进价（毛利估算的成本快照来源）。
 */
public class PurchaseRepository {

    private final AppDatabase database;
    private final PurchaseDao purchaseDao;
    private final ProductDao productDao;
    private final StockMovementDao stockMovementDao;
    private final ExecutorService writeExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PurchaseRepository(AppDatabase database, ExecutorService writeExecutor) {
        this.database = database;
        this.purchaseDao = database.purchaseDao();
        this.productDao = database.productDao();
        this.stockMovementDao = database.stockMovementDao();
        this.writeExecutor = writeExecutor;
    }

    // ---------------- 查询 ----------------

    /** 进货流水列表（带商品摘要），时间范围与"显示已作废"由调用方传入 */
    public LiveData<List<PurchaseWithSummary>> observePurchaseSummaries(
            long fromMillis, long toMillis, boolean showVoided) {
        return purchaseDao.observeSummariesBetween(fromMillis, toMillis, showVoided);
    }

    public LiveData<Long> observeTotalBetween(long fromMillis, long toMillis) {
        return purchaseDao.observeTotalBetween(fromMillis, toMillis);
    }

    public void loadPurchaseDetail(String purchaseId, DetailCallback callback) {
        writeExecutor.execute(() -> {
            PurchaseWithItems detail = purchaseDao.findWithItems(purchaseId);
            mainHandler.post(() -> callback.onLoaded(detail));
        });
    }

    public interface DetailCallback {
        void onLoaded(PurchaseWithItems detail);
    }

    // ---------------- 写入 ----------------

    /**
     * 记一笔进货。事务内：
     * 1. 进货主表（合计在此算定）
     * 2. 各行明细（进价/名称快照）
     * 3. 各商品库存增加 + 最近进价更新为本单进价
     * 4. 库存台账（PURCHASE 类型，正数）
     */
    public void recordPurchase(List<PurchaseCartLine> lines, String supplierName,
                               String note, long recordTimeMillis, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() ->
                        insertPurchaseLocked(lines, supplierName, note, recordTimeMillis));
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 拍照导入：把识别确认过的行保存为一张进货单。
     *
     * 与 recordPurchase 的区别是支持"自动建档"：productId 为 null 的行
     * 在事务内先按品名查找已有商品，找不到就创建一个新商品
     * （进价取本单进价、售价 0 待补录、库存 0 随本单进货增加）。
     * 建档和进货写在同一个事务里：中途任何一步失败整体回滚，
     * 不会出现"建了商品但没有进货记录"的半截数据。
     */
    public void recordPurchaseWithNewProducts(List<PurchaseCartLine> lines,
                                              String supplierName, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    if (lines == null || lines.isEmpty()) {
                        throw new IllegalArgumentException("没有可导入的行");
                    }
                    for (PurchaseCartLine line : lines) {
                        Product product = line.productId != null
                                ? productDao.findById(line.productId)
                                : productDao.findByName(line.productName);
                        if (product == null) {
                            product = new Product();
                            product.id = UUID.randomUUID().toString();
                            product.initTimestamps();
                            product.name = line.productName;
                            product.purchasePriceCents = line.unitCostCents;
                            product.salePriceCents = 0; // 售价待用户补录
                            product.unit = "个";
                            productDao.insert(product);
                        }
                        line.productId = product.id;
                    }
                    insertPurchaseLocked(lines, supplierName, "拍照导入",
                            System.currentTimeMillis());
                });
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 修改一笔已保存的进货（改供应商/数量/进价等）。
     * 与 editSale 同一设计：账本不可涂改，"修改"= 同一事务内
     * 【作废旧单（库存冲减、台账 VOID_PURCHASE）+ 按新内容重开一张新单】。
     */
    public void editPurchase(String purchaseId, List<PurchaseCartLine> lines,
                             String supplierName, long recordTimeMillis, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    voidPurchaseLocked(purchaseId);
                    insertPurchaseLocked(lines, supplierName, null, recordTimeMillis);
                });
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 作废进货：冲减库存（可能把库存打成负数——货已经卖出去了又退给供应商，
     * 属于正常账面现象，界面红色提示盘点）。
     */
    public void voidPurchase(String purchaseId, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> voidPurchaseLocked(purchaseId));
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    // ---------------- 事务内部步骤（必须在 runInTransaction 里调用） ----------------

    /** 插入一张进货单及其全部影响。调用方必须已处于事务中 */
    private void insertPurchaseLocked(List<PurchaseCartLine> lines, String supplierName,
                                      String note, long recordTimeMillis) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("本单没有任何商品");
        }
        // 不允许把账记到未来
        long safeRecordTime = Math.min(recordTimeMillis, System.currentTimeMillis());

        long sumLineTotals = 0;
        for (PurchaseCartLine line : lines) {
            if (line.quantityMilli <= 0) {
                throw new IllegalArgumentException(
                        "「" + line.productName + "」数量必须大于 0");
            }
            if (line.unitCostCents < 0) {
                throw new IllegalArgumentException(
                        "「" + line.productName + "」进价不能为负");
            }
            sumLineTotals += line.lineTotalCents();
        }

        long now = System.currentTimeMillis();

        Purchase purchase = new Purchase();
        purchase.id = UUID.randomUUID().toString();
        purchase.initTimestamps();
        purchase.purchaseTime = safeRecordTime;
        purchase.totalAmountCents = sumLineTotals;
        purchase.supplierName = normalizeText(supplierName);
        purchase.note = normalizeText(note);
        purchaseDao.insert(purchase);

        List<PurchaseItem> purchaseItems = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>();

        for (PurchaseCartLine line : lines) {
            Product product = productDao.findById(line.productId);
            if (product == null) {
                throw new IllegalStateException(
                        "商品不存在或已停用：" + line.productName);
            }

            PurchaseItem item = new PurchaseItem();
            item.id = UUID.randomUUID().toString();
            item.initTimestamps();
            item.purchaseId = purchase.id;
            item.productId = product.id;
            item.productName = line.productName;
            item.unitCostCents = line.unitCostCents;
            item.quantityMilli = line.quantityMilli;
            item.lineTotalCents = line.lineTotalCents();
            purchaseItems.add(item);

            product.stockQuantityMilli += line.quantityMilli;
            product.purchasePriceCents = line.unitCostCents;
            product.markPending();
            productDao.update(product);

            StockMovement movement = new StockMovement();
            movement.id = UUID.randomUUID().toString();
            movement.initTimestamps();
            movement.productId = product.id;
            movement.changeType = StockMovement.TYPE_PURCHASE;
            movement.changeQuantityMilli = line.quantityMilli;
            movement.relatedPurchaseId = purchase.id;
            movement.movementTime = now;
            movements.add(movement);
        }

        purchaseDao.insertItems(purchaseItems);
        stockMovementDao.insertAll(movements);
    }

    /** 作废一张进货单及其全部库存影响。调用方必须已处于事务中 */
    private void voidPurchaseLocked(String purchaseId) {
        Purchase purchase = purchaseDao.findById(purchaseId);
        if (purchase == null) {
            throw new IllegalStateException("进货单不存在");
        }
        if (purchase.isDeleted) {
            throw new IllegalStateException("该单已作废，不能重复操作");
        }

        long now = System.currentTimeMillis();
        purchase.markDeleted();
        purchaseDao.update(purchase);

        List<PurchaseItem> items = purchaseDao.listItems(purchaseId);
        List<StockMovement> movements = new ArrayList<>();
        for (PurchaseItem item : items) {
            item.markDeleted();
            purchaseDao.updateItem(item);

            Product product = productDao.findById(item.productId);
            if (product != null) {
                product.stockQuantityMilli -= item.quantityMilli;
                product.markPending();
                productDao.update(product);

                StockMovement movement = new StockMovement();
                movement.id = UUID.randomUUID().toString();
                movement.initTimestamps();
                movement.productId = product.id;
                movement.changeType = StockMovement.TYPE_VOID_PURCHASE;
                movement.changeQuantityMilli = -item.quantityMilli;
                movement.relatedPurchaseId = purchase.id;
                movement.movementTime = now;
                movement.note = "作废进货冲回";
                movements.add(movement);
            }
        }
        stockMovementDao.insertAll(movements);
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
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
