package com.example.accounting.ui.main;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.accounting.R;
import com.example.accounting.databinding.ActivityMainBinding;
import com.example.accounting.ui.home.HomeFragment;
import com.example.accounting.ui.inventory.InventoryFragment;
import com.example.accounting.ui.sales.SalesFragment;
import com.example.accounting.ui.settings.SettingsFragment;
import com.example.accounting.ui.statistics.StatisticsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.HashMap;
import java.util.Map;

/**
 * 主界面：底部导航 + 5 个页面。
 *
 * 用 add + show/hide 切换而不是 replace：已打开的页面（列表滚动位置等）
 * 会被保留，切回来还是原来的样子，对中老年用户更友好。
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    /** 已经创建过的页面，key 是底部导航的菜单项 id */
    private final Map<Integer, Fragment> pages = new HashMap<>();

    /** 当前显示的页面。用字段记录而不是 findFragmentById：
     *  容器里可能有多个 Fragment，findFragmentById 只返回其中一个，不可靠 */
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(
                (NavigationBarView.OnItemSelectedListener) item -> {
                    showPage(item.getItemId());
                    return true;
                });

        if (savedInstanceState != null) {
            // 修复"内容重叠"：屏幕旋转或进程被杀后重建时，FragmentManager 会把
            // 之前的所有页面原样还原（show/hide 状态不保存），它们全部叠在容器里；
            // 而 pages 表是空的，这时再点 Tab 还会 add 出重复的页面。
            // 最简单可靠的处理：清掉全部旧页面，从首页重新开始。
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
            }
            pages.clear();
        }

        showPage(R.id.nav_home);
        // 底部导航自己恢复选中状态时可能停在别的 Tab，强制回首页
        binding.bottomNav.setSelectedItemId(R.id.nav_home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从开单/进货页回来时：刷新桌面"今日经营"小组件；有未同步数据则请求同步
        com.example.accounting.widget.TodayWidgetProvider.refreshAll(this);
        com.example.accounting.data.sync.SyncScheduler.requestSync(this);
    }

    private void showPage(int navItemId) {
        Fragment target = pages.get(navItemId);
        if (target != null && target == currentFragment) {
            return; // 已经在这个页面，什么都不做
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }

        if (target == null) {
            target = createPage(navItemId);
            pages.put(navItemId, target);
            transaction.add(R.id.fragment_container, target);
        } else {
            transaction.show(target);
        }
        transaction.commit();
        currentFragment = target;
    }

    private Fragment createPage(int navItemId) {
        // 不用 switch：新版 AGP 里 R.id 不再是编译期常量，switch 会报
        // "constant expression required"，if-else 链同样直白
        if (navItemId == R.id.nav_sales) {
            return new SalesFragment();
        } else if (navItemId == R.id.nav_inventory) {
            return new InventoryFragment();
        } else if (navItemId == R.id.nav_statistics) {
            return new StatisticsFragment();
        } else if (navItemId == R.id.nav_settings) {
            return new SettingsFragment();
        } else {
            return new HomeFragment();
        }
    }
}
