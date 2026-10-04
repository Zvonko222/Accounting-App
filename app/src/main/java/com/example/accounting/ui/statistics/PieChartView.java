package com.example.accounting.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.accounting.data.model.CategorySales;
import com.example.accounting.util.MoneyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类占比扇形图：纯 Canvas 绘制。
 * 数据 = 各分类销售额（SQL 已按销售额倒序），颜色取固定调色板，
 * 顺序与下方"分类销售额"榜单一致——榜单即图例。
 */
public class PieChartView extends View {

    /** 固定调色板（品牌绿系 + 区分度高的辅助色） */
    private static final int[] PALETTE = {
            0xFF00695C, 0xFF26A69A, 0xFF8BC34A, 0xFFFFB300,
            0xFFEF6C00, 0xFFE53935, 0xFF5C6BC0, 0xFF8E24AA
    };

    private final List<CategorySales> items = new ArrayList<>();
    private long total = 0;

    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartView(Context context) {
        super(context);
        init();
    }

    public PieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setTextSize(sp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFF1C1B1F);
        textPaint.setFakeBoldText(true);
    }

    public void setData(@Nullable List<CategorySales> categorySales) {
        items.clear();
        total = 0;
        if (categorySales != null) {
            items.addAll(categorySales);
            for (CategorySales item : items) {
                total += item.totalCents;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (items.isEmpty() || total <= 0) {
            return;
        }

        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f + sp(6);
        float top = sp(6);
        RectF oval = new RectF(left, top, left + size - sp(12), top + size - sp(12));
        float startAngle = -90f;

        for (int i = 0; i < items.size(); i++) {
            CategorySales item = items.get(i);
            float sweep = (float) ((double) item.totalCents / total) * 360f;
            slicePaint.setColor(PALETTE[i % PALETTE.length]);
            canvas.drawArc(oval, startAngle, sweep, true, slicePaint);

            double share = (double) item.totalCents / total;
            float middleAngle = (float) Math.toRadians(startAngle + sweep / 2f);
            float labelRadius = oval.width() / 2f + sp(14);
            float labelX = oval.centerX() + (float) (labelRadius * Math.cos(middleAngle));
            float labelY = oval.centerY() + (float) (labelRadius * Math.sin(middleAngle));
            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(PALETTE[i % PALETTE.length]);
            labelPaint.setTextSize(sp(11));
            labelPaint.setFakeBoldText(true);
            labelPaint.setTextAlign(Math.cos(middleAngle) >= 0
                    ? Paint.Align.LEFT : Paint.Align.RIGHT);
            String label = item.name + " " + MoneyUtil.toYuan(item.totalCents)
                    + " (" + Math.round(share * 100) + "%)";
            canvas.drawText(label, labelX, labelY + sp(4), labelPaint);
            startAngle += sweep;
        }

        // 中心白圆 + 总额，扇形变环形，中间信息更清晰
        Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(0xFFFFFFFF);
        float centerX = oval.centerX();
        float centerY = oval.centerY();
        float radius = oval.width() / 2f;
        canvas.drawCircle(centerX, centerY, radius * 0.55f, centerPaint);
        canvas.drawText(MoneyUtil.toYuan(total), centerX, centerY + sp(6), textPaint);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
