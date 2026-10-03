package com.example.accounting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.accounting.data.model.OcrLine;
import com.example.accounting.util.OcrLineParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 进货单文字行解析器的单元测试。
 * 解析错了账就记错了，这里把常见单据格式和噪声行全部钉死。
 */
public class OcrLineParserTest {

    @Test
    public void 标准格式_品名_数量_进价() {
        OcrLine line = OcrLineParser.parse("可乐 2 3.50");
        assertEquals("可乐", line.productName);
        assertEquals(2000L, line.quantityMilli);
        assertEquals(350L, line.unitCostCents);
        assertTrue(line.valid);
    }

    @Test
    public void 数量粘在名字上_可乐x2() {
        OcrLine line = OcrLineParser.parse("可乐x2 3.5");
        assertEquals("可乐", line.productName);
        assertEquals(2000L, line.quantityMilli);
        assertEquals(350L, line.unitCostCents);
    }

    @Test
    public void 乘号和货币符号() {
        OcrLine line = OcrLineParser.parse("薯片 ×3 12.00元");
        assertEquals("薯片", line.productName);
        assertEquals(3000L, line.quantityMilli);
        assertEquals(1200L, line.unitCostCents);
    }

    @Test
    public void 全角数字与标点() {
        OcrLine line = OcrLineParser.parse("可乐　２　３．５０，");
        assertEquals("可乐", line.productName);
        assertEquals(2000L, line.quantityMilli);
        assertEquals(350L, line.unitCostCents);
    }

    @Test
    public void 小数数量_称重商品() {
        OcrLine line = OcrLineParser.parse("苹果 1.5 6.00");
        assertEquals("苹果", line.productName);
        assertEquals(1500L, line.quantityMilli);
        assertEquals(600L, line.unitCostCents);
    }

    @Test
    public void 只有一个数字_当作进价数量按1() {
        OcrLine line = OcrLineParser.parse("可乐 3.50");
        assertEquals("可乐", line.productName);
        assertEquals(1000L, line.quantityMilli);
        assertEquals(350L, line.unitCostCents);
    }

    @Test
    public void 价格在前面也能解析() {
        OcrLine line = OcrLineParser.parse("¥3.50 可乐 2");
        assertEquals("可乐", line.productName);
        assertEquals(2000L, line.quantityMilli);
        assertEquals(350L, line.unitCostCents);
    }

    @Test
    public void 表尾行被丢弃() {
        assertNull(OcrLineParser.parse("合计 25.00"));
        assertNull(OcrLineParser.parse("金额：12.00"));
        assertNull(OcrLineParser.parse("数量"));
    }

    @Test
    public void 纯数字和过短行被丢弃() {
        assertNull(OcrLineParser.parse("123"));
        assertNull(OcrLineParser.parse("a"));
        assertNull(OcrLineParser.parse(""));
        assertNull(OcrLineParser.parse(null));
        assertNull(OcrLineParser.parse("3.50 2")); // 没有品名
    }

    @Test
    public void 进价为0标记为无效但保留品名() {
        // 赠品行或识别噪声：不给用户静默导入，标无效让用户决定
        OcrLine line = OcrLineParser.parse("可乐 2 0");
        assertEquals("可乐", line.productName);
        assertFalse(line.valid);
    }

    @Test
    public void 批量解析_混合行() {
        List<OcrLine> lines = OcrLineParser.parseAll(Arrays.asList(
                "进货单", "可乐 2 3.50", "合计 7.00", "zhuozi 1 20.00"));
        assertEquals(2, lines.size());
        assertEquals("可乐", lines.get(0).productName);
        assertEquals("zhuozi", lines.get(1).productName);
    }
}
