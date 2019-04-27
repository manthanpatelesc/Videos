/*
 * Created on 2017/9/27.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videoplayer.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.videoplayer.BuildConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author 刘振林
 */
public class FileUtils {
    private static final String TAG = "FileUtils";

    private FileUtils() {
    }

    @NonNull
    public static String getFileSimpleName(@NonNull String name) {
        final int lastIndex = name.lastIndexOf(".");
        return lastIndex == -1 ? name : name.substring(0, lastIndex);
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

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(new FileInputStream(srcFile));
            out = new BufferedOutputStream(new FileOutputStream(desFile));
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
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    //
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    //
                }
            }
        }
    }

    public static class UriResolver {
        private static final String TAG = "FileUtils.UriResolver";

        private UriResolver() {
        }

        /**
         * Android4.4+从Uri获取文件绝对路径
         */
        @Nullable
        public static String getPath(@NonNull Context context, @NonNull Uri uri) {
            // Uri.parse(String)
            if ("android.net.Uri$StringUri".equals(uri.getClass().getName())) {
                return uri.toString();
            }

            // DocumentProvider
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                    DocumentsContract.isDocumentUri(context, uri)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "===== this is a document uri =====");
                }
                // ExternalStorageProvider
                if (isExternalStorageDocument(uri)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "===== this is an external storage document uri =====");
                    }
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + File.separator + split[1];
                    }

                    // DownloadsProvider
                } else if (isDownloadsDocument(uri)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "===== this is a downloads document uri =====");
                    }
                    final String id = DocumentsContract.getDocumentId(uri);
                    try {
                        Uri contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                        return getDataColumn(context, contentUri, null, null);
                    } catch (NumberFormatException e) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "NumberFormatException ——> id= " + id);
                        }
                        if (id.startsWith("raw:" +
                                Environment.getExternalStorageDirectory().getPath())) {
                            try {
                                return id.substring(id.indexOf(":") + 1);
                            } catch (IndexOutOfBoundsException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }

                    // MediaProvider
                } else if (isMediaDocument(uri)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "===== this is a media document uri =====");
                    }
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    Uri contentUri = null;
                    switch (type) {
                        case "image":
                            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "video":
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "audio":
                            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                            break;
                    }
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};
                    return getDataColumn(context, contentUri, selection, selectionArgs);
                }

                // MediaStore (and general)
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "===== the scheme of this uri is content =====");
                }
                return getDataColumn(context, uri, null, null);

                // File
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "===== the scheme of this uri is file =====");
                }
                return uri.getPath();
            }

            return null;
        }

        /**
         * Get the value of the data column for this Uri. This is useful for
         * MediaStore Uris, and other file-based ContentProviders.
         *
         * @param context       The context.
         * @param uri           The Uri to query.
         * @param selection     (Optional) Filter used in the query.
         * @param selectionArgs (Optional) Selection arguments used in the query.
         * @return The value of the '_data' column, which is typically a file path.
         */
        private static String getDataColumn(Context context, Uri uri, String selection,
                                            String[] selectionArgs) {
            final String column = "_data";
            final String[] projection = {column};
            Cursor cursor = context.getContentResolver().query(
                    uri, projection, selection, selectionArgs, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(column));
                    }
                } finally {
                    cursor.close();
                }
            }
            return null;
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is ExternalStorageProvider.
         */
        public static boolean isExternalStorageDocument(@NonNull Uri uri) {
            return "com.android.externalstorage.documents".equals(uri.getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is DownloadsProvider.
         */
        public static boolean isDownloadsDocument(@NonNull Uri uri) {
            return "com.android.providers.downloads.documents".equals(uri.getAuthority());
        }

        /**
         * @param uri The Uri to check.
         * @return Whether the Uri authority is MediaProvider.
         */
        public static boolean isMediaDocument(@NonNull Uri uri) {
            return "com.android.providers.media.documents".equals(uri.getAuthority());
        }
    }
}
