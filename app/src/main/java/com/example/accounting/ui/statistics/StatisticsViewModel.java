package com.example.accounting.ui.statistics;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

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

    public StatisticsViewModel(Application app) {
        super(app);
        AccountingApp accountingApp = (AccountingApp) app;
        SaleRepository saleRepository = accountingApp.getSaleRepository();
        PurchaseRepository purchaseRepository = accountingApp.getPurchaseRepository();
        ExpenseRepository expenseRepository = accountingApp.getExpenseRepository();
        StatisticsDao statisticsDao = accountingApp.getDatabase().statisticsDao();

        long monthStart = TimeUtil.monthStart();
        long monthEnd = TimeUtil.monthEnd();
        monthSalesTotal = saleRepository.observeTotalBetween(monthStart, monthEnd);
        monthSaleCount = saleRepository.observeCountBetween(monthStart, monthEnd);
        monthPurchaseTotal = purchaseRepository.observeTotalBetween(monthStart, monthEnd);
        monthExpenseTotal = expenseRepository.observeTotalBetween(monthStart, monthEnd);
        monthGrossProfit = saleRepository.observeGrossProfitBetween(monthStart, monthEnd);

        dailySales = statisticsDao.observeDailySalesSince(TimeUtil.daysAgoStart(6));
        topProducts = statisticsDao.observeTopProductsSince(monthStart);
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
}
