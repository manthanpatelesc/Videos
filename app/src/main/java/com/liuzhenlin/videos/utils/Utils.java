/*
 * Created on 2020-4-21 6:33:31 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.liuzhenlin.videos.App;

import java.io.File;

/**
 * @author 刘振林
 */
public class Utils {
    private Utils() {
    }

    /**
     * Convenience function for creating an {@link Intent#ACTION_INSTALL_PACKAGE} Intent used for
     * launching the system application installer to install the given <strong>apk</strong>.
     */
    @NonNull
    public static Intent createPackageInstaller(@NonNull Context context, @NonNull File apk) {
        //noinspection deprecation
        Intent it = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Android 7.0 共享文件需要通过 FileProvider 添加临时权限，否则系统会抛出 FileUriExposedException.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(
                    context, App.getInstance(context).getAuthority(), apk);
            it.setData(contentUri);
        } else {
            it.setData(Uri.fromFile(apk));
        }
        return it;
    }

    /**
     * Copies the given plain <strong>text</strong> onto system clipboard.
     *
     * @param context The {@link Context} to get the {@link Context#CLIPBOARD_SERVICE} Service
     * @param label   User-visible label for the copied text
     * @param text    The text to copy from
     */
    public static void copyPlainTextToClipboard(@NonNull Context context,
                                                @Nullable String label, @Nullable String text) {
        ClipboardManager cm = (ClipboardManager)
                context.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
        // 创建纯文本型ClipData
        ClipData cd = ClipData.newPlainText(label, text);
        // 将ClipData内容放到系统剪贴板里
        cm.setPrimaryClip(cd);
    }
}
