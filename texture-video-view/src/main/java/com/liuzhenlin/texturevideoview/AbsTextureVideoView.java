/*
 * Created on 5/6/19 2:55 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.exoplayer2.util.Util;
import com.liuzhenlin.texturevideoview.utils.Utils;

/**
 * @author 刘振林
 */
/* package-private */ abstract class AbsTextureVideoView extends DrawerLayout {

    protected final Context mContext;
    protected final Resources mResources;

    /* package-private */ final String mAppName;
    /**
     * A user agent string based on the application name resolved from this view's context object
     * and the `exoplayer-core` library version.
     */
    /* package-private */ final String mUserAgent;

    /* package-private */
    AbsTextureVideoView(@NonNull Context context) {
        this(context, null);
    }

    /* package-private */
    AbsTextureVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /* package-private */
    AbsTextureVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mResources = getResources();

        mAppName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        mUserAgent = Util.getUserAgent(context, mAppName);
    }

    /**
     * Sets whether to show the loading indicator ring in the center of this view, normally set while
     * the player is loading the video content or paused to buffer more data through progressive
     * HTTP download.
     */
    public abstract void showLoadingView(boolean show);

    /**
     * Calling this method will cause an invocation to the video seek bar's onStopTrackingTouch()
     * if the seek bar is being dragged, so as to hide the widgets showing in that case.
     */
    public abstract void cancelDraggingVideoSeekBar();

    /**
     * @return whether or not this view is in the foreground
     */
    /* package-private */
    boolean isInForeground() {
        return getWindowVisibility() == VISIBLE;
    }

    /**
     * @return the {@link Surface} onto which video will be rendered.
     */
    /* package-private */
    abstract @Nullable Surface getSurface();

    /* package-private */
    abstract void onVideoUriChanged();

    /* package-private */
    abstract void onVideoStarted();

    /* package-private */
    abstract void onVideoStopped();

    /* package-private */
    abstract void onVideoSizeChanged(int width, int height);

    /** @return true if we can skip the video played to the previous one */
    public abstract boolean canSkipToPrevious();

    /** @return true if we can skip the video played to the next one */
    public abstract boolean canSkipToNext();

    /* package-private */
    abstract void onAudioAllowedToPlayInBackgroundChanged(boolean allowed);

    /* package-private */
    abstract void onSingleVideoLoopPlaybackModeChanged(boolean looping);

    /* package-private */
    abstract boolean willTurnOffWhenThisEpisodeEnds();

    /* package-private */
    abstract void onVideoTurnedOffWhenTheEpisodeEnds();

    /* package-private */
    abstract void onPlaybackSpeedChanged(float speed);

    @Override
    public int getDrawerLockMode(@NonNull View drawerView) {
        LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        checkDrawerView(drawerView, Utils.getAbsoluteHorizontalGravity(this, lp.gravity));
        return getDrawerLockModeInternal(drawerView);
    }

    /* package-private */
    final int getDrawerLockModeInternal(@NonNull View drawerView) {
        return getDrawerLockMode(
                ((LayoutParams) drawerView.getLayoutParams()).gravity
                        & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK);
    }

    @Override
    public void setDrawerLockMode(int lockMode, @NonNull View drawerView) {
        LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        checkDrawerView(drawerView, Utils.getAbsoluteHorizontalGravity(this, lp.gravity));
        setDrawerLockModeInternal(lockMode, drawerView);
    }

    /* package-private */
    final void setDrawerLockModeInternal(int lockMode, @NonNull View drawerView) {
        setDrawerLockMode(lockMode,
                ((LayoutParams) drawerView.getLayoutParams()).gravity
                        & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK);
    }

    @SuppressLint("RtlHardcoded")
    private void checkDrawerView(View drawerView, int absHG) {
        if ((absHG & Gravity.LEFT) != Gravity.LEFT && (absHG & Gravity.RIGHT) != Gravity.RIGHT) {
            throw new IllegalArgumentException(
                    "View " + drawerView + " is not a drawer with appropriate layout_gravity");
        }
    }
}
