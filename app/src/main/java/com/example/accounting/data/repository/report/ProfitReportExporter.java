package com.example.accounting.data.repository.report;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.StatisticsDao;
import com.example.accounting.data.model.DailySales;
import com.example.accounting.util.MoneyUtil;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 月度利润表导出：近 90 天按日汇总
 * （销售额[已扣退货] / 毛利[快照成本口径] / 支出[进货+其他] / 当日净结余），
 * 顶部附期间合计行。写 CSV（UTF-8 BOM，Excel 直开）。
 *
 * 与备份的区别：这是给"人看/对账"的报表；备份是给"恢复数据"用的整库文件。
 */
public final class ProfitReportExporter {

    private static final long NINETY_DAYS = 90L * 24 * 60 * 60 * 1000;

    private ProfitReportExporter() {
    }

    /** 导出结果：写入行数（供提示） */
    public static int export(AppDatabase db, @NonNull Uri destination,
                             @NonNull Context context) throws Exception {
        StatisticsDao dao = db.statisticsDao();
        long from = System.currentTimeMillis() - NINETY_DAYS;

        List<DailySales> sales = dao.listDailySalesSince(from);
        List<DailySales> profit = dao.listDailyProfitSince(from);
        List<DailySales> expense = dao.listDailyExpenseSince(from);

        // 三个序列按日期合并成一张表（TreeMap 保证日期有序；无数据的日子不出现）
        Map<String, long[]> byDay = new TreeMap<>(); // day -> {销售额, 毛利, 支出}
        for (DailySales d : sales) {
            rowOf(byDay, d.day)[0] = d.totalCents;
        }
        for (DailySales d : profit) {
            rowOf(byDay, d.day)[1] = d.totalCents;
        }
        for (DailySales d : expense) {
            rowOf(byDay, d.day)[2] = d.totalCents;
        }

        long sumSales = 0, sumProfit = 0, sumExpense = 0;

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("利润表（近90天按日）\n");
        csv.append("日期,销售额(元),毛利(元),支出(元),当日净结余(元)\n");

        for (Map.Entry<String, long[]> entry : byDay.entrySet()) {
            long[] v = entry.getValue();
            long net = v[1] - v[2];
            csv.append(entry.getKey()).append(',')
               .append(MoneyUtil.toDisplay(v[0])).append(',')
               .append(MoneyUtil.toDisplay(v[1])).append(',')
               .append(MoneyUtil.toDisplay(v[2])).append(',')
               .append(MoneyUtil.toDisplay(net)).append('\n');
            sumSales += v[0];
            sumProfit += v[1];
            sumExpense += v[2];
        }

        csv.append("合计,").append(MoneyUtil.toDisplay(sumSales)).append(',')
           .append(MoneyUtil.toDisplay(sumProfit)).append(',')
           .append(MoneyUtil.toDisplay(sumExpense)).append(',')
           .append(MoneyUtil.toDisplay(sumProfit - sumExpense)).append('\n');

        try (OutputStream out = context.getContentResolver().openOutputStream(destination)) {
            if (out == null) {
                throw new Exception("无法写入所选位置");
            }
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        }
        return byDay.size();
    }

    // ---- 小工具：取某日的行数组（没有就新建并存入） ----

    private static long[] rowOf(Map<String, long[]> byDay, String day) {
        long[] v = byDay.get(day);
        if (v == null) {
            v = new long[3];
            byDay.put(day, v);
        }
        return v;
    }

    private static long[] row(Map<String, long[]> byDay, DailySales d) {
        long[] v = byDay.get(d.day);
        return v == null ? new long[3] : v;
    }

    /** 链式小助手：把销售额写进行数组（提高上面合并代码的可读性） */
    private static class RowWriter {
        private final Map<String, long[]> byDay;
        private final String day;
        RowWriter(Map<String, long[]> byDay, String day) { this.byDay = byDay; this.day = day; }
        long[] putSales(long v)   { long[] r = byDay.get(day); r[0] = v; return r; }
        long[] putProfit(long v)  { long[] r = byDay.get(day); r[1] = v; return r; }
        long[] putExpense(long v) { long[] r = byDay.get(day); r[2] = v; return r; }
    }
}
