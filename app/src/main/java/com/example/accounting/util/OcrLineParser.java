package com.example.accounting.util;

import com.example.accounting.data.model.OcrLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进货单文字行的解析器 v2：把 OCR 识别出的一行文本拆成 品名 / 数量 / 进价。
 *
 * 纯 Java 逻辑，无 Android 依赖，配单元测试。
 *
 * 解析策略（宁可解析出"数量或价格待补"的无效行让用户补，也不轻易整行丢弃）：
 * 1. 清洗：全角转半角、货币符号/单位字剥离、顿号逗号全角空格归一为空格；
 * 2. token 分类：数字 / 数量标记（×3、x2、可乐x2）/ 品名 / 独立单位字（丢弃）；
 * 3. 数量歧义：带小数点的更像价格（金额常带分位），整数更像数量；
 *    分不出再按位置（最后=进价，倒数第二=数量）；
 * 4. 只有品名没有数字的行保留为"无效行"——用户在确认界面补价后即可导入。
 */
public final class OcrLineParser {

    private OcrLineParser() {
    }

    /** 表头表尾这类"不是商品"的词：整行等于其中一个词就丢弃 */
    private static final List<String> STOP_WORDS = Arrays.asList(
            "合计", "总计", "金额", "数量", "单价", "品名", "名称", "货品",
            "日期", "编号", "单号", "店名", "地址", "电话", "应收", "实收",
            "找零", "小计", "优惠", "折扣", "会员", "货号", "条码", "积分",
            "送货", "收货", "签名", "单位", "备注", "进货单", "销货单", "清单");

    /** 独立出现的单位/规格字：不算品名也不算数字，直接丢弃 */
    private static final List<String> STANDALONE_UNITS = Arrays.asList(
            "元", "块", "瓶", "包", "个", "盒", "袋", "箱", "支", "听",
            "斤", "kg", "g", "ml", "l", "m", "cm", "件", "桶", "卷");

    /** 名字尾巴上粘着数量："可乐x2" / "可乐×2" / "可乐*2.5" */
    private static final Pattern TRAILING_QUANTITY =
            Pattern.compile("^(.+?)[xX×*](\\d+(?:\\.\\d+)?)$");

    /** OCR 常把乘号丢掉："可乐2" / "苹果1.5"。仅在前缀含中文时启用。 */
    private static final Pattern TRAILING_BARE_QUANTITY =
            Pattern.compile("^(.+?[\\u4e00-\\u9fa5])([0-9]+(?:\\.[0-9]+)?)$");

    /** 独立的数量标记："×3" / "x2"（前面没有品名的乘号） */
    private static final Pattern BARE_QUANTITY =
            Pattern.compile("^[xX×*](\\d+(?:\\.\\d+)?)$");

