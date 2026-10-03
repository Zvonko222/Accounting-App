package com.example.accounting.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import com.example.accounting.data.db.entity.Product;

import java.util.List;

/**
 * 只负责 products 表的读写。
 * 库存数量的增减不在 DAO 里做，而是读出实体、改字段、update，
 * 让整个改动过程（含记台账）包在 Repository 的同一个事务里。
 */
@Dao
public interface ProductDao {

    /**
     * 库存页列表：按关键字搜索。
     * :keyword 为空串时返回全部（SQL 里用 OR 短路实现"不筛选"）。
     */
    @Query("SELECT * FROM products WHERE isDeleted = 0 "
            + "AND (:keyword = '' OR name LIKE '%' || :keyword || '%') "
            + "ORDER BY name")
    LiveData<List<Product>> observeActive(String keyword);

    /** 开单页的商品网格：不搜索，一次全量（小商户商品数通常几十到几百） */
    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY name")
    LiveData<List<Product>> observeAllActive();

    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY name")
    List<Product> listAllActive();

    /** 不过滤软删除——历史单据作废冲回库存时，商品即使已停用也要能找到 */
    @Query("SELECT * FROM products WHERE id = :id")
    Product findById(String id);

    /** 按名称精确找（拍照导入时判断"已有商品还是建新商品"） */
    @Query("SELECT * FROM products WHERE name = :name AND isDeleted = 0 LIMIT 1")
    Product findByName(String name);

    /** 库存预警：设置了预警值且当前库存已低于等于预警值 */
    @Query("SELECT * FROM products WHERE isDeleted = 0 "
            + "AND lowStockThresholdMilli > 0 "
            + "AND stockQuantityMilli <= lowStockThresholdMilli "
            + "ORDER BY name")
    LiveData<List<Product>> observeLowStock();

    @Insert
    void insert(Product product);

    @Update
    void update(Product product);

    // ---- 同步子系统专用（syncStatus: 1=PENDING 3=FAILED） ----

    @Query("SELECT * FROM products WHERE syncStatus IN (1, 3) ORDER BY updatedAt LIMIT :limit")
    List<Product> listDirtyForSync(int limit);

    @Query("UPDATE products SET syncStatus = :status, lastSyncedAt = :syncedAt, "
            + "lastSyncError = :error, retryCount = retryCount + :incRetry WHERE id IN (:ids)")
    void markSyncStatus(List<String> ids, int status, Long syncedAt, String error, int incRetry);

    @Upsert
    void upsertFromRemote(Product product);
}
