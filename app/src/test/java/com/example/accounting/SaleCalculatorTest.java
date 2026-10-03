package com.example.accounting;

import static org.junit.Assert.assertEquals;

import com.example.accounting.util.SaleCalculator;

import org.junit.Test;

/**
 * 销售金额计算的单元测试。
 * 核心是验证"分 × 千分数量"的整数四舍五入全程无浮点参与。
 */
public class SaleCalculatorTest {

    @Test
    public void 行小计_整数数量() {
        // 350分 × 2个 = 700分
        assertEquals(700L, SaleCalculator.lineTotalCents(350, 2000));
    }

    @Test
    public void 行小计_小数数量四舍五入() {
        // 333分 × 1.5 = 499.5分 -> 四舍五入 500
        assertEquals(500L, SaleCalculator.lineTotalCents(333, 1500));
        // 333分 × 1.4 = 466.2分 -> 466
        assertEquals(466L, SaleCalculator.lineTotalCents(333, 1400));
    }

    @Test
    public void 行小计_非法输入返回0() {
        assertEquals(0L, SaleCalculator.lineTotalCents(-1, 1000));
        assertEquals(0L, SaleCalculator.lineTotalCents(100, 0));
        assertEquals(0L, SaleCalculator.lineTotalCents(100, -500));
    }

    @Test
    public void 单据合计_减优惠() {
        assertEquals(1000L, SaleCalculator.orderTotalCents(1100, 100));
    }

    @Test
    public void 单据合计_优惠大于合计时不出现负数() {
        // 手滑输入大额优惠：账面最低 0，不出负账
        assertEquals(0L, SaleCalculator.orderTotalCents(500, 800));
    }

    @Test
    public void 单据合计_负优惠按0处理() {
        assertEquals(1100L, SaleCalculator.orderTotalCents(1100, -50));
    }
}
