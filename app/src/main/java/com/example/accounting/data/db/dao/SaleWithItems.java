package com.example.accounting.data.db.dao;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.db.entity.SaleItem;

import java.util.List;

/**
 * 一张销售单 + 它的全部明细，Room 的 @Relation 自动把两张表拼在一起。
 * 用于"销售详情"弹窗。
 */
public class SaleWithItems {

    @Embedded
    public Sale sale;

    @Relation(parentColumn = "id", entityColumn = "saleId")
    public List<SaleItem> items;
}
