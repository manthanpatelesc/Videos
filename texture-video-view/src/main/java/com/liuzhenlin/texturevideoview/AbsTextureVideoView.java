/*
 * Created on 5/6/19 2:55 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.snackbar.Snackbar;
import com.liuzhenlin.texturevideoview.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.texturevideoview.receiver.MediaButtonEventHandler;
import com.liuzhenlin.texturevideoview.receiver.MediaButtonEventReceiver;
import com.liuzhenlin.texturevideoview.utils.FileUtils;
import com.liuzhenlin.texturevideoview.utils.Utils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

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
     * @return the {@link Surface} onto which video will be rendered.
     */
    @Nullable
    public abstract Surface getSurface();

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
    abstract void onPureAudioPlaybackModeChanged(boolean audioOnly);

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

    /**
     * Base implementation class to be extended, for you to create an {@link IVideoPlayer} component
     * that can be used for the {@link TextureVideoView} widget to play media contents.
     *
     * @author 刘振林
     */
    public static abstract class VideoPlayer implements IVideoPlayer {

        protected final Context mContext; // the Application Context

        @Nullable
        protected AbsTextureVideoView mVideoView;

        protected int mPrivateFlags;

        /** Set via {@link #setPureAudioPlayback(boolean)} */
        private static final int PFLAG_PURE_AUDIO_PLAYBACK = 1;

        /** Set via {@link #setSingleVideoLoopPlayback(boolean)} */
        private static final int PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK = 1 << 1;

        /** Indicates that the video info (width, height, duration, etc.) is now available. */
        protected static final int PFLAG_VIDEO_INFO_RESOLVED = 1 << 2;

        /**
         * Whether we can now get the actual video playback position directly from the video player.
         */
        protected static final int PFLAG_CAN_GET_ACTUAL_POSITION_FROM_PLAYER = 1 << 3;

        /** Indicates that the video is manually paused by the user. */
        protected static final int PFLAG_VIDEO_PAUSED_BY_USER = 1 << 4;

        /**
         * Flag indicates the video is being closed, i.e., we are releasing the player object,
         * during which we should not respond to the client such as restarting or resuming the video
         * (this may happen as we call the onVideoStopped() method of the VideoListener object in our
         * closeVideoInternal() method if the view is currently playing).
         */
        protected static final int PFLAG_VIDEO_IS_CLOSING = 1 << 5;

        /**
         * Listener to be notified whenever it is necessary to change the video played to
         * the previous or the next one in the playlist.
         */
        @Nullable
        /* package-private */ OnSkipPrevNextListener mOnSkipPrevNextListener;

        /** The set of listeners for all the events related to video we publish. */
        @Nullable
        /* package-private */ List<VideoListener> mVideoListeners;

        /** Listeners monitoring all state changes to the player or the playback of the video. */
        @Nullable
        /* package-private */ List<OnPlaybackStateChangeListener> mOnPlaybackStateChangeListeners;

        /**
         * Caches the listener that will be added to {@link #mOnPlaybackStateChangeListeners}
         * for debugging purpose while {@link PackageConsts#DEBUG_LISTENER} is turned on.
         */
        @Nullable
        private OnPlaybackStateChangeListener mOnPlaybackStateChangeDebuggingListener;

        /** The current state of the player or the playback of the video. */
        @PlaybackState
        private int mPlaybackState = PLAYBACK_STATE_IDLE;

        /** The Uri for the video to play, set in {@link #setVideoUri(Uri)}. */
        protected Uri mVideoUri;

        protected int mVideoWidth;
        protected int mVideoHeight;

        /** How long the playback will last for. */
        protected int mVideoDuration;

        /** The string representation of the video duration. */
        protected String mVideoDurationString = DEFAULT_STRING_VIDEO_DURATION;
        protected static final String DEFAULT_STRING_VIDEO_DURATION = "00:00";

        /**
         * Caches the speed at which the player works.
         */
        protected float mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
        /**
         * Caches the speed the user sets for the player at any time, even when the player has not
         * been created.
         * <p>
         * This may fail if the value is not supported by the framework.
         */
        protected float mUserPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;

        /**
         * Recording the seek position used when playback is just started.
         * <p>
         * Normally this is requested by the user (e.g., dragging the video progress bar while
         * the player is not playing) or saved when the user leaves current UI.
         */
        protected int mSeekOnPlay;

        /** The amount of time we are stepping forward or backward for fast-forward and fast-rewind. */
        public static final int FAST_FORWARD_REWIND_INTERVAL = 15000; // ms

        /**
         * Maximum cache size in bytes.
         * This is the limit on the size of all files that can be kept on disk.
         */
        protected static final long DEFAULT_MAXIMUM_CACHE_SIZE = 1024 * 1024 * 1024; // 1GB

        protected final AudioManager mAudioManager;

        /**
         * Default attributes for audio playback, which configure the underlying platform.
         * <p>
         * To get a {@link android.media.AudioAttributes} first accessible on api 21, simply call
         * the method {@link AudioAttributes#getAudioAttributesV21()} of this property.
         */
        protected static final AudioAttributes sDefaultAudioAttrs =
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.CONTENT_TYPE_MOVIE)
                        .build();

        protected static ComponentName sMediaButtonEventReceiverComponent;

        protected HeadsetEventsReceiver mHeadsetEventsReceiver;

        public VideoPlayer(@NonNull Context context) {
            mContext = context.getApplicationContext();
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (sMediaButtonEventReceiverComponent == null) {
                sMediaButtonEventReceiverComponent =
                        new ComponentName(context, MediaButtonEventReceiver.class);
            }
            if (PackageConsts.DEBUG_LISTENER) {
                final String videoPlayerTextualRepresentation =
                        getClass().getName() + "@" + Integer.toHexString(hashCode());
                mOnPlaybackStateChangeDebuggingListener = (oldState, newState) -> {
                    final String text = videoPlayerTextualRepresentation + ": "
                            + Utils.playbackStateIntToString(oldState) + " -> "
                            + Utils.playbackStateIntToString(newState);
                    if (mVideoView != null) {
                        Utils.showUserCancelableSnackbar(mVideoView, text, Snackbar.LENGTH_LONG);
                    } else {
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
                    }
                };
                addOnPlaybackStateChangeListener(mOnPlaybackStateChangeDebuggingListener);
            }
        }

        /**
         * @return Base directory for storing generated cache files of the video(s) that will be
         * downloaded from HTTP server onto disk.
         */
        @NonNull
        protected File getBaseVideoCacheDirectory() {
            return new File(FileUtils.getAvailableCacheDir(mContext), "videos");
        }

        /**
         * Sets the {@link AbsTextureVideoView} on which the video will be displayed.
         * <p>
         * After setting it, you probably need to call {@link TextureVideoView#setVideoPlayer(VideoPlayer)}
         * with this player object as the function argument so as to synchronize the UI state.
         */
        public void setVideoView(@Nullable AbsTextureVideoView videoView) {
            mVideoView = videoView;
        }

        @Override
        public void setVideoUri(@Nullable Uri uri) {
            mVideoUri = uri;
            if (mVideoView != null) {
                mVideoView.onVideoUriChanged();
            }
        }

        /** @see #openVideo(boolean) */
        @Override
        public final void openVideo() {
            openVideo(false);
        }

        /**
         * Initialize the player object and prepare for the video playback.
         * Normally, you should invoke this method to resume video playback instead of {@link #play(boolean)}
         * whenever the Activity's restart() or resume() method is called unless the player won't
         * be released as the Activity's lifecycle changes.
         * <p>
         * <strong>NOTE:</strong> When the window the view is attached to leaves the foreground,
         * if the video has already been paused by the user, the player will not be instantiated
         * even if you call this method when the view is displayed in front of the user again and
         * only when the user manually clicks to play, will it be initialized (see {@link #play(boolean)}),
         * but you should still call this method as usual.
         *
         * @param replayIfCompleted whether to replay the video if it is over
         * @see #closeVideo()
         * @see #play(boolean)
         */
        public final void openVideo(boolean replayIfCompleted) {
            if (replayIfCompleted || mPlaybackState != PLAYBACK_STATE_COMPLETED) {
                openVideoInternal();
            }
        }

        protected abstract void openVideoInternal();

        @Override
        public final void closeVideo() {
            if (!isPureAudioPlayback()) {
                closeVideoInternal(false /* ignored */);
            }
        }

        /**
         * The same as {@link #closeVideo()}, but closes the video in spite of the playback mode
         * (video or audio-only).
         *
         * @param fromUser `true` if the video is turned off by the user.
         */
        protected abstract void closeVideoInternal(boolean fromUser);

        @Override
        public void fastForward(boolean fromUser) {
            seekTo(getVideoProgress() + FAST_FORWARD_REWIND_INTERVAL, fromUser);
        }

        @Override
        public void fastRewind(boolean fromUser) {
            seekTo(getVideoProgress() - FAST_FORWARD_REWIND_INTERVAL, fromUser);
        }

        @Override
        public int getVideoDuration() {
            if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
                return mVideoDuration;
            }
            return UNKNOWN_DURATION;
        }

        @Override
        public int getVideoWidth() {
            if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
                return mVideoWidth;
            }
            return 0;
        }

        @Override
        public int getVideoHeight() {
            if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
                return mVideoHeight;
            }
            return 0;
        }

        @Override
        public boolean isPureAudioPlayback() {
            return (mPrivateFlags & PFLAG_PURE_AUDIO_PLAYBACK) != 0;
        }

        @CallSuper
        @Override
        public void setPureAudioPlayback(boolean audioOnly) {
            mPrivateFlags = mPrivateFlags & ~PFLAG_PURE_AUDIO_PLAYBACK
                    | (audioOnly ? PFLAG_PURE_AUDIO_PLAYBACK : 0);
            if (mVideoView != null) {
                mVideoView.onPureAudioPlaybackModeChanged(audioOnly);
            }
        }

        @Override
        public boolean isSingleVideoLoopPlayback() {
            return (mPrivateFlags & PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK) != 0;
        }

        /**
         * {@inheritDoc}
         * <p>
         * <strong>NOTE:</strong> This does not mean that the video played can not be changed,
         * which can be switched by the user when he/she clicks the 'skip next' button or
         * chooses another video from the playlist.
         */
        @CallSuper
        @Override
        public void setSingleVideoLoopPlayback(boolean looping) {
            mPrivateFlags = mPrivateFlags & ~PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK
                    | (looping ? PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK : 0);
            if (mVideoView != null) {
                mVideoView.onSingleVideoLoopPlaybackModeChanged(looping);
            }
        }

        @Override
        public float getPlaybackSpeed() {
            return isPlaying() ? mPlaybackSpeed : 0;
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
        @CallSuper
        @Override
        public void setPlaybackSpeed(float speed) {
            if (mVideoView != null) {
                mVideoView.onPlaybackSpeedChanged(speed);
            }
        }

        @PlaybackState
        @Override
        public int getPlaybackState() {
            return mPlaybackState;
        }

        protected void setPlaybackState(@PlaybackState int newState) {
            final int oldState = mPlaybackState;
            if (newState != oldState) {
                mPlaybackState = newState;
                if (hasOnPlaybackStateChangeListener()) {
                    // Since onPlaybackStateChange() is implemented by the app, it could do anything,
                    // including removing itself from {@link mOnPlaybackStateChangeListeners} — and
                    // that could cause problems if an iterator is used on the ArrayList.
                    // To avoid such problems, just march thru the list in the reverse order.
                    for (int i = mOnPlaybackStateChangeListeners.size() - 1; i >= 0; i--) {
                        mOnPlaybackStateChangeListeners.get(i).onPlaybackStateChange(oldState, newState);
                    }
                }
            }
        }

        private boolean hasOnPlaybackStateChangeListener() {
            return mOnPlaybackStateChangeListeners != null && !mOnPlaybackStateChangeListeners.isEmpty();
        }

        public void addOnPlaybackStateChangeListener(@Nullable OnPlaybackStateChangeListener listener) {
            if (listener != null) {
                if (mOnPlaybackStateChangeListeners == null) {
                    mOnPlaybackStateChangeListeners = new ArrayList<>(1);
                }
                if (!mOnPlaybackStateChangeListeners.contains(listener)) {
                    mOnPlaybackStateChangeListeners.add(listener);
                }
            }
        }

        public void removeOnPlaybackStateChangeListener(@Nullable OnPlaybackStateChangeListener listener) {
            if (listener != null && hasOnPlaybackStateChangeListener()) {
                mOnPlaybackStateChangeListeners.remove(listener);
            }
        }

        public void clearOnPlaybackStateChangeListener() {
            if (hasOnPlaybackStateChangeListener()) {
                OnPlaybackStateChangeListener debuggingListener = mOnPlaybackStateChangeDebuggingListener;
                if (debuggingListener == null) {
                    mOnPlaybackStateChangeListeners.clear();
                } else {
                    for (int i = mOnPlaybackStateChangeListeners.size() - 1; i >= 0; i--) {
                        if (mOnPlaybackStateChangeListeners.get(i) != debuggingListener) {
                            mOnPlaybackStateChangeListeners.remove(i);
                        }
                    }
                }
            }
        }

        private boolean hasVideoListener() {
            return mVideoListeners != null && !mVideoListeners.isEmpty();
        }

        public void addVideoListener(@Nullable VideoListener listener) {
            if (listener != null) {
                if (mVideoListeners == null) {
                    mVideoListeners = new ArrayList<>(1);
                }
                if (!mVideoListeners.contains(listener)) {
                    mVideoListeners.add(listener);
                }
            }
        }

        public void removeVideoListener(@Nullable VideoListener listener) {
            if (listener != null && hasVideoListener()) {
                mVideoListeners.remove(listener);
            }
        }

        public void clearVideoListeners() {
            if (hasVideoListener()) {
                mVideoListeners.clear();
            }
        }

        protected void onVideoStarted() {
            setPlaybackState(PLAYBACK_STATE_PLAYING);

            if (mVideoView != null) {
                mVideoView.onVideoStarted();
            }

            if (hasVideoListener()) {
                for (int i = mVideoListeners.size() - 1; i >= 0; i--) {
                    mVideoListeners.get(i).onVideoStarted();
                }
            }
        }

        protected void onVideoStopped() {
            onVideoStopped(false /* uncompleted */);
        }

        /**
         * @param canSkipNextOnCompletion `true` if we can skip the played video to the next one in
         *                                the playlist (if any) when the current playback ends
         */
        private void onVideoStopped(boolean canSkipNextOnCompletion) {
            final int oldState = mPlaybackState;
            final int currentState;
            if (oldState == PLAYBACK_STATE_PLAYING) {
                setPlaybackState(PLAYBACK_STATE_PAUSED);
                currentState = PLAYBACK_STATE_PAUSED;
            } else {
                currentState = oldState;
            }

            if (mVideoView != null) {
                mVideoView.onVideoStopped();
            }

            if (hasVideoListener()) {
                for (int i = mVideoListeners.size() - 1; i >= 0; i--) {
                    mVideoListeners.get(i).onVideoStopped();
                }

                // First, checks the current playback state here to see if it was changed in
                // the above calls to the onVideoStopped() methods of the VideoListeners.
                if (currentState == mPlaybackState)
                    // Then, checks whether or not the player object is released (whether the closeVideo()
                    // method was called unexpectedly by the client within the same calls as above).
                    if (isPlayerCreated())
                        // If all of the conditions above hold, skips to the next if possible.
                        if (currentState == PLAYBACK_STATE_COMPLETED && canSkipNextOnCompletion) {
                            skipToNextIfPossible();
                        }
            }
        }

        /**
         * @return whether or not the player object is created for playing the video(s)
         */
        protected abstract boolean isPlayerCreated();

        /**
         * @return true if the video is closed, as scheduled by the user, when playback completes
         */
        protected boolean onPlaybackCompleted() {
            setPlaybackState(PLAYBACK_STATE_COMPLETED);

            if (mVideoView != null && mVideoView.willTurnOffWhenThisEpisodeEnds()) {
                mVideoView.onVideoTurnedOffWhenTheEpisodeEnds();

                closeVideoInternal(true);
                return true;
            } else {
                onVideoStopped(true);
                return false;
            }
        }

        protected void onVideoSizeChanged(int width, int height) {
            final int oldWidth = mVideoWidth;
            final int oldHeight = mVideoHeight;
            if (oldWidth != width || oldHeight != height) {
                mVideoWidth = width;
                mVideoHeight = height;

                if (hasVideoListener()) {
                    for (int i = mVideoListeners.size() - 1; i >= 0; i--) {
                        mVideoListeners.get(i).onVideoSizeChanged(oldWidth, oldHeight, width, height);
                    }
                }

                if (mVideoView != null) {
                    mVideoView.onVideoSizeChanged(width, height);
                }
            }
        }

        protected boolean skipToPreviousIfPossible() {
            if (mVideoView != null && !mVideoView.canSkipToPrevious()) {
                return false;
            }

            if (mOnSkipPrevNextListener != null) {
                mOnSkipPrevNextListener.onSkipToPrevious();
            }
            return true;
        }

        protected boolean skipToNextIfPossible() {
            if (mVideoView != null && !mVideoView.canSkipToNext()) {
                return false;
            }

            if (mOnSkipPrevNextListener != null) {
                mOnSkipPrevNextListener.onSkipToNext();
            }
            return true;
        }

        public void setOnSkipPrevNextListener(@Nullable OnSkipPrevNextListener listener) {
            mOnSkipPrevNextListener = listener;
        }

        public interface OnSkipPrevNextListener {
            /**
             * Called when the previous video in the playlist (if any) needs to be played
             */
            default void onSkipToPrevious() {
            }

            /**
             * Called when the next video in the playlist (if any) needs to be played
             */
            default void onSkipToNext() {
            }
        }

        protected static class MsgHandler extends Handler {
            protected final WeakReference<VideoPlayer> mVideoPlayerRef;

            protected MsgHandler(VideoPlayer videoPlayer) {
                mVideoPlayerRef = new WeakReference<>(videoPlayer);
            }

            @Override
            public void handleMessage(@NonNull Message msg) {
                VideoPlayer videoPlayer = mVideoPlayerRef.get();
                if (videoPlayer == null) return;

                AbsTextureVideoView videoView = videoPlayer.mVideoView;
                if (!(videoView != null && videoView.isInForeground() || videoPlayer.isPureAudioPlayback())) {
                    return;
                }

                switch (msg.what) {
                    case MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_SINGLE_TAP:
                        videoPlayer.toggle(true);
                        break;
                    // Consider double tap as the next.
                    case MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_DOUBLE_TAP:
                    case MediaButtonEventHandler.MSG_MEDIA_NEXT:
                        videoPlayer.skipToNextIfPossible();
                        break;
                    // Consider triple tap as the previous.
                    case MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_TRIPLE_TAP:
                    case MediaButtonEventHandler.MSG_MEDIA_PREVIOUS:
                        videoPlayer.skipToPreviousIfPossible();
                        break;
                }
            }
        }

        public static final class Factory {
            @Nullable
            public static <VP extends VideoPlayer> VP newInstance(
                    @NonNull Class<VP> vpClass, @NonNull Context context) {
                if (TextureVideoView.MediaPlayer.class == vpClass) {
                    //noinspection unchecked
                    return (VP) new TextureVideoView.MediaPlayer(context);
                }
                if (TextureVideoView.ExoPlayer.class == vpClass) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        //noinspection unchecked
                        return (VP) new TextureVideoView.ExoPlayer(context);
                    }
                    return null;
                }
                //noinspection unchecked
                for (Constructor<VP> constructor : (Constructor<VP>[]) vpClass.getConstructors()) {
                    Class<?>[] paramTypes = constructor.getParameterTypes();
                    // Try to find a constructor that takes a single parameter whose type is the
                    // (super) type of the context.
                    if (paramTypes.length == 1) {
                        try {
                            context.getClass().asSubclass(paramTypes[0]);
                        } catch (ClassCastException e) {
                            continue;
                        }
                        try {
                            return constructor.newInstance(context);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InstantiationException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return null;
            }
        }
    }
}
