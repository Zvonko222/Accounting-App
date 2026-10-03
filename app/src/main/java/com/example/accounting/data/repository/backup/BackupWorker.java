package com.example.accounting.data.repository.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

/**
 * 每日自动备份的 Worker。
 *
 * 用 WorkManager 的原因（对应 ARCHITECTURE.md 的可靠性要求）：
 * 任务注册后由系统持久保存——手机重启、App 被杀都不影响，
 * 系统会在合适的时机唤醒执行（即使 App 进程没在运行）。
 *
 * Worker 自带后台线程，这里直接同步执行备份；
 * 失败返回 retry，WorkManager 会按退避策略重试。
 */
public class BackupWorker extends Worker {

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            BackupManager.runLocalBackup(getApplicationContext());
            return Result.success();
        } catch (IOException e) {
            return Result.retry();
        }
    }
}
