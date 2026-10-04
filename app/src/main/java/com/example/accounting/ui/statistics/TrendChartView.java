package com.example.accounting.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.accounting.data.model.DailySales;
import com.example.accounting.util.MoneyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 趋势图（柱状 / 折线两模式）：纯 Canvas 绘制，不引第三方库。
 * 数据点来自 SQL 聚合：周/月按"日"聚合，年按"月"聚合（day 字段格式不同，
 * 横轴标签自动识别：yyyy-MM-dd 显示当日号，yyyy-MM 显示"N月"）。
 */
public class TrendChartView extends View {

    public static final int MODE_BAR = 0;
    public static final int MODE_LINE = 1;

    private static final int MAX_POINTS = 31;

    private final List<DailySales> points = new ArrayList<>();
    private long maxValue = 1;
    private int mode = MODE_BAR;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TrendChartView(Context context) {
        super(context);
        init();
    }

    public TrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setColor(0xFF00695C);
        linePaint.setColor(0xFF00695C);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(sp(2.5f));
        valuePaint.setTextSize(sp(10));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(0xFF00695C);
        labelPaint.setTextSize(sp(11));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xFF5F6368);
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    /** Room 查询结果进来后触发重绘（点数超过 MAX_POINTS 时均匀抽稀） */
    public void setData(@Nullable List<DailySales> dailySales) {
        points.clear();
        maxValue = 1;
        if (dailySales != null) {
            int step = Math.max(1, dailySales.size() / MAX_POINTS);
            for (int i = 0; i < dailySales.size(); i += step) {
                points.add(dailySales.get(i));
                maxValue = Math.max(maxValue, dailySales.get(i).totalCents);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) {
            return;
        }

        float width = getWidth();
        float chartTop = sp(14);
        float chartBottom = getHeight() - sp(18);
        float slot = width / points.size();
        float barWidth = Math.min(slot * 0.6f, sp(24));

        // 柱状：画柱；折线：连线 + 圆点
        float previousX = -1;
        float previousY = -1;

        for (int i = 0; i < points.size(); i++) {
            DailySales point = points.get(i);
            float centerX = slot * i + slot / 2f;
            float full = chartBottom - chartTop;
            float filled = (float) ((double) point.totalCents / maxValue) * full;
            if (point.totalCents > 0 && filled < sp(2)) {
                filled = sp(2);
            }
            float topY = chartBottom - filled;

            if (mode == MODE_BAR) {
                canvas.drawRect(centerX - barWidth / 2, topY,
                        centerX + barWidth / 2, chartBottom, barPaint);
            } else {
                if (previousX >= 0) {
                    canvas.drawLine(previousX, previousY, centerX, topY, linePaint);
                }
                canvas.drawCircle(centerX, topY, sp(3), barPaint);
                previousX = centerX;
                previousY = topY;
            }

            // 柱顶金额（点太多时只画数值，标签隔点显示）
            if (points.size() <= 14 || i % 2 == 0) {
                canvas.drawText(compactAmount(point.totalCents), centerX,
                        topY - sp(3), valuePaint);
            }
            String label = axisLabel(point.day);
            if (label != null && (points.size() <= 16 || i % 2 == 0)) {
                canvas.drawText(label, centerX, getHeight() - sp(4), labelPaint);
            }
        }
    }

    /** 横轴标签：日粒度留当日号，月粒度留"N月" */
    private String axisLabel(String day) {
        if (day == null) {
            return "";
        }
        if (day.length() == 7) {
            return Integer.parseInt(day.substring(5)) + "月";
        }
        return day.length() >= 10 ? day.substring(8) : day;
    }

    /** 金额紧凑显示：万元以上按万计，其余显示整元 */
    private String compactAmount(long cents) {
        if (cents >= 100_000_00L) {
            return MoneyUtil.toDisplay(cents / 100 / 10000) + "万";
        }
        return String.valueOf(cents / 100);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
