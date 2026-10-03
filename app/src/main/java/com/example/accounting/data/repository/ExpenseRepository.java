package com.example.accounting.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.dao.ExpenseDao;
import com.example.accounting.data.db.entity.Expense;
import com.example.accounting.data.model.ExpenseType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 支出相关数据的唯一入口。支出是单表，无子表，逻辑最简单。
 */
public class ExpenseRepository {

    private final AppDatabase database;
    private final ExpenseDao expenseDao;
    private final ExecutorService writeExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ExpenseRepository(AppDatabase database, ExecutorService writeExecutor) {
        this.database = database;
        this.expenseDao = database.expenseDao();
        this.writeExecutor = writeExecutor;
    }

    // ---------------- 查询 ----------------

    public LiveData<List<Expense>> observeExpensesBetween(long fromMillis, long toMillis) {
        return expenseDao.observeBetween(fromMillis, toMillis);
    }

    public LiveData<Long> observeTotalBetween(long fromMillis, long toMillis) {
        return expenseDao.observeTotalBetween(fromMillis, toMillis);
    }

    // ---------------- 写入 ----------------

    public void recordExpense(int expenseType, long amountCents,
                              String note, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                if (amountCents <= 0) {
                    throw new IllegalArgumentException("金额必须大于 0");
                }

                Expense expense = new Expense();
                expense.id = UUID.randomUUID().toString();
                expense.initTimestamps();
                expense.expenseTime = System.currentTimeMillis();
                expense.expenseType = expenseType;
                expense.amountCents = amountCents;
                expense.note = normalizeText(note);
                expenseDao.insert(expense);

                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    /** 删除一笔支出 = 软删除（保持与其他业务数据同样的墓碑规则） */
    public void deleteExpense(String expenseId, SaveCallback callback) {
        writeExecutor.execute(() -> {
            try {
                Expense expense = expenseDao.findById(expenseId);
                if (expense != null && !expense.isDeleted) {
                    expense.markDeleted();
                    expenseDao.update(expense);
                }
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e.getMessage());
            }
        });
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
