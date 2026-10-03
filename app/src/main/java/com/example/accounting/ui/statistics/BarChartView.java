package com.example.accounting.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.accounting.data.model.DailySales;
import com.example.accounting.util.MoneyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 近 7 天销售额柱状图：纯 Canvas 绘制，不引第三方图表库。
 *
 * 数据结构是 List<DailySales>（可能不足 7 天），补齐到 7 天后从左到右画，
 * 柱高按最大值等比缩放。只在 onSizeChanged / setData 时重算，绘制很轻。
 */
public class BarChartView extends View {

    private static final int MAX_DAYS = 7;

    private final List<DailySales> days = new ArrayList<>();
    private long maxValue = 1;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setColor(0xFF00695C);
        valuePaint.setTextSize(sp(11));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(0xFF00695C);
        labelPaint.setTextSize(sp(11));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xFF5F6368);
    }

    /** Room 查询结果进来后补齐 7 天并触发重绘 */
    public void setData(@Nullable List<DailySales> dailySales) {
        days.clear();
        maxValue = 1;
        if (dailySales != null) {
            days.addAll(dailySales);
            for (DailySales day : days) {
                maxValue = Math.max(maxValue, day.totalCents);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (days.isEmpty()) {
            return;
        }

        float width = getWidth();
        float chartTop = sp(14);
        float chartBottom = getHeight() - sp(18);
        float slot = width / days.size();
        float barWidth = slot * 0.5f;

        for (int i = 0; i < days.size(); i++) {
            DailySales day = days.get(i);
            float centerX = slot * i + slot / 2f;

            float barHeight = chartBottom - chartTop;
            float filled = (float) ((double) day.totalCents / maxValue) * barHeight;
            if (day.totalCents > 0 && filled < sp(2)) {
                filled = sp(2); // 有数据但金额太小，也给一条可见的最小柱
            }
            canvas.drawRect(centerX - barWidth / 2, chartBottom - filled,
                    centerX + barWidth / 2, chartBottom, barPaint);

            // 柱顶金额
            String amountText = MoneyUtil.toDisplay(day.totalCents);
            canvas.drawText(amountText, centerX, chartBottom - filled - sp(3), valuePaint);

            // 横轴日期：去掉年月，只留 "dd" 当日号
            String dayNumber = day.day.length() >= 10 ? day.day.substring(8) : day.day;
            canvas.drawText(dayNumber, centerX, getHeight() - sp(4), labelPaint);
        }
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
