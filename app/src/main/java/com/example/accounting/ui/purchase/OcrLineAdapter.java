package com.example.accounting.ui.purchase;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accounting.R;
import com.example.accounting.data.model.OcrLine;
import com.example.accounting.databinding.ItemOcrLineBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 拍照导入的确认列表 Adapter：一行 = 一条识别结果。
 * 勾选决定是否导入；点行内容弹出修改框（改数量和进价）；
 * 无效行（数量/进价解析失败或进价为 0）灰显且不可勾选。
 */
public class OcrLineAdapter extends RecyclerView.Adapter<OcrLineAdapter.LineViewHolder> {

    public interface Listener {
        /** 点行内容：打开数量/进价修改弹窗 */
        void onLineClicked(OcrLine line);

        /** 勾选状态变化（用于更新"可导入 N 行"提示） */
        void onCheckedChanged();
    }

    final List<OcrLine> lines = new ArrayList<>();
    private final Listener listener;

    public OcrLineAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<OcrLine> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        notifyDataSetChanged();
    }

    /** 当前勾选且有效的行数 */
    public int checkedCount() {
        int count = 0;
        for (OcrLine line : lines) {
            if (line.checked && line.valid) {
                count++;
            }
        }
        return count;
    }

    /** 收集要导入的行 */
    public List<OcrLine> collectChecked() {
        List<OcrLine> result = new ArrayList<>();
        for (OcrLine line : lines) {
            if (line.checked && line.valid) {
                result.add(line);
            }
        }
        return result;
    }

    @NonNull
    @Override
    public LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOcrLineBinding binding = ItemOcrLineBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new LineViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
        OcrLine line = lines.get(position);
        holder.binding.lineCheck.setOnCheckedChangeListener(null);
        holder.binding.lineCheck.setChecked(line.checked);
        holder.binding.lineName.setText(line.productName);
        holder.binding.lineRaw.setText(line.rawText);
        holder.binding.lineQty.setText(
                QuantityUtil.toDisplay(line.quantityMilli));
        holder.binding.lineCost.setText(MoneyUtil.toYuan(line.unitCostCents));

        float alpha = line.valid ? 1f : 0.4f;
        holder.binding.lineName.setAlpha(alpha);
        holder.binding.lineQty.setAlpha(alpha);
        holder.binding.lineCost.setAlpha(alpha);

        holder.binding.lineCheck.setOnCheckedChangeListener((view, isChecked) -> {
            if (!line.valid) {
                // 无效行不可勾选：弹回原状态
                holder.binding.lineCheck.setChecked(false);
                return;
            }
            line.checked = isChecked;
            listener.onCheckedChanged();
        });

        // 点行内容（品名/数量/进价）：弹出修改框；无效行点了没反应
        View.OnClickListener rowClick = v -> {
            if (line.valid) {
                listener.onLineClicked(line);
            }
        };
        holder.binding.lineName.setOnClickListener(rowClick);
        holder.binding.lineQty.setOnClickListener(rowClick);
        holder.binding.lineCost.setOnClickListener(rowClick);
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class LineViewHolder extends RecyclerView.ViewHolder {

        final ItemOcrLineBinding binding;

        LineViewHolder(ItemOcrLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
