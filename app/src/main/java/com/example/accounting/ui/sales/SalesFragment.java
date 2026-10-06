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
import com.example.accounting.data.db.dao.SaleWithItems;
import com.example.accounting.data.model.LedgerItem;
import com.example.accounting.databinding.DialogSaleDetailBinding;
import com.example.accounting.databinding.FragmentSalesBinding;
import com.example.accounting.ui.expense.ExpenseDialog;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 流水页：一条按时间排序的大流水（销售/进货/支出混排），
 * 类型 Chip + 时间 Chip 两条筛选，点击行看详情（可修改/作废）。
 * 对不熟悉手机的用户：不用切 Tab，从头到尾就是"一本账"。
 */
public class SalesFragment extends Fragment implements LedgerAdapter.Listener {

    private FragmentSalesBinding binding;
    private SalesViewModel viewModel;

    private LedgerAdapter ledgerAdapter;

    /** 搜索关键字（空 = 不过滤）；过滤作用于当前已加载的流水 */
    private String searchKeyword = "";

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
        ledgerAdapter = new LedgerAdapter(this);
        binding.recycler.setAdapter(ledgerAdapter);

        // 合并大流水：筛选条件变化自动重绑查询并重新合成
        viewModel.getLedger().observe(getViewLifecycleOwner(), items -> {
            ledgerAdapter.submitList(applySearch(items));
            binding.emptyHint.setVisibility(
                    filteredEmpty(items) ? View.VISIBLE : View.GONE);
        });

