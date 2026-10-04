package com.example.accounting.ui.orders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.OrderEvent;
import com.example.accounting.data.db.entity.Sale;
import com.example.accounting.data.db.dao.SaleWithSummary;
import com.example.accounting.data.model.PayMethod;
import com.example.accounting.databinding.ItemOrderBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单列表 Adapter：一行 = 一张销售单。
 * 待交付的行带大按钮"确认交付"；已交付的行显示交付时间（灰显按钮区域）。
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    /** true = 展示待交付列表（带确认按钮）；false = 已交付列表 */
    private final boolean pendingMode;

    public interface Listener {
        void onDeliverClicked(SaleWithSummary order);
        void onOrderEventClicked(SaleWithSummary order);
    }

    private final List<SaleWithSummary> sales = new ArrayList<>();
    private final Listener listener;

    public OrderAdapter(boolean pendingMode, Listener listener) {
        this.pendingMode = pendingMode;
        this.listener = listener;
    }

    public void submitList(List<SaleWithSummary> newSales) {
        List<SaleWithSummary> next = newSales == null
                ? java.util.Collections.emptyList() : new ArrayList<>(newSales);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return sales.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return sales.get(oldItemPosition).sale.id.equals(next.get(newItemPosition).sale.id);
            }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                SaleWithSummary oldItem = sales.get(oldItemPosition);
                SaleWithSummary newItem = next.get(newItemPosition);
                return oldItem.sale.updatedAt == newItem.sale.updatedAt
                        && java.util.Objects.equals(oldItem.itemsSummary, newItem.itemsSummary)
                        && oldItem.sale.deliveryStatus == newItem.sale.deliveryStatus
                        && oldItem.latestEventType == newItem.latestEventType
                        && oldItem.latestEventDifferenceCents == newItem.latestEventDifferenceCents;
            }
        });
        sales.clear();
        sales.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOrderBinding binding = ItemOrderBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new OrderViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        SaleWithSummary order = sales.get(position);
        Sale sale = order.sale;
        holder.binding.orderTime.setText("订单 " + shortOrderId(sale.id) + " · " + TimeUtil.formatFull(sale.saleTime));
        holder.binding.orderItems.setText(order.itemsSummary == null || order.itemsSummary.isEmpty() ? "商品明细：无" : order.itemsSummary);
        holder.binding.orderPayMethod.setText("收款：" + PayMethod.displayName(sale.payMethod));
        if (sale.note != null && !sale.note.trim().isEmpty()) {
            holder.binding.orderNote.setText("备注：" + sale.note);
            holder.binding.orderNote.setVisibility(View.VISIBLE);
        } else {
            holder.binding.orderNote.setVisibility(View.GONE);
        }
        if (sale.deliveryAddress != null && !sale.deliveryAddress.trim().isEmpty()) {
            holder.binding.orderAddress.setText("地址：" + sale.deliveryAddress);
            holder.binding.orderAddress.setVisibility(View.VISIBLE);
        } else {
            holder.binding.orderAddress.setVisibility(View.GONE);
        }
        if (sale.deliveryPhone != null && !sale.deliveryPhone.trim().isEmpty()) {
            holder.binding.orderPhone.setText("电话：" + sale.deliveryPhone);
            holder.binding.orderPhone.setVisibility(View.VISIBLE);
        } else {
            holder.binding.orderPhone.setVisibility(View.GONE);
        }
        if (sale.discountCents > 0) {
            holder.binding.orderDiscount.setText("优惠：" + MoneyUtil.toYuan(sale.discountCents));
            holder.binding.orderDiscount.setVisibility(View.VISIBLE);
        } else {
            holder.binding.orderDiscount.setVisibility(View.GONE);
        }
        long displayedTotal = sale.totalAmountCents + order.latestEventDifferenceCents;
        holder.binding.orderTotal.setText(MoneyUtil.toYuan(displayedTotal));
        if (order.latestEventType == OrderEvent.TYPE_RETURN) {
            holder.binding.getRoot().setCardBackgroundColor(
                    ContextCompat.getColor(holder.binding.getRoot().getContext(), R.color.order_return_bg));
        } else if (order.latestEventType == OrderEvent.TYPE_EXCHANGE) {
            holder.binding.getRoot().setCardBackgroundColor(
                    ContextCompat.getColor(holder.binding.getRoot().getContext(), R.color.order_exchange_bg));
        } else {
            holder.binding.getRoot().setCardBackgroundColor(
                    ContextCompat.getColor(holder.binding.getRoot().getContext(), R.color.bg_card));
        }
        if (sale.customerName != null && !sale.customerName.trim().isEmpty()) {
            holder.binding.orderCustomer.setText(sale.customerName);
            holder.binding.orderCustomer.setVisibility(View.VISIBLE);
        } else {
            holder.binding.orderCustomer.setVisibility(View.GONE);
        }

        holder.binding.btnOrderEvent.setVisibility(View.VISIBLE);
        holder.binding.btnOrderEvent.setOnClickListener(v -> listener.onOrderEventClicked(order));
        if (pendingMode) {
            holder.binding.btnDeliver.setVisibility(View.VISIBLE);
            holder.binding.btnDeliver.setOnClickListener(v -> listener.onDeliverClicked(order));
            holder.binding.orderDeliveredTime.setText("");
            holder.binding.orderDeliveredTime.setVisibility(View.GONE);
        } else {
            holder.binding.btnDeliver.setVisibility(View.GONE);
            holder.binding.orderDeliveredTime.setVisibility(View.VISIBLE);
            holder.binding.orderDeliveredTime.setText(sale.deliveredAt == null ? ""
                    : "交付于 " + TimeUtil.formatFull(sale.deliveredAt));
            holder.binding.orderDeliveredTime.setTextColor(
                    ContextCompat.getColor(holder.binding.getRoot().getContext(),
                            R.color.money_in));
        }
    }

    private static String shortOrderId(String id) {
        if (id == null || id.isEmpty()) return "未知";
        return id.substring(0, Math.min(8, id.length()));
    }

    @Override
    public int getItemCount() {
        return sales.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {

        final ItemOrderBinding binding;

        OrderViewHolder(ItemOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}





