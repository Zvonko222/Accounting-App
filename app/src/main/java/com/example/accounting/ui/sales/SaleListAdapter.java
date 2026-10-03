package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.databinding.ItemSaleBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水页"销售"Tab 的 Adapter：每行显示 时间 / 商品摘要 / 合计。
 * 点卡片看详情（详情里有"修改"）；点"作废"弹确认框。已作废的单不再显示作废按钮。
 */
public class SaleListAdapter extends RecyclerView.Adapter<SaleListAdapter.SaleViewHolder> {

    public interface Listener {
        void onSaleClicked(SaleWithSummary sale);

        void onVoidSaleClicked(SaleWithSummary sale);
    }

    private final List<SaleWithSummary> sales = new ArrayList<>();
    private final Listener listener;

    public SaleListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<SaleWithSummary> newSales) {
        sales.clear();
        if (newSales != null) {
            sales.addAll(newSales);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SaleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSaleBinding binding = ItemSaleBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new SaleViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SaleViewHolder holder, int position) {
        SaleWithSummary item = sales.get(position);
        holder.binding.saleTime.setText(TimeUtil.formatFull(item.sale.saleTime));
        holder.binding.saleTotal.setText("+" + MoneyUtil.toYuan(item.sale.totalAmountCents));

        // 摘要：收款方式 + 商品摘要（"cola×2、薯片×1"）
        String summary = com.example.accounting.data.model.PayMethod.displayName(item.sale.payMethod);
        if (item.itemsSummary != null && !item.itemsSummary.isEmpty()) {
            summary += " · " + item.itemsSummary;
        }
        holder.binding.saleSummary.setText(summary);

        if (item.sale.isDeleted) {
            holder.binding.saleVoidedTag.setVisibility(View.VISIBLE);
            holder.binding.btnVoid.setVisibility(View.GONE);
            holder.binding.saleTotal.setAlpha(0.4f);
        } else {
            holder.binding.saleVoidedTag.setVisibility(View.GONE);
            holder.binding.btnVoid.setVisibility(View.VISIBLE);
            holder.binding.saleTotal.setAlpha(1f);
        }

        holder.binding.getRoot().setOnClickListener(v -> listener.onSaleClicked(item));
        holder.binding.btnVoid.setOnClickListener(v -> listener.onVoidSaleClicked(item));
    }

    @Override
    public int getItemCount() {
        return sales.size();
    }

    static class SaleViewHolder extends RecyclerView.ViewHolder {

        final ItemSaleBinding binding;

        SaleViewHolder(ItemSaleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
