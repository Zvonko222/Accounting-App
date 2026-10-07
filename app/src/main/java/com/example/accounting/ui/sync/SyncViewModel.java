package com.example.accounting.ui.sync;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.accounting.AccountingApp;
import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.repository.backup.BackupManager;
import com.example.accounting.data.sync.SyncEngine;

/**
 * 云同步页状态：服务器配置、立即同步、状态行、各表待同步明细。
 * 与设置页解耦——设置页只留"云同步"入口按钮。
 */
public class SyncViewModel extends AndroidViewModel {

    private static final String KEY_URL = "server_url";
    private static final String KEY_KEY = "server_key";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_LAST_SYNC_ERROR = "last_sync_error";

    private final BackupManager backupManager;

    private final MutableLiveData<String> configUrl = new MutableLiveData<>("");
    private final MutableLiveData<String> configKey = new MutableLiveData<>("");
    private final MutableLiveData<Long> lastSyncAt = new MutableLiveData<>();
    private final MutableLiveData<String> lastSyncError = new MutableLiveData<>();
    private final MutableLiveData<Integer> pendingCount = new MutableLiveData<>(0);
    private final MutableLiveData<String> pendingDetail = new MutableLiveData<>("");

    public SyncViewModel(Application app) {
        super(app);
        backupManager = ((AccountingApp) app).getBackupManager();
    }

    public LiveData<String> getConfigUrl() { return configUrl; }
    public LiveData<String> getConfigKey() { return configKey; }
    public LiveData<Long> getLastSyncAt() { return lastSyncAt; }
    public LiveData<String> getLastSyncError() { return lastSyncError; }
    public LiveData<Integer> getPendingCount() { return pendingCount; }
    public LiveData<String> getPendingDetail() { return pendingDetail; }

    /** 读配置与状态（进页面时调用） */
    public void loadSyncConfig() {
        AppDatabase db = ((AccountingApp) getApplication()).getDatabase();
        ((AccountingApp) getApplication()).getDatabaseWriteExecutor().execute(() -> {
            String url = SyncEngine.getConfigStatic(getApplication(), KEY_URL);
            String key = SyncEngine.getConfigStatic(getApplication(), KEY_KEY);
            configUrl.postValue(url == null ? "" : url);
            configKey.postValue(key == null ? "" : key);
            String lastAt = SyncEngine.getConfigStatic(getApplication(), KEY_LAST_SYNC_AT);
            if (lastAt != null) {
                try {
                    lastSyncAt.postValue(Long.parseLong(lastAt));
                } catch (NumberFormatException ignored) {
                }
            }
            lastSyncError.postValue(SyncEngine.getConfigStatic(getApplication(), KEY_LAST_SYNC_ERROR));
            pendingCount.postValue(countPending(db));
        });
    }

    public void saveSyncConfig(String url, String key) {
        SyncEngine.saveConfig(getApplication(), KEY_URL, url);
        SyncEngine.saveConfig(getApplication(), KEY_KEY, key);
    }

    public void syncNow(SyncEngine.Callback callback) {
        SyncEngine.syncNow(getApplication(), new SyncEngine.Callback() {
            @Override
            public void onDone(String message) {
                loadSyncConfig();
                callback.onDone(message);
            }

            @Override
            public void onError(String message) {
                lastSyncError.postValue(message);
                loadSyncConfig();
                callback.onError(message);
            }
        });
    }

    private int countPending(AppDatabase db) {
        return db.categoryDao().listDirtyForSync(Integer.MAX_VALUE).size()
                + db.productDao().listDirtyForSync(Integer.MAX_VALUE).size()
                + db.saleDao().listDirtyForSync(Integer.MAX_VALUE).size()
                + db.saleDao().listDirtyItemsForSync(Integer.MAX_VALUE).size()
                + db.purchaseDao().listDirtyForSync(Integer.MAX_VALUE).size()
                + db.purchaseDao().listDirtyItemsForSync(Integer.MAX_VALUE).size()
                + db.expenseDao().listDirtyForSync(Integer.MAX_VALUE).size()
                + db.stockMovementDao().listDirtyForSync(Integer.MAX_VALUE).size()
                + db.orderEventDao().listDirtyForSync(Integer.MAX_VALUE).size();
    }

    public long getLastExportTime() {
        return backupManager.getLastExportTime();
    }
}
