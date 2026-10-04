package com.example.accounting.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.accounting.data.db.dao.CategoryDao;
import com.example.accounting.data.db.dao.ExpenseDao;
import com.example.accounting.data.db.dao.OrderEventDao;
import com.example.accounting.data.db.dao.ProductDao;
import com.example.accounting.data.db.dao.PurchaseDao;
import com.example.accounting.data.db.dao.SaleDao;
import com.example.accounting.data.db.dao.StatisticsDao;
import com.example.accounting.data.db.dao.StockMovementDao;
import com.example.accounting.data.db.dao.SyncStateDao;
import com.example.accounting.data.db.entity.Category;
import com.example.accounting.data.db.entity.Expense;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.db.entity.Purchase;
import com.example.accounting.data.db.entity.PurchaseItem;
import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.db.entity.SaleItem;
import com.example.accounting.data.db.entity.StockMovement;
import com.example.accounting.data.db.entity.OrderEvent;
import com.example.accounting.data.db.entity.SyncState;

import java.util.UUID;

/**
 * Room 数据库：全 App 唯一事实源（ARCHITECTURE.md 第 1 节）。
 *
 * 版本纪律（ARCHITECTURE.md 2.6）：
 * - exportSchema = true：每个版本的表结构导出到 app/schemas/，是写 Migration 的依据；
 * - 改表必须新增 MIGRATION_N_M 并把 version 加一，禁止 fallbackToDestructiveMigration
 *   （那会清空用户全部账本）。
 */
@Database(
        entities = {
                Category.class,
                Product.class,
                Sale.class,
                SaleItem.class,
                Purchase.class,
                PurchaseItem.class,
                Expense.class,
                StockMovement.class,
                OrderEvent.class,
                SyncState.class
        },
        // 每次改表：DATABASE_VERSION + 1，同时 version 改成相同数字，并写 MIGRATION
        version = 7,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    /** 数据库文件名。备份/恢复直接操作这个文件，恢复后 Room 用同名文件重新打开 */
    public static final String DATABASE_NAME = "accounting.db";

    /** 当前数据库版本。改实体必须同步 +1 并写 Migration（见类注释） */
    public static final int DATABASE_VERSION = 7;

    public abstract CategoryDao categoryDao();

    public abstract ProductDao productDao();

    public abstract SaleDao saleDao();

    public abstract PurchaseDao purchaseDao();

    public abstract ExpenseDao expenseDao();

    public abstract StockMovementDao stockMovementDao();

    public abstract OrderEventDao orderEventDao();

    public abstract StatisticsDao statisticsDao();

    public abstract SyncStateDao syncStateDao();

    /** 进程内单例：多个实例指向同一文件会造成写入竞争 */
    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "accounting.db")
                            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3,
                                    Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5,
                                    Migrations.MIGRATION_5_6, Migrations.MIGRATION_6_7)
                            .addCallback(PRESET_CATEGORIES_CALLBACK)
                            .build();
                }
            }
        }
        return instance;
    }

    /** 整个进程结束后允许重建（备份恢复替换数据库文件时用到） */
    public static void resetInstance() {
        synchronized (AppDatabase.class) {
            instance = null;
        }
    }

    /**
     * 首次建库时预置常用分类，开箱即用，省得用户先建分类。
     * onCreate 运行在建库事务里，此时库还没"打开"，拿不到 DAO，
     * 只能用 SupportSQLiteDatabase 执行原生 SQL。
     */
    private static final RoomDatabase.Callback PRESET_CATEGORIES_CALLBACK =
            new RoomDatabase.Callback() {
                @Override
                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                    super.onCreate(db);
                    String[] presetNames = {"饮料", "零食", "日用品", "烟酒", "其他"};
                    long now = System.currentTimeMillis();
                    for (int i = 0; i < presetNames.length; i++) {
                        // 列清单必须覆盖所有 NOT NULL 且无默认值的列（同步列组）
                        db.execSQL("INSERT INTO categories "
                                        + "(id, name, sortOrder, createdAt, updatedAt, "
                                        + "isDeleted, syncStatus, retryCount) "
                                        + "VALUES (?, ?, ?, ?, ?, 0, 1, 0)",
                                new Object[]{
                                        UUID.randomUUID().toString(),
                                        presetNames[i],
                                        i,
                                        now,
                                        now
                                });
                    }
                }
            };
}
