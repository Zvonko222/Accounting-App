package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.model.SaleCartLine;
import com.example.accounting.databinding.ItemCartLineBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车 Adapter。
 * 每行显示：商品名、单价、数量、小计。
 * +/- 快速加减数量；点商品名或数量 → 打开行编辑弹窗（改单价和精确数量）。
 */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    public interface Listener {
        void onIncreaseClicked(String productId);

        void onDecreaseClicked(String productId);

        /** 点击行的商品名/数量：打开单价+数量编辑弹窗 */
        void onLineClicked(String productId);
    }

    private final List<SaleCartLine> lines = new ArrayList<>();

    /** productId -> product，用于显示计量单位 */
    private final Map<String, Product> productMap = new HashMap<>();

    private final Listener listener;

    public CartAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<SaleCartLine> newLines, List<Product> allProducts) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        productMap.clear();
        if (allProducts != null) {
            for (Product product : allProducts) {
                productMap.put(product.id, product);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCartLineBinding binding = ItemCartLineBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CartViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        SaleCartLine line = lines.get(position);
        Product product = productMap.get(line.productId);
        String unit = product == null ? "" : product.unit;

        holder.binding.cartName.setText(line.productName);
        holder.binding.cartUnitPrice.setText(
                MoneyUtil.toYuan(line.unitPriceCents) + "/" + unit);
        holder.binding.cartQuantity.setText(
                QuantityUtil.toDisplay(line.quantityMilli) + unit);
        holder.binding.cartLineTotal.setText(MoneyUtil.toYuan(line.lineTotalCents()));

        holder.binding.btnIncrease.setOnClickListener(
                v -> listener.onIncreaseClicked(line.productId));
        holder.binding.btnDecrease.setOnClickListener(
                v -> listener.onDecreaseClicked(line.productId));
        // 点商品名或数量：打开单价+数量编辑弹窗
        holder.binding.cartName.setOnClickListener(
                v -> listener.onLineClicked(line.productId));
        holder.binding.cartQuantity.setOnClickListener(
                v -> listener.onLineClicked(line.productId));
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {

        final ItemCartLineBinding binding;

        CartViewHolder(ItemCartLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
