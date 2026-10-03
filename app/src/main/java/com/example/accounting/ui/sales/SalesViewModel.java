package com.example.accounting.ui.sales;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.dao.PurchaseWithSummary;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.data.db.entity.Expense;
import com.example.accounting.data.repository.ExpenseRepository;
import com.example.accounting.data.repository.PurchaseRepository;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.data.repository.SaleRepository;
import com.example.accounting.util.TimeUtil;

import java.util.List;

/**
 * 流水页状态：时间范围 + "显示已作废" 开关 → 三个列表。
 *
 * 用 Transformations.switchMap：筛选条件（一个 Filter 对象）变化时，
 * 自动换绑新的数据库查询 LiveData——不用手动 removeObservers。
 */
public class SalesViewModel extends AndroidViewModel {

    /** 筛选条件：时间范围 + 是否显示已作废单 */
    public static class Filter {
        public final long fromMillis;
        public final long toMillis;
        public final boolean showVoided;

        public Filter(long fromMillis, long toMillis, boolean showVoided) {
            this.fromMillis = fromMillis;
            this.toMillis = toMillis;
            this.showVoided = showVoided;
        }
    }

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ExpenseRepository expenseRepository;

    /** 当前筛选。默认：本月 + 显示已作废（让用户能看到作废轨迹） */
    private final MutableLiveData<Filter> filter =
            new MutableLiveData<>(new Filter(TimeUtil.monthStart(), TimeUtil.monthEnd(), true));

    private final LiveData<List<SaleWithSummary>> sales;
    private final LiveData<List<PurchaseWithSummary>> purchases;
    private final LiveData<List<Expense>> expenses;

    public SalesViewModel(Application app) {
        super(app);
        AccountingApp accountingApp = (AccountingApp) app;
        saleRepository = accountingApp.getSaleRepository();
        purchaseRepository = accountingApp.getPurchaseRepository();
        expenseRepository = accountingApp.getExpenseRepository();

        sales = Transformations.switchMap(filter, f ->
                saleRepository.observeSaleSummaries(f.fromMillis, f.toMillis, f.showVoided));
        purchases = Transformations.switchMap(filter, f ->
                purchaseRepository.observePurchaseSummaries(f.fromMillis, f.toMillis, f.showVoided));
        expenses = Transformations.switchMap(filter, f ->
                expenseRepository.observeExpensesBetween(f.fromMillis, f.toMillis));
    }

    public LiveData<List<SaleWithSummary>> getSales() {
        return sales;
    }

    public LiveData<List<PurchaseWithSummary>> getPurchases() {
        return purchases;
    }

    public LiveData<List<Expense>> getExpenses() {
        return expenses;
    }

    /** 换时间范围（保持"显示已作废"开关不变） */
    public void setTimeRange(long fromMillis, long toMillis) {
        Filter current = filter.getValue();
        boolean showVoided = current != null && current.showVoided;
        filter.setValue(new Filter(fromMillis, toMillis, showVoided));
    }

    /** 切换"显示已作废"（保持时间范围不变） */
    public void setShowVoided(boolean showVoided) {
        Filter current = filter.getValue();
        if (current == null) {
            filter.setValue(new Filter(TimeUtil.monthStart(), TimeUtil.monthEnd(), showVoided));
        } else {
            filter.setValue(new Filter(current.fromMillis, current.toMillis, showVoided));
        }
    }

    public Filter getCurrentFilter() {
        return filter.getValue();
    }

    // ---------------- 详情与操作 ----------------

    public void loadSaleDetail(String saleId, SaleRepository.DetailCallback callback) {
        saleRepository.loadSaleDetail(saleId, callback);
    }

    public void loadPurchaseDetail(String purchaseId, PurchaseRepository.DetailCallback callback) {
        purchaseRepository.loadPurchaseDetail(purchaseId, callback);
    }

    public void voidSale(String saleId, SaveCallback callback) {
        saleRepository.voidSale(saleId, callback);
    }

    public void voidPurchase(String purchaseId, SaveCallback callback) {
        purchaseRepository.voidPurchase(purchaseId, callback);
    }

    public void deleteExpense(String expenseId, SaveCallback callback) {
        expenseRepository.deleteExpense(expenseId, callback);
    }
}
