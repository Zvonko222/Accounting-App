package com.example.accounting.data.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.exifinterface.media.ExifInterface;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拍照导入的识别引擎：图片解码 → EXIF 摆正 → ML Kit 本机文字识别 → 交给解析器。
 *
 * 为什么用 ML Kit bundled 版（text-recognition-chinese）：
 * 识别模型直接打进 APK，运行时完全离线、不依赖 Google 服务框架——
 * 这与项目 local-first 原则一致；OCR 是唯一无法手写实现的功能，
 * 这是全项目第一个也是唯一一个"黑盒"依赖，业务逻辑（行解析）仍然是
 * 我们自己可测试的纯 Java 代码（OcrLineParser）。
 */
public class OcrEngine {

    private static final String TAG = "OcrEngine";

    /** 识别结果回调（一定在主线程） */
    public interface Callback {
        void onLines(List<String> rawLines);

        void onError(String message);
    }

    /** 缩到这个尺寸以内再识别：足够清晰且省内存/提速 */
    private static final int MAX_DIMENSION = 2048;

    private static final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private OcrEngine() {
    }

    public static void recognizeFromUri(Context context, Uri imageUri, Callback callback) {
        decodeExecutor.execute(() -> {
            Bitmap bitmap = loadScaledUprightBitmap(context, imageUri);
            if (bitmap == null) {
                android.util.Log.w(TAG, "图片解码失败: " + imageUri);
                postError(callback, "无法读取图片");
                return;
            }
            android.util.Log.i(TAG, "解码完成 " + bitmap.getWidth() + "x" + bitmap.getHeight());

            TextRecognizer recognizer = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build());
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener(text -> {
                        recognizer.close();
                        List<String> lines = extractLines(text);
                        android.util.Log.i(TAG, "识别完成，原始行数=" + lines.size());
                        mainHandler.post(() -> callback.onLines(lines));
                    })
                    .addOnFailureListener(e -> {
                        recognizer.close();
                        android.util.Log.w(TAG, "识别失败", e);
                        postError(callback, e.getMessage() == null
                                ? "识别失败" : e.getMessage());
                    });
        });
    }

    /** 从 ML Kit 结果里按行取原文（保持单据的行结构，解析器按行工作） */
    private static List<String> extractLines(Text text) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String raw = line.getText();
                if (raw != null && !raw.trim().isEmpty()) {
                    lines.add(raw);
                }
            }
        }
        return lines;
    }

    /**
     * 解码图片并按 EXIF 摆正方向（手机竖拍的照片像素可能是横的），
     * 超过 MAX_DIMENSION 的等比缩小（inSampleSize 按 2 的幂降采样，省内存）。
     */
    private static Bitmap loadScaledUprightBitmap(Context context, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                android.util.Log.w(TAG, "图片头解码失败: " + bounds.outWidth + "x" + bounds.outHeight);
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight);
            Bitmap decoded;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, options);
            }
            if (decoded == null) {
                return null;
            }

            int rotation = readExifRotation(context, uri);
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(decoded, 0, 0,
                        decoded.getWidth(), decoded.getHeight(), matrix, true);
                if (rotated != decoded) {
                    decoded.recycle();
                }
                return rotated;
            }
            return decoded;
        } catch (Exception e) {
            android.util.Log.w(TAG, "读取图片异常", e);
            return null;
        }
    }

    private static int computeInSampleSize(int width, int height) {
        int sampleSize = 1;
        int larger = Math.max(width, height);
        while (larger / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /** EXIF 方向 → 需要顺时针旋转的角度 */
    private static int readExifRotation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static void postError(Callback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}
