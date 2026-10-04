package com.example.accounting.ui.purchase;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.accounting.R;
import com.example.accounting.data.model.OcrLine;
import com.example.accounting.data.model.PurchaseCartLine;
import com.example.accounting.data.ocr.OcrEngine;
import com.example.accounting.data.repository.SaveCallback;
import com.example.accounting.databinding.ActivityOcrImportBinding;
import com.example.accounting.util.MoneyUtil;
import com.example.accounting.util.QuantityUtil;
import com.example.accounting.util.SaleCalculator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 拍照导入进货：拍照或选相册图片 → ML Kit 本机识别 → OcrLineParser 解析 →
 * 用户勾选确认（可改数量/进价）→ 一个事务保存为进货单（缺商品自动建档）。
 *
 * 这是辅助录入通道：识别失败/不理想时，手动开单始终可用，互不影响。
 */
public class OcrImportActivity extends AppCompatActivity implements OcrLineAdapter.Listener {

    private ActivityOcrImportBinding binding;
    private PurchaseEditViewModel viewModel;

    private OcrLineAdapter lineAdapter;

    /** 相机照片的落地文件（FileProvider 分享给相机应用） */
    private Uri cameraImageUri;

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    recognize(cameraImageUri);
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    recognize(uri);
                }
            });

    public static void start(Context context) {
        context.startActivity(new Intent(context, OcrImportActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOcrImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(PurchaseEditViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        lineAdapter = new OcrLineAdapter(this);
        binding.lineList.setLayoutManager(new LinearLayoutManager(this));
        binding.lineList.setAdapter(lineAdapter);

        binding.btnCamera.setOnClickListener(v -> takePicture());
        binding.btnGallery.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.btnRetake.setOnClickListener(v -> showSourceSection());
        binding.btnImport.setOnClickListener(v -> saveImport());

        // 从其他应用（微信/文件管理器等）分享图片过来：跳过选图直接识别。
        // 这是比拍照更常用的入口——单据图片往往已经在手机里。
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())
                && intent.getType() != null && intent.getType().startsWith("image/")) {
            Uri sharedImage = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (sharedImage != null) {
                recognize(sharedImage);
            }
        }
    }

    /** 通过系统相机拍照：照片直接写到我们指定的文件（不需要相机权限） */
    private void takePicture() {
        File imageFile = new File(getCacheDir(), "ocr/camera_" + System.currentTimeMillis() + ".jpg");
        File parent = imageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", imageFile);
        takePictureLauncher.launch(cameraImageUri);
    }

    private void recognize(Uri imageUri) {
        binding.sourceSection.setVisibility(View.GONE);
        binding.resultSection.setVisibility(View.GONE);
        binding.textRecognizing.setVisibility(View.VISIBLE);

        OcrEngine.recognizeFromUri(this, imageUri, new OcrEngine.Callback() {
            @Override
            public void onLines(List<String> rawLines) {
                List<OcrLine> lines = com.example.accounting.util.OcrLineParser.parseAll(rawLines);
                binding.textRecognizing.setVisibility(View.GONE);
                if (lines.isEmpty()) {
                    Toast.makeText(OcrImportActivity.this,
                            R.string.ocr_nothing, Toast.LENGTH_LONG).show();
                    showSourceSection();
                    return;
                }
                lineAdapter.submit(lines);
                if (rawLines != null && !rawLines.isEmpty()) {
                    binding.textRawLines.setVisibility(View.VISIBLE);
                    String joined = TextUtils.join("\n", rawLines);
                    binding.textRawLines.setText(
                            getString(R.string.ocr_raw_lines) + "\n" + joined);
                }
                binding.resultSection.setVisibility(View.VISIBLE);
                updateImportSummary();
            }

            @Override
            public void onError(String message) {
                binding.textRecognizing.setVisibility(View.GONE);
                Toast.makeText(OcrImportActivity.this,
                        getString(R.string.ocr_failed) + "：" + message,
                        Toast.LENGTH_LONG).show();
                showSourceSection();
            }
        });
    }

    private void showSourceSection() {
        binding.sourceSection.setVisibility(View.VISIBLE);
        binding.resultSection.setVisibility(View.GONE);
        binding.textRecognizing.setVisibility(View.GONE);
    }

    // ---------------- OcrLineAdapter.Listener ----------------

    @Override
    public void onLineClicked(OcrLine line) {
        showFixLineDialog(line);
    }

    @Override
    public void onCheckedChanged() {
        updateImportSummary();
    }

    /** 点确认列表的一行：弹出数量/进价修改框（与开单页同款布局） */
    private void showFixLineDialog(OcrLine line) {
        View dialogView = getLayoutInflater()
                .inflate(R.layout.dialog_edit_cart_line, null, false);
        com.google.android.material.textfield.TextInputLayout priceLayout =
                dialogView.findViewById(R.id.layout_line_price);
        android.widget.EditText priceInput = dialogView.findViewById(R.id.input_line_price);
        android.widget.EditText quantityInput = dialogView.findViewById(R.id.input_line_quantity);

        priceLayout.setHint(getString(R.string.unit_cost_label));
        priceInput.setText(MoneyUtil.toDisplay(line.unitCostCents));
        quantityInput.setText(QuantityUtil.toDisplay(line.quantityMilli));

        new MaterialAlertDialogBuilder(this)
                .setTitle(line.productName)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    Long cost = MoneyUtil.parseYuan(String.valueOf(priceInput.getText()).trim());
                    Long quantity = QuantityUtil.parse(String.valueOf(quantityInput.getText()).trim());
                    if (cost != null && quantity != null) {
                        line.setValues(quantity, cost);
                        lineAdapter.notifyItemChanged(lineAdapter.lines.indexOf(line));
                        updateImportSummary();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateImportSummary() {
        int count = lineAdapter.checkedCount();
        long total = 0;
        for (OcrLine line : lineAdapter.collectChecked()) {
            total += SaleCalculator.lineTotalCents(line.unitCostCents, line.quantityMilli);
        }
        binding.btnImport.setText(getString(R.string.ocr_import_n, count,
                MoneyUtil.toYuan(total)));
        binding.btnImport.setEnabled(count > 0);
    }

    private void saveImport() {
        List<OcrLine> checked = lineAdapter.collectChecked();
        if (checked.isEmpty()) {
            Toast.makeText(this, R.string.ocr_nothing, Toast.LENGTH_SHORT).show();
            return;
        }

        List<PurchaseCartLine> cartLines = new ArrayList<>();
        for (OcrLine line : checked) {
            // productId 置 null：Repository 保存时按品名找商品，找不到自动建档
            cartLines.add(new PurchaseCartLine(null, line.productName,
                    line.unitCostCents, line.quantityMilli));
        }
        String supplier = String.valueOf(binding.inputSupplier.getText()).trim();

        viewModel.saveOcrImport(cartLines, supplier, new SaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(OcrImportActivity.this,
                        getString(R.string.ocr_imported, checked.size()),
                        Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(OcrImportActivity.this,
                        getString(R.string.backup_failed) + "：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
