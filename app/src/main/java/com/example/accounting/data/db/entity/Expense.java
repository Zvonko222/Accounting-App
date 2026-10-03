package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 支出：房租、水电、运费等与销售无关的钱流出。
 */
@Entity(tableName = "expenses",
        indices = {
                @Index("expenseTime"),
                @Index("syncStatus")
        })
public class Expense extends SyncEntity {

    @PrimaryKey
    @NonNull
    public String id;

    /** 业务发生时间，统计按它算 */
    public long expenseTime;

    /** 支出类型，见 {@link com.example.accounting.data.model.ExpenseType} */
    public int expenseType;

    /** 金额（分） */
    public long amountCents;

    public String note;
}
