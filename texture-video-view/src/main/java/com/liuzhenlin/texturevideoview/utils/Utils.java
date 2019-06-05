/*
 * Created on 4/16/19 10:11 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview.utils;

import android.os.Build;
import android.os.SystemClock;
import android.transition.Transition;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;

import com.liuzhenlin.texturevideoview.VideoPlayerControl;

/**
 * @author 刘振林
 */
public class Utils {
    private Utils() {
    }

    /**
     * Creates a new MotionEvent with {@link MotionEvent#ACTION_CANCEL} action being performed,
     * filling in a subset of the basic motion values. Those not specified here are:
     * <ul>
     * <li>down time (current milliseconds since boot)</li>
     * <li>event time (current milliseconds since boot)</li>
     * <li>x and y coordinates of this event (always 0)</li>
     * <li>
     * The state of any meta/modifier keys that were in effect when the event was generated (always 0)
     * </li>
     * </ul>
     */
    @NonNull
    public static MotionEvent obtainCancelEvent() {
        final long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
    }

    /**
     * Converts a playback state constant defined for {@link VideoPlayerControl.PlaybackState} to a
     * specified string
     *
     * @param playbackState one of the constant defined for {@link VideoPlayerControl.PlaybackState}
     * @return the string representation of the playback state
     */
    @NonNull
    public static String playbackStateIntToString(@VideoPlayerControl.PlaybackState int playbackState) {
        switch (playbackState) {
            case VideoPlayerControl.PLAYBACK_STATE_UNDEFINED:
                return "UNDEFINED";
            case VideoPlayerControl.PLAYBACK_STATE_ERROR:
                return "ERROR";
            case VideoPlayerControl.PLAYBACK_STATE_IDLE:
                return "IDLE";
            case VideoPlayerControl.PLAYBACK_STATE_PREPARING:
                return "PREPARING";
            case VideoPlayerControl.PLAYBACK_STATE_PREPARED:
                return "PREPARED";
            case VideoPlayerControl.PLAYBACK_STATE_PLAYING:
                return "PLAYING";
            case VideoPlayerControl.PLAYBACK_STATE_PAUSED:
                return "PAUSED";
            case VideoPlayerControl.PLAYBACK_STATE_COMPLETED:
                return "COMPLETED";
            default:
                throw new IllegalArgumentException("the `playbackState` must be one of the constant"
                        + " defined for VideoPlayerControl.PlaybackState");
        }
    }

    /**
     * Walks up the hierarchy for the given `view` to determine if it is inside a scrolling container.
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

    /**
     * Indicates whether or not the view's layout direction is right-to-left.
     * This is resolved from layout attribute and/or the inherited value from its parent
     *
     * @return true if the layout direction is right-to-left
     */
    public static boolean isLayoutRtl(@NonNull View view) {
        return ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    public static int getAbsoluteGravity(@NonNull View parent, int gravity) {
        final int layoutDirection = ViewCompat.getLayoutDirection(parent);
        return GravityCompat.getAbsoluteGravity(gravity, layoutDirection);
    }

    public static int getAbsoluteHorizontalGravity(@NonNull View parent, int gravity) {
        return getAbsoluteGravity(parent, gravity) & Gravity.HORIZONTAL_GRAVITY_MASK;
    }

    /**
     * Includes a set of children of the given `parent` ViewGroup (not necessary to be the root of
     * the transition) for the given Transition object to skip them while it is running on a
     * view hierarchy.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static void includeChildrenForTransition(
            @NonNull Transition transition, @NonNull ViewGroup parent, @Nullable View... children) {
        outsider:
        for (int i = 0, childCount = parent.getChildCount(); i < childCount; i++) {
            View child = parent.getChildAt(i);
            if (children != null) {
                for (View child2 : children) {
                    if (child2 == child) continue outsider;
                }
            }
            transition.excludeTarget(child, true);
        }
    }

    /**
     * Judges if two floating-point numbers (float) are equal, ignoring very small precision errors.
     */
    public static boolean areEqualIgnorePrecisionError(float value1, float value2) {
        return Math.abs(value1 - value2) < 0.0001f;
    }

    /**
     * Judges if two floating-point numbers (double) are equal, ignoring very small precision errors.
     */
    public static boolean areEqualIgnorePrecisionError(double value1, double value2) {
        return Math.abs(value1 - value2) < 0.0001d;
    }
}
