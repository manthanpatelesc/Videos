/*
 * Created on 2017/11/12.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

/**
 * @author 刘振林
 */
public class ScreenUtils {
    private ScreenUtils() {
    }

    /**
     * 获得系统亮度
     */
    @IntRange(from = 0, to = 255)
    public static int getScreenBrightness(@NonNull Context context) {
        int brightness = 0;
        try {
            brightness = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return brightness;
    }

    /**
     * 获取当前Window的亮度
     */
    public static int getWindowBrightness(@NonNull Window window) {
        final float brightness = window.getAttributes().screenBrightness;
        if (brightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            return getScreenBrightness(window.getContext());
        }
        return (int) (brightness * 255f + 0.5f);
    }

    /**
     * 改变当前Window的亮度
     */
    public static void setWindowBrightness(@NonNull Window window, int brightness) {
        WindowManager.LayoutParams lp = window.getAttributes();
        if (brightness == -1) {
            // 自动亮度
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            lp.screenBrightness = (brightness <= 0 ? 1f : brightness) / 255f;
        }
        window.setAttributes(lp);
    }

    /**
     * 判断屏幕是否自动旋转
     */
    public static boolean isRotationEnabled(@NonNull Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 设置屏幕是否自动旋转
     */
    public static void setRotationEnabled(@NonNull Context context, boolean enabled) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION);
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0);
        // 通知改变
        resolver.notifyChange(uri, null);
    }
}
