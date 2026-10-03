package com.example.accounting.data.db.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.SyncState;

/**
 * 本地内部键值表：目前存"上次备份时间"等 App 级状态。
 */
@Dao
public interface SyncStateDao {

    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    String getValue(String key);

    @Upsert
    void put(SyncState state);
}
