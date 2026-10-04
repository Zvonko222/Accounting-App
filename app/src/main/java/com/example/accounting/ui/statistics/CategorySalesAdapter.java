package com.example.accounting.ui.statistics;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.data.model.CategorySales;
import com.example.accounting.databinding.ItemCategorySalesBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类销售额榜 Adapter：分类名 + 销量 + 销售额（按销售额倒序，SQL 已排序）。
 */
public class CategorySalesAdapter
        extends RecyclerView.Adapter<CategorySalesAdapter.CategorySalesViewHolder> {

    private final List<CategorySales> items = new ArrayList<>();

    public void submitList(List<CategorySales> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategorySalesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategorySalesBinding binding = ItemCategorySalesBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CategorySalesViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategorySalesViewHolder holder, int position) {
        CategorySales item = items.get(position);
        holder.binding.categorySalesName.setText(item.name);
        holder.binding.categorySalesQty.setText(QuantityUtil.toDisplay(item.quantityMilli));
        holder.binding.categorySalesAmount.setText(MoneyUtil.toYuan(item.totalCents));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CategorySalesViewHolder extends RecyclerView.ViewHolder {

        final ItemCategorySalesBinding binding;

        CategorySalesViewHolder(ItemCategorySalesBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
