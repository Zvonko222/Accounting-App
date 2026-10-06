package com.example.accounting.ui.sales;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
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
public class LedgerAdapter extends ListAdapter<LedgerItem, LedgerAdapter.LedgerViewHolder> {

    /** DiffUtil：按单据 id 识别同一行，金额/状态变化才重绑——列表滚动不再闪烁 */
    private static final DiffUtil.ItemCallback<LedgerItem> DIFF =
            new DiffUtil.ItemCallback<LedgerItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull LedgerItem a, @NonNull LedgerItem b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull LedgerItem a, @NonNull LedgerItem b) {
                    return a.amountCents == b.amountCents
                            && a.deleted == b.deleted
                            && a.latestEventType == b.latestEventType
                            && a.deliveryStatus == b.deliveryStatus
                            && a.title.equals(b.title)
                            && a.subtitle.equals(b.subtitle);
                }
            };

    public interface Listener {
        void onItemClicked(LedgerItem item);

        /** 长按（支出删除用） */
        void onItemLongClicked(LedgerItem item);
    }

    private final Listener listener;

    public LedgerAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void submitList(List<LedgerItem> newItems) {
        super.submitList(newItems == null ? new ArrayList<>() : newItems);
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
        LedgerItem item = getItem(position);
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

        // 状态标签：退货（红）优先，其次待交付（橙）
        TextView statusTag = holder.binding.ledgerStatusTag;
        if (item.type == LedgerItem.TYPE_SALE
                && item.latestEventType == com.example.accounting.data.db.entity.OrderEvent.TYPE_RETURN) {
            statusTag.setText(R.string.ledger_returned);
            statusTag.setTextColor(ContextCompat.getColor(
                    statusTag.getContext(), R.color.money_out));
            statusTag.setVisibility(View.VISIBLE);
        } else if (item.type == LedgerItem.TYPE_SALE && item.deliveryStatus == 1) {
            statusTag.setText(R.string.ledger_pending_delivery);
            statusTag.setTextColor(ContextCompat.getColor(
                    statusTag.getContext(), R.color.warning));
            statusTag.setVisibility(View.VISIBLE);
        } else {
            statusTag.setVisibility(View.GONE);
        }

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

    static class LedgerViewHolder extends RecyclerView.ViewHolder {

        final ItemLedgerBinding binding;

        LedgerViewHolder(ItemLedgerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
