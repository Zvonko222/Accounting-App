package com.example.accounting.data.db.dao;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.example.accounting.data.db.entity.Purchase;
import com.example.accounting.data.db.entity.PurchaseItem;

import java.util.List;

/**
 * 一张进货单 + 它的全部明细。
 */
public class PurchaseWithItems {

    @Embedded
    public Purchase purchase;

    @Relation(parentColumn = "id", entityColumn = "purchaseId")
    public List<PurchaseItem> items;
}
