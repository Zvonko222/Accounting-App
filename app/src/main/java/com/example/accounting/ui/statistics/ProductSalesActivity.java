package com.example.accounting.ui.statistics;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.accounting.R;
import com.example.accounting.data.db.AppDatabase;
import com.example.accounting.data.db.entity.Product;
import com.example.accounting.databinding.ActivityProductSalesBinding;
import com.example.accounting.util.InsetsUtil;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;
import com.example.accounting.util.TimeUtil;

/**
 * 商品销量页：单个商品近 90 天的销量趋势（柱状图）+ 汇总。
 * 从库存页商品卡的"销量"按钮进入。
 */
public class ProductSalesActivity extends AppCompatActivity {

    private static final String EXTRA_PRODUCT_ID = "extra_product_id";
    private static final long NINETY_DAYS = 90L * 24 * 60 * 60 * 1000;

    private ActivityProductSalesBinding binding;

    public static void start(Context context, String productId) {
        Intent intent = new Intent(context, ProductSalesActivity.class);
        intent.putExtra(EXTRA_PRODUCT_ID, productId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductSalesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        InsetsUtil.applyTopInset(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        if (productId == null) {
            finish();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(this);
        Product product = db.productDao().findById(productId);
        if (product != null) {
            binding.toolbar.setTitle(product.name);
        }

        long from = System.currentTimeMillis() - NINETY_DAYS;

        // 每日趋势：柱状图展示销售额（房间查询自动随数据变化刷新）
        db.statisticsDao().observeProductDailySales(productId, from)
                .observe(this, points -> {
                    binding.trendChart.setMode(TrendChartView.MODE_BAR);
                    binding.trendChart.setData(toTrendPoints(points));
                });

        // 汇总：一次性查询（后台线程）
        new Thread(() -> {
            com.example.accounting.data.model.ProductDailySales sum =
                    db.statisticsDao().sumProductSalesSince(productId, from);
            runOnUiThread(() -> {
                binding.textSummary.setText(getString(R.string.product_sales_summary_fmt,
                        QuantityUtil.toDisplay(sum.quantityMilli),
                        MoneyUtil.toYuan(sum.totalCents)));
            });
        }, "product-sales-summary").start();
    }

    /** 把商品日销数据适配成趋势图的数据点（day/totalCents 字段恰好同名） */
    private java.util.List<com.example.accounting.data.model.DailySales> toTrendPoints(
            java.util.List<com.example.accounting.data.model.ProductDailySales> points) {
        java.util.List<com.example.accounting.data.model.DailySales> result =
                new java.util.ArrayList<>();
        for (com.example.accounting.data.model.ProductDailySales p : points) {
            com.example.accounting.data.model.DailySales d =
                    new com.example.accounting.data.model.DailySales();
            d.day = p.day;
            d.totalCents = p.totalCents;
            d.saleCount = 0;
            result.add(d);
        }
        return result;
    }
}
