package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.StockMovement;

import java.util.List;

/**
 * 只负责 stock_movements（库存台账）的写入与查询。
 * 台账只增不改：历史变动记录永远不更新，保证"当前库存 = 期初 + Σ变动"可验证。
 */
@Dao
public interface StockMovementDao {

    /** 某商品的变动记录，新的在前。台账只读最近 200 条，够查也够快 */
    @Query("SELECT * FROM stock_movements WHERE productId = :productId "
            + "ORDER BY movementTime DESC LIMIT 200")
    LiveData<List<StockMovement>> observeForProduct(String productId);

    @Insert
    void insert(StockMovement movement);

    @Insert
    void insertAll(List<StockMovement> movements);

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    StockMovement findById(String id);

    // ---- 同步子系统专用（syncStatus: 1=PENDING 3=FAILED） ----

    @Query("SELECT * FROM stock_movements WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<StockMovement> listDirtyForSync(int limit);

    @Query("UPDATE stock_movements SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertFromRemote(StockMovement movement);
}
