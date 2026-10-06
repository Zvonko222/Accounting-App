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
 * 行之间插入日期小标题（同一天只显示一次），翻账像翻一本按天记账的账本。
 *
 * 实现：submitList 时把流水行展开为 [日期头, 行, 行, 日期头, 行...]，
 * 日期头用伪 LedgerItem 表示（id = "header_" + 日期，type = TYPE_HEADER），
 * DiffUtil 统一按 id 识别，两种行型用 getItemViewType 区分。
 */
public class LedgerAdapter extends ListAdapter<LedgerItem, RecyclerView.ViewHolder> {

    /** 日期标题行的伪类型 */
    public static final int TYPE_HEADER = -1;

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

    /** 兼容原调用：提交流水行列表，内部展开日期标题 */
    @Override
    public void submitList(List<LedgerItem> newItems) {
        super.submitList(buildDisplayRows(newItems));
    }

    /** 把流水行展开为 [日期头, 行, ...]（行已按时间倒序，日期变化即新的一天） */
    private List<LedgerItem> buildDisplayRows(List<LedgerItem> items) {
        List<LedgerItem> rows = new ArrayList<>();
        if (items == null) {
            return rows;
        }
        String lastDay = "";
        for (LedgerItem item : items) {
            String day = TimeUtil.formatFull(item.time).substring(0, 10);
            if (!day.equals(lastDay)) {
                lastDay = day;
                rows.add(new LedgerItem(TYPE_HEADER, 0, day, "", 0, false,
                        "header_" + day));
            }
            rows.add(item);
        }
        return rows;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView header = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ledger_header, parent, false);
            return new HeaderViewHolder(header);
        }
        ItemLedgerBinding binding = ItemLedgerBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new LedgerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        LedgerItem item = getItem(position);
        if (item.type == TYPE_HEADER) {
            ((HeaderViewHolder) holder).bind(item.title);
            return;
        }
        ((LedgerViewHolder) holder).bind(item);
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {

        HeaderViewHolder(TextView textView) {
            super(textView);
        }

        void bind(String day) {
            ((TextView) itemView).setText(day);
        }
    }

    class LedgerViewHolder extends RecyclerView.ViewHolder {

        final ItemLedgerBinding binding;

        LedgerViewHolder(ItemLedgerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LedgerItem item) {
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
            binding.ledgerBadge.setText(badge);
            binding.ledgerBadge.setTextColor(
                    ContextCompat.getColor(binding.getRoot().getContext(), badgeColor));
            binding.ledgerTime.setText(TimeUtil.formatFull(item.time));
            binding.ledgerTitle.setText(item.title);
            binding.ledgerSubtitle.setText(
                    item.subtitle == null || item.subtitle.isEmpty() ? " " : item.subtitle);
            binding.ledgerAmount.setText(
                    sign + MoneyUtil.toYuan(Math.abs(item.amountCents)));
            binding.ledgerAmount.setTextColor(
                    ContextCompat.getColor(binding.getRoot().getContext(), amountColor));

            // 状态标签：退货（红）优先，其次待交付（橙）
            TextView statusTag = binding.ledgerStatusTag;
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
            binding.ledgerVoidedTag.setVisibility(
                    showVoided ? View.VISIBLE : View.GONE);
            binding.ledgerAmount.setAlpha(showVoided ? 0.4f : 1f);
            binding.ledgerTitle.setAlpha(showVoided ? 0.5f : 1f);

            binding.getRoot().setOnClickListener(v -> listener.onItemClicked(item));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onItemLongClicked(item);
                return true;
            });
        }
    }
}
