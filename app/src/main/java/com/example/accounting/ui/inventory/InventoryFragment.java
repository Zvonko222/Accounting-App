package com.example.accounting.ui.inventory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.databinding.FragmentInventoryBinding;
import com.example.accounting.util.QuantityUtil;

import java.util.List;

/**
 * 库存页：搜索商品、新增/编辑商品（跳转 ProductEditActivity）、
 * 盘点修正（弹窗）、查看库存变动记录（弹窗）。
 * 数据全部来自 InventoryViewModel 的 LiveData，列表自动刷新。
 */
public class InventoryFragment extends Fragment implements ProductAdapter.Listener {

    private FragmentInventoryBinding binding;
    private InventoryViewModel viewModel;
    private ProductAdapter adapter;

    /** 搜索框文字变化时换查询；null 表示没在搜索 */
    private LiveData<List<Product>> currentSource;

    /** 分类筛选条（"全部 / 各分类"），与搜索叠加 */
    private com.example.accounting.ui.common.CategoryFilter categoryFilter;

    /** 最新商品列表（分类切换时用它重新过滤提交） */
    private List<Product> latestProducts;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInventoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity())
                .get(InventoryViewModel.class);

        adapter = new ProductAdapter(this);
        binding.productList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.productList.setAdapter(adapter);

        // 分类筛选：分类变化重建 chips，选中变化用最新列表重新过滤
        categoryFilter = new com.example.accounting.ui.common.CategoryFilter(
                binding.chipGroupCategory, () ->
                        adapter.submitList(categoryFilter.apply(latestProducts)));
        viewModel.getCategories().observe(getViewLifecycleOwner(),
                categories -> categoryFilter.setCategories(categories));

        // 首次订阅：页面可见期间用 MutableLiveData 切换搜索结果
        subscribe(viewModel.getProducts());

        binding.searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String keyword = s.toString().trim();
                unsubscribeCurrent();
                subscribe(keyword.isEmpty()
                        ? viewModel.getProducts()
                        : viewModel.searchProducts(keyword));
            }
        });

        binding.fabAddProduct.setOnClickListener(v -> openEditor(null));

        // 分类管理入口（共享弹窗，含二级目录）
        binding.btnManageCategory.setOnClickListener(v ->
                com.example.accounting.ui.common.CategoryManageDialog.show(this,
                        ((com.example.accounting.AccountingApp) requireActivity().getApplication())
                                .getCategoryRepository(),
                        viewModel.getCategories()));
    }

    private void subscribe(LiveData<List<Product>> source) {
        currentSource = source;
        source.observe(getViewLifecycleOwner(), products -> {
            latestProducts = products;
            adapter.submitList(categoryFilter.apply(products));
        });
    }

    private void unsubscribeCurrent() {
        if (currentSource != null) {
            currentSource.removeObservers(getViewLifecycleOwner());
        }
    }

    private void openEditor(Product product) {
        ProductEditActivity.start(requireContext(), product);
    }

    // ---------------- ProductAdapter.Listener ----------------

    @Override
    public void onEditProduct(Product product) {
        openEditor(product);
    }

    @Override
    public void onAdjustStock(Product product) {
        showAdjustDialog(product);
    }

    @Override
    public void onShowHistory(Product product) {
        showHistoryDialog(product);
    }

    /** 盘点修正弹窗：输入实际数量 → Repository 算差额、改库存、记台账 */
    private void showAdjustDialog(Product product) {
        com.example.accounting.databinding.DialogAdjustStockBinding dialogBinding =
                com.example.accounting.databinding.DialogAdjustStockBinding
                        .inflate(getLayoutInflater());

        dialogBinding.currentStock.setText(QuantityUtil.toDisplay(product.stockQuantityMilli)
                + product.unit);
        dialogBinding.adjustUnit.setText(product.unit);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.stock_adjust) + " · " + product.name)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String text = String.valueOf(dialogBinding.actualQty.getText()).trim();
                    Long actual = QuantityUtil.parseNonNegative(text);
                    if (actual == null) {
                        dialogBinding.actualQty.setError(getString(R.string.stock_non_negative));
                        return;
                    }
                    String reason = String.valueOf(dialogBinding.adjustReason.getText()).trim();
                    viewModel.adjustStock(product.id, actual, reason, new SaveToastCallback());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 库存变动记录弹窗：台账只读展示，最新在前 */
    private void showHistoryDialog(Product product) {
        View contentView = getLayoutInflater()
                .inflate(R.layout.dialog_stock_movements, null, false);
        RecyclerViewHolder holder = new RecyclerViewHolder(contentView);
        StockMovementAdapter movementAdapter = new StockMovementAdapter();
        holder.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        holder.recycler.setAdapter(movementAdapter);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(product.name + " · " + getString(R.string.movements_title))
                .setView(contentView)
                .setPositiveButton(R.string.close, null)
                .show();

        viewModel.getMovements(product.id).observe(getViewLifecycleOwner(), movements -> {
            movementAdapter.submitList(movements);
            holder.empty.setVisibility(
                    movements == null || movements.isEmpty() ? View.VISIBLE : View.GONE);
        });

        // 弹窗关闭时取消观察，避免泄漏
        dialog.setOnDismissListener(d ->
                viewModel.getMovements(product.id)
                        .removeObservers(getViewLifecycleOwner()));
    }

    /** 简单持有弹窗里的 RecyclerView 和空提示 */
    private static class RecyclerViewHolder {
        final androidx.recyclerview.widget.RecyclerView recycler;
        final View empty;

        RecyclerViewHolder(View parent) {
            recycler = parent.findViewById(R.id.movement_list);
            empty = parent.findViewById(R.id.empty_hint);
        }
    }

    /** 保存结果统一用 Toast 提示（非静态内部类，可直接拿 Fragment 的 Context） */
    private class SaveToastCallback implements com.example.accounting.data.repository.SaveCallback {
        @Override
        public void onSuccess() {
            android.widget.Toast.makeText(requireContext(),
                    R.string.product_saved, android.widget.Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(String message) {
            android.widget.Toast.makeText(requireContext(),
                    getString(R.string.backup_failed) + "：" + message,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
