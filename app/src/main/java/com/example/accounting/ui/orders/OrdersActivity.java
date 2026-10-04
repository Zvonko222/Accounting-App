package com.example.accounting.ui.orders;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.accounting.R;
import com.example.accounting.util.InsetsUtil;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 订单页独立入口（桌面小组件"交货"按钮直达）。
 * 内容与底部导航"订单"Tab 完全相同（复用 OrdersFragment）。
 */
public class OrdersActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);
        InsetsUtil.applyTopInset(findViewById(R.id.toolbar));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.orders_container, new OrdersFragment())
                    .commit();
        }
    }
}
