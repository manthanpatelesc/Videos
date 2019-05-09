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

import com.liuzhenlin.texturevideoview.VideoPlayerControl;

/**
 * @author 刘振林
 */
public class Utils {
    private Utils() {
    }

    /**
     * Create a new MotionEvent with {@link MotionEvent#ACTION_CANCEL} action being performed,
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

    /**
     * Convert a playback state constant defined for {@link VideoPlayerControl.PlaybackState} to a
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
}
