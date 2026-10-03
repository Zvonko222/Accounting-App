package com.example.accounting.data.db.dao;

import androidx.room.Embedded;

import com.example.accounting.data.db.entity.Purchase;

/**
 * 进货列表行：单据 + 商品摘要（与 {@link SaleWithSummary} 对称）。
 */
public class PurchaseWithSummary {

    @Embedded
    public Purchase purchase;

    /** 商品摘要（"可乐×5"），无明细时为空串 */
    public String itemsSummary;

    /** 有效明细行数 */
    public int itemCount;
}
