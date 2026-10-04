package com.example.accounting.ui.purchase;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.model.PurchaseCartLine;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.databinding.ActivityPurchaseEditBinding;
import com.example.accounting.ui.sales.SaleProductGridAdapter;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;
import com.example.accounting.util.SaleCalculator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * 进货开单页：点商品网格 → 弹出"进价+数量"编辑框（进价预填商品上次进价）→ 加入本单；
 * 清单里点商品名/数量/进价 → 同一个弹窗重新编辑；+/- 快速加减；
 * 底部实时显示"本单共 N 种商品"和合计。保存后各商品库存与最近进价自动更新。
 */
public class PurchaseEditActivity extends AppCompatActivity
        implements PurchaseCartAdapter.Listener {

    private static final String EXTRA_EDIT_PURCHASE_ID = "extra_edit_purchase_id";

    private ActivityPurchaseEditBinding binding;
    private PurchaseEditViewModel viewModel;

    private SaleProductGridAdapter productGridAdapter;
    private PurchaseCartAdapter cartAdapter;

    private List<Product> currentProducts;

    /** 分类筛选 + 最新商品列表（分类切换时重新过滤网格） */
    private com.example.accounting.ui.common.CategoryFilter categoryFilter;
    private List<Product> latestProducts;

    /** 修改模式：被修改的进货单 id；开新单时为 null */
    private String editingPurchaseId;

    /** 修改模式下防止重复回填 */
    private boolean prefilled;

    public static void start(Context context) {
        context.startActivity(new Intent(context, PurchaseEditActivity.class));
    }

    /** 修改模式入口：把已保存的进货单内容载入本页编辑 */
    public static void startForEdit(Context context, String purchaseId) {
        Intent intent = new Intent(context, PurchaseEditActivity.class);
        intent.putExtra(EXTRA_EDIT_PURCHASE_ID, purchaseId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPurchaseEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(PurchaseEditViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        productGridAdapter = new SaleProductGridAdapter(this::onProductClicked);
        binding.productGrid.setLayoutManager(new GridLayoutManager(this, 2));
        binding.productGrid.setAdapter(productGridAdapter);

        cartAdapter = new PurchaseCartAdapter(this);
        binding.cartList.setLayoutManager(new LinearLayoutManager(this));
        binding.cartList.setAdapter(cartAdapter);

        categoryFilter = new com.example.accounting.ui.common.CategoryFilter(
                binding.chipGroupCategory, () ->
                        productGridAdapter.submitList(categoryFilter.apply(latestProducts)));
        viewModel.getCategories().observe(this,
                categories -> categoryFilter.setCategories(categories));

        subscribe();

        editingPurchaseId = getIntent().getStringExtra(EXTRA_EDIT_PURCHASE_ID);
        if (editingPurchaseId != null) {
            binding.toolbar.setTitle(R.string.edit_purchase_title);
            binding.btnSavePurchase.setText(R.string.save_changes);
            viewModel.loadPurchaseDetail(editingPurchaseId, detail -> {
                if (prefilled || detail == null || detail.purchase == null) {
                    return;
                }
                prefilled = true;
                List<PurchaseCartLine> lines = new java.util.ArrayList<>();
                for (com.example.accounting.data.db.entity.PurchaseItem item : detail.items) {
                    lines.add(new PurchaseCartLine(item.productId, item.productName,
                            item.unitCostCents, item.quantityMilli));
                }
                viewModel.prefillLines(lines);
                viewModel.prefillRecordTime(detail.purchase.purchaseTime);
                if (detail.purchase.supplierName != null) {
                    binding.inputSupplier.setText(detail.purchase.supplierName);
                }
            });
        }

        binding.btnSavePurchase.setOnClickListener(v -> save());

        // 记账时间：显示当前选定值，点击弹出日期 + 时间选择器（补录用）
        viewModel.getRecordTime().observe(this, time ->
                binding.textRecordTime.setText(getString(R.string.record_time_fmt,
                        com.example.accounting.util.TimeUtil.formatFull(time == null
                                ? System.currentTimeMillis() : time))));
        binding.textRecordTime.setOnClickListener(v -> showRecordTimePicker());
    }

    private void showRecordTimePicker() {
        Long current = viewModel.getRecordTime().getValue();
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(current == null ? System.currentTimeMillis() : current);

        new android.app.DatePickerDialog(this, (dateView, year, month, day) -> {
            new android.app.TimePickerDialog(this,
                    (timeView, hour, minute) -> {
                        java.util.Calendar chosen = java.util.Calendar.getInstance();
                        chosen.set(year, month, day, hour, minute, 0);
                        viewModel.setRecordTime(chosen.getTimeInMillis());
                    },
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE), true).show();
        }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void subscribe() {
        LiveData<List<Product>> productsLive = viewModel.getProducts();
        productsLive.observe(this, products -> {
            currentProducts = products;
            latestProducts = products;
            productGridAdapter.submitList(categoryFilter.apply(products));
            refreshCart();
        });
        viewModel.getLines().observe(this, lines -> refreshCart());
        viewModel.getTotalCents().observe(this, total ->
                binding.textTotal.setText(MoneyUtil.toYuan(total == null ? 0 : total)));
        viewModel.getLineCountLive().observe(this, count ->
                binding.textCartCount.setText(
                        getString(R.string.cart_count_fmt, count == null ? 0 : count)));
    }

    private void refreshCart() {
        cartAdapter.submit(viewModel.getLines().getValue(), currentProducts);
    }

    /** 点商品网格：默认进价 = 商品上次进价、默认数量 = 1，弹出编辑框确认或修改 */
    private void onProductClicked(Product product) {
        showLineDialog(product, findLine(product.id));
    }

    private PurchaseCartLine findLine(String productId) {
        List<PurchaseCartLine> lines = viewModel.getLines().getValue();
        if (lines != null) {
            for (PurchaseCartLine line : lines) {
                if (line.productId.equals(productId)) {
                    return line;
                }
            }
        }
        return null;
    }

    private Product findProduct(String productId) {
        if (currentProducts != null) {
            for (Product product : currentProducts) {
                if (product.id.equals(productId)) {
                    return product;
                }
            }
        }
        return null;
    }

    /**
     * 行编辑弹窗：进价 + 数量 + 实时小计预览。
     * 网格点击（existing == null）= 新增；清单点击 = 编辑已有行。
     */
    private void showLineDialog(Product product, PurchaseCartLine existing) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_cart_line, null, false);

        TextInputLayout priceLayout = dialogView.findViewById(R.id.layout_line_price);
        EditText priceInput = dialogView.findViewById(R.id.input_line_price);
        EditText quantityInput = dialogView.findViewById(R.id.input_line_quantity);
        TextView subtotalText = dialogView.findViewById(R.id.text_line_subtotal);

        priceLayout.setHint(getString(R.string.unit_cost_label));
        priceInput.setText(MoneyUtil.toDisplay(
                existing != null ? existing.unitCostCents : product.purchasePriceCents));
        quantityInput.setText(QuantityUtil.toDisplay(
                existing != null ? existing.quantityMilli : 1000L));

        Runnable refreshSubtotal = () -> {
            Long cost = MoneyUtil.parseYuan(String.valueOf(priceInput.getText()).trim());
            Long quantity = QuantityUtil.parse(String.valueOf(quantityInput.getText()).trim());
            if (cost != null && quantity != null) {
                subtotalText.setText(getString(R.string.line_subtotal,
                        MoneyUtil.toYuan(SaleCalculator.lineTotalCents(cost, quantity))));
            } else {
                subtotalText.setText(R.string.line_subtotal_invalid);
            }
        };
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshSubtotal.run();
            }
        };
        priceInput.addTextChangedListener(watcher);
        quantityInput.addTextChangedListener(watcher);
        refreshSubtotal.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(product.name)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    Long cost = MoneyUtil.parseYuan(String.valueOf(priceInput.getText()).trim());
                    Long quantity = QuantityUtil.parse(String.valueOf(quantityInput.getText()).trim());
                    if (cost == null || cost < 0) {
                        priceInput.setError(getString(R.string.amount_required));
                        return;
                    }
                    if (quantity == null) {
                        quantityInput.setError(getString(R.string.amount_required));
                        return;
                    }
                    viewModel.upsertLine(product, cost, quantity);
                    dialog.dismiss();
                }));
        dialog.show();

        binding.cartList.post(() -> binding.cartList.smoothScrollToPosition(
                Math.max(viewModel.getLineCount() - 1, 0)));
    }

    // ---------------- PurchaseCartAdapter.Listener ----------------

    @Override
    public void onIncreaseClicked(String productId) {
        viewModel.increaseQuantity(productId);
    }

    @Override
    public void onDecreaseClicked(String productId) {
        viewModel.decreaseQuantity(productId);
    }

    @Override
    public void onLineClicked(String productId) {
        Product product = findProduct(productId);
        if (product != null) {
            showLineDialog(product, findLine(productId));
        }
    }

    private void save() {
        if (viewModel.getLineCount() == 0) {
            Toast.makeText(this, R.string.need_items, Toast.LENGTH_SHORT).show();
            return;
        }
        String supplier = String.valueOf(binding.inputSupplier.getText()).trim();

        SaveCallback callback = new SaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(PurchaseEditActivity.this,
                        editingPurchaseId != null ? R.string.record_edited : R.string.purchase_saved,
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(PurchaseEditActivity.this,
                        getString(R.string.backup_failed) + "：" + message,
                        Toast.LENGTH_LONG).show();
            }
        };

        if (editingPurchaseId != null) {
            // 修改模式：一个事务里作废旧单 + 重开新单
            viewModel.editPurchase(editingPurchaseId, supplier, callback);
        } else {
            viewModel.recordPurchase(supplier, callback);
        }
    }
}
