package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.data.db.entity.PurchaseItem;
import com.example.accounting.data.db.entity.SaleItem;
import com.example.accounting.databinding.ItemDetailLineBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 单据详情弹窗里的明细行 Adapter。
 * 一个 Adapter 同时支持销售明细和进货明细（两者字段对称，只是单价叫法不同），
 * isSale 参数决定数量-单价列的显示方式。
 */
public class DetailLineAdapter extends RecyclerView.Adapter<DetailLineAdapter.LineViewHolder> {

    private final List<SaleItem> saleItems = new ArrayList<>();
    private final List<PurchaseItem> purchaseItems = new ArrayList<>();
    private final boolean isSale;

    public DetailLineAdapter(boolean isSale) {
        this.isSale = isSale;
    }

    public void submit(List<SaleItem> items) {
        saleItems.clear();
        if (items != null) {
            saleItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void submitPurchaseItems(List<PurchaseItem> items) {
        purchaseItems.clear();
        if (items != null) {
            purchaseItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return isSale ? saleItems.size() : purchaseItems.size();
    }

    @NonNull
    @Override
    public LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDetailLineBinding binding = ItemDetailLineBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new LineViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
        if (isSale) {
            SaleItem item = saleItems.get(position);
            holder.binding.detailName.setText(item.productName);
            holder.binding.detailQtyPrice.setText(
                    QuantityUtil.toDisplay(item.quantityMilli)
                            + " × " + MoneyUtil.toYuan(item.unitPriceCents));
            holder.binding.detailSubtotal.setText(MoneyUtil.toYuan(item.lineTotalCents));
        } else {
            PurchaseItem item = purchaseItems.get(position);
            holder.binding.detailName.setText(item.productName);
            holder.binding.detailQtyPrice.setText(
                    QuantityUtil.toDisplay(item.quantityMilli)
                            + " × " + MoneyUtil.toYuan(item.unitCostCents));
            holder.binding.detailSubtotal.setText(MoneyUtil.toYuan(item.lineTotalCents));
        }
    }

    static class LineViewHolder extends RecyclerView.ViewHolder {

        final ItemDetailLineBinding binding;

        LineViewHolder(ItemDetailLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