        // 搜索：按标题/摘要包含关键字过滤（当页流水）
        binding.searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                searchKeyword = s.toString().trim();
                ledgerAdapter.submitList(applySearch(viewModel.getLedger().getValue()));
            }
        });

        setupFilterChips();

        binding.fabAddExpense.setOnClickListener(v -> ExpenseDialog.show(requireContext()));
    }

    private void setupFilterChips() {
        // 类型：全部 / 销售 / 进货 / 支出
        binding.chipGroupType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_type_sale) {
                viewModel.setTypeFilter(LedgerItem.TYPE_SALE);
            } else if (checkedId == R.id.chip_type_purchase) {
                viewModel.setTypeFilter(LedgerItem.TYPE_PURCHASE);
            } else if (checkedId == R.id.chip_type_expense) {
                viewModel.setTypeFilter(LedgerItem.TYPE_EXPENSE);
            } else {
                viewModel.setTypeFilter(SalesViewModel.TYPE_ALL);
            }
        });

        // 时间：今天 / 近 7 天 / 本月 / 上月
        binding.chipGroupRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_custom_date) {
                showCustomDatePicker();
            } else if (checkedId == R.id.chip_today) {
                viewModel.setTimeRange(TimeUtil.todayStart(), TimeUtil.todayEnd());
            } else if (checkedId == R.id.chip_7days) {
                viewModel.setTimeRange(TimeUtil.daysAgoStart(6), TimeUtil.todayEnd());
            } else if (checkedId == R.id.chip_last_month) {
                viewModel.setTimeRange(TimeUtil.lastMonthStart(), TimeUtil.lastMonthEnd());
            } else {
                viewModel.setTimeRange(TimeUtil.monthStart(), TimeUtil.monthEnd());
            }
        });

        // 显示已作废（默认开，能看到作废轨迹）
        binding.chipShowVoided.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setShowVoided(isChecked));
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
                    Toast.makeText(requireContext(), R.string.custom_date_invalid,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.setCustomTimeRange(from.getTimeInMillis(), to.getTimeInMillis());
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
        }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private boolean filteredEmpty(List<LedgerItem> items) {
        List<LedgerItem> filtered = applySearch(items);
        return filtered == null || filtered.isEmpty();
    }

    /** 搜索过滤：标题或摘要包含关键字（不分大小写）；无关键字原样返回 */
    private List<LedgerItem> applySearch(List<LedgerItem> items) {
        if (searchKeyword.isEmpty() || items == null) {
            return items;
        }
        String kw = searchKeyword.toLowerCase();
        List<LedgerItem> result = new java.util.ArrayList<>();
        for (LedgerItem item : items) {
            String haystack = (item.title + " " + item.subtitle).toLowerCase();
            if (haystack.contains(kw)) {
                result.add(item);
            }
        }
        return result;
    }

    // ---------------- LedgerAdapter.Listener ----------------

    @Override
    public void onItemClicked(LedgerItem item) {
        if (item.type == LedgerItem.TYPE_SALE) {
            viewModel.loadSaleDetail(item.id, this::showSaleDetailDialog);
        } else if (item.type == LedgerItem.TYPE_PURCHASE) {
            viewModel.loadPurchaseDetail(item.id, this::showPurchaseDetailDialog);
        } else {
            viewModel.loadExpense(item.id, this::showExpenseDetailDialog);
        }
    }

    /** 支出详情弹窗：类型/时间/金额/备注 + 删除按钮（与其他详情交互一致） */
    private void showExpenseDetailDialog(com.example.accounting.data.db.entity.Expense expense) {
        if (expense == null) {
            return;
        }
        String message = getString(R.string.expense_type_label) + "："
                + com.example.accounting.data.model.ExpenseType.displayName(expense.expenseType)
                + "\n" + TimeUtil.formatFull(expense.expenseTime)
                + "\n" + getString(R.string.total_label) + "："
                + MoneyUtil.toYuan(expense.amountCents)
                + (expense.note == null ? "" : "\n" + expense.note);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.expense_title)
                .setMessage(message);
        if (!expense.isDeleted) {
            builder.setNeutralButton(R.string.delete, (dialog, which) ->
                    viewModel.deleteExpense(expense.id, new SaveToast()));
        }
        builder.setPositiveButton(R.string.close, null).show();
    }

    @Override
    public void onItemLongClicked(LedgerItem item) {
        // 统一交互：长按 = 作废/删除（都已软删除，历史可追溯）
        if (item.deleted) {
            return;
        }
        if (item.type == LedgerItem.TYPE_SALE) {
            confirmVoidSale(item.id);
        } else if (item.type == LedgerItem.TYPE_PURCHASE) {
            confirmVoidPurchase(item.id);
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.delete_expense_confirm)
                    .setPositiveButton(R.string.confirm, (dialog, which) ->
                            viewModel.deleteExpense(item.id, new SaveToast()))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
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

        // 更改经过：退货/换货等订单事件随时间列出（详情弹窗的核心诉求）
        ((com.example.accounting.AccountingApp) requireActivity().getApplication())
                .getSaleRepository()
                .observeOrderEvents(detail.sale.id)
                .observe(getViewLifecycleOwner(), events -> {
                    if (events == null || events.isEmpty()) {
                        return;
                    }
                    StringBuilder trail = new StringBuilder("\n\n更改经过：");
                    for (com.example.accounting.data.db.entity.OrderEvent e : events) {
                        trail.append("\n· ").append(eventTypeName(e.eventType))
                                .append(" ").append(TimeUtil.formatFull(e.eventTime));
                        if (e.eventType == com.example.accounting.data.db.entity.OrderEvent.TYPE_RETURN) {
                            trail.append("  −").append(MoneyUtil.toYuan(Math.abs(e.differenceCents)));
                        } else if (e.eventType == com.example.accounting.data.db.entity.OrderEvent.TYPE_EXCHANGE) {
                            trail.append("  差价 ").append(MoneyUtil.toYuan(e.differenceCents));
                        }
                        if (e.note != null && !e.note.isEmpty()) {
                            trail.append("（").append(e.note).append("）");
                        }
                    }
                    dialogBinding.detailInfo.append(trail.toString());
                });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sale_detail)
                .setView(dialogBinding.getRoot());

        // 未作废的单据提供"修改"与"作废"（作废有二次确认）
        if (!detail.sale.isDeleted) {
            builder.setPositiveButton(R.string.btn_modify, (dialog, which) ->
                    SaleEditActivity.startForEdit(requireContext(), detail.sale.id));
            builder.setNeutralButton(R.string.void_action, (dialog, which) ->
                    confirmVoidSale(detail.sale.id));
        }
        builder.setNegativeButton(R.string.close, null).show();
    }

    private String eventTypeName(int eventType) {
        if (eventType == com.example.accounting.data.db.entity.OrderEvent.TYPE_RETURN) {
            return "退货";
        }
        if (eventType == com.example.accounting.data.db.entity.OrderEvent.TYPE_EXCHANGE) {
            return "换货";
        }
        return "其他变更";
    }

    private void confirmVoidSale(String saleId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.void_sale_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.voidSale(saleId, new SaveToast()))
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

        if (!detail.purchase.isDeleted) {
            builder.setPositiveButton(R.string.btn_modify, (dialog, which) ->
                    com.example.accounting.ui.purchase.PurchaseEditActivity
                            .startForEdit(requireContext(), detail.purchase.id));
            builder.setNeutralButton(R.string.void_action, (dialog, which) ->
                    confirmVoidPurchase(detail.purchase.id));
        }
        builder.setNegativeButton(R.string.close, null).show();
    }

    private void confirmVoidPurchase(String purchaseId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.void_purchase_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.voidPurchase(purchaseId, new SaveToast()))
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
