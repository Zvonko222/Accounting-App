package com.example.accounting.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.CategoryDao;
import com.example.accounting.data.db.entity.Category;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 分类相关数据的唯一入口：新增 / 停用 / 观察。
 * 分类是软删除：停用后商品编辑页不再显示，但历史单据不受影响
 * （明细里存的是商品快照，与分类无关）。
 */
public class CategoryRepository {

    private final CategoryDao categoryDao;
    private final ExecutorService writeExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CategoryRepository(AppDatabase database, ExecutorService writeExecutor) {
        this.categoryDao = database.categoryDao();
        this.writeExecutor = writeExecutor;
    }

    public LiveData<List<Category>> observeCategories() {
        return categoryDao.observeActive();
    }

    /** 新增分类。重名会触发数据库唯一约束，转为用户能看懂的提示 */
    public void addCategory(String name, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                Category category = new Category();
                category.id = UUID.randomUUID().toString();
                category.initTimestamps();
                category.name = name.trim();
                // 排在已有分类之后（按现有数量给一个序号即可，分类很少）
                List<Category> existing = categoryDao.listActive();
                category.sortOrder = existing.size();
                categoryDao.insert(category);
                notifySuccess(callback);
            } catch (Exception e) {
                // 重名触发 categories.name 的唯一约束，转成人话提示
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("UNIQUE") || message.contains("unique")) {
                    notifyError(callback, "分类已存在");
                } else {
                    notifyError(callback, message.isEmpty() ? "保存失败" : message);
                }
            }
        });
    }

    /** 停用分类（软删除） */
    public void disableCategory(String categoryId, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                Category category = categoryDao.findById(categoryId);
                if (category != null) {
                    category.markDeleted();
                    categoryDao.update(category);
                }
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

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
