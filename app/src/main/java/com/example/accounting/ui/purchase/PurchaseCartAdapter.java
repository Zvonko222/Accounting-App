package com.example.accounting.ui.purchase;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.data.db.entity.Product;
import com.example.accounting.data.model.PurchaseCartLine;
import com.example.accounting.databinding.ItemPurchaseCartLineBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 进货待入库清单 Adapter。
 * 每行显示：商品名、数量、本单进价、小计。
 * +/- 快速加减数量；点商品名/数量/进价 → 打开行编辑弹窗（改进价和精确数量）。
 */
public class PurchaseCartAdapter extends RecyclerView.Adapter<PurchaseCartAdapter.PurchaseCartViewHolder> {

    public interface Listener {
        void onIncreaseClicked(String productId);

        void onDecreaseClicked(String productId);

        /** 点击行的商品名/数量/进价：打开进价+数量编辑弹窗 */
        void onLineClicked(String productId);
    }

    private final List<PurchaseCartLine> lines = new ArrayList<>();
    private final Map<String, Product> productMap = new HashMap<>();
    private final Listener listener;

    public PurchaseCartAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<PurchaseCartLine> newLines, List<Product> allProducts) {
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
    public PurchaseCartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPurchaseCartLineBinding binding = ItemPurchaseCartLineBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PurchaseCartViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PurchaseCartViewHolder holder, int position) {
        PurchaseCartLine line = lines.get(position);
        Product product = productMap.get(line.productId);
        String unit = product == null ? "" : product.unit;

        holder.binding.cartName.setText(line.productName);
        holder.binding.cartQuantity.setText(
                QuantityUtil.toDisplay(line.quantityMilli) + unit);
        holder.binding.cartLineTotal.setText(MoneyUtil.toYuan(line.lineTotalCents()));
        holder.binding.cartCost.setText("进价 " + MoneyUtil.toDisplay(line.unitCostCents));

        holder.binding.btnIncrease.setOnClickListener(
                v -> listener.onIncreaseClicked(line.productId));
        holder.binding.btnDecrease.setOnClickListener(
                v -> listener.onDecreaseClicked(line.productId));
        // 点商品名/数量/进价：打开进价+数量编辑弹窗
        holder.binding.cartName.setOnClickListener(
                v -> listener.onLineClicked(line.productId));
        holder.binding.cartQuantity.setOnClickListener(
                v -> listener.onLineClicked(line.productId));
        holder.binding.cartCost.setOnClickListener(
                v -> listener.onLineClicked(line.productId));
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class PurchaseCartViewHolder extends RecyclerView.ViewHolder {

        final ItemPurchaseCartLineBinding binding;

        PurchaseCartViewHolder(ItemPurchaseCartLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
