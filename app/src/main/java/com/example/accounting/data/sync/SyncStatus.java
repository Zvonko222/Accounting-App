package com.example.accounting.data.sync;

/**
 * 每行业务数据的同步状态（存在各表的 syncStatus 列）。
 *
 * 本期 App 是纯本地的：所有新数据保持 PENDING，等未来接入同步子系统后，
 * 这些列无需改表就能直接被同步引擎使用（提前埋管线，后期才通水）。
 */
public final class SyncStatus {

    /** 已同步到服务器 */
    public static final int SYNCED = 0;

    /** 新建或修改过，等待同步（默认值） */
    public static final int PENDING = 1;

    /** 正在上传的短暂状态；如果 App 在上传中被杀，下次启动会重置回 PENDING */
    public static final int SYNCING = 2;

    /** 上次同步失败，等待重试 */
    public static final int FAILED = 3;

    private SyncStatus() {
    }
}
