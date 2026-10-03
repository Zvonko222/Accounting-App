package com.example.accounting.data.model;

import java.io.Serializable;

/**
 * 拍照导入的一行识别结果（还没入库的草稿）。
 *
 * 由 OcrLineParser 从 OCR 文本行解析而来；在确认界面里用户可以
 * 勾选、修改数量和进价，最后转成 PurchaseCartLine 保存。
 * 实现 Serializable 以便通过 Activity Result 传回进货页。
 */
public class OcrLine implements Serializable {

    /** 原始识别文本，确认界面里展示给用户核对 */
    public final String rawText;

    /** 解析出的商品名 */
    public final String productName;

    /** 数量（×1000） */
    public long quantityMilli;

    /** 进价（分） */
    public long unitCostCents;

    /** 这一行是否够格导入（数量 > 0 且进价 >= 0） */
    public boolean valid;

    /** 用户是否勾选了这一行 */
    public boolean checked;

    public OcrLine(String rawText, String productName,
                   long quantityMilli, long unitCostCents) {
        this.rawText = rawText == null ? "" : rawText;
        this.productName = productName == null ? "" : productName;
        this.quantityMilli = quantityMilli;
        this.unitCostCents = unitCostCents;
        // 进价为 0 的行多半是赠品或识别噪声：默认无效，用户在确认界面手动放行
        this.valid = quantityMilli > 0 && unitCostCents > 0;
        this.checked = this.valid;
    }

    public void setValues(long quantityMilli, long unitCostCents) {
        this.quantityMilli = quantityMilli;
        this.unitCostCents = unitCostCents;
        this.valid = quantityMilli > 0 && unitCostCents > 0;
        if (valid) {
            this.checked = true;
        }
    }
}
