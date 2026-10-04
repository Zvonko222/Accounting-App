package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.Category;

import java.util.List;

/**
 * 只负责 categories 表的读写。
 */
@Dao
public interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder, name")
    LiveData<List<Category>> observeActive();

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder, name")
    List<Category> listActive();

    @Insert
    void insert(Category category);

    @Update
    void update(Category category);

    @Query("SELECT * FROM categories WHERE id = :id")
    Category findById(String id);

    @Query("SELECT * FROM categories WHERE name = :name AND isDeleted = 0 LIMIT 1")
    Category findActiveByName(String name);

    // ---- 同步子系统专用（SyncEngine 调用；syncStatus: 1=PENDING 3=FAILED） ----

    @Query("SELECT * FROM categories WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<Category> listDirtyForSync(int limit);

    @Query("UPDATE categories SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertFromRemote(Category category);
}
