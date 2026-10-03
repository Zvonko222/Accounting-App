package com.example.accounting.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.accounting.R;
import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.ui.purchase.PurchaseEditActivity;
import com.example.accounting.ui.sales.SaleEditActivity;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 桌面小组件"今日经营"：今日销售额 + 单数 + 两个一键记账按钮。
 *
 * AppWidget 的三个要点（学习 Android Framework 的经典组件）：
 * 1. 界面用 RemoteViews——跨进程塞给桌面启动器渲染，只能用少数基础控件；
 * 2. 点击用 PendingIntent——整个组件没有"事件监听器"，一切都是预登记的 Intent；
 * 3. 数据查询必须离开主线程（Room 禁止主线程查询），查完再更新 RemoteViews。
 *
 * 刷新点：系统定时（≥30 分钟兜底）+ 打开 App/记完账时主动调 refreshAll()。
 */
public class TodayWidgetProvider extends AppWidgetProvider {

    private static final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        refreshAll(context);
    }

    /**
     * 刷新桌面上所有"今日经营"小组件。
     * 没有放置小组件时 getAppWidgetIds 返回空数组，直接返回，不做任何查询。
     */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, TodayWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        queryExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            long from = TimeUtil.todayStart();
            long to = TimeUtil.todayEnd();
            long totalCents = db.saleDao().sumBetweenSync(from, to);
            int count = db.saleDao().countBetweenSync(from, to);

            RemoteViews views = new RemoteViews(context.getPackageName(),
                    R.layout.widget_today);
            views.setTextViewText(R.id.widget_sales_total, MoneyUtil.toYuan(totalCents));
            views.setTextViewText(R.id.widget_sale_count,
                    context.getString(R.string.widget_count_fmt, count));
            views.setOnClickPendingIntent(R.id.widget_btn_sale,
                    activityPendingIntent(context, SaleEditActivity.class, 1));
            views.setOnClickPendingIntent(R.id.widget_btn_purchase,
                    activityPendingIntent(context, PurchaseEditActivity.class, 2));
            manager.updateAppWidget(ids, views);
        });
    }

    /** 小组件按钮 → 打开对应页面（FLAG_IMMUTABLE 是 31+ 的强制要求） */
    private static PendingIntent activityPendingIntent(Context context,
                                                       Class<?> activityClass, int requestCode) {
        Intent intent = new Intent(context, activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
