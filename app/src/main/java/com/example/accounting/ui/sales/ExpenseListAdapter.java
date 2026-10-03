package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.data.db.entity.Expense;
import com.example.accounting.data.model.ExpenseType;
import com.example.accounting.databinding.ItemExpenseBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水页"支出"Tab 的 Adapter：类型 + 时间 + 金额。
 * 长按一项 → 删除（软删除，删除后从列表消失）。删除逻辑由 Fragment 决定。
 */
public class ExpenseListAdapter extends RecyclerView.Adapter<ExpenseListAdapter.ExpenseViewHolder> {

    public interface Listener {
        void onExpenseLongClicked(Expense expense);
    }

    private final List<Expense> expenses = new ArrayList<>();
    private final Listener listener;

    public ExpenseListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Expense> newExpenses) {
        expenses.clear();
        if (newExpenses != null) {
            expenses.addAll(newExpenses);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExpenseBinding binding = ItemExpenseBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ExpenseViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = expenses.get(position);
        holder.binding.expenseTitle.setText(ExpenseType.displayName(expense.expenseType));
        holder.binding.expenseTime.setText(TimeUtil.formatFull(expense.expenseTime));
        holder.binding.expenseAmount.setText("-" + MoneyUtil.toYuan(expense.amountCents));

        holder.binding.getRoot().setOnLongClickListener(v -> {
            listener.onExpenseLongClicked(expense);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    static class ExpenseViewHolder extends RecyclerView.ViewHolder {

        final ItemExpenseBinding binding;

        ExpenseViewHolder(ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
