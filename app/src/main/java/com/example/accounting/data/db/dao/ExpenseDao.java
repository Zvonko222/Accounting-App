package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.Expense;

import java.util.List;

/**
 * 只负责 expenses 表的读写。
 */
@Dao
public interface ExpenseDao {

    /** 支出流水列表：只显示未删除的（支出没有"作废"概念，删除即从列表消失） */
    @Query("SELECT * FROM expenses WHERE expenseTime BETWEEN :fromMillis AND :toMillis "
            + "AND isDeleted = 0 "
            + "ORDER BY expenseTime DESC")
    LiveData<List<Expense>> observeBetween(long fromMillis, long toMillis);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses "
            + "WHERE isDeleted = 0 AND expenseTime BETWEEN :fromMillis AND :toMillis")
    LiveData<Long> observeTotalBetween(long fromMillis, long toMillis);

    /** 导出 CSV 用：一次性取全量 */
    @Query("SELECT * FROM expenses ORDER BY expenseTime")
    List<Expense> listAll();

    @Query("SELECT * FROM expenses WHERE id = :id")
    Expense findById(String id);

    @Insert
    void insert(Expense expense);

    @Update
    void update(Expense expense);

    // ---- 同步子系统专用（syncStatus: 1=PENDING 3=FAILED） ----

    @Query("SELECT * FROM expenses WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<Expense> listDirtyForSync(int limit);

    @Query("UPDATE expenses SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertFromRemote(Expense expense);
}
