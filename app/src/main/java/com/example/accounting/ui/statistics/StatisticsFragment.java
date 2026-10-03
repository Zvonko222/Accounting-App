package com.example.accounting.ui.statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.accounting.databinding.FragmentStatisticsBinding;
import com.example.accounting.util.MoneyUtil;

/** 统计页：本月卡片 + 近 7 天柱状图 + 热销榜 */
public class StatisticsFragment extends Fragment {

    private FragmentStatisticsBinding binding;
    private StatisticsViewModel viewModel;

    private TopProductAdapter topProductAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(StatisticsViewModel.class);

        viewModel.getMonthSalesTotal().observe(getViewLifecycleOwner(), total ->
                binding.cardMonthSales.setText(MoneyUtil.toYuan(total == null ? 0 : total)));
        viewModel.getMonthSaleCount().observe(getViewLifecycleOwner(), count ->
                binding.cardMonthCount.setText(String.valueOf(count == null ? 0 : count)));
        viewModel.getMonthPurchaseTotal().observe(getViewLifecycleOwner(), total ->
                binding.cardMonthPurchase.setText(MoneyUtil.toYuan(total == null ? 0 : total)));
        viewModel.getMonthExpenseTotal().observe(getViewLifecycleOwner(), total ->
                binding.cardMonthExpense.setText(MoneyUtil.toYuan(total == null ? 0 : total)));
        viewModel.getMonthGrossProfit().observe(getViewLifecycleOwner(), profit ->
                binding.cardMonthProfit.setText(MoneyUtil.toYuan(profit == null ? 0 : profit)));

        viewModel.getDailySales().observe(getViewLifecycleOwner(),
                dailySales -> binding.barChart.setData(dailySales));

        topProductAdapter = new TopProductAdapter();
        binding.topProductList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.topProductList.setAdapter(topProductAdapter);
        viewModel.getTopProducts().observe(getViewLifecycleOwner(),
                products -> topProductAdapter.submitList(products));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
