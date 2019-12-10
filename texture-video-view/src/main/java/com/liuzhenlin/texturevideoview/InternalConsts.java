/*
 * Created on 7/1/18 11:22 AM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RestrictTo;

/**
 * @author 刘振林
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class InternalConsts {

    private InternalConsts() {
    }

    static final boolean DEBUG = false;

    static final boolean DEBUG_LISTENER = DEBUG && false;

    public static final String EXTRA_MESSENGER = "extra_messenger";
    public static final String EXTRA_PLAYBACK_ACTIVITY_CLASS = "extra_playbackActivityClass";
    public static final String EXTRA_MEDIA_TITLE = "extra_mediaTitle";
    public static final String EXTRA_MEDIA_URI = "extra_mediaUri";
    public static final String EXTRA_IS_PLAYING = "extra_isPlaying";
    public static final String EXTRA_CAN_SKIP_TO_PREVIOUS = "extra_canSkipToPrevious";
    public static final String EXTRA_CAN_SKIP_TO_NEXT = "extra_canSkipToNext";
    public static final String EXTRA_MEDIA_PROGRESS = "extra_mediaProgress";
    public static final String EXTRA_MEDIA_DURATION = "extra_mediaDuration";

    public static Handler getMainThreadHandler() {
        return NoPreloadHolder.MAIN_THREAD_HANDLER;
    }

    private static final class NoPreloadHolder {
        static final Handler MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());
    }
}
