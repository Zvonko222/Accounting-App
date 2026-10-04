package com.example.accounting.ui.sales;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.model.PayMethod;
import com.example.accounting.data.model.SaleCartLine;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.data.repository.SaleRepository;
import com.example.accounting.databinding.ActivitySaleEditBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;
import com.example.accounting.util.SaleCalculator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * 开单页：点商品网格 → 弹出"单价+数量"编辑框（预填默认值）→ 加入本单；
 * 购物车里点商品名或数量 → 同一个弹窗重新编辑；+/- 快速加减；
 * 底部实时显示"本单共 N 种商品"和合计。
 * 保存结果经 SaveCallback 回到主线程 Toast 提示。
 */
public class SaleEditActivity extends AppCompatActivity
        implements CartAdapter.Listener {

    private static final String EXTRA_EDIT_SALE_ID = "extra_edit_sale_id";

    private ActivitySaleEditBinding binding;
    private SaleEditViewModel viewModel;

    private SaleProductGridAdapter productGridAdapter;
    private CartAdapter cartAdapter;

    /** 商品网格当前数据（查商品对象用） */
    private List<Product> currentProducts;

    /** 分类筛选（货物多了以后按分类找货） */
    private com.example.accounting.ui.common.CategoryFilter categoryFilter;

    /** 最新商品列表（分类切换时重新过滤） */
    private List<Product> latestProducts;

    /** 修改模式：被修改的销售单 id；开新单时为 null */
    private String editingSaleId;

    /** 修改模式下防止重复回填 */
    private boolean prefilled;

    public static void start(Context context) {
        context.startActivity(new Intent(context, SaleEditActivity.class));
    }

    /** 修改模式入口：把已保存的销售单内容载入本页编辑 */
    public static void startForEdit(Context context, String saleId) {
        Intent intent = new Intent(context, SaleEditActivity.class);
        intent.putExtra(EXTRA_EDIT_SALE_ID, saleId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySaleEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(SaleEditViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnQuickProduct.setOnClickListener(v -> showQuickProductDialog());
        binding.btnEditDelivery.setOnClickListener(v -> showDeliveryEditor(null));

        // 商品网格：2 列
        productGridAdapter = new SaleProductGridAdapter(this::onProductClicked);
        binding.productGrid.setLayoutManager(new GridLayoutManager(this, 2));
        binding.productGrid.setAdapter(productGridAdapter);

        cartAdapter = new CartAdapter(this);
        binding.cartList.setLayoutManager(new LinearLayoutManager(this));
        binding.cartList.setAdapter(cartAdapter);

        categoryFilter = new com.example.accounting.ui.common.CategoryFilter(
                binding.chipGroupCategory, () ->
                        productGridAdapter.submitList(categoryFilter.apply(latestProducts)));
        viewModel.getCategories().observe(this,
                categories -> categoryFilter.setCategories(categories));

        subscribe();

        editingSaleId = getIntent().getStringExtra(EXTRA_EDIT_SALE_ID);
        if (editingSaleId != null) {
            // 修改模式：载入旧单内容回填，保存按钮变成"保存修改"
            binding.toolbar.setTitle(R.string.edit_sale_title);
            binding.btnComplete.setText(R.string.save_changes);
            viewModel.loadSaleDetail(editingSaleId, detail -> {
                if (prefilled || detail == null || detail.sale == null) {
                    return;
                }
                prefilled = true;
                List<SaleCartLine> lines = new java.util.ArrayList<>();
                for (com.example.accounting.data.db.entity.SaleItem item : detail.items) {
                    lines.add(new SaleCartLine(item.productId, item.productName,
                            item.unitPriceCents, item.quantityMilli));
                }
                viewModel.prefillCart(lines);
                viewModel.prefillDiscount(detail.sale.discountCents);
                viewModel.prefillRecordTime(detail.sale.saleTime);
                viewModel.prefillDelivery(detail.sale.deliveryStatus);
                setDeliveryFields(detail.sale.deliveryAddress, detail.sale.deliveryPhone);
                if (detail.sale.discountCents > 0) {
                    binding.inputDiscount.setText(
                            MoneyUtil.toDisplay(detail.sale.discountCents));
                }
                selectPayMethod(detail.sale.payMethod);
            });
        }

        // 优惠输入：变化即重算合计；清空/非法按 0 处理
        binding.inputDiscount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Long discount = MoneyUtil.parseYuan(String.valueOf(s).trim());
                viewModel.setDiscountCents(discount == null ? 0 : discount);
            }
        });

        binding.btnComplete.setOnClickListener(v -> {
            if (viewModel.getCartLineCount() == 0) {
                Toast.makeText(this, R.string.need_items, Toast.LENGTH_SHORT).show();
                return;
            }
            if (hasInsufficientStock()) {
                Toast.makeText(this, R.string.stock_insufficient_warning,
                        Toast.LENGTH_LONG).show();
            }
            showDeliveryEditor(this::executeSaleCompletion);
        });

        viewModel.getDeliveryRequested().observe(this, requested ->
                binding.cbDelivery.setChecked(Boolean.TRUE.equals(requested)));
        binding.cbDelivery.setOnCheckedChangeListener((view, isChecked) ->
                viewModel.setDeliveryRequested(isChecked));
        setDeliveryFields("", "");
        updateDeliverySummary();

        // 记账时间：显示当前选定值，点击弹出日期 + 时间选择器（补录昨天的单用）
        viewModel.getRecordTime().observe(this, time ->
                binding.textRecordTime.setText(getString(R.string.record_time_fmt,
                        com.example.accounting.util.TimeUtil.formatFull(time == null
                                ? System.currentTimeMillis() : time))));
        binding.textRecordTime.setOnClickListener(v -> showRecordTimePicker());
    }

    private void setDeliveryFields(String address, String phone) {
        viewModel.setDeliveryAddress(address);
        viewModel.setDeliveryPhone(phone);
        binding.addressFields.removeAllViews();
        binding.phoneFields.removeAllViews();
        String[] addresses = address == null || address.trim().isEmpty()
                ? new String[]{""} : address.split("\\n", -1);
        String[] phones = phone == null || phone.trim().isEmpty()
                ? new String[]{""} : phone.split("\\n", -1);
        for (String value : addresses) addDeliveryField(false, value);
        for (String value : phones) addDeliveryField(true, value);
        updateDeliverySummary();
    }

    private void addDeliveryField(boolean phone, String value) {
        LinearLayout container = phone ? binding.phoneFields : binding.addressFields;
        addDeliveryField(phone, value, container, this::publishDeliveryFields);
    }

    private void addDeliveryField(boolean phone, String value, LinearLayout container,
                                  Runnable publisher) {
        boolean firstField = container.getChildCount() == 0;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        EditText input = new EditText(this);
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        input.setSingleLine(true);
        input.setText(value);
        input.setHint(phone ? R.string.delivery_phone_hint : R.string.delivery_address_hint);
        input.setInputType(phone ? android.text.InputType.TYPE_CLASS_PHONE
                : android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        row.addView(input);

        MaterialButton action = new MaterialButton(this);
        action.setText(firstField ? "+" : "−");
        action.setTextSize(20);
        action.setMinWidth(0);
        action.setMinHeight(0);
        action.setPadding(8, 0, 8, 0);
        if (firstField) {
            action.setOnClickListener(v -> addDeliveryField(phone, "", container, publisher));
        } else {
            action.setOnClickListener(v -> {
                container.removeView(row);
                publisher.run();
            });
        }
        row.addView(action);
        container.addView(row);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                publisher.run();
            }
        });
    }

    private void publishDeliveryFields() {
        viewModel.setDeliveryAddress(joinDeliveryFields(binding.addressFields));
        viewModel.setDeliveryPhone(joinDeliveryFields(binding.phoneFields));
    }

    private String joinDeliveryFields(LinearLayout container) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < container.getChildCount(); i++) {
            EditText input = (EditText) ((LinearLayout) container.getChildAt(i)).getChildAt(0);
            String value = input.getText().toString().trim();
            if (value.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(value);
        }
        return result.toString();
    }

    private void updateDeliverySummary() {
        String address = joinDeliveryFields(binding.addressFields);
        String phone = joinDeliveryFields(binding.phoneFields);
        String summary = address.isEmpty() && phone.isEmpty()
                ? getString(R.string.delivery_summary_empty)
                : (address.isEmpty() ? "电话：" + phone
                : phone.isEmpty() ? "地址：" + address
                : "地址：" + address + " · 电话：" + phone);
        binding.textDeliverySummary.setText(summary.replace('\n', ' '));
    }

    private void showDeliveryEditor(@Nullable Runnable afterSave) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);
        LinearLayout addresses = new LinearLayout(this);
        addresses.setOrientation(LinearLayout.VERTICAL);
        LinearLayout phones = new LinearLayout(this);
        phones.setOrientation(LinearLayout.VERTICAL);
        TextView addressTitle = new TextView(this);
        addressTitle.setText(R.string.delivery_address_title);
        TextView phoneTitle = new TextView(this);
        phoneTitle.setText(R.string.delivery_phone_title);
        form.addView(addressTitle);
        form.addView(addresses);
        form.addView(phoneTitle);
        form.addView(phones);

        String address = joinDeliveryFields(binding.addressFields);
        String phone = joinDeliveryFields(binding.phoneFields);
        String[] addressValues = address.isEmpty() ? new String[]{""} : address.split("\\n", -1);
        String[] phoneValues = phone.isEmpty() ? new String[]{""} : phone.split("\\n", -1);
        for (String value : addressValues) {
            addDeliveryField(false, value, addresses, () -> { });
        }
        for (String value : phoneValues) {
            addDeliveryField(true, value, phones, () -> { });
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delivery_editor_title)
                .setView(form)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String newAddress = joinDeliveryFields(addresses);
                    String newPhone = joinDeliveryFields(phones);
                    setDeliveryFields(newAddress, newPhone);
                    dialog.dismiss();
                    if (afterSave != null) {
                        afterSave.run();
                    }
                }));
        dialog.show();
    }

    private void showQuickProductDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);
        EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.quick_product_name_hint);
        EditText priceInput = new EditText(this);
        priceInput.setHint(R.string.quick_product_price_hint);
        priceInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(nameInput);
        form.addView(priceInput);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.quick_product)
                .setView(form)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = nameInput.getText().toString().trim();
                    Long price = MoneyUtil.parseYuan(priceInput.getText().toString().trim());
                    if (name.isEmpty()) {
                        nameInput.setError(getString(R.string.name_required));
                        return;
                    }
                    if (price == null || price < 0) {
                        priceInput.setError(getString(R.string.price_required));
                        return;
                    }
                    viewModel.createTemporaryProduct(name, price, new SaveCallback() {
                        @Override public void onSuccess() {
                            dialog.dismiss();
                            Toast.makeText(SaleEditActivity.this, R.string.product_saved,
                                    Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(String message) {
                            Toast.makeText(SaleEditActivity.this, message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }));
        dialog.show();
    }

    /** 两级选择：先选日期，再选时间。选到未来的部分由 Repository 钳制到现在 */
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

        viewModel.getCart().observe(this, lines -> refreshCart());
        viewModel.getTotalCents().observe(this, total -> binding.textTotal.setText(
                MoneyUtil.toYuan(total == null ? 0 : total)));
        viewModel.getCartLineCountLive().observe(this, count ->
                binding.textCartCount.setText(
                        getString(R.string.cart_count_fmt, count == null ? 0 : count)));
    }

    private void refreshCart() {
        List<SaleCartLine> lines = viewModel.getCart().getValue();
        boolean empty = lines == null || lines.isEmpty();
        binding.cartEmptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        cartAdapter.submit(lines, currentProducts);
    }

    /** 点商品网格：默认单价 = 售价、默认数量 = 1，弹出编辑框让用户确认或修改 */
    private void onProductClicked(Product product) {
        SaleCartLine existing = findLine(product.id);
        showLineDialog(product, existing);
    }

    private SaleCartLine findLine(String productId) {
        List<SaleCartLine> lines = viewModel.getCart().getValue();
        if (lines != null) {
            for (SaleCartLine line : lines) {
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
     * 行编辑弹窗：一个弹窗同时编辑单价和数量，小计实时预览。
     * 网格点击（existing == null）= 新增一行；购物车点击 = 编辑已有行。
     */
    private void showLineDialog(Product product, SaleCartLine existing) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_cart_line, null, false);

        TextInputLayout priceLayout = dialogView.findViewById(R.id.layout_line_price);
        EditText priceInput = dialogView.findViewById(R.id.input_line_price);
        EditText quantityInput = dialogView.findViewById(R.id.input_line_quantity);
        TextView subtotalText = dialogView.findViewById(R.id.text_line_subtotal);

        priceLayout.setHint(getString(R.string.sale_price_label));
        priceInput.setText(MoneyUtil.toDisplay(
                existing != null ? existing.unitPriceCents : product.salePriceCents));
        quantityInput.setText(QuantityUtil.toDisplay(
                existing != null ? existing.quantityMilli : 1000L));

        // 单价或数量变化时实时重算小计，用户按下"确定"前就能看到金额
        Runnable refreshSubtotal = () -> {
            Long price = MoneyUtil.parseYuan(String.valueOf(priceInput.getText()).trim());
            Long quantity = QuantityUtil.parse(String.valueOf(quantityInput.getText()).trim());
            if (price != null && quantity != null) {
                subtotalText.setText(getString(R.string.line_subtotal,
                        MoneyUtil.toYuan(SaleCalculator.lineTotalCents(price, quantity))));
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

        // 确定：校验失败不关弹窗（用 setOnShowListener 拦截按钮点击）
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(product.name)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    Long price = MoneyUtil.parseYuan(String.valueOf(priceInput.getText()).trim());
                    Long quantity = QuantityUtil.parse(String.valueOf(quantityInput.getText()).trim());
                    if (price == null || price < 0) {
                        priceInput.setError(getString(R.string.price_required));
                        return;
                    }
                    if (quantity == null) {
                        quantityInput.setError(getString(R.string.amount_required));
                        return;
                    }
                    viewModel.upsertLine(product, price, quantity);
                    dialog.dismiss();
                }));
        dialog.show();

        // 让购物车滚到底部，露出刚加的商品
        binding.cartList.post(() -> binding.cartList.smoothScrollToPosition(
                Math.max(viewModel.getCartLineCount() - 1, 0)));
    }

    // ---------------- CartAdapter.Listener ----------------

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

    private boolean hasInsufficientStock() {
        List<Product> products = viewModel.getProducts().getValue();
        List<SaleCartLine> lines = viewModel.getCart().getValue();
        if (products == null || lines == null) {
            return false;
        }
        for (SaleCartLine line : lines) {
            for (Product product : products) {
                if (product.id.equals(line.productId)
                        && line.quantityMilli > product.stockQuantityMilli) {
                    return true;
                }
            }
        }
        return false;
    }

    private void executeSaleCompletion() {
        if (viewModel.getCartLineCount() == 0) {
            Toast.makeText(this, R.string.need_items, Toast.LENGTH_SHORT).show();
            return;
        }

        // 优惠来自输入框（已实时写入 ViewModel），合计 = Σ行小计 − 优惠（下限 0）
        int payMethod = resolvePayMethod();
        Long discount = viewModel.getDiscountCents().getValue();
        long discountCents = discount == null ? 0 : discount;

        SaveCallback callback = new SaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(SaleEditActivity.this,
                        editingSaleId != null ? R.string.record_edited : R.string.sale_saved,
                        Toast.LENGTH_SHORT).show();
                // 今日数据变了，马上刷新桌面小组件
                com.example.accounting.widget.TodayWidgetProvider.refreshAll(
                        SaleEditActivity.this);
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SaleEditActivity.this,
                        getString(R.string.backup_failed) + "：" + message,
                        Toast.LENGTH_LONG).show();
            }
        };

        if (editingSaleId != null) {
            // 修改模式：一个事务里作废旧单 + 重开新单
            viewModel.editSale(editingSaleId, discountCents, payMethod, callback);
        } else {
            viewModel.recordSale(discountCents, payMethod, callback);
        }
    }

    /** 修改模式回填收款方式 */
    private void selectPayMethod(int payMethod) {
        int chipId;
        switch (payMethod) {
            case PayMethod.WECHAT: chipId = R.id.chip_wechat; break;
            case PayMethod.ALIPAY: chipId = R.id.chip_alipay; break;
            case PayMethod.OTHER:  chipId = R.id.chip_other; break;
            default:               chipId = R.id.chip_cash; break;
        }
        binding.chipGroupPay.check(chipId);
    }

    private int resolvePayMethod() {
        int checkedId = binding.chipGroupPay.getCheckedChipId();
        if (checkedId == R.id.chip_wechat) {
            return PayMethod.WECHAT;
        } else if (checkedId == R.id.chip_alipay) {
            return PayMethod.ALIPAY;
        } else if (checkedId == R.id.chip_other) {
            return PayMethod.OTHER;
        } else {
            return PayMethod.CASH;
        }
    }
}
