package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.example.accounting.data.model.DailySales;
import com.example.accounting.data.model.TopProduct;

import java.util.List;

/**
 * 统计页专用：全部是聚合查询（GROUP BY），返回 POJO 而不是实体。
 * 小商户一年几千条流水，这些查询毫秒级完成，不需要预汇总表。
 */
@Dao
public interface StatisticsDao {

    /**
     * 近 N 天每日销售额。
     * strftime(...,'localtime') 把毫秒时间戳换算成本地时区的"日历日"再分组，
     * 否则按 UTC 分组会把一天的账切成两天。
     */
    @Query("SELECT strftime('%Y-%m-%d', saleTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "COALESCE(SUM(totalAmountCents), 0) AS totalCents, "
            + "COUNT(*) AS saleCount "
            + "FROM sales "
            + "WHERE isDeleted = 0 AND saleTime >= :fromMillis "
            + "GROUP BY day ORDER BY day")
    LiveData<List<DailySales>> observeDailySalesSince(long fromMillis);

    /**
     * 热销商品前 5（按销售额倒序）。
     * JOIN sales 过滤已作废单的明细——作废单的销售额不应计入热销。
     */
    /** 热销榜（销售额扣减退货冲减的按行比例分摊，口径与分类榜一致） */
    @Query("SELECT si.productName AS productName, "
            + "COALESCE(SUM(si.quantityMilli + COALESCE("
            + "(SELECT SUM(CASE WHEN oe.originalProductId IS NULL OR oe.originalProductId = '' "
            + "  THEN CAST(ROUND(si.quantityMilli * 1.0 * oe.differenceCents "
            + "    / s.totalAmountCents) AS INTEGER) "
            + "  ELSE CAST(ROUND(oe.quantityMilli * 1.0) AS INTEGER) END) "
            + "  FROM order_events oe "
            + "  WHERE oe.saleId = s.id AND oe.eventType = 1 "
            + "  AND s.totalAmountCents > 0 "
            + "  AND (oe.originalProductId IS NULL OR oe.originalProductId = '' "
            + "    OR oe.originalProductId = si.productId)), 0)), 0) AS quantityMilli, "
            + "COALESCE(SUM(si.lineTotalCents + COALESCE("
            + "(SELECT SUM(CASE WHEN oe.originalProductId IS NULL OR oe.originalProductId = '' "
            + "  THEN CAST(ROUND(si.lineTotalCents * 1.0 * oe.differenceCents "
            + "    / s.totalAmountCents) AS INTEGER) "
            + "  ELSE CAST(ROUND(oe.differenceCents * 1.0) AS INTEGER) END) "
            + "  FROM order_events oe "
            + "  WHERE oe.saleId = s.id AND oe.eventType = 1 "
            + "  AND s.totalAmountCents > 0 "
            + "  AND (oe.originalProductId IS NULL OR oe.originalProductId = '' "
            + "    OR oe.originalProductId = si.productId)), 0)), 0) AS totalCents "
            + "FROM sale_items si JOIN sales s ON si.saleId = s.id "
            + "WHERE s.isDeleted = 0 AND s.saleTime >= :fromMillis "
            + "GROUP BY si.productName "
            + "ORDER BY totalCents DESC LIMIT 5")
    LiveData<List<TopProduct>> observeTopProductsSince(long fromMillis);

    /** 日毛利（快照成本口径），按本地日历日分组 */
    @Query("SELECT strftime('%Y-%m-%d', s.saleTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "COALESCE(SUM(si.lineTotalCents - si.unitCostCents * si.quantityMilli / 1000), 0) AS totalCents, "
            + "COUNT(*) AS saleCount "
            + "FROM sale_items si JOIN sales s ON si.saleId = s.id "
            + "WHERE s.isDeleted = 0 AND s.saleTime >= :fromMillis "
            + "GROUP BY day ORDER BY day")
    LiveData<List<DailySales>> observeDailyProfitSince(long fromMillis);

