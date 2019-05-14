/*
 * Created on 2017/11/12.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import java.lang.reflect.Field;

/**
 * @author 刘振林
 */
public class UiUtils {
    private UiUtils() {
    }

    @DrawableRes
    public static int getWindowBackground(@NonNull Context context) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.windowBackground
        });
        final int background = a.getResourceId(0, 0);
        a.recycle();
        return background;
    }

    public static void setWindowAlpha(@NonNull Window window,
                                      @FloatRange(from = 0.0, to = 1.0) float alpha) {
        WindowManager.LayoutParams wmlp = window.getAttributes();
        wmlp.alpha = alpha;
        window.setAttributes(wmlp);
    }

    public static void requestViewMargins(@NonNull View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            mlp.setMargins(left, top, right, bottom);
            view.setLayoutParams(mlp);
        }
    }

    public static void setViewMargins(@NonNull View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            mlp.setMargins(left, top, right, bottom);
        }
    }

    public static void showSoftInput(@NonNull View view) {
        InputMethodManager inputManager = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputManager != null)
            inputManager.showSoftInput(view, 0);
    }

    public static void hideSoftInput(@NonNull Window window) {
        InputMethodManager imm = (InputMethodManager)
                window.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = window.getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    public static boolean isSoftInputShown(@NonNull Window window) {
        return isSoftInputShownInternal(window.getDecorView().getRootView());
    }

    public static boolean isSoftInputShown(@NonNull View view) {
        return ViewCompat.isAttachedToWindow(view)
                && isSoftInputShownInternal(view.getRootView());
    }

    private static boolean isSoftInputShownInternal(@NonNull View rootView) {
        Rect r = new Rect();
        rootView.getWindowVisibleDisplayFrame(r);

        final int heightDiff = rootView.getBottom() - r.bottom;
        final float assumedkeyboardHeiht = 50f * rootView.getResources().getDisplayMetrics().density;
        return heightDiff >= assumedkeyboardHeiht;
    }

    @Nullable
    public static TextView getAlertDialogTitle(@NonNull android.app.AlertDialog dialog) {
        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Field mAlert = android.app.AlertDialog.class.getDeclaredField("mAlert");
            mAlert.setAccessible(true);

            Object alertController = mAlert.get(dialog);
            Field mTitleView = alertController.getClass().getDeclaredField("mTitleView");
            mTitleView.setAccessible(true);

            return (TextView) mTitleView.get(alertController);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static TextView getAlertDialogTitle(@NonNull androidx.appcompat.app.AlertDialog dialog) {
        try {
            Field mAlert = androidx.appcompat.app.AlertDialog.class.getDeclaredField("mAlert");
            mAlert.setAccessible(true);

            Object alertController = mAlert.get(dialog);
            Field mTitleView = alertController.getClass().getDeclaredField("mTitleView");
            mTitleView.setAccessible(true);

            return (TextView) mTitleView.get(alertController);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
