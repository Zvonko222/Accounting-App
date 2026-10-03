package com.example.accounting.util;

import com.example.accounting.data.model.OcrLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进货单文字行的解析器：把 OCR 识别出的一行文本拆成 品名 / 数量 / 进价。
 *
 * 这是纯 Java 逻辑，没有任何 Android 依赖，可以（也必须）配单元测试。
 *
 * 能看懂的行（常见进货单格式）：
 *   "可乐 2 3.50"      → 可乐 ×2，进价 3.50
 *   "可乐x2 3.5"       → 数量粘在名字上
 *   "薯片 ×3 12.00元"  → 全角/货币符号/单位字都会被清洗
 *   "农夫山泉 1.5 2"   → 数量支持小数（称重）
 *   "可乐 3.50"        → 只有一个数字时当作进价，数量按 1
 *
 * 看不懂的行返回 null（调用方直接丢弃）：表头表尾（合计/金额/数量…）、
 * 纯数字、太短的行。识别不准的行宁可丢弃——用户手动补，绝不猜着记错账。
 */
public final class OcrLineParser {

    private OcrLineParser() {
    }

    /** 表头表尾这类"不是商品"的词：整行等于其中一个词就丢弃 */
    private static final List<String> STOP_WORDS = Arrays.asList(
            "合计", "总计", "金额", "数量", "单价", "品名", "名称", "货品",
            "日期", "编号", "单号", "店名", "地址", "电话", "应收", "实收",
            "找零", "小计", "优惠", "折扣", "会员", "单位", "备注", "元",
            "货号", "条码", "积分", "送货", "收货", "签名");

    /** 名字尾巴上粘着数量："可乐x2" / "可乐×2" / "可乐*2.5" */
    private static final Pattern TRAILING_QUANTITY =
            Pattern.compile("^(.+?)[xX×*](\\d+(?:\\.\\d+)?)$");

    /** 独立的数量标记："×3" / "x2"（前面没有品名的乘号） */
    private static final Pattern BARE_QUANTITY =
            Pattern.compile("^[xX×*](\\d+(?:\\.\\d+)?)$");

    /** 纯数字 token（已清洗过货币符号后） */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\d+(\\.\\d+)?$");

    /** 把一行原始文本解析成 OcrLine；解析不出有效商品返回 null */
    public static OcrLine parse(String rawText) {
        if (rawText == null) {
            return null;
        }
        String normalized = normalize(rawText);
        if (normalized.isEmpty()) {
            return null;
        }

        List<String> nameTokens = new ArrayList<>();
        List<String> numberTokens = new ArrayList<>();
        Long attachedQuantityMilli = null;

        for (String token : normalized.split(" ")) {
            String cleaned = stripCurrencySuffix(token);
            if (PLAIN_NUMBER.matcher(cleaned).matches()) {
                numberTokens.add(cleaned);
                continue;
            }
            Matcher bareQty = BARE_QUANTITY.matcher(cleaned);
            if (bareQty.matches()) {
                Long qty = QuantityUtil.parse(bareQty.group(1));
                if (qty != null) {
                    attachedQuantityMilli = qty;
                    continue;
                }
            }
            Matcher trailing = TRAILING_QUANTITY.matcher(cleaned);
            if (trailing.matches()) {
                Long qty = QuantityUtil.parse(trailing.group(2));
                if (qty != null) {
                    nameTokens.add(trailing.group(1));
                    attachedQuantityMilli = qty;
                    continue;
                }
            }
            nameTokens.add(cleaned);
        }

        String name = joinName(nameTokens);
        if (name.length() < 2 || STOP_WORDS.contains(name)) {
            return null;
        }

        // 数字解读：只有 1 个 → 进价（数量按 1 或名字上粘的数）；
        // 2 个以上 → 看"带小数点的更像价格"（金额总带分位，数量通常是整数），
        // 分不出来再按位置（最后一个是价格，倒数第二是数量）。
        Long unitCostCents;
        Long quantityMilli;
        if (numberTokens.isEmpty()) {
            return null; // 没有价格，不是一条能用的进货行
        }
        if (numberTokens.size() == 1) {
            unitCostCents = MoneyUtil.parseYuan(numberTokens.get(0));
            quantityMilli = attachedQuantityMilli != null
                    ? attachedQuantityMilli : 1000L;
        } else {
            String lastText = numberTokens.get(numberTokens.size() - 1);
            String prevText = numberTokens.get(numberTokens.size() - 2);
            boolean lastHasDot = lastText.contains(".");
            boolean prevHasDot = prevText.contains(".");
            if (!lastHasDot && prevHasDot) {
                // 只有前面的数带小数：它更像价格，整数的那个更像数量
                unitCostCents = MoneyUtil.parseYuan(prevText);
                quantityMilli = attachedQuantityMilli != null
                        ? attachedQuantityMilli : QuantityUtil.parse(lastText);
            } else {
                unitCostCents = MoneyUtil.parseYuan(lastText);
                quantityMilli = attachedQuantityMilli != null
                        ? attachedQuantityMilli : QuantityUtil.parse(prevText);
            }
        }

        if (unitCostCents == null || unitCostCents < 0 || quantityMilli == null) {
            // 价格或数量解析失败：保留品名让用户手补，但标记为无效
            return new OcrLine(rawText, name, 0, 0);
        }

        return new OcrLine(rawText, name, quantityMilli, unitCostCents);
    }

    /** 批量解析：丢掉所有 null 行 */
    public static List<OcrLine> parseAll(List<String> rawLines) {
        List<OcrLine> lines = new ArrayList<>();
        if (rawLines == null) {
            return lines;
        }
        for (String raw : rawLines) {
            OcrLine line = parse(raw);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // ---------------- 文本清洗 ----------------

    /**
     * 归一化：全角转半角、全角空格和顿号逗号变空格、多空格合一。
     * 只做这些安全变换，不动汉字。
     */
    static String normalize(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            if (ch >= '０' && ch <= '９') {
                builder.append((char) (ch - '０' + '0'));
            } else if (ch == '．' || ch == '。') {
                builder.append('.');
            } else if (ch == 'ｘ' || ch == 'Ｘ') {
                builder.append('x');
            } else if (ch == '　' || ch == '，' || ch == '、' || ch == '：' || ch == ';'
                    || ch == '；' || ch == '\t' || ch == '|') {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString().trim().replaceAll(" +", " ");
    }

    /** 去掉数字两侧的货币符号和单位："¥3.50" "3.50元" "￥3.5块" → "3.50" */
    static String stripCurrencySuffix(String token) {
        String cleaned = token;
        cleaned = cleaned.replaceFirst("^[¥￥￥¥$]", "");
        cleaned = cleaned.replaceFirst("^(元|人民币|RMB|rmb)", "");
        cleaned = cleaned.replaceFirst("(元|块|RMB|rmb)$", "");
        return cleaned;
    }

    /** 拼接品名：跳过被清空的 token，排除纯数字 token */
    private static String joinName(List<String> tokens) {
        StringBuilder name = new StringBuilder();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty() || PLAIN_NUMBER.matcher(trimmed).matches()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(trimmed);
        }
        return name.toString();
    }

    /** 供测试使用：数字判断 */
    static boolean isPlainNumber(String token) {
        return PLAIN_NUMBER.matcher(token).matches();
    }
}
