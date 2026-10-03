package com.example.accounting.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 重启 App 进程（恢复数据库后必须重启，让 Room 用新文件重建连接）。
 * 做法：设一个 300ms 后的闹钟拉起启动页，然后立刻杀掉当前进程。
 * 这是 Android 上不依赖任何第三方库的标准重启方式。
 */
public final class AppRestarter {

    private AppRestarter() {
    }

    public static void restart(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent == null) {
            launchIntent = new Intent(context, com.example.accounting.ui.main.MainActivity.class);
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 42, launchIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC,
                    System.currentTimeMillis() + 300, pendingIntent);
        }

        // 直接结束进程；闹钟会把新进程拉起来
        Runtime.getRuntime().exit(0);
    }
}
