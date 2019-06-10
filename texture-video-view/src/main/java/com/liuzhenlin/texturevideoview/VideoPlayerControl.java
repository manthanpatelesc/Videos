/*
 * Created on 5/6/19 1:11 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An interface that can be implemented by {@linkplain android.view.View View} subclasses that wish
 * to support basic audio/video playback operations exposable to external clients.
 *
 * @author 刘振林
 */
public interface VideoPlayerControl {

    /** Indicating that the brightness value of a window should follow the system's. */
    int BRIGHTNESS_FOLLOWS_SYSTEM = -1;
    /** Lowest value for the brightness of a window */
    int MIN_BRIGHTNESS = 0;
    /** Highest value for the brightness of a window */
    int MAX_BRIGHTNESS = 255;

    int INVALID_DURATION = -1;

    float DEFAULT_PLAYBACK_SPEED = 1.0f;

    /**
     * @return the brightness of the window this view is attached to
     */
    @IntRange(from = BRIGHTNESS_FOLLOWS_SYSTEM, to = MAX_BRIGHTNESS)
    int getBrightness();

    /**
     * Sets the brightness for the window to which this view is attached
     */
    void setBrightness(@IntRange(from = BRIGHTNESS_FOLLOWS_SYSTEM, to = MAX_BRIGHTNESS) int brightness);

    /**
     * @return the current volume of the media, maybe 0 if the ringer mode is silent or vibration
     */
    int getVolume();

    /**
     * Sets the media volume of the system used in the player
     */
    void setVolume(int volume);

    /**
     * Sets the raw resource ID of the video to play.
     */
    void setVideoResourceId(@RawRes int resId);

    /**
     * Sets the file path of the video to play
     */
    default void setVideoPath(@Nullable String path) {
        setVideoUri(TextUtils.isEmpty(path) ? null : Uri.parse(path));
    }

    /**
     * Sets the Uri for the video to play
     */
    void setVideoUri(@Nullable Uri uri);

    /**
     * Initialize the player object and prepare for the video playback.
     * Normally, you should invoke this method to resume video playback instead of {@link #play(boolean)}
     * whenever the Activity's restart() or resume() method is called unless the player won't
     * be released as the Activity's lifecycle changes.
     *
     * @see #play(boolean)
     * @see #closeVideo()
     */
    void openVideo();

    /**
     * Pauses playback and releases resources associated with it.
     * Usually, whenever an Activity of an application is paused (its onPaused() method is called),
     * or stopped (its onStop() method is called), this method should be invoked to release
     * the player object, unless the application has a special need to keep the object around.
     *
     * @see #openVideo()
     */
    void closeVideo();

    /**
     * Restarts playback of the video.
     */
    void restartVideo();

    /**
     * Checks whether the video is playing.
     *
     * @return {@code true} if currently playing, {@code false} otherwise
     */
    default boolean isPlaying() {
        return getPlaybackState() == PLAYBACK_STATE_PLAYING;
    }

    /**
     * Starts or resumes playback.
     * If previously paused, playback will continue from where it was paused.
     * If never started before, playback will start at the beginning.
     *
     * @param fromUser whether the playback is triggered by the user
     * @see #pause(boolean)
     */
    void play(boolean fromUser);

    /**
     * Pauses playback. Call {@link #play(boolean)} to resume.
     *
     * @param fromUser whether the video is paused by the user
     * @see #play(boolean)
     */
    void pause(boolean fromUser);

    /**
     * Switches the playback state between playing and non-playing
     */
    default void toggle(boolean fromUser) {
        if (isPlaying()) {
            pause(fromUser);
        } else {
            play(fromUser);
        }
    }

    /**
     * Skips video to the specified time position.
     *
     * @param fromUser whether the playback position change is initiated by the user
     */
    void seekTo(int progress, boolean fromUser);

    /**
     * Fast-forward the video.
     *
     * @param fromUser whether the video is forwarded by the user
     */
    void fastForward(boolean fromUser);

    /**
     * Fast-rewind the video.
     *
     * @param fromUser whether the video is rewound by the user
     */
    void fastRewind(boolean fromUser);

