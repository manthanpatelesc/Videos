/*
 * Created on 3/23/19 6:28 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * @author 刘振林
 */
public class BitmapUtils {
    private BitmapUtils() {
    }

    @NonNull
    public static Bitmap createScaledBitmap(@NonNull Bitmap src, int reqWidth, int reqHeight, boolean recycleInput) {
        if (reqWidth == 0 || reqHeight == 0) {
            return src;
        }

        // 记录src的宽高
        final int width = src.getWidth();
        final int height = src.getHeight();

        // 计算缩放比例
        final float widthScale = (float) reqWidth / width;
        final float heightScale = (float) reqHeight / height;

        // 创建一个matrix容器
        Matrix matrix = new Matrix();
        // 缩放
        matrix.postScale(widthScale, heightScale);
        // 创建缩放后的图片
        Bitmap out = Bitmap.createBitmap(src, 0, 0, width, height, matrix, true);

        if (recycleInput && out != src) {
            src.recycle();
        }
        return out;
    }

    @NonNull
    public static Bitmap createRoundCornerBitmap(@NonNull Bitmap src, /* px */ float connerRadius, boolean recycleInput) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 设置矩形大小
        Rect rect = new Rect(0, 0, out.getWidth(), out.getHeight());
        RectF rectF = new RectF(rect);

        canvas.drawRoundRect(rectF, connerRadius, connerRadius, paint); // 画圆角
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN)); // 取两层绘制，显示上层
        canvas.drawBitmap(src, rect, rect, paint);

        if (recycleInput && out != src) {
            src.recycle();
        }
        return out;
    }
}
