package com.example.accounting.ui.sales;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.accounting.R;
import com.example.accounting.data.db.dao.PurchaseWithItems;
import com.example.accounting.data.db.dao.PurchaseWithSummary;
import com.example.accounting.data.db.dao.SaleWithItems;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.data.db.entity.Expense;
import com.example.accounting.databinding.DialogSaleDetailBinding;
import com.example.accounting.databinding.FragmentSalesBinding;
import com.example.accounting.ui.expense.ExpenseDialog;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * 流水页：销售/进货/支出三个 Tab 共用一个 RecyclerView；
 * 顶部筛选行：时间范围 Chip（今天/近7天/本月/上月）+ "显示已作废"开关。
 * 销售/进货：点卡片弹详情（可修改）、可作废；支出：长按删除。
 */
public class SalesFragment extends Fragment
        implements SaleListAdapter.Listener, PurchaseListAdapter.Listener,
        ExpenseListAdapter.Listener {

    private static final int TAB_SALES = 0;
    private static final int TAB_PURCHASES = 1;
    private static final int TAB_EXPENSES = 2;

    private FragmentSalesBinding binding;
    private SalesViewModel viewModel;

    private SaleListAdapter saleAdapter;
    private PurchaseListAdapter purchaseAdapter;
    private ExpenseListAdapter expenseAdapter;

    private int currentTab = TAB_SALES;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSalesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SalesViewModel.class);

        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        saleAdapter = new SaleListAdapter(this);
        purchaseAdapter = new PurchaseListAdapter(this);
        expenseAdapter = new ExpenseListAdapter(this);

        // 三个列表都跟着筛选条件自动换数据（switchMap）
        viewModel.getSales().observe(getViewLifecycleOwner(), sales -> {
            if (currentTab == TAB_SALES) {
                saleAdapter.submitList(sales);
                binding.emptyHint.setVisibility(empty(sales));
            }
        });
        viewModel.getPurchases().observe(getViewLifecycleOwner(), purchases -> {
            if (currentTab == TAB_PURCHASES) {
                purchaseAdapter.submitList(purchases);
                binding.emptyHint.setVisibility(empty(purchases));
            }
        });
        viewModel.getExpenses().observe(getViewLifecycleOwner(), expenses -> {
            if (currentTab == TAB_EXPENSES) {
                expenseAdapter.submitList(expenses);
                binding.emptyHint.setVisibility(empty(expenses));
            }
        });

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        setupFilterChips();

        binding.fabAddExpense.setOnClickListener(v -> ExpenseDialog.show(requireContext()));

        switchTab(TAB_SALES);
    }

    private void setupFilterChips() {
        binding.chipGroupRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_today) {
                viewModel.setTimeRange(TimeUtil.todayStart(), TimeUtil.todayEnd());
            } else if (checkedId == R.id.chip_7days) {
                viewModel.setTimeRange(TimeUtil.daysAgoStart(6), TimeUtil.todayEnd());
            } else if (checkedId == R.id.chip_last_month) {
                viewModel.setTimeRange(TimeUtil.lastMonthStart(), TimeUtil.lastMonthEnd());
            } else {
                viewModel.setTimeRange(TimeUtil.monthStart(), TimeUtil.monthEnd());
            }
        });

        binding.chipShowVoided.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setShowVoided(isChecked));
    }

    private void switchTab(int position) {
        currentTab = position;
        int itemCount;
        if (position == TAB_SALES) {
            binding.recycler.setAdapter(saleAdapter);
            saleAdapter.submitList(viewModel.getSales().getValue());
            itemCount = saleAdapter.getItemCount();
        } else if (position == TAB_PURCHASES) {
            binding.recycler.setAdapter(purchaseAdapter);
            purchaseAdapter.submitList(viewModel.getPurchases().getValue());
            itemCount = purchaseAdapter.getItemCount();
        } else {
            binding.recycler.setAdapter(expenseAdapter);
            expenseAdapter.submitList(viewModel.getExpenses().getValue());
            itemCount = expenseAdapter.getItemCount();
        }
        binding.emptyHint.setVisibility(itemCount == 0 ? View.VISIBLE : View.GONE);
    }

    private static int empty(List<?> list) {
        return (list == null || list.isEmpty()) ? View.VISIBLE : View.GONE;
    }

    // ---------------- 销售列表交互 ----------------

    @Override
    public void onSaleClicked(SaleWithSummary item) {
        viewModel.loadSaleDetail(item.sale.id, this::showSaleDetailDialog);
    }

    @Override
    public void onVoidSaleClicked(SaleWithSummary item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.void_sale_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.voidSale(item.sale.id, new SaveToast()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSaleDetailDialog(SaleWithItems detail) {
        if (detail == null || detail.sale == null) {
            return;
        }
        DialogSaleDetailBinding dialogBinding = DialogSaleDetailBinding
                .inflate(getLayoutInflater());

        dialogBinding.detailList.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        DetailLineAdapter adapter = new DetailLineAdapter(true);
        dialogBinding.detailList.setAdapter(adapter);
        adapter.submit(detail.items);

        String info = getString(R.string.pay_method_label) + "："
                + com.example.accounting.data.model.PayMethod.displayName(detail.sale.payMethod)
                + " · " + TimeUtil.formatFull(detail.sale.saleTime);
        if (detail.sale.discountCents > 0) {
            info += "\n" + getString(R.string.discount_label) + "："
                    + MoneyUtil.toYuan(detail.sale.discountCents);
        }
        dialogBinding.detailInfo.setText(info);
        dialogBinding.detailTotal.setText(getString(R.string.total_label) + " "
                + MoneyUtil.toYuan(detail.sale.totalAmountCents));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sale_detail)
                .setView(dialogBinding.getRoot());

        // 未作废的单据提供"修改"入口：进入开单页回填编辑，
        // 保存时一个事务里作废旧单 + 重开新单
        if (!detail.sale.isDeleted) {
            builder.setPositiveButton(R.string.btn_modify, (dialog, which) ->
                    SaleEditActivity.startForEdit(requireContext(), detail.sale.id));
        }
        builder.setNegativeButton(R.string.close, null).show();
    }

    // ---------------- 进货列表交互 ----------------

    @Override
    public void onPurchaseClicked(PurchaseWithSummary item) {
        viewModel.loadPurchaseDetail(item.purchase.id, this::showPurchaseDetailDialog);
    }

    @Override
    public void onVoidPurchaseClicked(PurchaseWithSummary item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.void_purchase_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.voidPurchase(item.purchase.id, new SaveToast()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPurchaseDetailDialog(PurchaseWithItems detail) {
        if (detail == null || detail.purchase == null) {
            return;
        }
        DialogSaleDetailBinding dialogBinding = DialogSaleDetailBinding
                .inflate(getLayoutInflater());

        dialogBinding.detailList.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        DetailLineAdapter adapter = new DetailLineAdapter(false);
        dialogBinding.detailList.setAdapter(adapter);
        adapter.submitPurchaseItems(detail.items);

        String info = TimeUtil.formatFull(detail.purchase.purchaseTime);
        if (detail.purchase.supplierName != null) {
            info += "\n" + getString(R.string.supplier_label) + "：" + detail.purchase.supplierName;
        }
        dialogBinding.detailInfo.setText(info);
        dialogBinding.detailTotal.setText(getString(R.string.total_label) + " "
                + MoneyUtil.toYuan(detail.purchase.totalAmountCents));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.purchase_detail)
                .setView(dialogBinding.getRoot());

        // 未作废的进货单同样提供"修改"（供应商/数量/进价都可改）
        if (!detail.purchase.isDeleted) {
            builder.setPositiveButton(R.string.btn_modify, (dialog, which) ->
                    com.example.accounting.ui.purchase.PurchaseEditActivity
                            .startForEdit(requireContext(), detail.purchase.id));
        }
        builder.setNegativeButton(R.string.close, null).show();
    }

    // ---------------- 支出列表交互 ----------------

    @Override
    public void onExpenseLongClicked(Expense expense) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.delete_expense_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.deleteExpense(expense.id, new SaveToast()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 保存结果 Toast（作废/删除共用） */
    private class SaveToast implements com.example.accounting.data.repository.SaveCallback {
        @Override
        public void onSuccess() {
            Toast.makeText(requireContext(), R.string.sale_saved, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(String message) {
            Toast.makeText(requireContext(),
                    getString(R.string.backup_failed) + "：" + message,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
