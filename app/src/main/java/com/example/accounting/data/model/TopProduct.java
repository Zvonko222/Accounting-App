package com.example.accounting.data.model;

/**
 * 统计查询结果：一段时间内的热销商品。
 */
public class TopProduct {

    /** 商品名（取自销售明细的快照） */
    public String productName;

    /** 累计销量（数量×1000） */
    public long quantityMilli;

    /** 累计销售额（分） */
    public long totalCents;
}
