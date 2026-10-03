package com.example.accounting.ui.statistics;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.data.model.TopProduct;
import com.example.accounting.databinding.ItemTopProductBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 热销榜 Adapter：名次 + 商品名 + 销量 + 销售额。
 */
public class TopProductAdapter extends RecyclerView.Adapter<TopProductAdapter.TopViewHolder> {

    private final List<TopProduct> products = new ArrayList<>();

    public void submitList(List<TopProduct> newProducts) {
        products.clear();
        if (newProducts != null) {
            products.addAll(newProducts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTopProductBinding binding = ItemTopProductBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new TopViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TopViewHolder holder, int position) {
        TopProduct product = products.get(position);
        holder.binding.topRank.setText((position + 1) + ".");
        holder.binding.topName.setText(product.productName);
        holder.binding.topQty.setText(QuantityUtil.toDisplay(product.quantityMilli));
        holder.binding.topAmount.setText(MoneyUtil.toYuan(product.totalCents));
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class TopViewHolder extends RecyclerView.ViewHolder {

        final ItemTopProductBinding binding;

        TopViewHolder(ItemTopProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
