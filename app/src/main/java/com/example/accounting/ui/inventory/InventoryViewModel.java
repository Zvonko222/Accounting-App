package com.example.accounting.ui.inventory;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.entity.Category;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.db.entity.StockMovement;
import com.example.accounting.data.repository.ProductRepository;
import com.example.accounting.data.repository.SaveCallback;

import java.util.List;

/**
 * 库存页的状态与操作：商品列表（含搜索）、分类、库存预警、
 * 保存商品、停用商品、盘点修正、库存台账。
 * 写操作全部委托 ProductRepository，成败经 SaveCallback 回来。
 */
public class InventoryViewModel extends AndroidViewModel {

    private final ProductRepository repository;

    private final LiveData<List<Product>> products;

    public InventoryViewModel(Application app) {
        super(app);
        repository = ((AccountingApp) app).getProductRepository();
        products = repository.observeProducts("");
    }

    public LiveData<List<Product>> getProducts() {
        return products;
    }

    /** 搜索框文字变化时调用，换一个 LiveData 查询 */
    public LiveData<List<Product>> searchProducts(String keyword) {
        return repository.observeProducts(keyword);
    }

    public LiveData<List<Category>> getCategories() {
        return repository.observeCategories();
    }

    /** 商品编辑页里快捷新增分类 */
    public void addCategory(String name, com.example.accounting.data.repository.SaveCallback callback) {
        ((AccountingApp) getApplication()).getCategoryRepository().addCategory(name, callback);
    }

    public LiveData<List<Product>> getLowStockProducts() {
        return repository.observeLowStock();
    }

    public LiveData<List<StockMovement>> getMovements(String productId) {
        return repository.observeMovements(productId);
    }

    /**
     * @param initialStockMilli 仅新增商品时生效（编辑时传 0，Repository 会忽略）
     */
    public void saveProduct(Product product, long initialStockMilli, SaveCallback callback) {
        repository.saveProduct(product, initialStockMilli, callback);
    }

    public void disableProduct(String productId, SaveCallback callback) {
        repository.disableProduct(productId, callback);
    }

    public void adjustStock(String productId, long actualQuantityMilli,
                            String reason, SaveCallback callback) {
        repository.adjustStock(productId, actualQuantityMilli, reason, callback);
    }
}
