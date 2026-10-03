package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 进货单（主表）。结构与销售单对称。
 */
@Entity(tableName = "purchases",
        indices = {
                @Index("purchaseTime"),
                @Index("syncStatus")
        })
public class Purchase extends SyncEntity {

    @PrimaryKey
    @NonNull
    public String id;

    /** 业务发生时间，统计按它算 */
    public long purchaseTime;

    /** 进货合计（分） */
    public long totalAmountCents;

    /** 可空：供应商名。小商户记账不做供应商档案 */
    public String supplierName;

    public String note;
}
