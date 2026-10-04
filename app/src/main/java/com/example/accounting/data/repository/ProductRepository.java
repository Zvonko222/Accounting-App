package com.example.accounting.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.CategoryDao;
import com.example.accounting.data.db.dao.ProductDao;
import com.example.accounting.data.db.dao.StockMovementDao;
import com.example.accounting.data.db.entity.Category;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.db.entity.StockMovement;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 商品相关数据的唯一入口：查询直接返回 LiveData（Room 自动后台执行），
 * 写入在单线程 Executor 上排队执行，完成后切回主线程回调。
 */
public class ProductRepository {

    private final AppDatabase database;
    private final ProductDao productDao;
    private final CategoryDao categoryDao;
    private final StockMovementDao stockMovementDao;
    private final ExecutorService writeExecutor;

    /** 把回调切回主线程（界面只能在主线程更新） */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ProductRepository(AppDatabase database, ExecutorService writeExecutor) {
        this.database = database;
        this.productDao = database.productDao();
        this.categoryDao = database.categoryDao();
        this.stockMovementDao = database.stockMovementDao();
        this.writeExecutor = writeExecutor;
    }

    // ---------------- 查询（Room 自带后台线程，直接返回 LiveData） ----------------

    public LiveData<List<Product>> observeProducts(String keyword) {
        return productDao.observeActive(keyword == null ? "" : keyword.trim());
    }

    public LiveData<List<Product>> observeAllProducts() {
        return productDao.observeAllActive();
    }

    public LiveData<List<Product>> observeLowStock() {
        return productDao.observeLowStock();
    }

    public LiveData<List<Category>> observeCategories() {
        return categoryDao.observeActive();
    }

    public LiveData<List<StockMovement>> observeMovements(String productId) {
        return stockMovementDao.observeForProduct(productId);
    }

    // ---------------- 写入 ----------------

    /**
     * 新增或编辑商品。
     *
     * @param initialStockMilli 仅新增时生效：期初库存，并写一条 INITIAL 台账，
     *                          保证"当前库存 = 期初 + Σ变动"从第一笔起就成立
     */
    public void saveProduct(Product product, long initialStockMilli, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                if (product.id == null) {
                    product.id = UUID.randomUUID().toString();
                    product.initTimestamps();
                    product.stockQuantityMilli = initialStockMilli;
                    database.runInTransaction(() -> {
                        productDao.insert(product);
                        if (initialStockMilli != 0) {
                            StockMovement movement = new StockMovement();
                            movement.id = UUID.randomUUID().toString();
                            movement.initTimestamps();
                            movement.productId = product.id;
                            movement.changeType = StockMovement.TYPE_INITIAL;
                            movement.changeQuantityMilli = initialStockMilli;
                            movement.movementTime = System.currentTimeMillis();
                            movement.note = "期初建账";
                            stockMovementDao.insert(movement);
                        }
                    });
                } else {
                    Product existing = productDao.findById(product.id);
                    if (existing == null) {
                        notifyError(callback, "商品不存在，可能已被停用");
                        return;
                    }
                    // 编辑保留原创建时间与库存（库存只能通过销售/进货/盘点改动）；
                    // 同步状态改为待同步
                    product.createdAt = existing.createdAt;
                    product.stockQuantityMilli = existing.stockQuantityMilli;
                    product.markPending();
                    productDao.update(product);
                }
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 停用商品 = 软删除：列表不再显示，但历史单据和台账全部保留
     * （快照设计保证历史不受影响，见 SaleItem 的注释）。
     */
    public void disableProduct(String productId, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                Product product = productDao.findById(productId);
                if (product != null) {
                    product.markDeleted();
                    productDao.update(product);
                }
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 盘点修正：用户数出实际数量，系统自动算差额、改库存、记台账。
     * 一个事务里完成"改库存 + 记台账"，两者同生同死。
     */
    public void adjustStock(String productId, long actualQuantityMilli,
                            String reason, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    Product product = productDao.findById(productId);
                    if (product == null) {
                        throw new IllegalStateException("商品不存在");
                    }
                    long safeActualQuantityMilli = Math.max(actualQuantityMilli, 0);
                    long changeMilli = safeActualQuantityMilli - product.stockQuantityMilli;
                    if (changeMilli == 0) {
                        return;
                    }
                    product.stockQuantityMilli = safeActualQuantityMilli;
                    product.markPending();
                    productDao.update(product);

                    StockMovement movement = new StockMovement();
                    movement.id = UUID.randomUUID().toString();
                    movement.initTimestamps();
                    movement.productId = productId;
                    movement.changeType = StockMovement.TYPE_ADJUST;
                    movement.changeQuantityMilli = changeMilli;
                    movement.movementTime = System.currentTimeMillis();
                    movement.note = (reason == null || reason.trim().isEmpty())
                            ? "盘点修正" : reason.trim();
                    stockMovementDao.insert(movement);
                });
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    public void createTemporaryProduct(String name, long salePriceCents,
                                       SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    String categoryName = "临时商品";
                    Category category = categoryDao.findActiveByName(categoryName);
                    if (category == null) {
                        category = new Category();
                        category.id = UUID.randomUUID().toString();
                        category.initTimestamps();
                        category.name = categoryName;
                        category.sortOrder = categoryDao.listActive().size();
                        categoryDao.insert(category);
                    }
                    Product product = new Product();
                    product.id = UUID.randomUUID().toString();
                    product.initTimestamps();
                    product.name = name.trim();
                    product.categoryId = category.id;
                    product.salePriceCents = Math.max(0, salePriceCents);
                    product.unit = "个";
                    productDao.insert(product);
                });
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    // ---------------- 回调工具 ----------------

    private void notifySuccess(SaveCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(SaveCallback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }
}
