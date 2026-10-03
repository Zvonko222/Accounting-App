package com.example.accounting.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.databinding.FragmentHomeBinding;
import com.example.accounting.ui.expense.ExpenseDialog;
import com.example.accounting.ui.purchase.PurchaseEditActivity;
import com.example.accounting.ui.sales.SaleEditActivity;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.List;

/**
 * 首页：今日四张卡片 + 库存预警 + 三个大按钮。
 * 这里的按钮是全 App 的主要操作入口，所以放最大。
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        viewModel.getTodaySalesTotal().observe(getViewLifecycleOwner(), total ->
                binding.cardTodaySales.setText(MoneyUtil.toYuan(total == null ? 0 : total)));
        viewModel.getTodaySaleCount().observe(getViewLifecycleOwner(), count ->
                binding.cardTodayCount.setText(String.valueOf(count == null ? 0 : count)));
        viewModel.getTodayExpenseTotal().observe(getViewLifecycleOwner(), total ->
                binding.cardTodayExpense.setText(MoneyUtil.toYuan(total == null ? 0 : total)));
        viewModel.getTodayGrossProfit().observe(getViewLifecycleOwner(), profit ->
                binding.cardTodayProfit.setText(MoneyUtil.toYuan(profit == null ? 0 : profit)));

        viewModel.getLowStockProducts().observe(getViewLifecycleOwner(),
                this::showLowStockWarning);

        binding.btnRecordSale.setOnClickListener(v ->
                SaleEditActivity.start(requireContext()));
        binding.btnRecordPurchase.setOnClickListener(v ->
                PurchaseEditActivity.start(requireContext()));
        binding.btnRecordExpense.setOnClickListener(v ->
                ExpenseDialog.show(requireContext()));
        binding.btnOcrPurchase.setOnClickListener(v ->
                com.example.accounting.ui.purchase.OcrImportActivity.start(requireContext()));
    }

    /** 预警卡片：有预警才显示，列出商品名和当前库存 */
    private void showLowStockWarning(List<Product> lowStockProducts) {
        if (lowStockProducts == null || lowStockProducts.isEmpty()) {
            binding.cardLowStock.setVisibility(View.GONE);
            return;
        }
        StringBuilder text = new StringBuilder(
                getString(R.string.low_stock_items, lowStockProducts.size()));
        int shown = Math.min(lowStockProducts.size(), 5);
        for (int i = 0; i < shown; i++) {
            Product product = lowStockProducts.get(i);
            text.append("\n· ").append(product.name).append("：")
                    .append(QuantityUtil.toDisplay(product.stockQuantityMilli))
                    .append(product.unit);
        }
        binding.textLowStock.setText(text);
        binding.cardLowStock.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
