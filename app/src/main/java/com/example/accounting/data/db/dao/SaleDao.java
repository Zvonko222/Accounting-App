package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.db.entity.SaleItem;

import java.util.List;

/**
 * 只负责 sales / sale_items 两张表的读写（同一张单据的主子表）。
 */
@Dao
public interface SaleDao {

    /**
     * 流水列表（带商品摘要）：包含已作废的单（showVoided = 1 时），
     * 界面灰显并标"已作废"，让用户能看到"作废"这个动作本身。
     * 摘要 = GROUP_CONCAT(商品名×数量)，已作废的明细不计入。
     */
    @Query("SELECT s.*, "
            + "(SELECT COALESCE(GROUP_CONCAT(si.productName || '×' || printf('%g', si.quantityMilli / 1000.0)), '') "
            + "  FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemsSummary, "
            + "(SELECT COUNT(*) FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemCount, "
            + "COALESCE((SELECT oe.eventType FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventType, "
            + "COALESCE((SELECT oe.differenceCents FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventDifferenceCents "
            + "FROM sales s "
            + "WHERE s.saleTime BETWEEN :fromMillis AND :toMillis "
            + "AND (:showVoided = 1 OR s.isDeleted = 0) "
            + "ORDER BY s.saleTime DESC")
    LiveData<List<SaleWithSummary>> observeSummariesBetween(long fromMillis, long toMillis,
                                                            boolean showVoided);

    @Query("SELECT * FROM sales WHERE id = :id")
    Sale findById(String id);

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :id")
    SaleWithItems findWithItems(String id);

    /** 今日单数（首页卡片） */
    @Query("SELECT COUNT(*) FROM sales "
            + "WHERE isDeleted = 0 AND saleTime BETWEEN :fromMillis AND :toMillis")
    LiveData<Integer> observeCountBetween(long fromMillis, long toMillis);

    /** 期间销售额（首页/统计卡片）。COALESCE：无记录时 SUM 返回 NULL，当作 0 */
    @Query("SELECT COALESCE(SUM(totalAmountCents), 0) + "
            + "COALESCE((SELECT SUM(oe.differenceCents) FROM order_events oe "
            + "JOIN sales ss ON ss.id = oe.saleId WHERE ss.isDeleted = 0 "
            + "AND ss.saleTime BETWEEN :fromMillis AND :toMillis), 0) FROM sales "
            + "WHERE isDeleted = 0 AND saleTime BETWEEN :fromMillis AND :toMillis")
    LiveData<Long> observeTotalBetween(long fromMillis, long toMillis);

    /** 同步版销售额（桌面小组件用：RemoteViews 更新不在 LiveData 世界里） */
    @Query("SELECT COALESCE(SUM(totalAmountCents), 0) FROM sales "
            + "WHERE isDeleted = 0 AND saleTime BETWEEN :fromMillis AND :toMillis")
    long sumBetweenSync(long fromMillis, long toMillis);

    /** 同步版单数（桌面小组件用） */
    @Query("SELECT COUNT(*) FROM sales "
            + "WHERE isDeleted = 0 AND saleTime BETWEEN :fromMillis AND :toMillis")
    int countBetweenSync(long fromMillis, long toMillis);

    /** 作废时读出全部明细：给明细打墓碑、把库存冲回去 */
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    List<SaleItem> listItems(String saleId);

    @Update
    void updateItem(SaleItem item);

    @Insert
    void insert(Sale sale);

    @Insert
    void insertItems(List<SaleItem> items);

    @Update
    void update(Sale sale);

    @Query("SELECT * FROM sale_items WHERE id = :id")
    SaleItem findItemById(String id);

    // ---- 订单交付（订单页） ----

    /** 订单页共用的主表 + 商品摘要查询，避免列表只有空壳主表。 */
    // 查询定义见下方两个交付查询。

