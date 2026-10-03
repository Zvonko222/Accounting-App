package com.example.accounting.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * 同步任务的注册与触发（WorkManager）。
 *
 * 两个入口：
 * - requestSync：记完账/回到主页时调用的"尽快同步一次"（有网才跑，无网自动等）；
 * - schedulePeriodic：App 启动时注册的每 6 小时兜底同步（幂等，重复注册不叠加）。
 *
 * 任务持久化在 WorkManager 里，手机重启、App 被杀都不丢；失败按指数退避重试。
 */
public final class SyncScheduler {

    private static final String ONE_TIME_WORK = "sync_now";
    private static final String PERIODIC_WORK = "sync_periodic";

    private SyncScheduler() {
    }

    public static void requestSync(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        // KEEP：已有排队任务时不再加新的——同步引擎运行时重新读库，天然覆盖最新数据
        WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK, ExistingWorkPolicy.KEEP, request);
    }

    public static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag(PERIODIC_WORK)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
