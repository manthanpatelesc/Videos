/*
 * Created on 4/16/19 10:11 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview.utils;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;

/**
 * @author 刘振林
 */
public class Utils {
    private Utils() {
    }

    @NonNull
    public static MotionEvent obtainCancelEvent() {
        final long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
    }

    /**
     * Walk up the hierarchy for the given `view` to determine if it is inside a scrolling container.
     */
    public static boolean isInScrollingContainer(@NonNull View view) {
        ViewParent p = view.getParent();
        while (p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }
}
