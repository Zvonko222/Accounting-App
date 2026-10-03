package com.example.accounting.ui.home;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.repository.ExpenseRepository;
import com.example.accounting.data.repository.ProductRepository;
import com.example.accounting.data.repository.SaleRepository;
import com.example.accounting.util.TimeUtil;

import java.util.List;

/**
 * 首页状态：今日四张卡片 + 库存预警。
 * 所有统计直接来自 Repository 的 LiveData，当天有新记录自动刷新。
 */
public class HomeViewModel extends AndroidViewModel {

    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final ProductRepository productRepository;

    private final LiveData<Long> todaySalesTotal;
    private final LiveData<Integer> todaySaleCount;
    private final LiveData<Long> todayExpenseTotal;
    private final LiveData<Long> todayGrossProfit;

    public HomeViewModel(Application app) {
        super(app);
        AccountingApp accountingApp = (AccountingApp) app;
        saleRepository = accountingApp.getSaleRepository();
        expenseRepository = accountingApp.getExpenseRepository();
        productRepository = accountingApp.getProductRepository();

        long from = TimeUtil.todayStart();
        long to = TimeUtil.todayEnd();
        todaySalesTotal = saleRepository.observeTotalBetween(from, to);
        todaySaleCount = saleRepository.observeCountBetween(from, to);
        todayExpenseTotal = expenseRepository.observeTotalBetween(from, to);
        todayGrossProfit = saleRepository.observeGrossProfitBetween(from, to);
    }

    public LiveData<Long> getTodaySalesTotal() {
        return todaySalesTotal;
    }

    public LiveData<Integer> getTodaySaleCount() {
        return todaySaleCount;
    }

    public LiveData<Long> getTodayExpenseTotal() {
        return todayExpenseTotal;
    }

    public LiveData<Long> getTodayGrossProfit() {
        return todayGrossProfit;
    }

    public LiveData<List<Product>> getLowStockProducts() {
        return productRepository.observeLowStock();
    }
}
