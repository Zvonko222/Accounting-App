package com.example.accounting.util;

/**
 * 销售金额计算。纯函数、无 Android 依赖，可以直接写单元测试。
 *
 * 单价 × 数量 = 分 × 千分数量，结果是"千分位分"（×1000），
 * 需要四舍五入到分。这里用整数实现 (+500)/1000 的四舍五入，
 * 避免 double 的 0.1 精度问题，全程误差为 0。
 */
public final class SaleCalculator {

    private SaleCalculator() {
    }

    /**
     * 行小计 = 单价(分) × 数量(千分) 四舍五入到分。
     * 例：单价 333 分 × 1.5 个 = 499.5 分 -> 500 分。
     */
    public static long lineTotalCents(long unitPriceCents, long quantityMilli) {
        if (unitPriceCents < 0 || quantityMilli <= 0) {
            return 0;
        }
        return (unitPriceCents * quantityMilli + 500) / 1000;
    }

    /**
     * 单据合计 = Σ行小计 − 优惠。优惠不允许把单据打成负数。
     */
    public static long orderTotalCents(long sumOfLineTotalsCents, long discountCents) {
        if (discountCents < 0) {
            discountCents = 0;
        }
        return Math.max(sumOfLineTotalsCents - discountCents, 0);
    }
}
