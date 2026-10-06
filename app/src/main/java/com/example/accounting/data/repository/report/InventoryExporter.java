package com.example.accounting.data.repository.report;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.ProductWithCategory;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 商品库存表导出：名称/分类/库存/单位/售价/进价/状态（盘点与对账用）。
 * CSV（UTF-8 BOM，Excel 直开）。
 */
public final class InventoryExporter {

    private InventoryExporter() {
    }

    /** @return 导出商品数 */
    public static int export(AppDatabase db, @NonNull Uri destination,
                             @NonNull Context context) throws Exception {
        List<ProductWithCategory> products =
                db.productDao().listAllWithCategory();

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("商品名,分类,库存,单位,售价(元),进价(元),状态\n");
        for (ProductWithCategory p : products) {
            String status = p.product.stockQuantityMilli < 0 ? "需盘点"
                    : (p.product.lowStockThresholdMilli > 0
                       && p.product.stockQuantityMilli <= p.product.lowStockThresholdMilli)
                    ? "库存不足" : "正常";
            csv.append(csvCell(p.product.name)).append(',')
               .append(csvCell(p.categoryName)).append(',')
               .append(csvCell(QuantityUtil.toDisplay(p.product.stockQuantityMilli))).append(',')
               .append(csvCell(p.product.unit)).append(',')
               .append(csvCell(MoneyUtil.toDisplay(p.product.salePriceCents))).append(',')
               .append(csvCell(MoneyUtil.toDisplay(p.product.purchasePriceCents))).append(',')
               .append(status).append('\n');
        }

        try (OutputStream out = context.getContentResolver().openOutputStream(destination)) {
            if (out == null) {
                throw new Exception("无法写入所选位置");
            }
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        }
        return products.size();
    }

    private static String csvCell(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
}
