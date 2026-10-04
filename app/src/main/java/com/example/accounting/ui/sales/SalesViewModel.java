package com.example.accounting.ui.sales;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.dao.PurchaseWithSummary;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.data.db.entity.Expense;
import com.example.accounting.data.db.entity.Purchase;
import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.model.ExpenseType;
import com.example.accounting.data.model.LedgerItem;
import com.example.accounting.data.model.PayMethod;
import com.example.accounting.data.repository.ExpenseRepository;
import com.example.accounting.data.repository.PurchaseRepository;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.data.repository.SaleRepository;
import com.example.accounting.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 流水页状态：三类单据合并成**一条按时间排序的大流水**（对新手最直观），
 * 上面两条筛选：类型（全部/销售/进货/支出）+ 时间范围。
 *
 * 实现：三个仓库查询各自缓存，任一变化都触发"重新合成"——过滤、合并、倒序。
 */
public class SalesViewModel extends AndroidViewModel {

    /** 类型筛选：-1 = 全部 */
    public static final int TYPE_ALL = -1;

    public static class Filter {
        public final long fromMillis;
        public final long toMillis;
        public final boolean showVoided;
        public final int typeFilter;

        public Filter(long fromMillis, long toMillis, boolean showVoided, int typeFilter) {
            this.fromMillis = fromMillis;
            this.toMillis = toMillis;
            this.showVoided = showVoided;
            this.typeFilter = typeFilter;
        }
    }

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ExpenseRepository expenseRepository;

    private final MutableLiveData<Filter> filter = new MutableLiveData<>(
            new Filter(TimeUtil.monthStart(), TimeUtil.monthEnd(), true, TYPE_ALL));

    private final MediatorLiveData<List<LedgerItem>> ledger = new MediatorLiveData<>();

    // 三类数据的缓存（source 回调里刷新）
    private List<SaleWithSummary> salesCache = Collections.emptyList();
    private List<PurchaseWithSummary> purchasesCache = Collections.emptyList();
    private List<Expense> expensesCache = Collections.emptyList();

    private LiveData<List<SaleWithSummary>> salesSource;
    private LiveData<List<PurchaseWithSummary>> purchasesSource;
    private LiveData<List<Expense>> expensesSource;

    public SalesViewModel(Application app) {
        super(app);
        AccountingApp accountingApp = (AccountingApp) app;
        saleRepository = accountingApp.getSaleRepository();
        purchaseRepository = accountingApp.getPurchaseRepository();
        expenseRepository = accountingApp.getExpenseRepository();

        rebindSources(filter.getValue());
    }

    public LiveData<List<LedgerItem>> getLedger() {
        return ledger;
    }

    public Filter getCurrentFilter() {
        return filter.getValue();
    }

    /** 换时间范围（保持其他条件不变） */
    public void setTimeRange(long fromMillis, long toMillis) {
        Filter current = filter.getValue();
        if (current == null) {
            filter.setValue(new Filter(fromMillis, toMillis, true, TYPE_ALL));
        } else {
            filter.setValue(new Filter(fromMillis, toMillis,
                    current.showVoided, current.typeFilter));
        }
        rebindSources(filter.getValue());
    }

    /** 切换类型筛选（保持其他条件不变） */
    public void setTypeFilter(int typeFilter) {
        Filter current = filter.getValue();
        if (current == null) {
            filter.setValue(new Filter(TimeUtil.monthStart(), TimeUtil.monthEnd(),
                    true, typeFilter));
        } else {
            filter.setValue(new Filter(current.fromMillis, current.toMillis,
                    current.showVoided, typeFilter));
        }
        rebindSources(filter.getValue());
    }

    /** 切换"显示已作废" */
    public void setCustomTimeRange(long fromMillis, long toMillis) {
        Filter current = filter.getValue();
        filter.setValue(new Filter(fromMillis, toMillis,
                current == null || current.showVoided,
                current == null ? TYPE_ALL : current.typeFilter));
        rebindSources(filter.getValue());
    }

