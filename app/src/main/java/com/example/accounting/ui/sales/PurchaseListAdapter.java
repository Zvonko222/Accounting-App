package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.db.dao.PurchaseWithSummary;
import com.example.accounting.databinding.ItemPurchaseBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水页"进货"Tab 的 Adapter（与 SaleListAdapter 对称）。
 */
public class PurchaseListAdapter extends RecyclerView.Adapter<PurchaseListAdapter.PurchaseViewHolder> {

    public interface Listener {
        void onPurchaseClicked(PurchaseWithSummary purchase);

        void onVoidPurchaseClicked(PurchaseWithSummary purchase);
    }

    private final List<PurchaseWithSummary> purchases = new ArrayList<>();
    private final Listener listener;

    public PurchaseListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<PurchaseWithSummary> newPurchases) {
        purchases.clear();
        if (newPurchases != null) {
            purchases.addAll(newPurchases);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PurchaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPurchaseBinding binding = ItemPurchaseBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PurchaseViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PurchaseViewHolder holder, int position) {
        PurchaseWithSummary item = purchases.get(position);
        holder.binding.purchaseTime.setText(TimeUtil.formatFull(item.purchase.purchaseTime));
        holder.binding.purchaseTotal.setText("-" + MoneyUtil.toYuan(item.purchase.totalAmountCents));

        // 摘要：供应商（或"进货"）+ 商品摘要
        String summary = item.purchase.supplierName == null
                ? holder.binding.getRoot().getContext().getString(R.string.tab_purchases)
                : item.purchase.supplierName;
        if (item.itemsSummary != null && !item.itemsSummary.isEmpty()) {
            summary += " · " + item.itemsSummary;
        }
        holder.binding.purchaseSummary.setText(summary);

        if (item.purchase.isDeleted) {
            holder.binding.purchaseVoidedTag.setVisibility(View.VISIBLE);
            holder.binding.btnVoid.setVisibility(View.GONE);
            holder.binding.purchaseTotal.setAlpha(0.4f);
        } else {
            holder.binding.purchaseVoidedTag.setVisibility(View.GONE);
            holder.binding.btnVoid.setVisibility(View.VISIBLE);
            holder.binding.purchaseTotal.setAlpha(1f);
        }

        holder.binding.getRoot().setOnClickListener(v -> listener.onPurchaseClicked(item));
        holder.binding.btnVoid.setOnClickListener(v -> listener.onVoidPurchaseClicked(item));
    }

    @Override
    public int getItemCount() {
        return purchases.size();
    }

    static class PurchaseViewHolder extends RecyclerView.ViewHolder {

        final ItemPurchaseBinding binding;

        PurchaseViewHolder(ItemPurchaseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
