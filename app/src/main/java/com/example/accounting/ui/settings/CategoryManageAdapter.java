package com.example.accounting.ui.settings;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.data.db.entity.Category;
import com.example.accounting.databinding.ItemCategoryBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类管理弹窗的列表 Adapter：分类名 + "停用"按钮（软删除）。
 */
public class CategoryManageAdapter
        extends RecyclerView.Adapter<CategoryManageAdapter.CategoryViewHolder> {

    public interface Listener {
        void onDisableCategoryClicked(Category category);
    }

    private final List<Category> categories = new ArrayList<>();
    private final Listener listener;

    public CategoryManageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Category> newCategories) {
        categories.clear();
        if (newCategories != null) {
            categories.addAll(newCategories);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryBinding binding = ItemCategoryBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CategoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        // 子分类缩进显示，一眼看出层级
        String label = category.parentId == null
                ? category.name : "　└ " + category.name;
        holder.binding.categoryName.setText(label);
        holder.binding.btnDisableCategory.setOnClickListener(
                v -> listener.onDisableCategoryClicked(category));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {

        final ItemCategoryBinding binding;

        CategoryViewHolder(ItemCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
