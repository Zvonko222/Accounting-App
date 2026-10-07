package com.example.accounting.ui.settings;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.accounting.R;
import com.example.accounting.data.repository.backup.BackupManager;
import com.example.accounting.data.sync.SyncEngine;
import com.example.accounting.databinding.FragmentSettingsBinding;
import com.example.accounting.util.AppRestarter;
import com.example.accounting.util.TimeUtil;

import java.util.List;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 设置页：备份与恢复的全部入口。
 *
 * 文件选择用系统 SAF（Storage Access Framework）：备份文件存到用户选的
 * 位置（下载/网盘/电脑），不需要存储权限。
 * 恢复是破坏性操作，弹确认框后才执行，成功后重启进程。
 */
public class SettingsFragment extends Fragment {

    /** 利润表导出：用户所选的统计区间（区间弹窗记录，SAF 回调使用） */
    private long pendingProfitFrom = 0;
    private String pendingProfitLabel = "";

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    /** 选位置导出数据库备份 */
    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"),
                    uri -> {
                        if (uri != null) {
                            viewModel.exportBackup(uri, new BackupToast());
                        }
                    });

    /** 选位置导出销售 CSV */
    private final ActivityResultLauncher<String> exportCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),
                    uri -> {
                        if (uri != null) {
                            viewModel.exportSalesCsv(uri, new BackupToast());
                        }
                    });

    /** 选位置导出利润表 */
    private final ActivityResultLauncher<String> exportProfitLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),
                    uri -> {
                        if (uri != null && pendingProfitFrom > 0) {
                            viewModel.exportProfitReport(uri, pendingProfitFrom,
                                    pendingProfitLabel, new BackupToast());
                        }
                    });

    /** 选位置导出库存表 */
    private final ActivityResultLauncher<String> exportInventoryLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),
                    uri -> {
                        if (uri != null) {
                            viewModel.exportInventory(uri, new BackupToast());
                        }
                    });

    /** 选备份文件恢复 */
    private final ActivityResultLauncher<String[]> restoreLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            confirmRestore(uri);
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

        binding.btnBackupNow.setOnClickListener(v ->
                viewModel.backupNow(new BackupToast()));

        binding.btnExportBackup.setOnClickListener(v -> exportLauncher.launch(
                "accounting_backup_" + TimeUtil.formatForFileName(System.currentTimeMillis())
                        + ".db"));

        // 利润表导出：先选区间，再选保存位置
        binding.btnExportProfit.setOnClickListener(v -> showProfitRangePicker());

        binding.btnDataCheck.setOnClickListener(v -> runDataCheck());

        binding.btnExportInventory.setOnClickListener(v -> exportInventoryLauncher.launch(
                "inventory_" + TimeUtil.formatForFileName(System.currentTimeMillis()) + ".csv"));

        binding.btnExportCsv.setOnClickListener(v -> exportCsvLauncher.launch(
                "sales_" + TimeUtil.formatForFileName(System.currentTimeMillis()) + ".csv"));

        binding.btnRestoreBackup.setOnClickListener(v ->
                restoreLauncher.launch(new String[]{"application/octet-stream", "application/x-sqlite3",
                        "application/vnd.sqlite3", "application/x-sqlite"}));

        binding.btnCategoryManage.setOnClickListener(v ->
                com.example.accounting.ui.common.CategoryManageDialog.show(this,
                        ((com.example.accounting.AccountingApp) requireActivity().getApplication())
                                .getCategoryRepository(),
                        viewModel.getCategories()));

        // 引导把"今日经营"小组件钉到桌面（26+ 系统支持一键确认，旧系统给文字指引）
        binding.btnAddWidget.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                androidx.appcompat.app.AppCompatActivity activity =
                        requireAppCompatActivity();
                android.appwidget.AppWidgetManager manager =
                        android.appwidget.AppWidgetManager.getInstance(activity);
                android.content.ComponentName provider = new android.content.ComponentName(
                        requireContext(), com.example.accounting.widget.TodayWidgetProvider.class);
                manager.requestPinAppWidget(provider, null, null);
            } else {
                Toast.makeText(requireContext(),
                        R.string.widget_add_unsupported, Toast.LENGTH_LONG).show();
            }
        });

        // 云同步入口：整页在 SyncActivity（配置/状态/明细）
        binding.btnOpenSync.setOnClickListener(v ->
                com.example.accounting.ui.sync.SyncActivity.start(requireContext()));

        viewModel.getLastBackupTime().observe(getViewLifecycleOwner(), this::showLastBackup);
        viewModel.getBackupSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary != null) binding.textBackupSummary.setText(summary);
        });
    }

    /** 利润表区间选择：本月/上月/近90天/本年 → 选保存位置 → 导出 */
    private void showProfitRangePicker() {
        String[] labels = {
                getString(R.string.filter_month2),
                getString(R.string.filter_last_month2),
                getString(R.string.filter_90days),
                getString(R.string.filter_year)};
        long now = System.currentTimeMillis();
        long[] froms = {
                com.example.accounting.util.TimeUtil.monthStart(),
                com.example.accounting.util.TimeUtil.lastMonthStart(),
                com.example.accounting.util.TimeUtil.daysAgoStart(89),
                com.example.accounting.util.TimeUtil.yearStart()};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profit_range_title)
                .setItems(labels, (dialog, which) -> {
                    // 记住所选区间：SAF 保存位置回调里用它作为统计起点
                    pendingProfitFrom = froms[which];
                    pendingProfitLabel = labels[which];
                    exportProfitLauncher.launch(
                            "profit_report_" + TimeUtil.formatForFileName(now) + ".csv");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 数据体检：后台对账，弹窗展示报告（只读，不修改任何数据） */
    private void runDataCheck() {
        Toast.makeText(requireContext(), R.string.data_check_running, Toast.LENGTH_SHORT).show();
        ((com.example.accounting.AccountingApp) requireActivity().getApplication())
                .getDatabaseWriteExecutor().execute(() -> {
                    com.example.accounting.data.db.AppDatabase db =
                            ((com.example.accounting.AccountingApp) requireActivity().getApplication())
                                    .getDatabase();
                    long lastBackup = ((com.example.accounting.AccountingApp)
                            requireActivity().getApplication())
                            .getBackupManager().getLastBackupTime() == null ? 0
                            : ((com.example.accounting.AccountingApp) requireActivity().getApplication())
                                    .getBackupManager().getLastBackupTime();
                    // 待交付超期：超过 3 天未交付
                    long cutoff = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000;
                    List<com.example.accounting.data.db.entity.Sale> overdue =
                            db.saleDao().listPendingDeliveryOverdue(cutoff);
                    String report = com.example.accounting.data.repository.report.DataCheck
                            .run(db, lastBackup, overdue);
                    requireActivity().runOnUiThread(() ->
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                                    requireContext())
                                    .setTitle(R.string.data_check)
                                    .setMessage(report)
                                    .setPositiveButton(R.string.close, null)
                                    .show());
                });
    }

    /** 同步状态详情：各表待同步行数 + 手动重传提示 */
    private void showSyncDetailDialog() {
        viewModel.loadPendingDetail(requireContext());
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sync_detail_title)
                .setMessage(viewModel.getPendingDetail().getValue())
                .setPositiveButton(R.string.sync_now, (dialog, which) -> {
                    Toast.makeText(requireContext(), R.string.sync_now, Toast.LENGTH_SHORT).show();
                    viewModel.syncNow(requireContext(), new SyncEngine.Callback() {
                        @Override
                        public void onDone(String message) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private androidx.appcompat.app.AppCompatActivity requireAppCompatActivity() {
        return (androidx.appcompat.app.AppCompatActivity) requireActivity();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.reloadLastBackupTime();
        viewModel.reloadBackupSummary();
    }

    private void showLastBackup(Long lastBackupMillis) {
        if (lastBackupMillis == null) {
            binding.textLastBackup.setText(R.string.backup_never);
        } else {
            binding.textLastBackup.setText(
                    getString(R.string.backup_last, TimeUtil.formatFull(lastBackupMillis)));
        }
    }

    /** 恢复前的强确认：这一步覆盖当前全部数据 */
    private void confirmRestore(Uri uri) {
        String location = getBackupLocation(uri);
        String message = getString(R.string.backup_restore_confirm)
                + "\n\n" + location;
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(message)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.restoreBackup(uri, new BackupManager.BackupCallback() {
                            @Override
                            public void onDone(String message) {
                                Toast.makeText(requireContext(),
                                        R.string.restore_done, Toast.LENGTH_LONG).show();
                                // 恢复后必须重启进程：让 Room 用新文件重建连接
                                AppRestarter.restart(requireContext());
                            }

                            @Override
                            public void onError(String message) {
                                int textRes = "newer_version".equals(message)
                                        ? R.string.restore_incompatible
                                        : R.string.restore_invalid_file;
                                Toast.makeText(requireContext(), textRes,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String getBackupLocation(Uri uri) {
        String name = null;
        Cursor cursor = requireContext().getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(
                            OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        String displayName = name == null || name.trim().isEmpty() ? "未命名文件" : name;
        return "文件：" + displayName + "\n位置：" + uri;
    }

    /** 备份相关操作统一 Toast 反馈 */
    private class BackupToast implements BackupManager.BackupCallback {
        @Override
        public void onDone(String message) {
            Toast.makeText(requireContext(),
                    getString(R.string.backup_done) + "：" + message,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(String message) {
            Toast.makeText(requireContext(),
                    getString(R.string.backup_failed) + "：" + message,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}


