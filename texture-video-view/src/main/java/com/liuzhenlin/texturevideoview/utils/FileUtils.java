/*
 * Created on 5/16/19 7:19 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.liuzhenlin.texturevideoview.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author 刘振林
 */
public class FileUtils {
    private FileUtils() {
    }

    @Nullable
    public static File saveBitmapToDisk(@NonNull Context context,
                                        @NonNull Bitmap bitmap, @NonNull Bitmap.CompressFormat format,
                                        @IntRange(from = 0, to = 100) int quality,
                                        @NonNull String directory, @NonNull String fileName) {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File dirFile = new File(directory);
            if (!dirFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dirFile.mkdirs();
            }

            File file = new File(dirFile, fileName);

            boolean successful = false;
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                if (bitmap.compress(format, quality, out)) {
                    out.flush();
                    successful = true;

                    final String path = file.getAbsolutePath();
                    final String mimeType;
                    switch (format) {
                        case JPEG:
                            mimeType = "image/jpeg";
                            break;
                        case PNG:
                            mimeType = "image/png";
                            break;
                        case WEBP:
                            mimeType = "image/webp";
                            break;
                        default:
                            mimeType = null;
                    }
                    ContentValues values = new ContentValues(5);
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
                    values.put(MediaStore.Images.Media.DATA, path);
                    values.put(MediaStore.Images.Media.SIZE, file.length());
                    values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                    values.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
                    context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    // 刷新相册
                    MediaScannerConnection.scanFile(context,
                            new String[]{path}, new String[]{mimeType}, null);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (successful) {
                return file;
            } else {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        return null;
    }

    @Nullable
    public static String getMimeType(@NonNull String path, @NonNull String defMineType) {
        final int index = path.lastIndexOf(".");
        if (index != -1) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substring(index + 1));
        }
        return defMineType;
    }

    public static void shareFile(@NonNull Context context, @NonNull String authority,
                                 @NonNull File file, @NonNull String defMimeType /* default file MIME type */) {
        Intent it = new Intent().setAction(Intent.ACTION_SEND);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            uri = FileProvider.getUriForFile(context, authority, file);
        } else {
            uri = Uri.fromFile(file);
        }
        it.putExtra(Intent.EXTRA_STREAM, uri);
        it.setType(getMimeType(file.getPath(), defMimeType));
        context.startActivity(Intent.createChooser(it, context.getString(R.string.share)));
    }
}
