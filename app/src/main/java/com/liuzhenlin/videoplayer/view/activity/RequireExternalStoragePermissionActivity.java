/*
 * Created on 12/8/18 10:07 PM.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videoplayer.view.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.liuzhenlin.videoplayer.R;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.PermissionRequest;

/**
 * @author 刘振林
 */
public class RequireExternalStoragePermissionActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks, EasyPermissions.RationaleCallbacks {
    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION = 1;

    // Read-only Fields
    public static String cancel;
    public static String confirm;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        if (cancel == null) {
            cancel = getString(R.string.cancel);
            confirm = getString(R.string.confirm);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkStoragePermission();
    }

    @AfterPermissionGranted(REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION)
    public void checkStoragePermission() {
        if (hasStoragePermission()) {
            // Have permission, do the thing!
            onPermissionGranted();
        } else {
            // Ask for one permission
            EasyPermissions.requestPermissions(
                    new PermissionRequest.Builder(this, //@formatter:off
                                    REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE) //@formatter:on
                            .setTheme(R.style.DialogStyle_MinWidth_NoTitle)
                            .setRationale(R.string.rationale_askExternalStorage)
                            .setNegativeButtonText(cancel)
                            .setPositiveButtonText(confirm)
                            .build());
        }
    }

    private boolean hasStoragePermission() {
        return EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void onPermissionGranted() {
        Uri uri = getIntent().getData();
        if (uri == null) {
            startActivity(new Intent(this, MainActivity.class));
        } else {
            startActivity(new Intent(this, VideoActivity.class).setData(uri));
        }

        finish();
    }

    private void onPermissionDenied() {
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // EasyPermissions handles the request result.
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            // Do something after user returned from app settings screen.
            if (hasStoragePermission()) {
                onPermissionGranted();
            } else {
                onPermissionDenied();
            }
        }
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN".
        // This will display a dialog directing them to enable the permission in app settings.
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this)
                    .setThemeResId(R.style.DialogStyle_MinWidth)
                    .setTitle(R.string.permissionRequired)
                    .setRationale(R.string.rationale_askExternalStorageAgain)
                    .setNegativeButton(cancel)
                    .setPositiveButton(confirm)
                    .build().show();
        } else {
            onPermissionDenied();
        }
    }

    @Override
    public void onRationaleAccepted(int requestCode) {
    }

    @Override
    public void onRationaleDenied(int requestCode) {
        onPermissionDenied();
    }
}
