package com.example.accounting.data.repository.report;

import androidx.annotation.NonNull;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.StockMovementDao;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据体检：对记账软件而言，"账对得上"比任何功能都重要。
 *
 * 检查项：
 * 1. 库存恒等式——每个商品当前库存应等于 期初 + Σ台账变动；
 *    不等说明某次写入绕过了台账（或历史数据迁移差异），体检会点名商品与差额；
 * 2. 负库存商品——合法（先卖后补）但通常意味着该盘点；
 * 3. 备份状态——上次本机备份距今多久。
 *
 * 只读不写：体检永远不会修改数据。
 */
public final class DataCheck {

    private DataCheck() {
    }

    /**
     * @param db 数据库
     * @param lastBackupAt 上次本机备份时间（0=从未）
     * @param pendingOverdue 待交付超期单（调用方按 3 天查询），文本由本方法拼接
     */
    public static String run(@NonNull AppDatabase db, long lastBackupAt,
                             @NonNull List<com.example.accounting.data.db.entity.Sale> pendingOverdue) {
        StringBuilder report = new StringBuilder();
        int problems = 0;

        // ---- 1. 库存恒等式 ----
        StockMovementDao movementDao = db.stockMovementDao();
        List<Product> products = db.productDao().findByIdForCheckAll();
        Map<String, Long> netByProduct = new HashMap<>();
        for (com.example.accounting.data.db.entity.StockMovement m : movementDao.listAllForCheck()) {
            Long net = netByProduct.get(m.productId);
            netByProduct.put(m.productId, (net == null ? 0 : net) + m.changeQuantityMilli);
        }

        int mismatch = 0;
        for (Product p : products) {
            long expected = netByProduct.containsKey(p.id) ? netByProduct.get(p.id) : 0;
            if (p.stockQuantityMilli != expected) {
                mismatch++;
                if (mismatch <= 5) {
                    report.append("· 库存对不上：").append(p.name)
                          .append("（账面 ").append(QuantityUtil.toDisplay(p.stockQuantityMilli))
                          .append("，按台账应为 ").append(QuantityUtil.toDisplay(expected))
                          .append("）\n");
                }
            }
        }
        if (mismatch == 0) {
            report.append("· 库存恒等式：全部 ").append(products.size())
                  .append(" 个商品核对一致\n");
        } else {
            report.append("· 共 ").append(mismatch)
                  .append(" 个商品库存与台账不一致（可用盘点修正）\n");
            problems += mismatch;
        }

        // ---- 2. 负库存 ----
        int negative = 0;
        StringBuilder negativeNames = new StringBuilder();
        for (Product p : products) {
            if (p.stockQuantityMilli < 0) {
                negative++;
                if (negative <= 3) {
                    negativeNames.append("· ").append(p.name).append("：")
                            .append(QuantityUtil.toDisplay(p.stockQuantityMilli))
                            .append(p.unit).append("\n");
                }
            }
        }
        if (negative == 0) {
            report.append("· 负库存：无\n");
        } else {
            report.append("· 负库存 ").append(negative).append(" 个商品（先卖后补，建议进货或盘点）：\n");
            report.append(negativeNames);
            problems += negative;
        }

        // ---- 2.5 待交付超期（外卖/预订超过 3 天未交付） ----
        if (!pendingOverdue.isEmpty()) {
            report.append("· 待交付超期 ").append(pendingOverdue.size())
                  .append(" 单（超过 3 天未交付，尽快联系客户）：\n");
            int shown = Math.min(pendingOverdue.size(), 3);
            for (int i = 0; i < shown; i++) {
                com.example.accounting.data.db.entity.Sale sale = pendingOverdue.get(i);
                report.append("  - ").append(sale.customerName == null ? "（未留客户）" : sale.customerName)
                      .append(" ").append(com.example.accounting.util.TimeUtil.formatFull(sale.saleTime))
                      .append(" ").append(MoneyUtil.toYuan(sale.totalAmountCents)).append("\n");
            }
            problems += pendingOverdue.size();
        }

        // ---- 3. 备份状态 ----
        if (lastBackupAt == 0) {
            report.append("· 本机备份：从未备份过（设置页可手动备份）\n");
            problems++;
        } else {
            long days = (System.currentTimeMillis() - lastBackupAt) / (24 * 60 * 60 * 1000);
            report.append("· 本机备份：").append(days).append(" 天前");
            if (days > 7) {
                report.append("（超过 7 天，建议检查自动备份）");
                problems++;
            }
            report.append('\n');
        }

        if (problems == 0) {
            report.insert(0, "体检通过，未发现问题。\n\n");
        } else {
            report.insert(0, "发现 " + problems + " 项需要关注：\n\n");
        }
        report.append("\n体检时间：")
              .append(com.example.accounting.util.TimeUtil.formatFull(System.currentTimeMillis()));
        return report.toString();
    }
}