    /**
     * @return the current playback position of the video, in milliseconds.
     */
    int getVideoProgress();

    /**
     * @return an estimate of the position in the current content window up to which data is
     * buffered, in milliseconds.
     */
    int getVideoBufferedProgress();

    /**
     * Gets the duration of the video.
     *
     * @return the duration in milliseconds, if no duration is available (the duration is
     * not determined yet), then {@value INVALID_DURATION} is returned.
     */
    int getVideoDuration();

    /**
     * @return the width of the video, or 0 if there is no video or the width has not been
     * determined yet.
     */
    int getVideoWidth();

    /**
     * @return the height of the video, or 0 if there is no video or the height has not been
     * determined yet.
     */
    int getVideoHeight();

    /**
     * @return true if the player is currently in audio-only playback (no video displayed)
     */
    boolean isPureAudioPlayback();

    /**
     * Sets the player to audio-only playback mode to preserve the playback in the background
     * but will cause no video frame displayed onto the screen.
     */
    void setPureAudioPlayback(boolean audioOnly);

    /**
     * @return whether or not the player is looping through a single video.
     */
    boolean isSingleVideoLoopPlayback();

    /**
     * Sets the player to be looping through a single video or not.
     */
    void setSingleVideoLoopPlayback(boolean looping);

    /**
     * @return the current playback speed of the video
     */
    default float getPlaybackSpeed() {
        return 0;
    }

    /**
     * Sets the playback speed for the video player
     */
    default void setPlaybackSpeed(float speed) {
    }

    /**
     * @return the current state of the player or the playback of the video
     */
    @PlaybackState
    int getPlaybackState();

    /**
     * Adds a {@link OnPlaybackStateChangeListener} to get notified when the state of the player
     * or the playback state of the video changes
     */
    void addOnPlaybackStateChangeListener(@Nullable OnPlaybackStateChangeListener listener);

    /**
     * Removes a {@link OnPlaybackStateChangeListener} from the set of listeners previously added.
     */
    void removeOnPlaybackStateChangeListener(@Nullable OnPlaybackStateChangeListener listener);

    /**
     * Represents an undefined playback state of the video, usually set when the video is closing
     * but another op is requested, in which case the player cannot perform that action immediately.
     */
    int PLAYBACK_STATE_UNDEFINED = Integer.MIN_VALUE;

    /**
     * A fatal player error occurred that paused the playback
     */
    int PLAYBACK_STATE_ERROR = -1;

    /**
     * The player does not have any video to play.
     */
    int PLAYBACK_STATE_IDLE = 0;

    /**
     * The player is currently preparing for the video playback asynchronously.
     */
    int PLAYBACK_STATE_PREPARING = 1;

    /**
     * The video is prepared to be started
     */
    int PLAYBACK_STATE_PREPARED = 2;

    /**
     * The video is currently playing
     */
    int PLAYBACK_STATE_PLAYING = 3;

    /**
     * The video is temporarily paused
     */
    int PLAYBACK_STATE_PAUSED = 4;

    /**
     * The playback of the video is ended
     */
    int PLAYBACK_STATE_COMPLETED = 5;

    @IntDef({
            PLAYBACK_STATE_UNDEFINED,
            PLAYBACK_STATE_ERROR,
            PLAYBACK_STATE_IDLE,
            PLAYBACK_STATE_PREPARING, PLAYBACK_STATE_PREPARED,
            PLAYBACK_STATE_PLAYING, PLAYBACK_STATE_PAUSED, PLAYBACK_STATE_COMPLETED
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface PlaybackState {
    }

    /**
     * A listener to monitor all state changes to the player or the playback of the video
     */
    interface OnPlaybackStateChangeListener {
        /**
         * Called when the state of the player or the playback state of the video changes
         *
         * @param oldState the old state of the player or the playback of the video
         * @param newState the new state of the player or the playback of the video
         * @see PlaybackState
         */
        void onPlaybackStateChange(@PlaybackState int oldState, @PlaybackState int newState);
    }
}
