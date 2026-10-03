package com.example.accounting.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * 云同步 Worker：网络可用时被系统唤醒，阻塞执行一轮 push + pull。
 * 失败返回 retry（指数退避）；连续多次失败后放弃本轮，等下次定时任务。
 */
public class SyncWorker extends Worker {

    private static final int MAX_ATTEMPTS = 5;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SyncEngine.syncBlocking(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            // 尚未配置服务器不算失败：静默成功，等用户在设置页配置
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("尚未配置服务器地址")) {
                return Result.success();
            }
            return getRunAttemptCount() < MAX_ATTEMPTS
                    ? Result.retry()
                    : Result.failure();
        }
    }
}
