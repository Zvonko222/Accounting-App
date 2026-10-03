package com.example.accounting.ui.expense;

import android.app.Application;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.accounting.R;
import com.example.accounting.data.model.ExpenseType;
import com.example.accounting.data.repository.ExpenseRepository;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.databinding.DialogRecordExpenseBinding;
import com.example.accounting.util.MoneyUtil;
import com.google.android.material.chip.Chip;

/**
 * "记支出"弹窗：选类型（chip 免打字）→ 输金额 → 保存。
 * 首页和流水页都用这一个入口，行为一致。
 */
public final class ExpenseDialog {

    private ExpenseDialog() {
    }

    public static void show(Context context) {
        DialogRecordExpenseBinding binding = DialogRecordExpenseBinding
                .inflate(LayoutInflater.from(context));

        // 按类型常量动态生成 chip：加一种支出类型只改 ExpenseType 一个类
        int[] options = ExpenseType.options();
        for (int i = 0; i < options.length; i++) {
            Chip chip = new Chip(context);
            chip.setId(View.generateViewId());
            chip.setText(ExpenseType.displayName(options[i]));
            chip.setCheckable(true);
            chip.setTag(options[i]);
            binding.chipGroupExpenseType.addView(chip);
            if (i == 0) {
                chip.setChecked(true);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.expense_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.save, null)   // 先置空，点按时校验，不合法不关闭
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Object selectedTag = binding.chipGroupExpenseType.getCheckedChipId() == View.NO_ID
                        ? null
                        : binding.chipGroupExpenseType.findViewById(
                                binding.chipGroupExpenseType.getCheckedChipId()).getTag();
                int expenseType = selectedTag instanceof Integer ? (Integer) selectedTag : ExpenseType.OTHER;

                String amountText = String.valueOf(binding.inputExpenseAmount.getText()).trim();
                Long amount = MoneyUtil.parseYuan(amountText);
                if (amount == null || amount <= 0) {
                    binding.inputExpenseAmount.setError(
                            context.getString(R.string.amount_required));
                    return;
                }

                Application app = (Application) context.getApplicationContext();
                ExpenseRepository repository = ((com.example.accounting.AccountingApp) app)
                        .getExpenseRepository();
                String note = String.valueOf(binding.inputExpenseNote.getText()).trim();

                repository.recordExpense(expenseType, amount,
                        note.isEmpty() ? null : note, new SaveCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(context,
                                        R.string.expense_saved, Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(context,
                                        context.getString(R.string.backup_failed) + "：" + message,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            });
        });

        dialog.show();
    }
}
