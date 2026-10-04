package com.example.accounting.data.db.dao;

import androidx.room.Embedded;

import com.example.accounting.data.db.entity.Sale;

/**
 * 销售列表行：单据 + 商品摘要。
 *
 * itemsSummary 由 SQL 里的 GROUP_CONCAT 子查询生成，形如 "可乐×2、薯片×1"，
 * 让用户不点详情就知道这单卖了什么。已作废的明细不进摘要。
 */
public class SaleWithSummary {

    @Embedded
    public Sale sale;

    /** 商品摘要（"可乐×2、薯片×1"），无明细时为空串 */
    public String itemsSummary;

    /** 有效明细行数 */
    public int itemCount;

    /** 最近售后事件类型与差额 */
    public int latestEventType;
    public long latestEventDifferenceCents;
}
