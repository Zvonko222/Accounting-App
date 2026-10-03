package com.example.accounting;

import android.app.Application;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.repository.CategoryRepository;
import com.example.accounting.data.repository.ExpenseRepository;
import com.example.accounting.data.repository.ProductRepository;
import com.example.accounting.data.repository.PurchaseRepository;
import com.example.accounting.data.repository.SaleRepository;
import com.example.accounting.data.repository.backup.BackupManager;
import com.example.accounting.data.repository.backup.BackupScheduler;
import com.example.accounting.data.sync.SyncScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自定义 Application：进程入口，持有全局唯一的
 * 数据库、写线程和各 Repository（代替 DI 框架，直白、看得见）。
 *
 * 各 ViewModel 通过 ((AccountingApp) getApplication()).getXxx() 拿到它们。
 */
public class AccountingApp extends Application {

    private AppDatabase database;

    /**
     * 单线程串行写库：所有写操作排队执行，
     * 不存在两个写事务并发竞争，行为完全可预期（ARCHITECTURE.md 第 1 节）。
     */
    private ExecutorService databaseWriteExecutor;

    private ProductRepository productRepository;
    private SaleRepository saleRepository;
    private PurchaseRepository purchaseRepository;
    private ExpenseRepository expenseRepository;
    private CategoryRepository categoryRepository;
    private BackupManager backupManager;

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getInstance(this);
        databaseWriteExecutor = Executors.newSingleThreadExecutor();

        productRepository = new ProductRepository(database, databaseWriteExecutor);
        saleRepository = new SaleRepository(database, databaseWriteExecutor);
        purchaseRepository = new PurchaseRepository(database, databaseWriteExecutor);
        expenseRepository = new ExpenseRepository(database, databaseWriteExecutor);
        categoryRepository = new CategoryRepository(database, databaseWriteExecutor);
        backupManager = new BackupManager(this, databaseWriteExecutor, database.syncStateDao());

        // 每日自动备份：幂等注册，App 每次启动确认任务在册即可
        BackupScheduler.scheduleDaily(this);

        // 每 6 小时兜底云同步（有网才跑；未配置服务器时 Worker 静默跳过）
        SyncScheduler.schedulePeriodic(this);
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public ExecutorService getDatabaseWriteExecutor() {
        return databaseWriteExecutor;
    }

    public ProductRepository getProductRepository() {
        return productRepository;
    }

    public SaleRepository getSaleRepository() {
        return saleRepository;
    }

    public PurchaseRepository getPurchaseRepository() {
        return purchaseRepository;
    }

    public ExpenseRepository getExpenseRepository() {
        return expenseRepository;
    }

    public CategoryRepository getCategoryRepository() {
        return categoryRepository;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }
}