    /** 日支出 = 进货 + 其他支出（UNION 后按日汇总） */
    @Query("SELECT day, COALESCE(SUM(amount), 0) AS totalCents, 0 AS saleCount FROM ("
            + "SELECT strftime('%Y-%m-%d', purchaseTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "totalAmountCents AS amount FROM purchases "
            + "WHERE isDeleted = 0 AND purchaseTime >= :fromMillis "
            + "UNION ALL "
            + "SELECT strftime('%Y-%m-%d', expenseTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "amountCents AS amount FROM expenses "
            + "WHERE isDeleted = 0 AND expenseTime >= :fromMillis) "
            + "GROUP BY day ORDER BY day")
    LiveData<List<DailySales>> observeDailyExpenseSince(long fromMillis);

    // ---- 月度利润表导出用（一次性查询，非 LiveData） ----

    @Query("SELECT strftime('%Y-%m-%d', s.saleTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "COALESCE(SUM(s.totalAmountCents - COALESCE("
            + "(SELECT SUM(oe.differenceCents) FROM order_events oe "
            + "  WHERE oe.saleId = s.id AND oe.eventType = 1), 0)), 0) AS totalCents, "
            + "COUNT(*) AS saleCount "
            + "FROM sales s "
            + "WHERE s.isDeleted = 0 AND s.saleTime >= :fromMillis "
            + "GROUP BY day ORDER BY day")
    List<DailySales> listDailySalesSince(long fromMillis);

    @Query("SELECT strftime('%Y-%m-%d', s.saleTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "COALESCE(SUM(si.lineTotalCents - si.unitCostCents * si.quantityMilli / 1000), 0) AS totalCents, "
            + "0 AS saleCount "
            + "FROM sale_items si JOIN sales s ON si.saleId = s.id "
            + "WHERE s.isDeleted = 0 AND s.saleTime >= :fromMillis "
            + "GROUP BY day ORDER BY day")
    List<DailySales> listDailyProfitSince(long fromMillis);

    @Query("SELECT day, COALESCE(SUM(amount), 0) AS totalCents, 0 AS saleCount FROM ("
            + "SELECT strftime('%Y-%m-%d', purchaseTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "totalAmountCents AS amount FROM purchases "
            + "WHERE isDeleted = 0 AND purchaseTime >= :fromMillis "
            + "UNION ALL "
            + "SELECT strftime('%Y-%m-%d', expenseTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "amountCents AS amount FROM expenses "
            + "WHERE isDeleted = 0 AND expenseTime >= :fromMillis) "
            + "GROUP BY day ORDER BY day")
    List<DailySales> listDailyExpenseSince(long fromMillis);

    /** 单商品近 N 天的每日销量/销售额（商品销量页） */
    @Query("SELECT strftime('%Y-%m-%d', s.saleTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "COALESCE(SUM(si.quantityMilli), 0) AS quantityMilli, "
            + "COALESCE(SUM(si.lineTotalCents), 0) AS totalCents "
            + "FROM sale_items si JOIN sales s ON si.saleId = s.id "
            + "WHERE si.productId = :productId AND s.isDeleted = 0 "
            + "AND s.saleTime >= :fromMillis "
            + "GROUP BY day ORDER BY day")
    LiveData<List<com.example.accounting.data.model.ProductDailySales>> observeProductDailySales(
            String productId, long fromMillis);

    /** 单商品期间销售汇总（一次性查询） */
    @Query("SELECT COALESCE(SUM(si.quantityMilli), 0) AS quantityMilli, "
            + "COALESCE(SUM(si.lineTotalCents), 0) AS totalCents "
            + "FROM sale_items si JOIN sales s ON si.saleId = s.id "
            + "WHERE si.productId = :productId AND s.isDeleted = 0 "
            + "AND s.saleTime >= :fromMillis")
    com.example.accounting.data.model.ProductDailySales sumProductSalesSince(
            String productId, long fromMillis);

    /** 支出构成：按支出类型占比（名称映射与 ExpenseType.displayName 保持一致） */
    @Query("SELECT CASE e.expenseType "
            + "WHEN 1 THEN '房租' WHEN 2 THEN '水电' WHEN 3 THEN '运费' "
            + "WHEN 4 THEN '工资' WHEN 5 THEN '损耗' ELSE '其他' END AS name, "
            + "COALESCE(SUM(e.amountCents), 0) AS totalCents, 0 AS quantityMilli "
            + "FROM expenses e "
            + "WHERE e.isDeleted = 0 AND e.expenseTime >= :fromMillis "
            + "GROUP BY e.expenseType ORDER BY totalCents DESC")
    LiveData<List<com.example.accounting.data.model.CategorySales>> observeExpenseTypeShareSince(
            long fromMillis);

    /** 年度趋势：按月聚合（day 字段 = "yyyy-MM"，与日粒度共用 DailySales 结构） */
    @Query("SELECT strftime('%Y-%m', saleTime / 1000, 'unixepoch', 'localtime') AS day, "
            + "COALESCE(SUM(totalAmountCents), 0) AS totalCents, "
            + "COUNT(*) AS saleCount "
            + "FROM sales "
            + "WHERE isDeleted = 0 AND saleTime >= :fromMillis "
            + "GROUP BY day ORDER BY day")
    LiveData<List<DailySales>> observeMonthlySalesSince(long fromMillis);

    /** 本月各分类销售额（经商品所属分类聚合；未设分类归入"未分类"）。
     *  退货冲减按"该商品行占订单总额的比例"分摊到分类——退货是整单事件，
     *  比例分摊让每个分类承担自己那一份，与流水/首页统计口径一致。 */
    @Query("SELECT COALESCE(c.name, '未分类') AS name, "
            + "COALESCE(SUM(si.lineTotalCents + COALESCE("
            + "(SELECT SUM(CASE WHEN oe.originalProductId IS NULL OR oe.originalProductId = '' "
            + "  THEN CAST(ROUND(si.lineTotalCents * 1.0 * oe.differenceCents "
            + "    / s.totalAmountCents) AS INTEGER) "
            + "  ELSE CAST(ROUND(oe.differenceCents * 1.0 * si.quantityMilli "
            + "    / NULLIF(si2.quantityMilli, 0)) AS INTEGER) END) "
            + "  FROM order_events oe LEFT JOIN sale_items si2 "
            + "    ON si2.saleId = oe.saleId AND si2.productId = oe.originalProductId "
            + "  WHERE oe.saleId = s.id AND oe.eventType = 1 "
            + "  AND s.totalAmountCents > 0), 0)), 0) AS totalCents, "
            + "COALESCE(SUM(si.quantityMilli), 0) AS quantityMilli "
            + "FROM sale_items si "
            + "JOIN sales s ON si.saleId = s.id "
            + "LEFT JOIN products p ON si.productId = p.id "
            + "LEFT JOIN categories c ON p.categoryId = c.id "
            + "WHERE s.isDeleted = 0 AND s.saleTime BETWEEN :fromMillis AND :toMillis "
            + "GROUP BY c.name ORDER BY totalCents DESC")
    LiveData<List<com.example.accounting.data.model.CategorySales>> observeCategorySalesBetween(
            long fromMillis, long toMillis);

    /**
     * 毛利估算 = Σ(行小计 − 成本快照 × 数量)。
     * 成本用的是"成交当时的进价快照"，而不是当前进价，这样改进货价
     * 不会改写历史毛利。注意先乘后除，减少舍入误差（误差 ≤ 1 分/行）。
     */
    @Query("SELECT COALESCE(SUM(si.lineTotalCents "
            + "- si.unitCostCents * si.quantityMilli / 1000), 0) "
            + "FROM sale_items si JOIN sales s ON si.saleId = s.id "
            + "WHERE s.isDeleted = 0 AND s.saleTime BETWEEN :fromMillis AND :toMillis")
    LiveData<Long> observeGrossProfitBetween(long fromMillis, long toMillis);
}
