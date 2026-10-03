package com.example.accounting.data.model;

/**
 * 开单时购物车里的一行（还没入库的草稿）。
 * 保存时 Repository 把它转成 SaleItem（含价格/名称快照）。
 */
public class SaleCartLine {

    public final String productId;

    /** 快照来源：加入购物车时的商品名 */
    public final String productName;

    /** 快照来源：售价（分）。行编辑弹窗里允许按单改价（可变） */
    public long unitPriceCents;

    /** 数量（×1000）。可变：购物车里可以加减 */
    public long quantityMilli;

    public SaleCartLine(String productId, String productName,
                        long unitPriceCents, long quantityMilli) {
        this.productId = productId;
        this.productName = productName;
        this.unitPriceCents = unitPriceCents;
        this.quantityMilli = quantityMilli;
    }

    /** 行小计（分） */
    public long lineTotalCents() {
        return com.example.accounting.util.SaleCalculator
                .lineTotalCents(unitPriceCents, quantityMilli);
    }
}
