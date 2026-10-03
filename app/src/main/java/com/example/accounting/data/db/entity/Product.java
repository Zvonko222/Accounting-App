package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 商品：本 App 使用频率最高的表，库存现值和售价都在这里。
 */
@Entity(tableName = "products",
        foreignKeys = @ForeignKey(
                entity = Category.class,
                parentColumns = "id",
                childColumns = "categoryId"),
        indices = {
                @Index("name"),
                @Index("barcode"),
                @Index("categoryId"),
                @Index("syncStatus")
        })
public class Product extends SyncEntity {

    @PrimaryKey
    @NonNull
    public String id;

    public String name;

    /** 可空：条码，用于快速检索 */
    public String barcode;

    /** 可空：所属分类 id。分类只做软删除，外键不会悬空 */
    public String categoryId;

    /** 计量单位：个/斤/瓶/箱，仅显示用 */
    public String unit = "个";

    /** 售价，单位：分。金额一律用 long 分存储，禁止浮点 */
    public long salePriceCents;

    /** 最近进价（分）。开单时的成本快照来源，毛利估算用 */
    public long purchasePriceCents;

    /**
     * 当前库存 = 数量 × 1000（1.7 斤存 1700），避开浮点误差。
     * 允许为负：先卖后补是实体店常态，界面用红色提示该盘点。
     */
    public long stockQuantityMilli;

    /** 库存预警值（数量×1000）。库存低于等于此值时首页提醒；0 = 不提醒 */
    public long lowStockThresholdMilli;

    public String note;
}
