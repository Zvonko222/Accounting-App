package com.example.accounting.ui.inventory;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.db.entity.StockMovement;
import com.example.accounting.databinding.ItemStockMovementBinding;
import com.example.accounting.util.QuantityUtil;
import com.example.accounting.util.TimeUtil;

import java.util.List;

/**
 * 库存台账 Adapter：一行 = 一次变动（类型 + 时间 + 带符号数量）。
 * ListAdapter + DiffUtil：按日志 id 识别，只重绑变化的行。
 */
public class StockMovementAdapter
        extends ListAdapter<StockMovement, StockMovementAdapter.MovementViewHolder> {

    /** DiffUtil：按日志 id 识别同一行，内容一致不重绑 */
    private static final DiffUtil.ItemCallback<StockMovement> DIFF =
            new DiffUtil.ItemCallback<StockMovement>() {
                @Override
                public boolean areItemsTheSame(@NonNull StockMovement a, @NonNull StockMovement b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull StockMovement a, @NonNull StockMovement b) {
                    return a.changeQuantityMilli == b.changeQuantityMilli
                            && a.changeType == b.changeType
                            && a.movementTime == b.movementTime;
                }
            };

    public StockMovementAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public MovementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStockMovementBinding binding = ItemStockMovementBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MovementViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MovementViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {

        private final ItemStockMovementBinding binding;

        MovementViewHolder(ItemStockMovementBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(StockMovement movement) {
            binding.movementTitle.setText(typeName(movement.changeType));
            binding.movementTime.setText(TimeUtil.formatFull(movement.movementTime));

            String change = (movement.changeQuantityMilli > 0 ? "+" : "")
                    + QuantityUtil.toDisplay(movement.changeQuantityMilli);
            binding.movementChange.setText(change);
            int color = movement.changeQuantityMilli >= 0 ? R.color.money_in : R.color.money_out;
            binding.movementChange.setTextColor(
                    ContextCompat.getColor(binding.getRoot().getContext(), color));
        }

        private String typeName(int changeType) {
            switch (changeType) {
                case StockMovement.TYPE_SALE:           return "销售";
                case StockMovement.TYPE_PURCHASE:       return "进货";
                case StockMovement.TYPE_ADJUST:         return "盘点修正";
                case StockMovement.TYPE_INITIAL:        return "期初";
                case StockMovement.TYPE_VOID_SALE:      return "作废销售冲回";
                case StockMovement.TYPE_VOID_PURCHASE:  return "作废进货冲回";
                default:                                return "其他";
            }
        }
    }
}
