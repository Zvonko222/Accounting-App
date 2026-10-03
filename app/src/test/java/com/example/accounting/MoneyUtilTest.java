package com.example.accounting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.accounting.util.MoneyUtil;

import org.junit.Test;

/**
 * 金额换算的单元测试。
 * 记账软件的算错一分钱都是事故，所以边界值必须钉死。
 */
public class MoneyUtilTest {

    @Test
    public void 分转元_普通值() {
        assertEquals("12.50", MoneyUtil.toDisplay(1250));
        assertEquals("0.00", MoneyUtil.toDisplay(0));
        assertEquals("0.05", MoneyUtil.toDisplay(5));
        assertEquals("9999.99", MoneyUtil.toDisplay(999999));
    }

    @Test
    public void 分转元_负数() {
        assertEquals("-12.50", MoneyUtil.toDisplay(-1250));
    }

    @Test
    public void 元转分_普通输入() {
        assertEquals(Long.valueOf(1250L), MoneyUtil.parseYuan("12.5"));
        assertEquals(Long.valueOf(1250L), MoneyUtil.parseYuan("12.50"));
        assertEquals(Long.valueOf(1200L), MoneyUtil.parseYuan("12"));
        assertEquals(Long.valueOf(1L), MoneyUtil.parseYuan("0.01"));
    }

    @Test
    public void 元转分_带空格() {
        assertEquals(Long.valueOf(1250L), MoneyUtil.parseYuan(" 12.5 "));
    }

    @Test
    public void 元转分_非法输入返回null() {
        assertNull(MoneyUtil.parseYuan(null));
        assertNull(MoneyUtil.parseYuan(""));
        assertNull(MoneyUtil.parseYuan("abc"));
        assertNull(MoneyUtil.parseYuan("12.3.4"));
    }

    @Test
    public void 元转分_超过一亿返回null() {
        assertNull(MoneyUtil.parseYuan("100000000")); // 一亿元 = 上限，拒绝
        assertEquals(Long.valueOf(9999999999L), MoneyUtil.parseYuan("99999999.99"));
    }

    @Test
    public void 元转分_四舍五入到分() {
        // 三位小数：银行家们吵了几个世纪的问题，这里直接四舍五入
        assertEquals(Long.valueOf(1251L), MoneyUtil.parseYuan("12.505"));
        assertEquals(Long.valueOf(1250L), MoneyUtil.parseYuan("12.504"));
    }
}
