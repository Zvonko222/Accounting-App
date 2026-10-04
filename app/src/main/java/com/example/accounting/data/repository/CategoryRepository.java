package com.example.accounting.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.CategoryDao;
import com.example.accounting.data.db.entity.Category;

import java.util.ArrayList;
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

    /** 新增顶级分类。重名会触发数据库唯一约束，转为用户能看懂的提示 */
    public void addCategory(String name, SaveCallback callback) {
        addCategory(name, null, callback);
    }

    /** 新增分类（可指定上级分类形成二级目录）。重名转为友好提示 */
    public void addCategory(String name, String parentId, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                Category category = new Category();
                category.id = UUID.randomUUID().toString();
                category.initTimestamps();
                category.name = name.trim();
                // 分类已按用户要求退回一级：列保留兼容，新数据一律顶级
                category.parentId = null;
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

    /**
     * 二级目录排序：顶级分类按 sortOrder 在前，各自的子分类紧跟其后（供界面缩进显示）。
     * 无父的分类视为顶级；父分类被停用的子分类按顶级处理。
     */
    public static List<Category> sortHierarchical(List<Category> categories) {
        if (categories == null) {
            return new ArrayList<>();
        }
        List<Category> parents = new ArrayList<>();
        for (Category category : categories) {
            if (category.parentId == null) {
                parents.add(category);
            }
        }
        parents.sort((a, b) -> a.sortOrder != b.sortOrder
                ? Integer.compare(a.sortOrder, b.sortOrder)
                : a.name.compareTo(b.name));
        List<Category> result = new ArrayList<>();
        for (Category parent : parents) {
            result.add(parent);
            for (Category category : categories) {
                if (parent.id.equals(category.parentId)) {
                    result.add(category);
                }
            }
        }
        // 防御：父分类不在激活列表里的"孤儿"子分类也追加进去，不丢数据
        for (Category category : categories) {
            if (category.parentId != null && !result.contains(category)) {
                result.add(category);
            }
        }
        return result;
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
