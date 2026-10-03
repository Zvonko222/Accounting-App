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
    @Query("SELECT si.productName AS productName, "
            + "COALESCE(SUM(si.quantityMilli), 0) AS quantityMilli, "
            + "COALESCE(SUM(si.lineTotalCents), 0) AS totalCents "
            + "FROM sale_items si JOIN sales s ON si.saleId = s.id "
            + "WHERE s.isDeleted = 0 AND s.saleTime >= :fromMillis "
            + "GROUP BY si.productName "
            + "ORDER BY totalCents DESC LIMIT 5")
    LiveData<List<TopProduct>> observeTopProductsSince(long fromMillis);

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
