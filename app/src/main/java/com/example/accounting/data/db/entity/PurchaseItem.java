package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 进货明细（子表）。与 SaleItem 结构对称；
 * 进货时商品成本以本单进价为准，保存后会更新商品的"最近进价"。
 */
@Entity(tableName = "purchase_items",
        foreignKeys = {
                @ForeignKey(entity = Purchase.class, parentColumns = "id",
                        childColumns = "purchaseId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Product.class, parentColumns = "id",
                        childColumns = "productId")
        },
        indices = {
                @Index("purchaseId"),
                @Index("productId"),
                @Index("syncStatus")
        })
public class PurchaseItem extends SyncEntity {

    @PrimaryKey
    @NonNull
    public String id;

    public String purchaseId;

    public String productId;

    /** 快照：进货时的商品名 */
    public String productName;

    /** 快照：本单进价（分） */
    public long unitCostCents;

    /** 数量 × 1000 */
    public long quantityMilli;

    /** 快照：行小计（分） */
    public long lineTotalCents;
}
