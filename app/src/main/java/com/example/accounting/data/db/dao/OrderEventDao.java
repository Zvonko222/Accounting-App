package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.OrderEvent;

import java.util.List;

/**
 * 只负责 order_events（订单售后事件）的读写。
 */
@Dao
public interface OrderEventDao {

    @Query("SELECT * FROM order_events WHERE saleId = :saleId ORDER BY eventTime DESC")
    LiveData<List<OrderEvent>> observeForSale(String saleId);

    @Insert
    void insert(OrderEvent event);

    @Query("SELECT * FROM order_events WHERE id = :id")
    OrderEvent findById(String id);

    // ---- 同步子系统专用（syncStatus: 1=PENDING 3=FAILED） ----

    @Query("SELECT * FROM order_events WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<OrderEvent> listDirtyForSync(int limit);

    @Query("UPDATE order_events SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertFromRemote(OrderEvent event);
}
