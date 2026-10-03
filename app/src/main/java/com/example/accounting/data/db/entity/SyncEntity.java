package com.example.accounting.data.db.entity;

import com.example.accounting.data.sync.SyncStatus;

/**
 * 所有需要同步的业务表的公共列（ARCHITECTURE.md 2.2 同步列组）。
 *
 * Room 会自动把父类的 public 字段并入子类的表结构，
 * 因此每张业务表都自带这九列，不需要在九个实体里重复声明。
 *
 * 为什么用"嵌入列"而不是单独一张 SyncRecord 表：
 * 行自带同步状态，查询 WHERE syncStatus != 0 就是待同步队列，
 * 不会出现两张表记录同一状态而互相矛盾的问题。
 */
public abstract class SyncEntity {

    /** 行创建时间（毫秒） */
    public long createdAt;

    /** 最后修改时间（毫秒）——未来同步排序与冲突判定的依据 */
    public long updatedAt;

    /** 软删除标记：true 表示用户已删除，查询业务数据时要过滤掉 */
    public boolean isDeleted = false;

    /** 删除时间，未删除时为 null */
    public Long deletedAt = null;

    /** 同步状态，见 {@link SyncStatus}。本期恒为 PENDING */
    public int syncStatus = SyncStatus.PENDING;

    /** 服务器最后一次确认时间，从未同步过为 null */
    public Long lastSyncedAt = null;

    /** 同步失败重试次数 */
    public int retryCount = 0;

    /** 最近一次同步失败的原因，方便排障 */
    public String lastSyncError = null;

    /** 新建一行时初始化时间戳。业务代码创建实体后必须调用。 */
    public void initTimestamps() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 任何业务修改后调用：先改本地，再标记待同步。
     * 本地数据库是唯一事实源，写库成功即业务成功，与网络无关。
     */
    public void markPending() {
        this.updatedAt = System.currentTimeMillis();
        this.syncStatus = SyncStatus.PENDING;
    }

    /**
     * 软删除（墓碑）。
     * 同步体系下的"删除"是打标记而不是 DELETE，否则服务器会把旧数据
     * 再推回手机，造成已删数据"复活"（ARCHITECTURE.md 第 4 节）。
     */
    public void markDeleted() {
        this.isDeleted = true;
        this.deletedAt = System.currentTimeMillis();
        markPending();
    }
}
