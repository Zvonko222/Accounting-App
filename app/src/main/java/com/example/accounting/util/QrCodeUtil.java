package com.example.accounting.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * 二维码生成（纯 Java，zxing core）：把店铺标识编码成二维码位图。
 * 识别走 ML Kit 条码扫描（从相册图片离线解码），不引相机。
 */
public final class QrCodeUtil {

    private QrCodeUtil() {
    }

    /** @return 黑白二维码位图；文本过长或编码失败返回 null */
    public static Bitmap generate(String text, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }
}
