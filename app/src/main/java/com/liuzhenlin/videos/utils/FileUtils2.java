/*
 * Created on 2017/9/27.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author 刘振林
 */
public class FileUtils2 {
    private static final String TAG = "FileUtils2";

    private FileUtils2() {
    }

    @NonNull
    public static String formatFileSize(double size) {
        // 如果字节数少于1024，则直接以B为单位，否则先除于1024
        if (size < 1024) {
            return (double) Math.round(size * 100d) / 100d + "B";
        } else {
            size = size / 1024d;
        }
        // 如果原字节数除于1024之后，少于1024，则可以直接以KB作为单位
        if (size < 1024) {
            return (double) Math.round(size * 100d) / 100d + "KB";
            // #.00 表示两位小数 #.0000四位小数 以此类推…
        } else {
            size = size / 1024d;
        }
        if (size < 1024) {
            return (double) Math.round(size * 100d) / 100d + "MB";
            // %.2f %.表示 小数点前任意位数 2 表示两位小数 格式后的结果为f 表示浮点型。
        } else {
            // 否则要以GB为单位
            size = size / 1024d;
            return (double) Math.round(size * 100d) / 100d + "GB";
        }
    }

    /** 判断SD卡是否已挂载 */
    public static boolean isExternalStorageMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /** 判断SD卡上是否有足够的空间 */
    public static boolean hasEnoughStorageOnDisk(long size) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            final String storage = Environment.getExternalStorageDirectory().getPath();
            final StatFs fs = new StatFs(storage);
            final long available = fs.getAvailableBlocksLong() * fs.getBlockSizeLong();
            return available >= size;
        }
        return true;
    }

    public static boolean copyFile(@NonNull File srcFile, @NonNull File desFile) {
        if (isExternalStorageMounted()) {
            if (!hasEnoughStorageOnDisk(srcFile.length())) {
                Log.e(TAG, "内存卡空间不足",
                        new IllegalStateException("sdcard does not have enough storage"));
                return false;
            }
        } else {
            Log.e(TAG, "请先插入内存卡", new IllegalStateException("not found sdcard"));
            return false;
        }

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(srcFile));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(desFile))) {
            final byte[] bytes = new byte[8 * 1024];
            int i;
            while ((i = in.read(bytes)) != -1) {
                out.write(bytes, 0, i);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
