/*
 * Created on 2018/04/12.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * @author 刘振林
 */
public class BitmapUtils2 {
    private BitmapUtils2() {
    }

    @NonNull
    public static Bitmap getTintedBitmap(@NonNull Bitmap bitmap, @ColorInt int color) {
        if (!bitmap.isMutable()) {
            bitmap = bitmap.copy(bitmap.getConfig(), true);
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmap;
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

    public static Bitmap createScaledBitmap(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return createScaledBitmap(BitmapFactory.decodeFile(path, options), reqWidth, reqHeight, true);
    }

    /**
     * 计算图片的缩放值
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;
        if (width > reqWidth || height > reqHeight) {
            final int widthRatio = (int) ((float) width / reqWidth + 0.5f);
            final int heightRatio = (int) ((float) height / reqHeight + 0.5f);
            inSampleSize = Math.min(widthRatio, heightRatio);
        }
        return inSampleSize;
    }
}
