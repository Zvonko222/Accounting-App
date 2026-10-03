package com.example.accounting.ui.inventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.databinding.ItemProductBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品列表 Adapter：一行商品卡片，提供 编辑 / 盘点 / 记录 三个按钮。
 * Fragment 通过 listener 接口拿到点击事件，Adapter 自己不做业务判断。
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    /** 列表按钮的回调，由 InventoryFragment 实现 */
    public interface Listener {
        void onEditProduct(Product product);

        void onAdjustStock(Product product);

        void onShowHistory(Product product);
    }

    private final List<Product> products = new ArrayList<>();
    private final Listener listener;

    public ProductAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Room 的 LiveData 每次发新列表都调用这里 */
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
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemProductBinding binding = ItemProductBinding.inflate(inflater, parent, false);
        return new ProductViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        holder.bind(products.get(position));
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    class ProductViewHolder extends RecyclerView.ViewHolder {

        private final ItemProductBinding binding;

        ProductViewHolder(ItemProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Product product) {
            binding.productName.setText(product.name);
            binding.productStock.setText(
                    QuantityUtil.toDisplay(product.stockQuantityMilli) + product.unit);

            String priceText = "售价 " + MoneyUtil.toYuan(product.salePriceCents);
            if (product.purchasePriceCents > 0) {
                priceText += " · 进价 " + MoneyUtil.toYuan(product.purchasePriceCents);
            }
            binding.productPrice.setText(priceText);

            bindStockState(product);
            bindButtons(product);
        }

        /** 库存状态：负数标"需盘点"，低于预警值标"库存不足" */
        private void bindStockState(Product product) {
            TextView tag = binding.productStockTag;
            if (product.stockQuantityMilli < 0) {
                tag.setVisibility(View.VISIBLE);
                tag.setText(R.string.negative_stock_tag);
                tag.setTextColor(ContextCompat.getColor(tag.getContext(), R.color.money_out));
                binding.productStock.setTextColor(
                        ContextCompat.getColor(tag.getContext(), R.color.money_out));
            } else if (product.lowStockThresholdMilli > 0
                    && product.stockQuantityMilli <= product.lowStockThresholdMilli) {
                tag.setVisibility(View.VISIBLE);
                tag.setText(R.string.low_stock_tag);
                tag.setTextColor(ContextCompat.getColor(tag.getContext(), R.color.warning));
                binding.productStock.setTextColor(
                        ContextCompat.getColor(tag.getContext(), R.color.warning));
            } else {
                tag.setVisibility(View.GONE);
                binding.productStock.setTextColor(
                        ContextCompat.getColor(tag.getContext(), R.color.text_primary));
            }
        }

        private void bindButtons(Product product) {
            binding.btnEdit.setOnClickListener(
                    v -> listener.onEditProduct(product));
            binding.btnAdjust.setOnClickListener(
                    v -> listener.onAdjustStock(product));
            binding.btnHistory.setOnClickListener(
                    v -> listener.onShowHistory(product));
        }
    }
}