    /** 价格×数量："3.50*2" / "3.5x2"（乘号前是数字） */
    private static final Pattern PRICE_TIMES_QTY =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)[xX×*](\\d+(?:\\.\\d+)?)$");

    /** 纯数字 token（已清洗过货币符号后） */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\d+(\\.\\d+)?$");

    /** 含中文或字母（品名 token 的最低要求） */
    private static final Pattern HAS_NAME_CHAR = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z]");

    /** 把一行原始文本解析成 OcrLine（无品名才返回 null） */
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
        Long attachedPriceCents = null;
        Long priceTimesQtyCents = null;   // "3.5*2" 形式：价格与数量一体

        for (String token : normalized.split(" ")) {
            String cleaned = stripCurrencySuffix(token);
            if (cleaned.isEmpty()) {
                continue;
            }
            String numericCleaned = normalizeOcrNumber(cleaned);
            if (PLAIN_NUMBER.matcher(numericCleaned).matches()) {
                numberTokens.add(numericCleaned);
                continue;
            }
            Matcher priceQty = PRICE_TIMES_QTY.matcher(cleaned);
            if (priceQty.matches()) {
                numberTokens.add(priceQty.group(1));            // 价格按普通数字参与歧义判定
                attachedQuantityMilli = QuantityUtil.parse(priceQty.group(2));
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
            if (trailing.matches() && HAS_NAME_CHAR.matcher(trailing.group(1)).find()) {
                Long qty = QuantityUtil.parse(trailing.group(2));
                if (qty != null) {
                    nameTokens.add(trailing.group(1));
                    attachedQuantityMilli = qty;
                    continue;
                }
            }
            Matcher bareTrailing = TRAILING_BARE_QUANTITY.matcher(cleaned);
            if (bareTrailing.matches()) {
                Long qty = QuantityUtil.parse(bareTrailing.group(2));
                if (qty != null && !bareTrailing.group(1).matches(".*\\d$")) {
                    nameTokens.add(bareTrailing.group(1));
                    attachedQuantityMilli = qty;
                    continue;
                }
            }
            // 独立单位字（元/瓶/kg…）不进品名
            if (STANDALONE_UNITS.contains(cleaned.toLowerCase())) {
                continue;
            }
            nameTokens.add(cleaned);
        }

        String name = joinName(nameTokens);
        if (name.length() < 2 || STOP_WORDS.contains(name.replace(" ", ""))
                || isHeaderNoise(name, numberTokens)) {
            return null;
        }

        Long unitCostCents = attachedPriceCents;
        Long quantityMilli = attachedQuantityMilli;

        if (!numberTokens.isEmpty()) {
            if (numberTokens.size() == 1) {
                String only = numberTokens.get(0);
                if (only.contains(".")) {
                    // 单个带小数的数字：几乎一定是价格
                    unitCostCents = MoneyUtil.parseYuan(only);
                    if (quantityMilli == null) {
                        quantityMilli = 1000L;
                    }
                } else {
                    // 单个整数：更可能是数量，价格未知 → 行标无效待补
                    if (quantityMilli == null) {
                        quantityMilli = QuantityUtil.parse(only);
                    }
                }
            } else {
                String lastText = numberTokens.get(numberTokens.size() - 1);
                String prevText = numberTokens.get(numberTokens.size() - 2);
                boolean lastHasDot = lastText.contains(".");
                boolean prevHasDot = prevText.contains(".");
                if (!lastHasDot && prevHasDot) {
                    // 只有前面的数带小数：它更像价格
                    unitCostCents = MoneyUtil.parseYuan(prevText);
                    if (quantityMilli == null) {
                        quantityMilli = QuantityUtil.parse(lastText);
                    }
                } else {
                    unitCostCents = MoneyUtil.parseYuan(lastText);
                    if (quantityMilli == null) {
                        quantityMilli = QuantityUtil.parse(prevText);
                    }
                }
            }
        }

        if (unitCostCents == null || unitCostCents < 0 || quantityMilli == null) {
            // 价格或数量解析失败：保留品名（数量给个 1 起步），用户在确认界面补
            return new OcrLine(rawText, name,
                    quantityMilli == null ? 1000L : quantityMilli, 0);
        }

        return new OcrLine(rawText, name, quantityMilli, unitCostCents);
    }

    /** 批量解析：只丢弃连品名都认不出的行 */
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
     * 归一化：全角转半角、全角空格和顿号逗号归一为空格、多空格合一。
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
                    || ch == '；' || ch == '\t' || ch == '|' || ch == '/') {
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
        cleaned = cleaned.replaceFirst("^[¥￥$]", "");
        cleaned = cleaned.replaceFirst("^(元|人民币|RMB|rmb)", "");
        cleaned = cleaned.replaceFirst("(元|块|RMB|rmb)$", "");
        return cleaned;
    }

    /** 处理 OCR 常见的数字混淆，只对数字 token 使用。 */
    private static String normalizeOcrNumber(String token) {
        return token.replace('O', '0').replace('o', '0')
                .replace('I', '1').replace('l', '1');
    }

    private static boolean isHeaderNoise(String name, List<String> numbers) {
        if (!numbers.isEmpty()) return false;
        String compact = name.replace(" ", "");
        int hits = 0;
        for (String word : STOP_WORDS) {
            if (compact.contains(word)) hits++;
            if (hits >= 2) return true;
        }
        return false;
    }

    /** 拼接品名：跳过被清空的 token、纯数字 token 和独立单位字 */
    private static String joinName(List<String> tokens) {
        StringBuilder name = new StringBuilder();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty() || PLAIN_NUMBER.matcher(trimmed).matches()
                    || STANDALONE_UNITS.contains(trimmed.toLowerCase())) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(trimmed);
        }
        return name.toString();
    }
}



