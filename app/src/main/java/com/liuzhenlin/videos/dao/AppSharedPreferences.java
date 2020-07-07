/*
 * Created on 2018/06/26.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.dao;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.texturevideoview.misc.Singleton;

/**
 * @author 刘振林
 */
public final class AppSharedPreferences {

    private final SharedPreferences mSP;

    private static final String DRAWER_BACKGROUND_PATH = "drawerBackgroundPath";
    private static final String IS_LIGHT_DRAWER_STATUS = "isLightDrawerStatus";
    private static final String IS_LIGHT_DRAWER_LIST_FOREGROUND = "isLightDrawerListForeground";

    private static final Singleton<Context, AppSharedPreferences> sAppSharedPreferencesSingleton =
            new Singleton<Context, AppSharedPreferences>() {
                @SuppressLint("SyntheticAccessor")
                @NonNull
                @Override
                protected AppSharedPreferences onCreate(Context... ctxs) {
                    return new AppSharedPreferences(ctxs[0]);
                }
            };

    public static AppSharedPreferences getSingleton(@NonNull Context context) {
        return sAppSharedPreferencesSingleton.get(context);
    }

    private AppSharedPreferences(Context context) {
        context = context.getApplicationContext();
        mSP = context.getSharedPreferences("Videos.sp", Context.MODE_PRIVATE);
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
