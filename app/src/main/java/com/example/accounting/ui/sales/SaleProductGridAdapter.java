package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.databinding.ItemSaleProductBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 开单页的商品网格 Adapter（配合 GridLayoutManager 使用）。
 * 点一下整个卡片 = 加购，没有别的隐藏交互。
 */
public class SaleProductGridAdapter
        extends RecyclerView.Adapter<SaleProductGridAdapter.ProductViewHolder> {

    /** 点击回调，SaleEditActivity 实现 */
    public interface Listener {
        void onProductClicked(Product product);
    }

    private final List<Product> products = new ArrayList<>();
    private final Listener listener;

    public SaleProductGridAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Product> newProducts) {
        products.clear();
        if (newProducts != null) {
            products.addAll(newProducts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSaleProductBinding binding = ItemSaleProductBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ProductViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.binding.productName.setText(product.name);
        holder.binding.productPrice.setText(
                MoneyUtil.toYuan(product.salePriceCents) + "/" + product.unit);
        // 当前库存：负库存红字"需盘点"，提醒先盘点再卖
        holder.binding.productStock.setText(
                QuantityUtil.toDisplay(product.stockQuantityMilli) + product.unit);
        holder.binding.productStock.setTextColor(ContextCompat.getColor(
                holder.binding.getRoot().getContext(),
                product.stockQuantityMilli < 0 ? R.color.money_out : R.color.text_secondary));
        holder.binding.getRoot().setOnClickListener(v -> listener.onProductClicked(product));
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {

        final ItemSaleProductBinding binding;

        ProductViewHolder(ItemSaleProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
