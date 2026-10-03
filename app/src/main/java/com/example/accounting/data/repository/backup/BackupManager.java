package com.example.accounting.data.repository.backup;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.SyncStateDao;
import com.example.accounting.data.db.entity.SyncState;
import com.example.accounting.util.TimeUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 备份管理器——与"同步"完全独立的一套数据保险机制（ARCHITECTURE.md 第 5 节）：
 *
 * 1. 本机自动备份：每天一次，备份文件放 App 私有目录，只保留最近 7 份。
 *    数据库文件损坏、误操作清数据时，还能从本机备份救回。
 * 2. 导出备份文件：把数据库复制到用户选的位置（下载/网盘/电脑），防手机丢失。
 * 3. 恢复：校验备份文件后替换当前数据库并重启应用。
 * 4. CSV 导出：把销售明细导成商家能直接打开的表格（这个是"导出"，不是备份）。
 *
 * 两个关键动作：
 * - 复制前必须执行 PRAGMA wal_checkpoint(TRUNCATE)，把 Room 默认 WAL 模式下
 *   还停留在 -wal 文件里的最近写入合并回主 .db 文件，否则备份会丢最新数据；
 * - 恢复替换文件前必须先 close 数据库连接，并删除 -wal / -shm 残留文件。
 */
public class BackupManager {

    public interface BackupCallback {
        void onDone(String message);

        void onError(String message);
    }

    /** 本机自动备份保留份数 */
    private static final int KEEP_LOCAL_BACKUPS = 7;

    /** logcat 过滤标签 */
    private static final String TAG = "BackupManager";

    /** SQLite 文件头的固定魔数，用来快速判断"这是个数据库文件吗" */
    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    private final Context context;
    private final ExecutorService writeExecutor;
    private final SyncStateDao syncStateDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BackupManager(Context context, ExecutorService writeExecutor, SyncStateDao syncStateDao) {
        this.context = context.getApplicationContext();
        this.writeExecutor = writeExecutor;
        this.syncStateDao = syncStateDao;
    }

    // ---------------- 1. 本机自动备份 ----------------

    /**
     * 手动"立即备份"入口（设置页用）。
     * 备份在后台线程执行，但结果回调一定切回主线程——
     * Toast 只能在主线程创建，直接在后台线程回调会闪退（已踩过的坑）。
     */
    public void backupNow(BackupCallback callback) {
        writeExecutor.execute(() -> {
            try {
                String fileName = performLocalBackup();
                notifyDone(callback, fileName);
            } catch (Exception e) {
                // 排障依据：失败原因必须留在 logcat，过滤 BackupManager 即可
                android.util.Log.e(TAG, "本机备份失败", e);
                notifyError(callback, e.getMessage() == null ? "未知错误" : e.getMessage());
            }
        });
    }

    /**
     * 在当前线程同步执行本机备份。Worker 与 UI 回调都走这里。
     *
     * @return 备份文件名（出错抛异常，由调用方处理）
     */
    public String performLocalBackup() throws IOException {
        File backupDir = localBackupDir();
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("无法创建备份目录");
        }

        String fileName = "backup_accounting_"
                + TimeUtil.formatForFileName(System.currentTimeMillis()) + ".db";
        checkpointAndCopyTo(new File(backupDir, fileName));

