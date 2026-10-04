package com.example.accounting.ui.common;

import android.content.Context;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Category;
import com.example.accounting.data.db.entity.Product;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品分类筛选条：一排"全部 / 各分类"Chip，点击后过滤商品列表。
 * 开单页、进货页、库存页共用同一套逻辑（三处行为必须完全一致）。
 *
 * 用法：setCategories() 喂分类（重建 chips，保留当前选中）；
 * apply() 过滤商品列表；选中变化时通过回调通知页面重新提交列表。
 */
public class CategoryFilter {

    private final ChipGroup chipGroup;
    private final Context context;
    private final Runnable onSelectionChanged;

    /** 当前选中的分类 id；null = 全部 */
    private String selectedCategoryId = null;

    private List<Category> categories = new ArrayList<>();

    public CategoryFilter(ChipGroup chipGroup, Runnable onSelectionChanged) {
        this.chipGroup = chipGroup;
        this.context = chipGroup.getContext();
        this.onSelectionChanged = onSelectionChanged;
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            View checked = group.findViewById(checkedIds.get(0));
            Object tag = checked == null ? null : checked.getTag();
            selectedCategoryId = tag instanceof String ? (String) tag : null;
            onSelectionChanged.run();
        });
    }

    /** 分类表变化时重建 chips（保留当前选中项） */
    public void setCategories(List<Category> loaded) {
        categories.clear();
        if (loaded != null) {
            categories.addAll(loaded);
        }
        chipGroup.removeAllViews();

        chipGroup.addView(buildChip(null, context.getString(R.string.filter_all),
                selectedCategoryId == null));

        for (Category category : categories) {
            chipGroup.addView(buildChip(category.id, category.name,
                    category.id.equals(selectedCategoryId)));
        }
    }

    private Chip buildChip(String categoryId, String label, boolean checked) {
        Chip chip = new Chip(context);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(categoryId);
        chip.setChecked(checked);
        chip.setCheckedIcon(null);
        chip.setChipBackgroundColorResource(R.color.bg_card);
        chip.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        return chip;
    }

    /** 按选中分类过滤商品（未选中 = 原样返回） */
    public List<Product> apply(List<Product> products) {
        if (selectedCategoryId == null || products == null) {
            return products;
        }
        List<Product> result = new ArrayList<>();
        for (Product product : products) {
            if (selectedCategoryId.equals(product.categoryId)) {
                result.add(product);
            }
        }
        return result;
    }
}
