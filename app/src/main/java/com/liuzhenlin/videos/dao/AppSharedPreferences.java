/*
 * Created on 2018/06/26.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.dao;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author 刘振林
 */
public final class AppSharedPreferences {

    private static volatile AppSharedPreferences sAppSP;

    private final SharedPreferences mSP;

    private static final String DRAWER_BACKGROUND_PATH = "drawerBackgroundPath";
    private static final String IS_LIGHT_DRAWER_STATUS = "isLightDrawerStatus";
    private static final String IS_LIGHT_DRAWER_LIST_FOREGROUND = "isLightDrawerListForeground";

    private AppSharedPreferences(Context context) {
        mSP = context.getSharedPreferences("Videos.sp", Context.MODE_PRIVATE);
    }

    public static AppSharedPreferences getInstance(@NonNull Context context) {
        if (sAppSP == null) {
            synchronized (AppSharedPreferences.class) {
                if (sAppSP == null) {
                    sAppSP = new AppSharedPreferences(context.getApplicationContext());
                }
            }
        }
        return sAppSP;
    }

    @Nullable
    public String getDrawerBackgroundPath() {
        return mSP.getString(DRAWER_BACKGROUND_PATH, null);
    }

    public void setDrawerBackgroundPath(@Nullable String path) {
        mSP.edit().putString(DRAWER_BACKGROUND_PATH, path).apply();
    }

    public boolean isLightDrawerStatus() {
        return mSP.getBoolean(IS_LIGHT_DRAWER_STATUS, true);
    }

    public void setLightDrawerStatus(boolean light) {
        mSP.edit().putBoolean(IS_LIGHT_DRAWER_STATUS, light).apply();
    }

    public boolean isLightDrawerListForeground() {
        return mSP.getBoolean(IS_LIGHT_DRAWER_LIST_FOREGROUND, false);
    }

    public void setLightDrawerListForeground(boolean light) {
        mSP.edit().putBoolean(IS_LIGHT_DRAWER_LIST_FOREGROUND, light).apply();
    }
}