    public void setShowVoided(boolean showVoided) {
        Filter current = filter.getValue();
        if (current == null) {
            filter.setValue(new Filter(TimeUtil.monthStart(), TimeUtil.monthEnd(),
                    showVoided, TYPE_ALL));
        } else {
            filter.setValue(new Filter(current.fromMillis, current.toMillis,
                    showVoided, current.typeFilter));
        }
        rebindSources(filter.getValue());
    }

    /** 筛选变化：换绑三个仓库查询（switchMap 手工版） */
    private void rebindSources(Filter f) {
        if (salesSource != null) {
            ledger.removeSource(salesSource);
        }
        if (purchasesSource != null) {
            ledger.removeSource(purchasesSource);
        }
        if (expensesSource != null) {
            ledger.removeSource(expensesSource);
        }

        salesSource = saleRepository.observeSaleSummaries(f.fromMillis, f.toMillis, f.showVoided);
        ledger.addSource(salesSource, list -> {
            salesCache = list == null ? Collections.emptyList() : list;
            recompose(f);
        });

        purchasesSource = purchaseRepository.observePurchaseSummaries(
                f.fromMillis, f.toMillis, f.showVoided);
        ledger.addSource(purchasesSource, list -> {
            purchasesCache = list == null ? Collections.emptyList() : list;
            recompose(f);
        });

        expensesSource = expenseRepository.observeExpensesBetween(f.fromMillis, f.toMillis);
        ledger.addSource(expensesSource, list -> {
            expensesCache = list == null ? Collections.emptyList() : list;
            recompose(f);
        });
    }

    /** 合成大流水：映射 → 类型过滤 → 按时间倒序 */
    private void recompose(Filter f) {
        List<LedgerItem> items = new ArrayList<>();
        if (f.typeFilter == LedgerItem.TYPE_SALE || f.typeFilter == TYPE_ALL) {
            for (SaleWithSummary s : salesCache) {
                String title = "销售 · " + PayMethod.displayName(s.sale.payMethod);
                items.add(new LedgerItem(LedgerItem.TYPE_SALE, s.sale.saleTime, title,
                        s.itemsSummary, s.sale.totalAmountCents, s.sale.isDeleted, s.sale.id));
            }
        }
        if (f.typeFilter == LedgerItem.TYPE_PURCHASE || f.typeFilter == TYPE_ALL) {
            for (PurchaseWithSummary p : purchasesCache) {
                String title = p.purchase.supplierName == null
                        ? "进货" : "进货 · " + p.purchase.supplierName;
                items.add(new LedgerItem(LedgerItem.TYPE_PURCHASE, p.purchase.purchaseTime,
                        title, p.itemsSummary, -p.purchase.totalAmountCents,
                        p.purchase.isDeleted, p.purchase.id));
            }
        }
        if (f.typeFilter == LedgerItem.TYPE_EXPENSE || f.typeFilter == TYPE_ALL) {
            for (Expense e : expensesCache) {
                items.add(new LedgerItem(LedgerItem.TYPE_EXPENSE, e.expenseTime,
                        "支出 · " + ExpenseType.displayName(e.expenseType), e.note,
                        -e.amountCents, e.isDeleted, e.id));
            }
        }
        Collections.sort(items, (a, b) -> Long.compare(b.time, a.time));
        ledger.setValue(items);
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

    /** 支出详情（点击支出行）：后台查库，主线程回调 */
    public void loadExpense(String expenseId,
            com.example.accounting.data.repository.ExpenseRepository.ExpenseDetailCallback callback) {
        expenseRepository.getExpenseById(expenseId, callback);
    }



    /** 供列表按 id 找 Sale 实体（判断点击类型时用不到，保留给详情路由） */
    public Sale findSaleInCache(String saleId) {
        for (SaleWithSummary s : salesCache) {
            if (s.sale.id.equals(saleId)) {
                return s.sale;
            }
        }
        return null;
    }
}
