package com.example.accounting.ui.settings;

import android.net.Uri;
import android.os.Bundle;
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
import com.example.accounting.databinding.FragmentSettingsBinding;
import com.example.accounting.util.AppRestarter;
import com.example.accounting.util.TimeUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 设置页：备份与恢复的全部入口。
 *
 * 文件选择用系统 SAF（Storage Access Framework）：备份文件存到用户选的
 * 位置（下载/网盘/电脑），不需要存储权限。
 * 恢复是破坏性操作，弹确认框后才执行，成功后重启进程。
 */
public class SettingsFragment extends Fragment {

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

        binding.btnExportCsv.setOnClickListener(v -> exportCsvLauncher.launch(
                "sales_" + TimeUtil.formatForFileName(System.currentTimeMillis()) + ".csv"));

        binding.btnRestoreBackup.setOnClickListener(v ->
                restoreLauncher.launch(new String[]{"*/*"}));

        binding.btnCategoryManage.setOnClickListener(v -> showCategoryManageDialog());

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

        // ---- 云同步 ----
        viewModel.getConfigUrl().observe(getViewLifecycleOwner(), url -> {
            if (binding.inputSyncUrl.getText() == null
                    || binding.inputSyncUrl.getText().length() == 0) {
                binding.inputSyncUrl.setText(url);
            }
        });
        viewModel.getConfigKey().observe(getViewLifecycleOwner(), key -> {
            if (binding.inputSyncKey.getText() == null
                    || binding.inputSyncKey.getText().length() == 0) {
                binding.inputSyncKey.setText(key);
            }
        });

        binding.btnSyncSave.setOnClickListener(v -> {
            String url = String.valueOf(binding.inputSyncUrl.getText()).trim();
            String key = String.valueOf(binding.inputSyncKey.getText()).trim();
            viewModel.saveSyncConfig(requireContext(),
                    url.isEmpty() ? null : url, key.isEmpty() ? null : key);
            Toast.makeText(requireContext(), R.string.sync_saved, Toast.LENGTH_SHORT).show();
        });

        binding.btnSyncNow.setOnClickListener(v -> {
            Toast.makeText(requireContext(), R.string.sync_now, Toast.LENGTH_SHORT).show();
            viewModel.syncNow(requireContext(), new com.example.accounting.data.sync.SyncEngine.Callback() {
                @Override
                public void onDone(String message) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(requireContext(),
                            getString(R.string.backup_failed) + "：" + message,
                            Toast.LENGTH_LONG).show();
                }
            });
        });

        // 状态行：上次同步时间 + 错误 + 待上行行数
        viewModel.getLastSyncAt().observe(getViewLifecycleOwner(), this::updateSyncStatusText);
        viewModel.getLastSyncError().observe(getViewLifecycleOwner(), s -> updateSyncStatusText(null));
        viewModel.getPendingCount().observe(getViewLifecycleOwner(), c -> updateSyncStatusText(null));

        viewModel.getLastBackupTime().observe(getViewLifecycleOwner(), this::showLastBackup);
    }

    /** 三个 LiveData 任一变化都重建状态行文本 */
    private void updateSyncStatusText(Long ignored) {
        Long lastAt = viewModel.getLastSyncAt().getValue();
        String error = viewModel.getLastSyncError().getValue();
        Integer pending = viewModel.getPendingCount().getValue();

        StringBuilder text = new StringBuilder();
        if (lastAt == null) {
            text.append(getString(R.string.sync_last_never));
        } else {
            text.append(getString(R.string.sync_last_time,
                    TimeUtil.formatFull(lastAt)));
        }
        if (error != null && !error.isEmpty()) {
            text.append("\n").append(getString(R.string.sync_last_error, error));
        }
        if (pending != null && pending > 0) {
            text.append("\n").append(getString(R.string.sync_status_pending, pending));
        }
        binding.textSyncStatus.setText(text);
    }

    private androidx.appcompat.app.AppCompatActivity requireAppCompatActivity() {
        return (androidx.appcompat.app.AppCompatActivity) requireActivity();
    }

    /** 分类管理弹窗：输入新分类 + 列表停用。LiveData 一直在弹窗生命周期内观察 */
    private void showCategoryManageDialog() {
        View contentView = getLayoutInflater()
                .inflate(R.layout.dialog_category_manage, null, false);
        androidx.recyclerview.widget.RecyclerView recycler =
                contentView.findViewById(R.id.category_list);
        android.widget.EditText nameInput =
                contentView.findViewById(R.id.input_category_name);

        CategoryManageAdapter adapter = new CategoryManageAdapter(this::confirmDisableCategory);
        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        viewModel.getCategories().observe(getViewLifecycleOwner(), adapter::submitList);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.category_manage)
                .setView(contentView)
                .setPositiveButton(R.string.close, null)
                .create();

        contentView.findViewById(R.id.btn_add_category).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            if (name.isEmpty()) {
                nameInput.setError(getString(R.string.name_required));
                return;
            }
            viewModel.addCategory(name, new com.example.accounting.data.repository.SaveCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(requireContext(),
                            R.string.category_added, Toast.LENGTH_SHORT).show();
                    nameInput.setText("");
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
        // 关弹窗时取消观察
        dialog.setOnDismissListener(d ->
                viewModel.getCategories().removeObservers(getViewLifecycleOwner()));
    }

    /** 停用分类是软删除、可逆性差（同名单会被唯一约束挡住），加一道确认 */
    private void confirmDisableCategory(com.example.accounting.data.db.entity.Category category) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.disable_confirm) + "\n（" + category.name + "）")
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        viewModel.disableCategory(category.id,
                                new com.example.accounting.data.repository.SaveCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Toast.makeText(requireContext(),
                                                R.string.category_disabled,
                                                Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(requireContext(), message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.reloadLastBackupTime();
        viewModel.loadSyncConfig(requireContext());
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
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.backup_restore_confirm)
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
