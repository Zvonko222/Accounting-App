package com.example.accounting.data.model;

/**
 * 记进货时"待入库清单"里的一行（还没入库的草稿）。
 * 保存时 Repository 把它转成 PurchaseItem，并给商品补库存。
 */
public class PurchaseCartLine {

    /**
     * 商品 id。手动开单时是已存在的商品；拍照导入的行可以是 null
     * （Repository 保存时按品名查找，找不到会自动建新商品并回填 id）。
     */
    public String productId;

    public final String productName;

    /** 本单进价（分）。可变：每次进货价格可能不同 */
    public long unitCostCents;

    /** 数量（×1000）。可变 */
    public long quantityMilli;

    public PurchaseCartLine(String productId, String productName,
                            long unitCostCents, long quantityMilli) {
        this.productId = productId;
        this.productName = productName;
        this.unitCostCents = unitCostCents;
        this.quantityMilli = quantityMilli;
    }

    /** 行小计（分） */
    public long lineTotalCents() {
        return com.example.accounting.util.SaleCalculator
                .lineTotalCents(unitCostCents, quantityMilli);
    }
}
