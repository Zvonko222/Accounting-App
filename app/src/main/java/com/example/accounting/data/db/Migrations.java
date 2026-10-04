package com.example.accounting.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * 数据库迁移清单：每个版本一条，按顺序在 AppDatabase 里注册。
 * 原则：只加不改——新列全部允许 NULL 或带默认值，老数据原样保留。
 */
public final class Migrations {

    private Migrations() {
    }

    /** v2：分类支持二级目录（新增 parentId，null = 顶级） */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE categories ADD COLUMN parentId TEXT");
        }
    };

    /** v3：销售单支持交付确认（外卖/预订单标记待交付，订单页确认交付） */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE sales ADD COLUMN deliveryStatus INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sales ADD COLUMN deliveredAt INTEGER");
        }
    };

    /** v4：销售单保存交付地址 */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE sales ADD COLUMN deliveryAddress TEXT");
        }
    };

    /** v5：销售单保存联系电话 */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE sales ADD COLUMN deliveryPhone TEXT");
        }
    };

    /** v6：订单事件（退货、换货及其他售后事件） */
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS order_events ("
                    + "id TEXT NOT NULL, saleId TEXT, eventType INTEGER NOT NULL, "
                    + "eventTime INTEGER NOT NULL, amountCents INTEGER NOT NULL, note TEXT, "
                    + "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, "
                    + "isDeleted INTEGER NOT NULL, deletedAt INTEGER, syncStatus INTEGER NOT NULL, "
                    + "lastSyncedAt INTEGER, retryCount INTEGER NOT NULL, lastSyncError TEXT, "
                    + "PRIMARY KEY(id), FOREIGN KEY(saleId) REFERENCES sales(id) ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_events_saleId ON order_events(saleId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_events_eventTime ON order_events(eventTime)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_events_syncStatus ON order_events(syncStatus)");
        }
    };

    /** v7：订单事件保存原货、新货与差额 */
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE order_events ADD COLUMN originalAmountCents INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE order_events ADD COLUMN replacementAmountCents INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE order_events ADD COLUMN differenceCents INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE order_events ADD COLUMN originalProductId TEXT");
            db.execSQL("ALTER TABLE order_events ADD COLUMN replacementProductId TEXT");
            db.execSQL("ALTER TABLE order_events ADD COLUMN quantityMilli INTEGER NOT NULL DEFAULT 0");
        }
    };
}
