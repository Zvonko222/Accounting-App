package com.example.accounting.ui.purchase;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.model.PurchaseCartLine;
import com.example.accounting.data.repository.PurchaseRepository;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.util.SaleCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * 进货开单页的状态：商品网格 + 待入库清单。
 * 与 SaleEditViewModel 结构对称；新行默认数量 1、默认进价 = 商品当前进价。
 */
public class PurchaseEditViewModel extends AndroidViewModel {

    private static final long DEFAULT_STEP_MILLI = 1000;

    private final PurchaseRepository purchaseRepository;

    private final LiveData<List<Product>> products;

    private final MutableLiveData<List<PurchaseCartLine>> lines =
            new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<Long> totalCents = new MutableLiveData<>(0L);

    /** 清单里的商品种数（"本单共 N 种商品"） */
    private final MutableLiveData<Integer> lineCount = new MutableLiveData<>(0);

    /** 记账时间（毫秒）。默认当下；补录时可选过去的时间（不允许未来） */
    private final MutableLiveData<Long> recordTime =
            new MutableLiveData<>(System.currentTimeMillis());

    public PurchaseEditViewModel(Application app) {
        super(app);
        purchaseRepository = ((AccountingApp) app).getPurchaseRepository();
        products = ((AccountingApp) app).getProductRepository().observeAllProducts();
    }

    public LiveData<List<Product>> getProducts() {
        return products;
    }

    public LiveData<List<PurchaseCartLine>> getLines() {
        return lines;
    }

    public LiveData<Long> getTotalCents() {
        return totalCents;
    }

    public LiveData<Integer> getLineCountLive() {
        return lineCount;
    }

    public LiveData<Long> getRecordTime() {
        return recordTime;
    }

    /** 用户在日期/时间选择器里选定的记账时间 */
    public void setRecordTime(long timeMillis) {
        recordTime.setValue(timeMillis);
    }

    /** 修改模式：回填旧单的记账时间 */
    public void prefillRecordTime(long timeMillis) {
        recordTime.setValue(timeMillis);
    }

    private long currentRecordTime() {
        Long time = recordTime.getValue();
        return time == null ? System.currentTimeMillis() : time;
    }

    public int getLineCount() {
        List<PurchaseCartLine> current = lines.getValue();
        return current == null ? 0 : current.size();
    }

    public boolean hasProduct(String productId) {
        for (PurchaseCartLine line : currentLines()) {
            if (line.productId.equals(productId)) {
                return true;
            }
        }
        return false;
    }

    public void addProduct(Product product) {
        List<PurchaseCartLine> current = currentLines();
        for (PurchaseCartLine line : current) {
            if (line.productId.equals(product.id)) {
                line.quantityMilli += DEFAULT_STEP_MILLI;
                publish(current);
                return;
            }
        }
        // 默认进价取商品上次的进价，改起来比每次从零输入快
        current.add(new PurchaseCartLine(product.id, product.name,
                product.purchasePriceCents, DEFAULT_STEP_MILLI));
        publish(current);
    }

    public void increaseQuantity(String productId) {
        List<PurchaseCartLine> current = currentLines();
        for (PurchaseCartLine line : current) {
            if (line.productId.equals(productId)) {
                line.quantityMilli += DEFAULT_STEP_MILLI;
                publish(current);
                return;
            }
        }
    }

    public void decreaseQuantity(String productId) {
        List<PurchaseCartLine> current = currentLines();
        for (int i = 0; i < current.size(); i++) {
            PurchaseCartLine line = current.get(i);
            if (line.productId.equals(productId)) {
                line.quantityMilli -= DEFAULT_STEP_MILLI;
                if (line.quantityMilli <= 0) {
                    current.remove(i);
                }
                publish(current);
                return;
            }
        }
    }

    public void setQuantity(String productId, long quantityMilli) {
        List<PurchaseCartLine> current = currentLines();
        for (PurchaseCartLine line : current) {
            if (line.productId.equals(productId)) {
                if (quantityMilli <= 0) {
                    current.remove(line);
                } else {
                    line.quantityMilli = quantityMilli;
                }
                publish(current);
                return;
            }
        }
    }

    /** 修改本单进价 */
    public void setUnitCost(String productId, long unitCostCents) {
        if (unitCostCents < 0) {
            return;
        }
        List<PurchaseCartLine> current = currentLines();
        for (PurchaseCartLine line : current) {
            if (line.productId.equals(productId)) {
                line.unitCostCents = unitCostCents;
                publish(current);
                return;
            }
        }
    }

    /**
     * 新增或编辑一行（行编辑弹窗的出口）：已有该商品则覆盖进价与数量，
     * 没有则新增。数量 <= 0 视为删除该行。
     */
    public void upsertLine(Product product, long unitCostCents, long quantityMilli) {
        List<PurchaseCartLine> current = currentLines();
        for (int i = 0; i < current.size(); i++) {
            PurchaseCartLine line = current.get(i);
            if (line.productId.equals(product.id)) {
                if (quantityMilli <= 0) {
                    current.remove(i);
                } else {
                    line.unitCostCents = unitCostCents;
                    line.quantityMilli = quantityMilli;
                }
                publish(current);
                return;
            }
        }
        if (quantityMilli > 0) {
            current.add(new PurchaseCartLine(product.id, product.name,
                    unitCostCents, quantityMilli));
            publish(current);
        }
    }

    public void recordPurchase(String supplierName, SaveCallback callback) {
        purchaseRepository.recordPurchase(currentLines(), supplierName, null,
                currentRecordTime(), callback);
    }

    /** 修改模式：加载已保存的进货单（回调在主线程） */
    public void loadPurchaseDetail(String purchaseId, PurchaseRepository.DetailCallback callback) {
        purchaseRepository.loadPurchaseDetail(purchaseId, callback);
    }

    /** 修改模式：把旧单明细回填进清单（进价/数量用快照值） */
    public void prefillLines(List<PurchaseCartLine> newLines) {
        publish(new ArrayList<>(newLines));
    }

    /** 保存修改：一个事务里"作废旧单 + 重开新单" */
    public void editPurchase(String purchaseId, String supplierName, SaveCallback callback) {
        purchaseRepository.editPurchase(purchaseId, currentLines(), supplierName,
                currentRecordTime(), callback);
    }

    /** 拍照导入：识别确认过的行直接保存为一张进货单（自动建档在 Repository 事务内） */
    public void saveOcrImport(List<PurchaseCartLine> lines, String supplierName,
                              SaveCallback callback) {
        purchaseRepository.recordPurchaseWithNewProducts(lines, supplierName, callback);
    }

    private List<PurchaseCartLine> currentLines() {
        List<PurchaseCartLine> current = lines.getValue();
        return current == null ? new ArrayList<>() : new ArrayList<>(current);
    }

    private void publish(List<PurchaseCartLine> newLines) {
        lines.setValue(newLines);
        lineCount.setValue(newLines.size());
        long sum = 0;
        for (PurchaseCartLine line : newLines) {
            sum += line.lineTotalCents();
        }
        totalCents.setValue(SaleCalculator.orderTotalCents(sum, 0));
    }
}
