package com.example.accounting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.accounting.util.QuantityUtil;

import org.junit.Test;

/**
 * 数量换算的单元测试：数据库存 ×1000 整数，界面显示原数量。
 */
public class QuantityUtilTest {

    @Test
    public void 毫转显示_普通值() {
        assertEquals("1.7", QuantityUtil.toDisplay(1700));
        assertEquals("2", QuantityUtil.toDisplay(2000));   // 整数不带小数点
        assertEquals("1.5", QuantityUtil.toDisplay(1500));
        assertEquals("0", QuantityUtil.toDisplay(0));
        assertEquals("-1.5", QuantityUtil.toDisplay(-1500)); // 负库存显示
    }

    @Test
    public void 显示转毫_普通值() {
        assertEquals(Long.valueOf(1700L), QuantityUtil.parse("1.7"));
        assertEquals(Long.valueOf(2000L), QuantityUtil.parse("2"));
        assertEquals(Long.valueOf(1750L), QuantityUtil.parse("1.75"));
        assertEquals(Long.valueOf(1L), QuantityUtil.parse("0.001"));
    }

    @Test
    public void 显示转毫_非法输入返回null() {
        assertNull(QuantityUtil.parse(null));
        assertNull(QuantityUtil.parse(""));
        assertNull(QuantityUtil.parse("abc"));
        assertNull(QuantityUtil.parse("-1"));   // 数量不允许负数输入
        assertNull(QuantityUtil.parse("0"));
    }
}
