/*
 * Created on 2018/06/26.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videoplayer.dao;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author 刘振林
 */
public class AppSharedPreferences {

    private static volatile AppSharedPreferences sAppSP;

    private final SharedPreferences mSP;
    private final SharedPreferences.Editor mEditor;

    private static final String DRAWER_BACKGROUND_PATH = "drawerBackgroundPath";
    private static final String IS_LIGHT_DRAWER_STATUS = "isLightDrawerStatus";
    private static final String IS_LIGHT_DRAWER_LIST_FOREGROUND = "isLightDrawerListForeground";

    @SuppressLint("CommitPrefEdits")
    private AppSharedPreferences(Context context) {
        mSP = context.getSharedPreferences("VideoPlayer.sp", Context.MODE_PRIVATE);
        mEditor = mSP.edit();
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
        mEditor.putString(DRAWER_BACKGROUND_PATH, path).apply();
    }

    public boolean isLightDrawerStatus() {
        return mSP.getBoolean(IS_LIGHT_DRAWER_STATUS, true);
    }

    public void setLightDrawerStatus(boolean light) {
        mEditor.putBoolean(IS_LIGHT_DRAWER_STATUS, light).apply();
    }

    public boolean isLightDrawerListForeground() {
        return mSP.getBoolean(IS_LIGHT_DRAWER_LIST_FOREGROUND, false);
    }

    public void setLightDrawerListForeground(boolean light) {
        mEditor.putBoolean(IS_LIGHT_DRAWER_LIST_FOREGROUND, light).apply();
    }
}
