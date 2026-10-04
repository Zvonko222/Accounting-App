package com.example.accounting.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数量换算：数据库存"数量×1000"的整数（1.7 斤 = 1700），界面显示原数量。
 * 与金额同理，用整数避开浮点误差；×1000 的存在感被限制在本类之内。
 */
public final class QuantityUtil {

    private QuantityUtil() {
    }

    /** 1700 -> "1.7"，2000 -> "2"，-1500 -> "-1.5" */
    public static String toDisplay(long quantityMilli) {
        return BigDecimal.valueOf(quantityMilli)
                .movePointLeft(3)
                .stripTrailingZeros()
                .toPlainString();
    }

    /**
     * "1.7" / "1.750" / "2" -> 1750 / 2 -> 2000。
     *
     * @return 非法输入（空串、负数、非数字、超过一百万）返回 null
     */
    public static Long parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(trimmed);
            // 数量必须大于 0：0 与负数视为无效输入（删行走 UI 的减号，不走输 0）
            if (value.signum() <= 0) {
                return null;
            }
            BigDecimal milli = value.movePointRight(3).setScale(0, RoundingMode.HALF_UP);
            if (milli.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) {
                return null;
            }
            return milli.longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 盘点数量解析：允许 0，但不允许负数。 */
    public static Long parseNonNegative(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(trimmed);
            if (value.signum() < 0) {
                return null;
            }
            BigDecimal milli = value.movePointRight(3).setScale(0, RoundingMode.HALF_UP);
            if (milli.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) {
                return null;
            }
            return milli.longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
