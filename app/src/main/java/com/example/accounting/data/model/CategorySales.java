package com.example.accounting.data.model;

/** 统计查询结果：某分类在一段时间内的销售额（服务端列名与字段一一对应） */
public class CategorySales {

    /** 分类名；商品未设分类时为 "未分类" */
    public String name;

    /** 销售额（分） */
    public long totalCents;

    /** 销量（数量×1000） */
    public long quantityMilli;
}
