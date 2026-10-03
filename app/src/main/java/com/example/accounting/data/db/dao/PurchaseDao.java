package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.Purchase;
import com.example.accounting.data.db.entity.PurchaseItem;

import java.util.List;

/**
 * 只负责 purchases / purchase_items 两张表的读写。
 */
@Dao
public interface PurchaseDao {

    /** 进货流水列表（带商品摘要），规则与销售列表相同 */
    @Query("SELECT p.*, "
            + "(SELECT COALESCE(GROUP_CONCAT(pi.productName || '×' || printf('%g', pi.quantityMilli / 1000.0)), '') "
            + "  FROM purchase_items pi WHERE pi.purchaseId = p.id AND pi.isDeleted = 0) AS itemsSummary, "
            + "(SELECT COUNT(*) FROM purchase_items pi WHERE pi.purchaseId = p.id AND pi.isDeleted = 0) AS itemCount "
            + "FROM purchases p "
            + "WHERE p.purchaseTime BETWEEN :fromMillis AND :toMillis "
            + "AND (:showVoided = 1 OR p.isDeleted = 0) "
            + "ORDER BY p.purchaseTime DESC")
    LiveData<List<PurchaseWithSummary>> observeSummariesBetween(long fromMillis, long toMillis,
                                                                boolean showVoided);

    @Query("SELECT * FROM purchases WHERE id = :id")
    Purchase findById(String id);

    @Transaction
    @Query("SELECT * FROM purchases WHERE id = :id")
    PurchaseWithItems findWithItems(String id);

    /** 期间进货支出（统计卡片） */
    @Query("SELECT COALESCE(SUM(totalAmountCents), 0) FROM purchases "
            + "WHERE isDeleted = 0 AND purchaseTime BETWEEN :fromMillis AND :toMillis")
    LiveData<Long> observeTotalBetween(long fromMillis, long toMillis);

    /** 作废时读出全部明细 */
    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    List<PurchaseItem> listItems(String purchaseId);

    @Update
    void updateItem(PurchaseItem item);

    @Insert
    void insert(Purchase purchase);

    @Insert
    void insertItems(List<PurchaseItem> items);

    @Update
    void update(Purchase purchase);

    @Query("SELECT * FROM purchase_items WHERE id = :id")
    PurchaseItem findItemById(String id);

    // ---- 同步子系统专用（syncStatus: 1=PENDING 3=FAILED） ----

    @Query("SELECT * FROM purchases WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<Purchase> listDirtyForSync(int limit);

    @Query("UPDATE purchases SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertFromRemote(Purchase purchase);

    @Query("SELECT * FROM purchase_items WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<PurchaseItem> listDirtyItemsForSync(int limit);

    @Query("UPDATE purchase_items SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markItemsSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertItemFromRemote(PurchaseItem item);
}
