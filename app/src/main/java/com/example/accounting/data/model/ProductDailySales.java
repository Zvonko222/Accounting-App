package com.example.accounting.data.model;

/** 统计查询结果：某商品某天卖出的数量与金额 */
public class ProductDailySales {

    /** 本地日期 yyyy-MM-dd */
    public String day;

    /** 销量（数量×1000） */
    public long quantityMilli;

    /** 销售额（分） */
    public long totalCents;
}
