package com.example.accounting.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 系统栏避让工具。
 *
 * targetSdk 35 起系统强制 edge-to-edge：页面内容会顶到状态栏/挖孔下面。
 * 对根视图调用本方法，即可把状态栏高度加回顶部 padding。
 * 记录初始 padding：窗口尺寸变化（旋转/分屏）回调多次时不会叠加。
 */
public final class InsetsUtil {

    private InsetsUtil() {
    }

    public static void applyTopInset(View view) {
        final int initialTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), initialTop + bars.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }
}