    /** 待交付订单（外卖/预订），新的在前 */
    @Query("SELECT s.*, " +
            "(SELECT COALESCE(GROUP_CONCAT(si.productName || '×' || printf('%g', si.quantityMilli / 1000.0)), '') " +
            " FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemsSummary, " +
             "(SELECT COUNT(*) FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemCount, " +
             "COALESCE((SELECT oe.eventType FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventType, " +
             "COALESCE((SELECT oe.differenceCents FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventDifferenceCents " +
            "FROM sales s WHERE s.deliveryStatus = 1 AND s.isDeleted = 0 ORDER BY s.saleTime DESC")
    LiveData<List<SaleWithSummary>> observePendingDelivery();

    /** 最近已交付（最多 50 条） */
    @Query("SELECT s.*, " +
            "(SELECT COALESCE(GROUP_CONCAT(si.productName || '×' || printf('%g', si.quantityMilli / 1000.0)), '') " +
            " FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemsSummary, " +
             "(SELECT COUNT(*) FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemCount, " +
             "COALESCE((SELECT oe.eventType FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventType, " +
             "COALESCE((SELECT oe.differenceCents FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventDifferenceCents " +
            "FROM sales s WHERE s.deliveryStatus = 2 AND s.isDeleted = 0 ORDER BY deliveredAt DESC LIMIT 50")
    LiveData<List<SaleWithSummary>> observeDeliveredRecently();

    @Query("SELECT s.*, " +
            "(SELECT COALESCE(GROUP_CONCAT(si.productName || '×' || printf('%g', si.quantityMilli / 1000.0)), '') " +
            " FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemsSummary, " +
            "(SELECT COUNT(*) FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemCount, " +
            "COALESCE((SELECT oe.eventType FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventType, " +
            "COALESCE((SELECT oe.differenceCents FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventDifferenceCents " +
            "FROM sales s WHERE s.deliveryStatus = 1 AND s.isDeleted = 0 " +
            "AND s.saleTime BETWEEN :fromMillis AND :toMillis ORDER BY s.saleTime DESC")
    LiveData<List<SaleWithSummary>> observePendingDeliveryBetween(long fromMillis, long toMillis);

    @Query("SELECT s.*, " +
            "(SELECT COALESCE(GROUP_CONCAT(si.productName || '×' || printf('%g', si.quantityMilli / 1000.0)), '') " +
            " FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemsSummary, " +
            "(SELECT COUNT(*) FROM sale_items si WHERE si.saleId = s.id AND si.isDeleted = 0) AS itemCount, " +
            "COALESCE((SELECT oe.eventType FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventType, " +
            "COALESCE((SELECT oe.differenceCents FROM order_events oe WHERE oe.saleId = s.id ORDER BY oe.eventTime DESC LIMIT 1), 0) AS latestEventDifferenceCents " +
            "FROM sales s WHERE s.deliveryStatus = 2 AND s.isDeleted = 0 " +
            "AND s.saleTime BETWEEN :fromMillis AND :toMillis ORDER BY deliveredAt DESC LIMIT 50")
    LiveData<List<SaleWithSummary>> observeDeliveredRecentlyBetween(long fromMillis, long toMillis);

    /** 确认交付：状态置已交付 + 记交付时间 + 标记待同步（交付状态也要上云） */
    @Query("UPDATE sales SET deliveryStatus = 2, deliveredAt = :deliveredAt, "
            + "updatedAt = :deliveredAt, syncStatus = 1 WHERE id = :id")
    void markDelivered(String id, long deliveredAt);

    // ---- 同步子系统专用（syncStatus: 1=PENDING 3=FAILED；主表与明细都要同步） ----

    @Query("SELECT * FROM sales WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<Sale> listDirtyForSync(int limit);

    @Query("UPDATE sales SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertFromRemote(Sale sale);

    @Query("SELECT * FROM sale_items WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<SaleItem> listDirtyItemsForSync(int limit);

    @Query("UPDATE sale_items SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markItemsSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertItemFromRemote(SaleItem item);
}


