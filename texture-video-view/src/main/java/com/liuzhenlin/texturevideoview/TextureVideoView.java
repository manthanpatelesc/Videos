/*
 * Created on 18-9-16 下午4:09.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.core.util.ObjectsCompat;

import com.liuzhenlin.texturevideoview.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.texturevideoview.utils.TimeUtil;

import java.io.File;
import java.io.IOException;

/**
 * A sub implementation class of {@link AbsTextureVideoView} to deal with the audio/video playback
 * logic related to the media player component through a {@link MediaPlayer} object.
 *
 * @author 刘振林
 */
public class TextureVideoView extends AbsTextureVideoView {

    private static final String TAG = "TextureVideoView";

    /**
     * Flag used to indicate that the volume of the video is auto-turned down by the system
     * when the player temporarily loses the audio focus.
     */
    private static final int PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY =
            PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS << 1;

    /**
     * Flag indicates that a position seek happens when the video is not playing.
     */
    private static final int PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED =
            PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS << 2;

    private MediaPlayer mMediaPlayer;

    /**
     * How much of the network-based video has been buffered from the media stream received
     * through progressive HTTP download.
     */
    private int mBuffering;

    private final AudioAttributes mAudioAttrs;
    private final AudioFocusRequest mAudioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                // Audio focus gained
                case AudioManager.AUDIOFOCUS_GAIN:
                    if ((mPrivateFlags & PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY) != 0) {
                        mPrivateFlags &= ~PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                        mMediaPlayer.setVolume(1.0f, 1.0f);
                    }
                    play();
                    break;

                // Loss of audio focus of unknown duration.
                // This usually happens when the user switches to another audio/video application
                // that causes our view to stop playing, so the video can be thought of as
                // being paused/closed by the user.
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (getWindowVisibility() == VISIBLE) {
                        // If the window container of this view is still visible to the user,
                        // pauses the video only.
                        pause(true);
                    } else {
                        // But if this occurs during background playback, we must close the video
                        // to release the resources associated with it.
                        closeVideoInternal(true);
                    }
                    break;

                // Temporarily lose the audio focus and will probably gain it again soon.
                // Must stop the video playback but no need for releasing the MediaPlayer here.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    pause(false);
                    break;

