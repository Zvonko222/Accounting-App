package com.example.accounting.data.repository.backup;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * 把"每天一次本机备份"注册进 WorkManager。
 * 幂等：任务名固定，重复调用不会叠加任务（ExistingPeriodicWorkPolicy.KEEP）。
 * 在 AccountingApp.onCreate 里调用一次即可，App 每次启动都会确保任务在册。
 */
public final class BackupScheduler {

    private static final String WORK_NAME = "daily_local_backup";

    private BackupScheduler() {
    }

    public static void scheduleDaily(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackupWorker.class, 1, TimeUnit.DAYS)
                .addTag(WORK_NAME)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request);
    }
}
