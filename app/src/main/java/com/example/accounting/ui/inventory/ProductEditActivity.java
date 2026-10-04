package com.example.accounting.ui.inventory;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Category;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.databinding.ActivityProductEditBinding;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 新增 / 编辑商品页面。
 *
 * 通过 Intent extras 区分两种模式：不传 EXTRA_PRODUCT_ID 就是新增。
 * 编辑模式下先从 ViewModel 拿商品原数据填进表单。
 */
public class ProductEditActivity extends AppCompatActivity {

    private static final String EXTRA_PRODUCT_ID = "extra_product_id";

    private ActivityProductEditBinding binding;
    private InventoryViewModel viewModel;

    /** 编辑模式下被编辑的商品，新增时为 null */
    private Product editingProduct;

    /** 全部激活分类（原始列表） */
    private final List<Category> categories = new ArrayList<>();

    /** 下拉框展示顺序（父在前、子紧随），与 displayNames 一一对应 */
    private final List<Category> displayCategories = new ArrayList<>();

    /** 快捷新增分类：新增成功后要自动选中的分类名（等 LiveData 回来再选） */
    private String pendingNewCategoryName;

    public static void start(Context context, Product product) {
        Intent intent = new Intent(context, ProductEditActivity.class);
        if (product != null) {
            intent.putExtra(EXTRA_PRODUCT_ID, product.id);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(InventoryViewModel.class);

        String productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        boolean isEditMode = productId != null;

        binding.toolbar.setTitle(isEditMode ? R.string.edit_product : R.string.add_product);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.rowInitialStock.setVisibility(isEditMode ? View.GONE : View.VISIBLE);

        setupCategorySpinner();
        observeCategories();

        if (isEditMode) {
            observeEditingProduct(productId);
            binding.btnDisable.setVisibility(View.VISIBLE);
            binding.btnDisable.setOnClickListener(v -> confirmDisable());
        }

        binding.btnAddCategoryInline.setOnClickListener(v -> showQuickAddCategory());
        binding.btnSave.setOnClickListener(v -> save());
    }

    private void setupCategorySpinner() {
        // 第 0 项固定"不分类"，后面按层级展示：子分类带"└"缩进
        List<String> displayNames = new ArrayList<>();
        displayNames.add(getString(R.string.no_category));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCategory.setAdapter(adapter);
    }

    /** 分类表变化时同步下拉框（预置分类完成后会自动收到一次） */
    private void observeCategories() {
        viewModel.getCategories().observe(this, loaded -> {
            categories.clear();
            if (loaded != null) {
                categories.addAll(loaded);
            }
            List<String> displayNames = new ArrayList<>();
            displayNames.add(getString(R.string.no_category));
            for (Category category : categories) {
                displayNames.add(category.name);
            }
            // 平铺展示（分类已按用户要求退回一级）
            displayCategories.clear();
            displayNames.clear();
            displayNames.add(getString(R.string.no_category));
            for (Category category : categories) {
                displayCategories.add(category);
                displayNames.add(category.name);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, displayNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerCategory.setAdapter(adapter);

            // 编辑模式：等分类加载完再回填选中项
            if (editingProduct != null) {
                selectCategory(editingProduct.categoryId);
            }
            // 快捷新增：分类列表刷新后自动选中刚加的分类
            if (pendingNewCategoryName != null) {
                for (Category category : categories) {
                    if (category.name.equals(pendingNewCategoryName)) {
                        binding.spinnerCategory.setSelection(
                                displayNames.indexOf(pendingNewCategoryName));
                        pendingNewCategoryName = null;
                        break;
                    }
                }
            }
        });
    }

    private void observeEditingProduct(String productId) {
        // 直接观察全量列表找出目标商品（商品数量少，不值得为编辑页单独加 DAO 查询）
        viewModel.getProducts().observe(this, products -> {
            if (editingProduct != null) {
                return; // 已经填过表单，不再覆盖用户正在编辑的内容
            }
            if (products == null) {
                return;
            }
            for (Product product : products) {
                if (product.id.equals(productId)) {
                    editingProduct = product;
                    fillForm(product);
                    return;
                }
            }
        });
    }

    /** 快捷新增分类：不用跑去设置页，录商品的途中顺手加 */
    private void showQuickAddCategory() {
        android.view.View dialogView = getLayoutInflater()
                .inflate(R.layout.dialog_input_quantity, null, false);
        android.widget.EditText input = dialogView.findViewById(R.id.input_quantity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.category_name_hint);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.add_category_short)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String name = String.valueOf(input.getText()).trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    pendingNewCategoryName = name;
                    viewModel.addCategory(name, new SaveCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(ProductEditActivity.this,
                                    R.string.category_added, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String message) {
                            pendingNewCategoryName = null;
                            Toast.makeText(ProductEditActivity.this, message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void fillForm(Product product) {
        binding.inputName.setText(product.name);
        binding.inputSalePrice.setText(MoneyUtil.toDisplay(product.salePriceCents));
        if (product.purchasePriceCents > 0) {
            binding.inputPurchasePrice.setText(MoneyUtil.toDisplay(product.purchasePriceCents));
        }
        binding.inputUnit.setText(product.unit);
        binding.inputLowStock.setText(product.lowStockThresholdMilli == 0 ? ""
                : QuantityUtil.toDisplay(product.lowStockThresholdMilli));
        binding.inputBarcode.setText(product.barcode);
        binding.inputNote.setText(product.note);
        selectCategory(product.categoryId);
    }

    private void selectCategory(String categoryId) {
        if (categoryId == null) {
            binding.spinnerCategory.setSelection(0);
            return;
        }
        for (int i = 0; i < displayCategories.size(); i++) {
            if (displayCategories.get(i).id.equals(categoryId)) {
                binding.spinnerCategory.setSelection(i + 1);
                return;
            }
        }
        binding.spinnerCategory.setSelection(0);
    }

    private void save() {
        String name = text(binding.inputName);
        if (name.isEmpty()) {
            binding.inputName.setError(getString(R.string.name_required));
            return;
        }
        Long salePrice = MoneyUtil.parseYuan(text(binding.inputSalePrice));
        if (salePrice == null || salePrice < 0) {
            binding.inputSalePrice.setError(getString(R.string.price_required));
            return;
        }

        Product product = (editingProduct != null) ? editingProduct : new Product();
        product.name = name;
        product.salePriceCents = salePrice;
        product.purchasePriceCents = orZero(MoneyUtil.parseYuan(text(binding.inputPurchasePrice)));
        product.unit = text(binding.inputUnit).isEmpty() ? "个" : text(binding.inputUnit);
        product.barcode = text(binding.inputBarcode).isEmpty() ? null : text(binding.inputBarcode);
        product.note = text(binding.inputNote).isEmpty() ? null : text(binding.inputNote);
        product.lowStockThresholdMilli = orZero(QuantityUtil.parse(text(binding.inputLowStock)));
        product.categoryId = selectedCategoryId();

        long initialStock = (editingProduct == null)
                ? orZero(QuantityUtil.parse(text(binding.inputInitialStock))) : 0;

        viewModel.saveProduct(product, initialStock, new SaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ProductEditActivity.this,
                        R.string.product_saved, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ProductEditActivity.this,
                        getString(R.string.backup_failed) + "：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDisable() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.disable_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.disableProduct(editingProduct.id, new SaveCallback() {
                            @Override
                            public void onSuccess() {
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(ProductEditActivity.this, message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String selectedCategoryId() {
        int position = binding.spinnerCategory.getSelectedItemPosition();
        if (position <= 0 || position > displayCategories.size()) {
            return null;
        }
        return displayCategories.get(position - 1).id;
    }

    private static String text(com.google.android.material.textfield.TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static String text(android.widget.EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
