package com.example.accounting.ui.statistics;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.model.DailySales;
import com.example.accounting.data.model.TopProduct;
import com.example.accounting.data.repository.ExpenseRepository;
import com.example.accounting.data.repository.PurchaseRepository;
import com.example.accounting.data.repository.SaleRepository;
import com.example.accounting.data.db.dao.StatisticsDao;
import com.example.accounting.util.TimeUtil;

import java.util.List;

/**
 * 统计页状态：本月五张卡片 + 近 7 天柱状 + 热销前 5。
 * 全部是 SQL 聚合查询的 LiveData，不落任何预汇总表。
 */
public class StatisticsViewModel extends AndroidViewModel {

    private final LiveData<Long> monthSalesTotal;
    private final LiveData<Integer> monthSaleCount;
    private final LiveData<Long> monthPurchaseTotal;
    private final LiveData<Long> monthExpenseTotal;
    private final LiveData<Long> monthGrossProfit;
    private final LiveData<List<DailySales>> dailySales;
    private final LiveData<List<TopProduct>> topProducts;
    private final LiveData<List<com.example.accounting.data.model.CategorySales>> categorySales;
    private final LiveData<List<DailySales>> trendPoints;
    private final androidx.lifecycle.MutableLiveData<String> range =
            new androidx.lifecycle.MutableLiveData<>("month");
    private final androidx.lifecycle.MutableLiveData<String> metric =
            new androidx.lifecycle.MutableLiveData<>("sales");
    private final androidx.lifecycle.MutableLiveData<Long> customFrom = new androidx.lifecycle.MutableLiveData<>();
    private final androidx.lifecycle.MutableLiveData<Long> customTo = new androidx.lifecycle.MutableLiveData<>();

    public StatisticsViewModel(Application app) {
        super(app);
        AccountingApp accountingApp = (AccountingApp) app;
        SaleRepository saleRepository = accountingApp.getSaleRepository();
        PurchaseRepository purchaseRepository = accountingApp.getPurchaseRepository();
        ExpenseRepository expenseRepository = accountingApp.getExpenseRepository();
        StatisticsDao statisticsDao = accountingApp.getDatabase().statisticsDao();

        long monthStart = TimeUtil.monthStart();
        long monthEnd = TimeUtil.monthEnd();
        monthSalesTotal = Transformations.switchMap(customFrom, from -> {
            Long to = customTo.getValue();
            return saleRepository.observeTotalBetween(from, to == null ? monthEnd : to);
        });
        monthSaleCount = Transformations.switchMap(customFrom, from -> {
            Long to = customTo.getValue();
            return saleRepository.observeCountBetween(from, to == null ? monthEnd : to);
        });
        monthPurchaseTotal = Transformations.switchMap(customFrom, from -> {
            Long to = customTo.getValue();
            return purchaseRepository.observeTotalBetween(from, to == null ? monthEnd : to);
        });
        monthExpenseTotal = Transformations.switchMap(customFrom, from -> {
            Long to = customTo.getValue();
            return expenseRepository.observeTotalBetween(from, to == null ? monthEnd : to);
        });
        monthGrossProfit = Transformations.switchMap(customFrom, from -> {
            Long to = customTo.getValue();
            return saleRepository.observeGrossProfitBetween(from, to == null ? monthEnd : to);
        });
        customFrom.setValue(monthStart);
        customTo.setValue(monthEnd);

        dailySales = statisticsDao.observeDailySalesSince(TimeUtil.daysAgoStart(6));
        topProducts = statisticsDao.observeTopProductsSince(monthStart);
        // 统计图区间：周（近7天按日）/ 月（本月按日）/ 年（本年按月）
        // 趋势点 = 区间 × 指标 两维 switchMap（销售额 / 毛利 / 支出）
        trendPoints = Transformations.switchMap(range, r ->
                Transformations.switchMap(metric, m -> {
                    long since = "week".equals(r) ? TimeUtil.daysAgoStart(6)
                            : "year".equals(r) ? TimeUtil.yearStart()
                            : "custom".equals(r) && customFrom.getValue() != null
                            ? customFrom.getValue() : TimeUtil.monthStart();
                    if ("profit".equals(m)) {
                        return statisticsDao.observeDailyProfitSince(since);
                    }
                    if ("expense".equals(m)) {
                        return statisticsDao.observeDailyExpenseSince(since);
                    }
                    return "year".equals(r)
                            ? statisticsDao.observeMonthlySalesSince(since)
                            : statisticsDao.observeDailySalesSince(since);
                }));
        categorySales = Transformations.switchMap(range, r -> {
            if ("week".equals(r)) {
                return statisticsDao.observeCategorySalesBetween(
                        TimeUtil.daysAgoStart(6), TimeUtil.todayEnd());
            }
            if ("year".equals(r)) {
                return statisticsDao.observeCategorySalesBetween(
                        TimeUtil.yearStart(), TimeUtil.todayEnd());
            }
            if ("custom".equals(r) && customFrom.getValue() != null) {
                return statisticsDao.observeCategorySalesBetween(customFrom.getValue(),
                        customTo.getValue() == null ? TimeUtil.todayEnd() : customTo.getValue());
            }
            return statisticsDao.observeCategorySalesBetween(monthStart, monthEnd);
        });
    }

    public LiveData<Long> getMonthSalesTotal() {
        return monthSalesTotal;
    }

    public LiveData<Integer> getMonthSaleCount() {
        return monthSaleCount;
    }

    public LiveData<Long> getMonthPurchaseTotal() {
        return monthPurchaseTotal;
    }

    public LiveData<Long> getMonthExpenseTotal() {
        return monthExpenseTotal;
    }

    public LiveData<Long> getMonthGrossProfit() {
        return monthGrossProfit;
    }

    public LiveData<List<DailySales>> getDailySales() {
        return dailySales;
    }

    public LiveData<List<TopProduct>> getTopProducts() {
        return topProducts;
    }

    public LiveData<List<com.example.accounting.data.model.CategorySales>> getCategorySales() {
        return categorySales;
    }

    public LiveData<List<DailySales>> getTrendPoints() {
        return trendPoints;
    }

    public void setChartRange(String range) {
        this.range.setValue(range);
    }

    public void setCustomDateRange(long fromMillis, long toMillis) {
        customTo.setValue(toMillis);
        customFrom.setValue(fromMillis);
        range.setValue("custom");
    }

    /** 图表指标：sales / profit / expense */
    public void setChartMetric(String metric) {
        this.metric.setValue(metric);
    }
}
