package com.example.accounting.ui.orders;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.OrderEvent;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.db.entity.SaleItem;
import com.example.accounting.util.QuantityUtil;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.databinding.FragmentOrdersBinding;
import com.example.accounting.data.repository.SaveCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 订单页：确认外卖/预订订单是否已交付。
 * 上面是"待交付"（每行大按钮确认），下面是"最近已交付"（只读）。
 */
public class OrdersFragment extends Fragment implements OrderAdapter.Listener {

    private FragmentOrdersBinding binding;
    private OrdersViewModel viewModel;

    private OrderAdapter pendingAdapter;
    private OrderAdapter deliveredAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOrdersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(OrdersViewModel.class);

        pendingAdapter = new OrderAdapter(true, this);
        binding.pendingList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.pendingList.setAdapter(pendingAdapter);

        deliveredAdapter = new OrderAdapter(false, this);
        binding.deliveredList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.deliveredList.setAdapter(deliveredAdapter);

        viewModel.getPendingDelivery().observe(getViewLifecycleOwner(), sales -> {
            pendingAdapter.submitList(sales);
            binding.pendingEmpty.setVisibility(
                    sales == null || sales.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getDeliveredRecently().observe(getViewLifecycleOwner(), sales -> {
            deliveredAdapter.submitList(sales);
            binding.deliveredSection.setVisibility(
                    sales == null || sales.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onDeliverClicked(SaleWithSummary order) {
        Sale sale = order.sale;
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.deliver_confirm) + "¥"
                        + com.example.accounting.util.MoneyUtil
                                .toDisplay(sale.totalAmountCents) + "？")
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.markDelivered(sale.id, new SaveCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(requireContext(),
                                        R.string.delivered_done, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(requireContext(), message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onOrderEventClicked(SaleWithSummary order) {
        String[] labels = {getString(R.string.order_event_return),
                getString(R.string.order_event_exchange),
                getString(R.string.order_event_other)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.order_event_title)
                .setSingleChoiceItems(labels, -1, (dialog, which) -> {
                    int eventType = which == 0 ? OrderEvent.TYPE_RETURN
                            : which == 1 ? OrderEvent.TYPE_EXCHANGE : OrderEvent.TYPE_OTHER;
                    dialog.dismiss();
                    if (eventType == OrderEvent.TYPE_EXCHANGE) {
                        showExchangeEditor(order);
                    } else {
                        showEventNote(order, eventType, "");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showExchangeEditor(SaleWithSummary order) {
        viewModel.loadSaleDetail(order.sale.id, detail -> {
            if (detail == null || detail.items == null || detail.items.isEmpty()) {
                Toast.makeText(requireContext(), R.string.empty_list, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] originalNames = new String[detail.items.size()];
            for (int i = 0; i < detail.items.size(); i++) {
                originalNames[i] = detail.items.get(i).productName;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.order_exchange_original)
                    .setItems(originalNames, (dialog, which) -> {
                        SaleItem original = detail.items.get(which);
                        viewModel.getProducts().observe(getViewLifecycleOwner(), products ->
                                chooseReplacement(order, original, products));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void chooseReplacement(SaleWithSummary order, SaleItem original,
                                   java.util.List<Product> products) {
        if (products == null || products.isEmpty()) {
            Toast.makeText(requireContext(), R.string.empty_list, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[products.size()];
        for (int i = 0; i < products.size(); i++) names[i] = products.get(i).name;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.order_exchange_replacement)
                .setItems(names, (dialog, which) -> showExchangeDetails(order, original,
                        products.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showExchangeDetails(SaleWithSummary order, SaleItem original,
                                     Product replacement) {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);
        EditText quantity = new EditText(requireContext());
        quantity.setHint(R.string.order_exchange_quantity);
        quantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        quantity.setText(QuantityUtil.toDisplay(original.quantityMilli));
        EditText note = new EditText(requireContext());
        note.setHint(R.string.order_event_note_hint);
        form.addView(quantity);
        form.addView(note);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(original.productName + " → " + replacement.name)
                .setView(form)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    Long quantityMilli = QuantityUtil.parse(quantity.getText().toString());
                    if (quantityMilli == null || quantityMilli <= 0) {
                        quantity.setError(getString(R.string.amount_required));
                        return;
                    }
                    viewModel.recordExchange(order.sale.id, original.productId, replacement.id,
                            quantityMilli, note.getText().toString(), new SaveCallback() {
                                @Override public void onSuccess() {
                                    Toast.makeText(requireContext(), R.string.order_event_saved,
                                            Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onError(String message) {
                                    Toast.makeText(requireContext(), message,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEventNote(SaleWithSummary order, int eventType, String initialNote) {
        EditText note = new EditText(requireContext());
        note.setText(initialNote);
        note.setHint(R.string.order_event_note_hint);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.order_event_note_title)
                .setView(note)
                .setPositiveButton(R.string.save, (dialog, which) ->
                        viewModel.recordOrderEvent(order.sale.id, eventType,
                                note.getText().toString(), new SaveCallback() {
                                    @Override public void onSuccess() {
                                        Toast.makeText(requireContext(),
                                                R.string.order_event_saved, Toast.LENGTH_SHORT).show();
                                    }
                                    @Override public void onError(String message) {
                                        Toast.makeText(requireContext(), message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}



