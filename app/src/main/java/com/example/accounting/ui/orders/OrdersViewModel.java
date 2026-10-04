package com.example.accounting.ui.orders;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.dao.SaleWithItems;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.data.repository.SaleRepository;

import java.util.List;

/**
 * 订单页状态：待交付 / 最近已交付 两个列表 + 确认交付。
 */
public class OrdersViewModel extends AndroidViewModel {

    private final SaleRepository saleRepository;

    private final LiveData<List<SaleWithSummary>> pendingDelivery;
    private final LiveData<List<SaleWithSummary>> deliveredRecently;
    private final MutableLiveData<long[]> dateRange = new MutableLiveData<>(
            new long[]{com.example.accounting.util.TimeUtil.daysAgoStart(6),
                    com.example.accounting.util.TimeUtil.todayEnd()});

    public OrdersViewModel(Application app) {
        super(app);
        saleRepository = ((AccountingApp) app).getSaleRepository();
        pendingDelivery = Transformations.switchMap(dateRange, range ->
                saleRepository.observePendingDeliveryBetween(range[0], range[1]));
        deliveredRecently = Transformations.switchMap(dateRange, range ->
                saleRepository.observeDeliveredRecentlyBetween(range[0], range[1]));
    }

    public LiveData<List<SaleWithSummary>> getPendingDelivery() {
        return pendingDelivery;
    }

    public LiveData<List<SaleWithSummary>> getDeliveredRecently() {
        return deliveredRecently;
    }

    public void setDateRange(long fromMillis, long toMillis) {
        dateRange.setValue(new long[]{fromMillis, toMillis});
    }

    public void markDelivered(String saleId, SaveCallback callback) {
        saleRepository.markDelivered(saleId, callback);
    }

    public void recordOrderEvent(String saleId, int eventType, String note,
                                 SaveCallback callback) {
        saleRepository.recordOrderEvent(saleId, eventType, note, callback);
    }

    public LiveData<List<Product>> getProducts() {
        return ((AccountingApp) getApplication()).getProductRepository().observeAllProducts();
    }

    public void loadSaleDetail(String saleId, SaleRepository.DetailCallback callback) {
        saleRepository.loadSaleDetail(saleId, callback);
    }

    public void recordExchange(String saleId, String originalProductId,
                               String replacementProductId, long quantityMilli,
                               String note, SaveCallback callback) {
        saleRepository.recordOrderEvent(saleId,
                com.example.accounting.data.db.entity.OrderEvent.TYPE_EXCHANGE,
                originalProductId, replacementProductId, quantityMilli, note, callback);
    }
}

