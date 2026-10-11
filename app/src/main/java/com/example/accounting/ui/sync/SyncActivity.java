package com.example.accounting.ui.sync;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.accounting.R;
import com.example.accounting.databinding.ActivitySyncBinding;
import com.example.accounting.util.InsetsUtil;
import com.example.accounting.util.TimeUtil;
import com.example.accounting.data.sync.SyncEngine;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 云同步页（独立整页，从设置页进入）：
 * 服务器配置 + 立即同步 + 状态行 + 各表待同步明细。
 */
public class SyncActivity extends AppCompatActivity {

    private ActivitySyncBinding binding;
    private SyncViewModel viewModel;

    public static void start(android.content.Context context) {
        context.startActivity(new android.content.Intent(context, SyncActivity.class));
    }

    /** 从相册图片识别店铺标识二维码 */
    private final androidx.activity.result.ActivityResultLauncher<String> qrImageLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            importStoreIdFromImage(uri);
                        }
                    });

    /** 弹窗展示店铺标识二维码（主设备出示，新设备用任意相机/识别工具扫描） */
    private void showStoreQr() {
        String storeId = com.example.accounting.data.sync.SyncEngine
                .getDeviceId(getApplicationContext());
        android.graphics.Bitmap qr = com.example.accounting.util.QrCodeUtil
                .generate(storeId, 512);
        if (qr == null) {
            Toast.makeText(this, R.string.store_id_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.ImageView imageView = new android.widget.ImageView(this);
        imageView.setImageBitmap(qr);
        imageView.setPadding(24, 24, 24, 24);
        new AlertDialog.Builder(this)
                .setTitle(R.string.store_id_qr_title)
                .setView(imageView)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    /** 从相册图片解码店铺标识二维码（ML Kit 条码识别，离线） */
    private void importStoreIdFromImage(android.net.Uri imageUri) {
        com.google.mlkit.vision.barcode.BarcodeScanner scanner =
                com.google.mlkit.vision.barcode.BarcodeScanning.getClient(
                        new com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(
                                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                .build());
        try {
            android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            options.inSampleSize = 1;
            android.graphics.Bitmap image = android.graphics.BitmapFactory.decodeStream(
                    getContentResolver().openInputStream(imageUri), null, options);
            if (image == null) {
                Toast.makeText(this, R.string.store_id_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            com.google.mlkit.vision.common.InputImage inputImage =
                    com.google.mlkit.vision.common.InputImage.fromBitmap(image, 0);
            scanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        scanner.close();
                        image.recycle();
                        if (barcodes.isEmpty()) {
                            Toast.makeText(this, R.string.store_id_qr_not_found,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        String value = barcodes.get(0).getRawValue();
                        if (value == null || value.trim().length() < 8) {
                            Toast.makeText(this, R.string.store_id_invalid,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        com.example.accounting.data.sync.SyncEngine
                                .importDeviceId(getApplicationContext(), value.trim());
                        Toast.makeText(this, R.string.store_id_imported,
                                Toast.LENGTH_LONG).show();
                        viewModel.loadSyncConfig();
                    })
                    .addOnFailureListener(e -> {
                        scanner.close();
                        image.recycle();
                        Toast.makeText(this, R.string.store_id_qr_not_found,
                                Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            Toast.makeText(this, R.string.store_id_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySyncBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        InsetsUtil.applyTopInset(binding.toolbar);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(SyncViewModel.class);

        viewModel.getConfigUrl().observe(this, url -> {
            if (binding.inputSyncUrl.getText() == null
                    || binding.inputSyncUrl.getText().length() == 0) {
                binding.inputSyncUrl.setText(url);
            }
        });
        viewModel.getConfigKey().observe(this, key -> {
            if (binding.inputSyncKey.getText() == null
                    || binding.inputSyncKey.getText().length() == 0) {
                binding.inputSyncKey.setText(key);
            }
        });

        binding.btnSyncSave.setOnClickListener(v -> {
            String url = String.valueOf(binding.inputSyncUrl.getText()).trim();
            String key = String.valueOf(binding.inputSyncKey.getText()).trim();
            viewModel.saveSyncConfig(url.isEmpty() ? null : url, key.isEmpty() ? null : key);
            Toast.makeText(this, R.string.sync_saved, Toast.LENGTH_SHORT).show();
        });

        // 店铺标识二维码：生成展示 / 从图片识别导入
        binding.btnQrShow.setOnClickListener(v -> showStoreQr());
        binding.btnQrImport.setOnClickListener(v -> qrImageLauncher.launch("image/*"));
        viewModel.getPendingDetail().observe(this, detail ->
                binding.textSyncDetail.setText(detail == null ? "" : detail.trim()));

        binding.btnSyncNow.setOnClickListener(v -> {
            Toast.makeText(this, R.string.sync_now, Toast.LENGTH_SHORT).show();
            viewModel.syncNow(new SyncEngine.Callback() {
                @Override
                public void onDone(String message) {
                    Toast.makeText(SyncActivity.this, message, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(SyncActivity.this,
                            getString(R.string.backup_failed) + "：" + message,
                            Toast.LENGTH_LONG).show();
                }
            });
        });

        // 状态行 + 明细
        viewModel.getLastSyncAt().observe(this, this::updateStatusText);
        viewModel.getLastSyncError().observe(this, e -> updateStatusText(null));
        viewModel.getPendingCount().observe(this, c -> updateStatusText(null));
        viewModel.getPendingDetail().observe(this, detail -> {
            binding.textSyncDetail.setText(detail == null ? "" : detail.trim());
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadSyncConfig();
    }

    private void updateStatusText(Long ignored) {
        Long lastAt = viewModel.getLastSyncAt().getValue();
        String error = viewModel.getLastSyncError().getValue();
        Integer pending = viewModel.getPendingCount().getValue();

        StringBuilder text = new StringBuilder();
        if (lastAt == null) {
            text.append(getString(R.string.sync_last_never));
        } else {
            text.append(getString(R.string.sync_last_time, TimeUtil.formatFull(lastAt)));
        }
        if (error != null && !error.isEmpty()) {
            text.append("\n").append(getString(R.string.sync_last_error, error));
        }
        if (pending != null && pending > 0) {
            text.append("\n").append(getString(R.string.sync_status_pending, pending));
        }
        binding.textSyncStatus.setText(text);
    }
}