                // Temporarily lose the audio focus but the playback can continue.
                // The volume of the playback needs to be turned down.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mPrivateFlags |= PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                    mMediaPlayer.setVolume(0.5f, 0.5f);
                    break;
            }
        }
    };

    /* class initializer */ {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAudioAttrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(mAudioAttrs)
                        .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
                        .setAcceptsDelayedFocusGain(true)
                        .build();
            } else {
                mAudioFocusRequest = null;
            }
        } else {
            mAudioAttrs = null;
            mAudioFocusRequest = null;
        }
    }

    public TextureVideoView(Context context) {
        super(context);
    }

    public TextureVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextureVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setVideoResourceId(@RawRes int resId) {
        setVideoPath(resId == 0 ?
                null : "android.resource://" + mContext.getPackageName() + File.separator + resId);
    }

    @Override
    public void setVideoUri(@Nullable Uri uri) {
        if (!ObjectsCompat.equals(uri, mVideoUri)) {
            mVideoUri = uri;
            mVideoDuration = 0;
            mVideoDurationString = DEFAULT_STRING_VIDEO_DURATION;
            mPrivateFlags &= ~PFLAG_VIDEO_INFO_RESOLVED;
            if (mMediaPlayer == null) {
                // Removes the flags PFLAG_VIDEO_PLAYBACK_COMPLETED and PFLAG_VIDEO_PAUSED_BY_USER
                // and resets mSeekOnPlay to 0 in case the MediaPlayer was previously released
                // and has not been initialized yet.
                mPrivateFlags &= ~(PFLAG_VIDEO_PLAYBACK_COMPLETED | PFLAG_VIDEO_PAUSED_BY_USER);
                mSeekOnPlay = 0;
                openVideoInternal();
            } else {
                restartVideo();
            }
        }
    }

    @Override
    void openVideoInternal() {
        if (mMediaPlayer == null && mSurface != null && mVideoUri != null
                && (mPrivateFlags & PFLAG_VIDEO_PAUSED_BY_USER) == 0) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setSurface(isPureAudioPlayback() ? null : mSurface);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mMediaPlayer.setAudioAttributes(mAudioAttrs);
            } else {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            mMediaPlayer.setOnPreparedListener(mp -> {
                showLoadingView(false);

                mVideoDuration = mp.getDuration();
                mVideoDurationString = TimeUtil.formatTimeByColon(mVideoDuration);
                mPrivateFlags |= PFLAG_VIDEO_INFO_RESOLVED;

                mPrivateFlags &= ~PFLAG_PLAYER_IS_PREPARING;
                play();
            });
            mMediaPlayer.setOnSeekCompleteListener(mp -> showLoadingView(false));
            mMediaPlayer.setOnBufferingUpdateListener((mp, percent)
                    -> mBuffering = (int) (mVideoDuration * percent / 100f + 0.5f));
            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error occurred while playing video:" +
                            " what= " + what + "; extra= " + extra);
                }
                showVideoErrorMsg(extra);

                mPrivateFlags |= PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO;
                mPrivateFlags &= ~(PFLAG_PLAYER_IS_PREPARING | PFLAG_VIDEO_PLAYBACK_COMPLETED);
                pause(false);
                return true;
            });
            mMediaPlayer.setOnCompletionListener(mp -> onPlaybackCompleted());
            mMediaPlayer.setOnVideoSizeChangedListener((mp, width, height)
                    -> onVideoSizeChanged(width, height));
            startVideo();

            mSession = new MediaSessionCompat(mContext, TAG);
            mSession.setCallback(new SessionCallback());
            mSession.setActive(true);
            mHeadsetEventsReceiver = new HeadsetEventsReceiver(mContext) {
                @Override
                public void onHeadsetPluggedOutOrBluetoothDisconnected() {
                    pause(true);
                }
            };
            mHeadsetEventsReceiver.register(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        }
    }

    private void showVideoErrorMsg(int errorType) {
        final int stringRes;
        switch (errorType) {
            case MediaPlayer.MEDIA_ERROR_IO:
                stringRes = R.string.failedToLoadThisVideo;
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                stringRes = R.string.videoInThisFormatIsNotSupported;
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                stringRes = R.string.loadTimedOut;
                break;
            default:
                stringRes = R.string.unknownErrorOccurredWhenVideoIsPlaying;
                break;
        }
        Toast.makeText(mContext, stringRes, Toast.LENGTH_SHORT).show();
    }

    private void startVideo() {
        if (mVideoUri != null) {
            try {
                mMediaPlayer.setDataSource(mContext, mVideoUri);
                showLoadingView(true);
                mPrivateFlags |= PFLAG_PLAYER_IS_PREPARING;
                mMediaPlayer.prepareAsync();
                mMediaPlayer.setLooping(isSingleVideoLoopPlayback());
            } catch (IOException e) {
                e.printStackTrace();
                showVideoErrorMsg(-1004 /* MediaPlayer.MEDIA_ERROR_IO */);
                showLoadingView(false); // in case it is already showing
            }
        } else {
            showLoadingView(false);
        }
        cancelDraggingVideoSeekBar();
    }

    @Override
    public void restartVideo() {
        // Resets mSeekOnPlay to 0 and removes the PFLAG_VIDEO_PLAYBACK_COMPLETED flag in case
        // the MediaPlayer object is (being) released. This ensures the video to be started
        // at its beginning position when the next time it resumes.
        mSeekOnPlay = 0;
        mPrivateFlags &= ~PFLAG_VIDEO_PLAYBACK_COMPLETED;
        if (mMediaPlayer != null && (mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) == 0) {
            // Not clear the PFLAG_VIDEO_INFO_RESOLVED flag
            mPrivateFlags &= ~(PFLAG_PLAYER_IS_PREPARING
                    | PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO
                    | PFLAG_VIDEO_PAUSED_BY_USER);
            pause(false);
            // Resets below to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;
            mMediaPlayer.reset();
            startVideo();
        }
    }

    @Override
    public void play() {
        if ((mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) != 0) {
            // In case the video playback is closing
            return;
        }

        if (mMediaPlayer == null) {
            // Maybe the MediaPlayer has not been created since this page showed again after
            // the video had been paused by the user instead of our program itself or ended
            // in the last playback. Initialize it here as the user hits play.
            if ((mPrivateFlags & (PFLAG_VIDEO_PAUSED_BY_USER | PFLAG_VIDEO_PLAYBACK_COMPLETED)) != 0) {
                mPrivateFlags &= ~PFLAG_VIDEO_PAUSED_BY_USER;
                openVideoInternal();
            }
            return;
            // Already in the preparing or playing state
        } else if ((mPrivateFlags & (PFLAG_PLAYER_IS_PREPARING | PFLAG_VIDEO_IS_PLAYING)) != 0) {
            return;
        }

        if ((mPrivateFlags & PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO) != 0) {
            mPrivateFlags &= ~PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO;
            // Retries the failed playback after error occurred
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;
            if ((mPrivateFlags & PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED) == 0) {
                // Record the current playback position only if there is no external program code
                // requesting a position seek in this case.
                mSeekOnPlay = getVideoProgress();
            }
            mMediaPlayer.reset();
            startVideo();

            // Starts the video only if we have prepared it for the player
        } else if (isVideoPrepared()) {
            //@formatter:off
            final int result = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    mAudioManager.requestAudioFocus(mAudioFocusRequest)
                  : mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                            AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            //@formatter:on
            switch (result) {
                case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Failed to request audio focus");
                    }
                    // Starts to play video even if the audio focus is not gained, but it is best
                    // not to happen.
                case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                    mPrivateFlags &= ~(PFLAG_VIDEO_PAUSED_BY_USER | PFLAG_VIDEO_PLAYBACK_COMPLETED);
                    // Ensure the player's volume is at its maximum
                    if ((mPrivateFlags & PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY) != 0) {
                        mPrivateFlags &= ~PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                        mMediaPlayer.setVolume(1.0f, 1.0f);
                    }
                    if (mUserPlaybackSpeed != mPlaybackSpeed) {
                        setPlaybackSpeed(mUserPlaybackSpeed);
                    }
                    mMediaPlayer.start();
                    // Position seek each time works correctly only if the player engine is started
                    if (mSeekOnPlay != 0) {
                        mPrivateFlags |= PFLAG_VIDEO_IS_PLAYING;
                        seekTo(mSeekOnPlay);
                        mSeekOnPlay = 0;
                    }
                    onVideoStarted();
                    break;

                case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                    // do nothing
                    break;
            }
        }
    }

    @Override
    public void pause(boolean fromUser) {
        if (isPlaying()) {
            mMediaPlayer.pause();
            mPrivateFlags = mPrivateFlags & ~PFLAG_VIDEO_PAUSED_BY_USER
                    | (fromUser ? PFLAG_VIDEO_PAUSED_BY_USER : 0);
            onVideoStopped();
        }
    }

    @Override
    void closeVideoInternal(boolean fromUser) {
        if (mMediaPlayer != null && (mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) == 0) {
            mPrivateFlags |= PFLAG_VIDEO_IS_CLOSING;

            if (!isPlaybackCompleted()) {
                mSeekOnPlay = getVideoProgress();
            }
            pause(fromUser);
            abandonAudioFocus();
            mMediaPlayer.release();
            mMediaPlayer = null;
            // Not clear the flags PFLAG_VIDEO_PLAYBACK_COMPLETED and PFLAG_VIDEO_INFO_RESOLVED
            mPrivateFlags &= ~(PFLAG_PLAYER_IS_PREPARING
                    | PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO
                    | PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY);
            // Resets below to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;

            mSession.setActive(false);
            mSession.release();
            mSession = null;
            mHeadsetEventsReceiver.unregister();
            mHeadsetEventsReceiver = null;

            mPrivateFlags &= ~PFLAG_VIDEO_IS_CLOSING;

            showLoadingView(false);
        }
        cancelDraggingVideoSeekBar();
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        } else {
            mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
        }
    }

    @Override
    public void seekTo(int progress) {
        if (isPlaying()) {
            showLoadingView(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Precise seek with larger performance overhead compared to the default one.
                // Slow! Really slow!
                mMediaPlayer.seekTo(progress, MediaPlayer.SEEK_CLOSEST);
            } else {
                mMediaPlayer.seekTo(progress /*, MediaPlayer.SEEK_PREVIOUS_SYNC*/);
            }
        } else {
            mPrivateFlags |= PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED;
            mSeekOnPlay = progress;
            play();
            mPrivateFlags &= ~PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED;
        }
    }

    /**
     * @return whether or not the video is prepared for the player
     */
    private boolean isVideoPrepared() {
        return mMediaPlayer != null && (mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0;
    }

    @Override
    public int getVideoProgress() {
        if (isPlaybackCompleted()) {
            // The playback position from the MediaPlayer, usually, is not the duration of the video
            // but the position at the last video key-frame when the playback is finished, in the
            // case of which instead, here is the duration returned to avoid progress inconsistencies.
            return mVideoDuration;
        }
        if (isVideoPrepared()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return mSeekOnPlay;
    }

    @Override
    public int getVideoBufferedProgress() {
        return mBuffering;
    }

    @Override
    public void setPureAudioPlayback(boolean audioOnly) {
        if (audioOnly != isPureAudioPlayback()) {
            if (mMediaPlayer != null) {
                mMediaPlayer.setSurface(audioOnly ? null : mSurface);
            }
            super.setPureAudioPlayback(audioOnly);
        }
    }

    @Override
    public void setSingleVideoLoopPlayback(boolean looping) {
        if (looping != isSingleVideoLoopPlayback()) {
            if (mMediaPlayer != null) {
                mMediaPlayer.setLooping(looping);
            }
            super.setSingleVideoLoopPlayback(looping);
        }
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != mPlaybackSpeed) {
            mUserPlaybackSpeed = speed;
            // When video is not playing or has no tendency of to be started, we prefer recording
            // the user request to forcing the player to start at that given speed.
            if ((mPrivateFlags & PFLAG_PLAYER_IS_PREPARING) != 0 || isPlaying()) {
                PlaybackParams pp = mMediaPlayer.getPlaybackParams().allowDefaults();
                pp.setSpeed(speed);
                try {
                    mMediaPlayer.setPlaybackParams(pp);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    mUserPlaybackSpeed = mPlaybackSpeed;
                    return;
                }
                // If the above fails due to an unsupported playback speed, then our speed would
                // remain unchanged. This ensures the program runs stably.
                mPlaybackSpeed = speed;
                super.setPlaybackSpeed(speed);
            }
        }
    }
}