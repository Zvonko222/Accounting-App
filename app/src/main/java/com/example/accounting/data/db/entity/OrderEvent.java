package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "order_events",
        foreignKeys = @ForeignKey(entity = Sale.class, parentColumns = "id",
                childColumns = "saleId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("saleId"), @Index("eventTime"), @Index("syncStatus")})
public class OrderEvent extends SyncEntity {

    public static final int TYPE_RETURN = 1;
    public static final int TYPE_EXCHANGE = 2;
    public static final int TYPE_OTHER = 3;

    @PrimaryKey
    @NonNull
    public String id;

    public String saleId;
    public int eventType;
    public long eventTime;
    public long amountCents;
    public long originalAmountCents;
    public long replacementAmountCents;
    public long differenceCents;
    public String originalProductId;
    public String replacementProductId;
    public long quantityMilli;
    public String note;
}
