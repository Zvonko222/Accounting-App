package com.example.accounting.data.model;

/**
 * 统计查询结果：某一天（本地时区）的销售合计。
 * 字段名与 SQL 别名一一对应，Room 自动填充。
 */
public class DailySales {

    /** 本地日期，格式 yyyy-MM-dd */
    public String day;

    /** 当天有效销售合计（分），已扣除优惠 */
    public long totalCents;

    /** 当天有效单数 */
    public int saleCount;
}
