package com.example.accounting.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 时间工具：数据库存毫秒时间戳，界面显示字符串；"今天/本月"的边界也在这里算。
 * 用 Calendar 而不是手算 86400000，因为本地时区有夏令时/闰秒边界问题。
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /** 某天的 00:00:00.000（本地时区） */
    public static long startOfDay(long epochMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(epochMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** 某天的最后一毫秒（23:59:59.999） */
    public static long endOfDay(long epochMillis) {
        return startOfDay(epochMillis) + 24 * 60 * 60 * 1000L - 1;
    }

    public static long todayStart() {
        return startOfDay(System.currentTimeMillis());
    }

    public static long todayEnd() {
        return endOfDay(System.currentTimeMillis());
    }

    /** N 天前那天的 00:00（含今天，比如 N=6 就是"近 7 天"） */
    public static long daysAgoStart(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        return startOfDay(calendar.getTimeInMillis());
    }

    /** 本月 1 号 00:00 */
    public static long monthStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(calendar.getTimeInMillis());
    }

    /** 本月最后一天 23:59:59.999 */
    public static long monthEnd() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return endOfDay(calendar.getTimeInMillis());
    }

    /** 本年 1 月 1 日 00:00（统计"年"区间用） */
    public static long yearStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_YEAR, 1);
        return startOfDay(calendar.getTimeInMillis());
    }

    /** 上月 1 号 00:00 */
    public static long lastMonthStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(calendar.getTimeInMillis());
    }

    /** 上月最后一天 23:59:59.999 */
    public static long lastMonthEnd() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return endOfDay(calendar.getTimeInMillis());
    }

    /** 列表显示："10-03 14:05" */
    public static String formatShort(long epochMillis) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(epochMillis));
    }

    /** 详情显示："2026-10-03 14:05" */
    public static String formatFull(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(epochMillis));
    }

    /** 备份文件名用："20261003_140505" */
    public static String formatForFileName(long epochMillis) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date(epochMillis));
    }
}
