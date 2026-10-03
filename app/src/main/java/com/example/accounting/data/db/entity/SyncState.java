package com.example.accounting.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 本地内部键值表：存放备份时间等 App 级状态。
 * 不参与同步，也不含同步列。
 */
@Entity(tableName = "sync_state")
public class SyncState {

    @PrimaryKey
    @NonNull
    public String key;

    public String value;

    public long updatedAt;
}
