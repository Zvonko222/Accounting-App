package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 销售明细（子表）。
 *
 * 关键设计——三个"快照"字段：productName / unitPriceCents / lineTotalCents。
 * 账本必须记录"当时发生了什么"：商品以后改名、改价、停用，
 * 历史单据的显示和统计都不得受影响。所以明细不回读商品表，只读自己的快照。
 */
@Entity(tableName = "sale_items",
        foreignKeys = {
                @ForeignKey(entity = Sale.class, parentColumns = "id",
                        childColumns = "saleId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Product.class, parentColumns = "id",
                        childColumns = "productId")
        },
        indices = {
                @Index("saleId"),
                @Index("productId"),
                @Index("syncStatus")
        })
public class SaleItem extends SyncEntity {

    @PrimaryKey
    @NonNull
    public String id;

    /** 所属销售单 id */
    public String saleId;

    /** 商品 id。商品只做软删除，外键安全 */
    public String productId;

    /** 快照：成交时的商品名 */
    public String productName;

    /** 快照：成交单价（分） */
    public long unitPriceCents;

    /** 快照：成交时的商品进价（分）。毛利估算 = 行小计 - 此成本 × 数量 */
    public long unitCostCents;

    /** 数量 × 1000 */
    public long quantityMilli;

    /** 快照：行小计（分）= 单价 × 数量，保存时算定 */
    public long lineTotalCents;
}
