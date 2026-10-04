package com.example.accounting.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Category;
import com.example.accounting.data.repository.CategoryRepository;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.ui.settings.CategoryManageAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类管理弹窗（共享）：库存页与设置页共用。
 * 支持二级目录：新增时可选上级分类（只列顶级分类作上级）；
 * 子分类在列表里缩进显示（"└ 子分类"）。
 */
public final class CategoryManageDialog {

    private CategoryManageDialog() {
    }

    public static void show(Fragment fragment, CategoryRepository repository,
                            LiveData<List<Category>> categoriesLiveData) {
        View contentView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.dialog_category_manage, null, false);
        RecyclerView recycler = contentView.findViewById(R.id.category_list);
        EditText nameInput = contentView.findViewById(R.id.input_category_name);

        CategoryManageAdapter adapter = new CategoryManageAdapter(category ->
                confirmDisable(fragment, repository, category));
        recycler.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        recycler.setAdapter(adapter);

        // 用 Fragment 的生命周期观察（弹窗可能比 View 活得久）
        categoriesLiveData.observe(fragment, categories -> {
            adapter.submitList(categories);
        });

        contentView.findViewById(R.id.btn_add_category).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            if (name.isEmpty()) {
                nameInput.setError(fragment.getString(R.string.name_required));
                return;
            }
            repository.addCategory(name, null, new SaveCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(fragment.requireContext(),
                            R.string.category_added, Toast.LENGTH_SHORT).show();
                    nameInput.setText("");
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        });

        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.category_manage)
                .setView(contentView)
                .setPositiveButton(R.string.close, null)
                .create()
                .show();
    }

    private static void confirmDisable(Fragment fragment, CategoryRepository repository,
                                       Category category) {
        new AlertDialog.Builder(fragment.requireContext())
                .setMessage(fragment.getString(R.string.disable_confirm)
                        + "\n（" + category.name + "）")
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        repository.disableCategory(category.id, new SaveCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(fragment.requireContext(),
                                        R.string.category_disabled, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(fragment.requireContext(), message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
