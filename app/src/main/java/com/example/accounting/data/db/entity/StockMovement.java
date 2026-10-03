package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 库存变动台账：每一次库存变化都记一笔，回答"库存为什么是这个数"。
 *
 * products.stockQuantityMilli 是当前值（读得快），
 * 本表是变动流水（可追溯），两者在同一个事务里更新，永远互相印证：
 * 当前库存 = 期初 + 所有变动之和。
 */
@Entity(tableName = "stock_movements",
        foreignKeys = @ForeignKey(
                entity = Product.class,
                parentColumns = "id",
                childColumns = "productId"),
        indices = {
                @Index(value = {"productId", "movementTime"}),
                @Index("syncStatus")
        })
public class StockMovement extends SyncEntity {

    /** 卖出 */
    public static final int TYPE_SALE = 1;
    /** 进货 */
    public static final int TYPE_PURCHASE = 2;
    /** 盘点修正 */
    public static final int TYPE_ADJUST = 3;
    /** 期初建账 */
    public static final int TYPE_INITIAL = 4;
    /** 销售作废冲回 */
    public static final int TYPE_VOID_SALE = 5;
    /** 进货作废冲回 */
    public static final int TYPE_VOID_PURCHASE = 6;

    @PrimaryKey
    @NonNull
    public String id;

    public String productId;

    /** 变动类型：TYPE_SALE / TYPE_PURCHASE / … */
    public int changeType;

    /** 带符号变动量（数量×1000）：卖出 -1500，进货 +2400，修正 ±N */
    public long changeQuantityMilli;

    /** 可空：来源销售单 id */
    public String relatedSaleId;

    /** 可空：来源进货单 id */
    public String relatedPurchaseId;

    /** 变动时间 */
    public long movementTime;

    public String note;
}
