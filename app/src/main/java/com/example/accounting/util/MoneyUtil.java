package com.example.accounting.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额换算：数据库存 long 分，界面显示元。全 App 金额显示/输入只走这里。
 *
 * 为什么不用 double：二进制浮点无法精确表示 0.1，
 * 记账软件用浮点存钱迟早出现 12.499999999 这类错误（ARCHITECTURE.md 2.1）。
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /** 1250 -> "12.50"，-1250 -> "-12.50" */
    public static String toDisplay(long cents) {
        return BigDecimal.valueOf(cents)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** 带人民币符号：1250 -> "¥12.50" */
    public static String toYuan(long cents) {
        return "¥" + toDisplay(cents);
    }

    /**
     * "12.5" / "12.50" / "12" -> 1250。
     *
     * @return 非法输入（空串、非数字、超过一亿元）返回 null，由界面提示用户
     */
    public static Long parseYuan(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            BigDecimal cents = new BigDecimal(trimmed)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP);
            // 上限一亿元（分）：达到即拒绝，防手滑输入离谱数字
            if (cents.abs().compareTo(BigDecimal.valueOf(10_000_000_000L)) >= 0) {
                return null;
            }
            return cents.longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