        pruneOldBackups(backupDir);
        saveLastBackupTime();
        return fileName;
    }

    /** App 私有目录下的 backups 子目录，只有本 App 能访问 */
    public static File localBackupDir(Context context) {
        return new File(context.getFilesDir(), "backups");
    }

    private File localBackupDir() {
        return localBackupDir(context);
    }

    /** 只保留最近 KEEP_LOCAL_BACKUPS 份，老的删掉，避免占满存储 */
    private void pruneOldBackups(File backupDir) {
        File[] files = backupDir.listFiles(
                (dir, name) -> name.startsWith("backup_accounting_") && name.endsWith(".db"));
        if (files == null || files.length <= KEEP_LOCAL_BACKUPS) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = KEEP_LOCAL_BACKUPS; i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    private void saveLastBackupTime() {
        SyncState state = new SyncState();
        state.key = "last_backup_at";
        state.value = String.valueOf(System.currentTimeMillis());
        state.updatedAt = System.currentTimeMillis();
        syncStateDao.put(state);
    }

    /** 上次本机备份时间（毫秒），从未备份返回 null */
    public Long getLastBackupTime() {
        String value = syncStateDao.getValue("last_backup_at");
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------- 2. 导出备份文件（SAF） ----------------

    public void exportBackupTo(Uri destination, BackupCallback callback) {
        writeExecutor.execute(() -> {
            try {
                checkpointAndCopyToUri(destination);
                notifyDone(callback, "备份已导出");
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    // ---------------- 3. 恢复（SAF） ----------------

    /**
     * 从用户选择的备份文件恢复。
     *
     * 流程：先把备份复制到临时文件并校验（SQLite 头 + 版本不能比当前 App 新），
     * 校验通过才关闭数据库、替换正式文件、删除 wal/shm。替换后由调用方重启进程。
     */
    public void importBackupFrom(Uri source, BackupCallback callback) {
        writeExecutor.execute(() -> {
            File tempFile = new File(context.getFilesDir(), "restore_pending.db");
            try {
                copyUriToFile(source, tempFile);
                validateDatabaseFile(tempFile);

                closeDatabase();
                File dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME);
                //noinspection ResultOfMethodCallIgnored
                copyFile(tempFile, dbFile);
                deleteWalFiles(dbFile);
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();

                notifyDone(callback, "恢复完成");
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
                notifyError(callback, e.getMessage());
            }
        });
    }

    /**
     * 校验：1) 前 16 字节是 SQLite 魔数；2) 文件头里的 user_version
     * 不能比当前 App 的数据库版本新（防止新备份装回旧 App 后 Room 拒绝降级）。
     * SQLite 文件格式：偏移 60 起 4 字节大端序是 user_version。
     * 所有失败统一归为 "invalid_file" / "newer_version" 两种文案（界面友好提示用）。
     */
    static void validateDatabaseFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[64];
            int read = in.read(header);
            if (read < 64 || !startsWith(header, SQLITE_HEADER)) {
                throw new IOException("invalid_file");
            }
            int userVersion = ((header[60] & 0xFF) << 24)
                    | ((header[61] & 0xFF) << 16)
                    | ((header[62] & 0xFF) << 8)
                    | (header[63] & 0xFF);
            if (userVersion > AppDatabase.DATABASE_VERSION) {
                throw new IOException("newer_version");
            }
        } catch (java.io.FileNotFoundException e) {
            // 文件读不到（不存在/无权限）：与"不是数据库"同样对待
            throw new IOException("invalid_file");
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 关闭 Room 单例连接。恢复流程里必须先做这一步，
     * 否则文件替换时还有打开的句柄，Windows/Linux 语义下都会失败或脏写。
     */
    private void closeDatabase() {
        AppDatabase db = AppDatabase.getInstance(context);
        db.close();
        AppDatabase.resetInstance();
    }

    private void deleteWalFiles(File dbFile) {
        //noinspection ResultOfMethodCallIgnored
        new File(dbFile.getPath() + "-wal").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dbFile.getPath() + "-shm").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dbFile.getPath() + "-journal").delete();
    }

    // ---------------- 4. CSV 导出（SAF） ----------------

    /**
     * 导出销售明细 CSV（一行 = 一个明细行）。数字列都已是纯文本安全格式；
     * 字段统一加引号防逗号；BOM 头让 Excel 用 UTF-8 打开不乱码。
     */
    public void exportSalesCsvTo(Uri destination, BackupCallback callback) {
        writeExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                StringBuilder csv = new StringBuilder();
                csv.append('\uFEFF');
                csv.append("销售时间,商品,数量,单价(元),小计(元),收款方式,优惠(元),合计(元)\n");

                // JOIN 出"单 + 明细"，按销售时间排序，导出文件可直接阅读
                List<String> rows = new ArrayList<>();
                try (Cursor cursor = db.query(new androidx.sqlite.db.SimpleSQLiteQuery(
                        "SELECT s.saleTime, si.productName, si.quantityMilli, si.unitPriceCents, "
                                + "si.lineTotalCents, s.payMethod, s.discountCents, s.totalAmountCents "
                                + "FROM sales s JOIN sale_items si ON si.saleId = s.id "
                                + "WHERE s.isDeleted = 0 ORDER BY s.saleTime"))) {
                    while (cursor.moveToNext()) {
                        long saleTime = cursor.getLong(0);
                        String product = cursor.getString(1);
                        long qtyMilli = cursor.getLong(2);
                        long unitPrice = cursor.getLong(3);
                        long lineTotal = cursor.getLong(4);
                        int payMethod = cursor.getInt(5);
                        long discount = cursor.getLong(6);
                        long total = cursor.getLong(7);

                        rows.add(TimeUtil.formatFull(saleTime) + ","
                                + quote(product) + ","
                                + quote(QuantityText(qtyMilli)) + ","
                                + quote(MoneyText(unitPrice)) + ","
                                + quote(MoneyText(lineTotal)) + ","
                                + quote(com.example.accounting.data.model.PayMethod.displayName(payMethod)) + ","
                                + quote(MoneyText(discount)) + ","
                                + quote(MoneyText(total)));
                    }
                }
                for (String row : rows) {
                    csv.append(row).append('\n');
                }

                try (OutputStream out = context.getContentResolver()
                        .openOutputStream(destination)) {
                    if (out == null) {
                        throw new IOException("无法写入所选位置");
                    }
                    out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                }
                notifyDone(callback, "CSV 已导出");
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    private static String QuantityText(long quantityMilli) {
        return com.example.accounting.util.QuantityUtil.toDisplay(quantityMilli);
    }

    private static String MoneyText(long cents) {
        return com.example.accounting.util.MoneyUtil.toDisplay(cents);
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    // ---------------- 文件操作核心 ----------------

    /** 合并 WAL 后把数据库主文件复制到目标文件（本机备份用） */
    private void checkpointAndCopyTo(File target) throws IOException {
        checkpointWAL();
        File dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME);
        //noinspection ResultOfMethodCallIgnored
        copyFile(dbFile, target);
    }

    private void checkpointAndCopyToUri(Uri destination) throws IOException {
        checkpointWAL();
        File dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME);
        try (InputStream in = new FileInputStream(dbFile);
             OutputStream out = context.getContentResolver().openOutputStream(destination)) {
            if (out == null) {
                throw new IOException("无法写入所选位置");
            }
            copy(in, out);
        }
    }

    /** 把 -wal 日志合并回主文件（TRUNCATE 顺带清空 wal 文件，减少磁盘占用） */
    private void checkpointWAL() {
        AppDatabase db = AppDatabase.getInstance(context);
        try (Cursor cursor = db.query(new androidx.sqlite.db.SimpleSQLiteQuery(
                "PRAGMA wal_checkpoint(TRUNCATE)"))) {
            // cursor 遍历一次才真正执行；结果行（busy/log/checkpointed）无需关心
            while (cursor.moveToNext()) {
                // no-op
            }
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            copy(in, out);
            out.getFD().sync(); // 强制落盘：断电场景下备份文件也必须完整
        }
    }

    private void copyUriToFile(Uri source, File target) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("无法读取所选文件");
            }
            copy(in, out);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    // ---------------- 回调工具 ----------------

    private void notifyDone(BackupCallback callback, String message) {
        mainHandler.post(() -> callback.onDone(message));
    }

    private void notifyError(BackupCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    // ---------------- 供 Worker 使用 ----------------

    /** WorkManager 调用的静态入口；Worker 线程直接执行，不走回调 */
    public static void runLocalBackup(@NonNull Context context) throws IOException {
        AppDatabase db = AppDatabase.getInstance(context);
        BackupManager manager = new BackupManager(
                context,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                db.syncStateDao());
        manager.performLocalBackup();
    }
}
