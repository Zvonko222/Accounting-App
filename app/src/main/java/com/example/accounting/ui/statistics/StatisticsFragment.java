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

import com.example.accounting.R;
import com.example.accounting.databinding.FragmentStatisticsBinding;
import com.example.accounting.util.MoneyUtil;

/** 统计页：本月卡片 + 近 7 天柱状图 + 热销榜 */
public class StatisticsFragment extends Fragment {

    private FragmentStatisticsBinding binding;
    private StatisticsViewModel viewModel;
    private CategorySalesAdapter categorySalesAdapter;

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
        // 毛利与销售额共同决定毛利率；任一变化都重算
        androidx.lifecycle.Observer<Long> rateObserver = profit -> updateProfitRateAndDailyAvg();
        androidx.lifecycle.Observer<Long> salesObserver = total -> updateProfitRateAndDailyAvg();
        viewModel.getMonthGrossProfit().observe(getViewLifecycleOwner(), rateObserver);
        viewModel.getMonthSalesTotal().observe(getViewLifecycleOwner(), salesObserver);

        viewModel.getMonthGrossProfit().observe(getViewLifecycleOwner(), profit ->
                binding.cardMonthProfit.setText(MoneyUtil.toYuan(profit == null ? 0 : profit)));

        // 趋势图数据（随 周/月/年 切换换绑查询）
        viewModel.getTrendPoints().observe(getViewLifecycleOwner(),
                points -> renderChart());

        // 图型切换：柱状/折线共用 TrendChartView，扇形切 PieChartView
        binding.chipGroupChartType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_chart_line) {
                binding.trendChart.setMode(com.example.accounting.ui.statistics.TrendChartView.MODE_LINE);
            } else {
                binding.trendChart.setMode(com.example.accounting.ui.statistics.TrendChartView.MODE_BAR);
            }
            renderChart();
        });

        // 指标切换：销售额 / 毛利 / 支出
        binding.chipGroupMetric.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_metric_profit) {
                viewModel.setChartMetric("profit");
            } else if (id == R.id.chip_metric_expense) {
                viewModel.setChartMetric("expense");
            } else {
                viewModel.setChartMetric("sales");
            }
            renderChart();
        });

        // 时间区间切换（周/月/年）
        binding.chipGroupRange2.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_range_week) {
                viewModel.setChartRange("week");
            } else if (id == R.id.chip_range_custom) {
                showCustomDatePicker();
            } else if (id == R.id.chip_range_year) {
                viewModel.setChartRange("year");
            } else {
                viewModel.setChartRange("month");
            }
        });

        // 分类占比扇形图：数据随区间变化
        viewModel.getCategorySales().observe(getViewLifecycleOwner(), items -> {
            categorySalesAdapter.submitList(items);
            if (binding.pieChart.getVisibility() == View.VISIBLE) {
                binding.pieChart.setData(items);
            }
        });

        topProductAdapter = new TopProductAdapter();
        binding.topProductList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.topProductList.setAdapter(topProductAdapter);
        viewModel.getTopProducts().observe(getViewLifecycleOwner(),
                products -> topProductAdapter.submitList(products));

        categorySalesAdapter = new CategorySalesAdapter();
        binding.categorySalesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.categorySalesList.setAdapter(categorySalesAdapter);

    }

    private void showCustomDatePicker() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        new android.app.DatePickerDialog(requireContext(), (dateView, year, month, day) -> {
            java.util.Calendar from = java.util.Calendar.getInstance();
            from.set(year, month, day, 0, 0, 0);
            from.set(java.util.Calendar.MILLISECOND, 0);
            new android.app.DatePickerDialog(requireContext(), (toView, toYear, toMonth, toDay) -> {
                java.util.Calendar to = java.util.Calendar.getInstance();
                to.set(toYear, toMonth, toDay, 23, 59, 59);
                to.set(java.util.Calendar.MILLISECOND, 999);
                if (from.getTimeInMillis() > to.getTimeInMillis()) {
                    android.widget.Toast.makeText(requireContext(), R.string.custom_date_invalid,
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.setCustomDateRange(from.getTimeInMillis(), to.getTimeInMillis());
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
        }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    /** 当前图型渲染：扇形模式显示 PieChartView，否则显示 TrendChartView */
    private void renderChart() {
        boolean pie = binding.chipGroupChartType.getCheckedChipId() == R.id.chip_chart_pie;
        binding.pieChart.setVisibility(pie ? View.VISIBLE : View.GONE);
        binding.trendChart.setVisibility(pie ? View.GONE : View.VISIBLE);
        if (pie) {
            binding.pieChart.setData(viewModel.getCategorySales().getValue());
        } else {
            binding.trendChart.setData(viewModel.getTrendPoints().getValue());
        }
    }

    /** 毛利率 = 毛利 / 销售额；日均 = 本月销售额 / 本月已过天数 */
    private void updateProfitRateAndDailyAvg() {
        Long profit = viewModel.getMonthGrossProfit().getValue();
        Long sales = viewModel.getMonthSalesTotal().getValue();
        long p = profit == null ? 0 : profit;
        long sv = sales == null ? 0 : sales;

        if (sv > 0) {
            binding.textProfitRate.setText(getString(R.string.profit_rate_fmt,
                    String.valueOf(Math.round(p * 100.0 / sv))));
            int dayOfMonth = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH);
            binding.textDailyAvg.setText(getString(R.string.daily_avg_fmt,
                    MoneyUtil.toYuan(sv / dayOfMonth)));
            binding.textProfitRate.setVisibility(View.VISIBLE);
            binding.textDailyAvg.setVisibility(View.VISIBLE);
        } else {
            binding.textProfitRate.setVisibility(View.GONE);
            binding.textDailyAvg.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
