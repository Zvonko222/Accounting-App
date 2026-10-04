package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.model.LedgerItem;
import com.example.accounting.databinding.ItemLedgerBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水合并列表 Adapter：销售/进货/支出统一成一条时间线。
 * 左侧色点徽标区分类型（销售绿 / 进货橙 / 支出红），点击行看详情。
 */
public class LedgerAdapter extends RecyclerView.Adapter<LedgerAdapter.LedgerViewHolder> {

    public interface Listener {
        void onItemClicked(LedgerItem item);

        /** 长按（支出删除用） */
        void onItemLongClicked(LedgerItem item);
    }

    private final List<LedgerItem> items = new ArrayList<>();
    private final Listener listener;

    public LedgerAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<LedgerItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LedgerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLedgerBinding binding = ItemLedgerBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new LedgerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LedgerViewHolder holder, int position) {
        LedgerItem item = items.get(position);
        int badgeColor;
        int amountColor;
        String badge;
        String sign;
        switch (item.type) {
            case LedgerItem.TYPE_SALE:
                badge = "售";
                badgeColor = R.color.money_in;
                amountColor = R.color.money_in;
                sign = "+";
                break;
            case LedgerItem.TYPE_PURCHASE:
                badge = "进";
                badgeColor = R.color.warning;
                amountColor = R.color.money_out;
                sign = "-";
                break;
            default:
                badge = "支";
                badgeColor = R.color.money_out;
                amountColor = R.color.money_out;
                sign = "-";
                break;
        }
        holder.binding.ledgerBadge.setText(badge);
        holder.binding.ledgerBadge.setTextColor(
                ContextCompat.getColor(holder.binding.getRoot().getContext(), badgeColor));
        holder.binding.ledgerTime.setText(TimeUtil.formatFull(item.time));
        holder.binding.ledgerTitle.setText(item.title);
        holder.binding.ledgerSubtitle.setText(
                item.subtitle == null || item.subtitle.isEmpty() ? " " : item.subtitle);
        holder.binding.ledgerAmount.setText(
                sign + MoneyUtil.toYuan(Math.abs(item.amountCents)));
        holder.binding.ledgerAmount.setTextColor(
                ContextCompat.getColor(holder.binding.getRoot().getContext(), amountColor));

        boolean showVoided = item.deleted;
        holder.binding.ledgerVoidedTag.setVisibility(
                showVoided ? View.VISIBLE : View.GONE);
        holder.binding.ledgerAmount.setAlpha(showVoided ? 0.4f : 1f);
        holder.binding.ledgerTitle.setAlpha(showVoided ? 0.5f : 1f);

        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClicked(item));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            listener.onItemLongClicked(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class LedgerViewHolder extends RecyclerView.ViewHolder {

        final ItemLedgerBinding binding;

        LedgerViewHolder(ItemLedgerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
