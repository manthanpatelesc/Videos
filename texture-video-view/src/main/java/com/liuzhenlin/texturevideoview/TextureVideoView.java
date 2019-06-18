/*
 * Created on 18-9-16 下午4:09.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.content.Context;
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
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

import com.liuzhenlin.texturevideoview.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.texturevideoview.utils.TimeUtil;

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
    private static final int PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY = PFLAG_VIDEO_IS_CLOSING << 1;

    /**
     * Flag indicates that a position seek request happens when the video is not playing.
     */
    private static final int PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED = PFLAG_VIDEO_IS_CLOSING << 2;

    /**
     * If true, MediaPlayer is moving the media to some specified time position
     */
    private static final int PFLAG_SEEKING = PFLAG_VIDEO_IS_CLOSING << 3;

    /**
     * If true, MediaPlayer is temporarily pausing playback internally in order to buffer more data.
     */
    private static final int PFLAG_BUFFERING = PFLAG_VIDEO_IS_CLOSING << 4;

    private MediaPlayer mMediaPlayer;

    /**
     * How much of the network-based video has been buffered from the media stream received
     * through progressive HTTP download.
     */
    private int mBuffering;

    private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                // Audio focus gained
                case AudioManager.AUDIOFOCUS_GAIN:
                    play(false);
                    break;

                // Loss of audio focus of unknown duration.
                // This usually happens when the user switches to another audio/video application
                // that causes our view to stop playing, so the video can be thought of as
                // being paused/closed by the user.
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (isInForeground()) {
                        // If this view is still in the foreground, pauses the video only.
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
                    if (mMediaPlayer != null) {
                        mPrivateFlags |= PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                        mMediaPlayer.setVolume(0.5f, 0.5f);
                    }
                    break;
            }
        }
    };
    private final AudioFocusRequest mAudioFocusRequest =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(sDefaultAudioAttrs.getAudioAttributesV21())
                            .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
                            .setAcceptsDelayedFocusGain(true)
                            .build()
                    : null;

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
                null : "android.resource://" + mContext.getPackageName() + "/" + resId);
    }

    @Override
    public void setVideoUri(@Nullable Uri uri) {
        if (!ObjectsCompat.equals(uri, mVideoUri)) {
            super.setVideoUri(uri);
            mVideoDuration = 0;
            mVideoDurationString = DEFAULT_STRING_VIDEO_DURATION;
            mPrivateFlags &= ~PFLAG_VIDEO_INFO_RESOLVED;
            if (mMediaPlayer == null) {
                // Removes the PFLAG_VIDEO_PAUSED_BY_USER flag and resets mSeekOnPlay to 0 in case
                // the MediaPlayer was previously released and has not been initialized yet.
                mPrivateFlags &= ~PFLAG_VIDEO_PAUSED_BY_USER;
                mSeekOnPlay = 0;
                if (uri == null) {
                    // Sets the playback state to idle directly when the player is not created
                    // and no video is set
                    setPlaybackState(PLAYBACK_STATE_IDLE);
                } else {
                    openVideoInternal();
                }
            } else {
                restartVideo();
            }
        }
    }

    @Override
    protected void openVideoInternal() {
        if (mMediaPlayer == null && mSurface != null && mVideoUri != null
                && (mPrivateFlags & PFLAG_VIDEO_PAUSED_BY_USER) == 0) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setSurface(isPureAudioPlayback() ? null : mSurface);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mMediaPlayer.setAudioAttributes(sDefaultAudioAttrs.getAudioAttributesV21());
            } else {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            mMediaPlayer.setOnPreparedListener(mp -> {
                showLoadingView(false);
                if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) == 0) {
                    mVideoDuration = mp.getDuration();
                    mVideoDurationString = TimeUtil.formatTimeByColon(mVideoDuration);
                    mPrivateFlags |= PFLAG_VIDEO_INFO_RESOLVED;
                }
                setPlaybackState(PLAYBACK_STATE_PREPARED);
                play(false);
            });
            mMediaPlayer.setOnSeekCompleteListener(mp -> {
                if ((mPrivateFlags & PFLAG_BUFFERING) == 0) {
                    showLoadingView(false);
                }
                mPrivateFlags &= ~PFLAG_SEEKING;
            });
            mMediaPlayer.setOnInfoListener((mp, what, extra) -> {
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        mPrivateFlags |= PFLAG_BUFFERING;
                        if ((mPrivateFlags & PFLAG_SEEKING) == 0) {
                            showLoadingView(true);
                        }
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        mPrivateFlags &= ~PFLAG_BUFFERING;
                        if ((mPrivateFlags & PFLAG_SEEKING) == 0) {
                            showLoadingView(false);
                        }
                        break;
                }
                return false;
            });
            mMediaPlayer.setOnBufferingUpdateListener((mp, percent)
                    -> mBuffering = (int) (mVideoDuration * percent / 100f + 0.5f));
            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error occurred while playing video:" +
                            " what= " + what + "; extra= " + extra);
                }
                showVideoErrorMsg(extra);

                showLoadingView(false);
                final boolean playing = isPlaying();
                setPlaybackState(PLAYBACK_STATE_ERROR);
                if (playing) {
                    pauseInternal(false);
                }
                return true;
            });
            mMediaPlayer.setOnCompletionListener(mp -> onPlaybackCompleted());
            mMediaPlayer.setOnVideoSizeChangedListener((mp, width, height)
                    -> onVideoSizeChanged(width, height));
            startVideo();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mSession = new MediaSessionCompat(mContext, TAG);
                mSession.setCallback(new SessionCallback());
                mSession.setActive(true);
            }
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
                stringRes = R.string.loadTimeout;
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
                setPlaybackState(PLAYBACK_STATE_PREPARING);
                mMediaPlayer.prepareAsync();
                mMediaPlayer.setLooping(isSingleVideoLoopPlayback());
            } catch (IOException e) {
                e.printStackTrace();
                showVideoErrorMsg(-1004 /* MediaPlayer.MEDIA_ERROR_IO */);
                showLoadingView(false); // in case it is already showing
                setPlaybackState(PLAYBACK_STATE_ERROR);
            }
        } else {
            showLoadingView(false);
            setPlaybackState(PLAYBACK_STATE_IDLE);
        }
        cancelDraggingVideoSeekBar();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>NOTE: </strong> If this method is called during the video being closed, it does
     * nothing other than setting the playback state to {@link #PLAYBACK_STATE_UNDEFINED}, so as
     * not to suppress he next call to the {@link #openVideo()) method if the current playback state
     * is {@link #PLAYBACK_STATE_COMPLETED}, and the state is usually needed to be updated in
     * this call, too. Thus for all of the above reasons, it is the best to switch over to.
     */
    @Override
    public void restartVideo() {
        // First, resets mSeekOnPlay to 0 in case the MediaPlayer object is (being) released.
        // This ensures the video to be started at its beginning position the next time it resumes.
        mSeekOnPlay = 0;
        if (mMediaPlayer != null) {
            if ((mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) != 0) {
                setPlaybackState(PLAYBACK_STATE_UNDEFINED);
            } else {
                // Not clear the PFLAG_VIDEO_INFO_RESOLVED flag
                mPrivateFlags &= ~(PFLAG_VIDEO_PAUSED_BY_USER
                        | PFLAG_SEEKING
                        | PFLAG_BUFFERING);
                pause(false);
                // Resets below to prepare for the next resume of the video player
                mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
                mBuffering = 0;
                mMediaPlayer.reset();
                startVideo();
            }
        }
    }

    @Override
    public void play(boolean fromUser) {
        if ((mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) != 0) {
            // In case the video playback is closing
            return;
        }

        final int playbackState = getPlaybackState();

        if (mMediaPlayer == null) {
            // Opens the video only if this is a user request
            if (fromUser) {
                // If the video playback finished, skip to the next video if possible
                if (playbackState == PLAYBACK_STATE_COMPLETED &&
                        skipToNextIfPossible() && mMediaPlayer != null) {
                    return;
                }

                mPrivateFlags &= ~PFLAG_VIDEO_PAUSED_BY_USER;
                openVideoInternal();
            } else {
                Log.w(TAG, "Cannot start playback programmatically before the video is opened");
            }
            return;
        }

        switch (playbackState) {
            case PLAYBACK_STATE_UNDEFINED:
            case PLAYBACK_STATE_IDLE: // no video is set
                // Already in the preparing or playing state
            case PLAYBACK_STATE_PREPARING:
            case PLAYBACK_STATE_PLAYING:
                break;

            case PLAYBACK_STATE_ERROR:
                // Retries the failed playback after error occurred
                mPrivateFlags &= ~(PFLAG_SEEKING | PFLAG_BUFFERING);
                mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
                mBuffering = 0;
                if ((mPrivateFlags & PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED) == 0) {
                    // Record the current playback position only if there is no external program code
                    // requesting a position seek in this case.
                    mSeekOnPlay = getVideoProgress();
                }
                mMediaPlayer.reset();
                startVideo();
                break;

            case PLAYBACK_STATE_COMPLETED:
                if (skipToNextIfPossible() && getPlaybackState() != PLAYBACK_STATE_COMPLETED) {
                    break;
                }
                // Starts the video only if we have prepared it for the player
            case PLAYBACK_STATE_PREPARED:
            case PLAYBACK_STATE_PAUSED:
                //@formatter:off
                final int result = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                          mAudioManager.requestAudioFocus(mAudioFocusRequest)
                        : mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                //@formatter:on
                switch (result) {
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Failed to request audio focus");
                        }
                        // Starts to play video even if the audio focus is not gained, but it is best
                        // not to happen.
                    case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                        mPrivateFlags &= ~PFLAG_VIDEO_PAUSED_BY_USER;
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
                            seekToInternal(mSeekOnPlay);
                            mSeekOnPlay = 0;
                        }
                        onVideoStarted();
                        break;

                    case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                        // do nothing
                        break;
                }
                break;
        }
    }

    @Override
    public void pause(boolean fromUser) {
        if (isPlaying()) {
            pauseInternal(fromUser);
        }
    }

    /**
     * Similar to {@link #pause(boolean)}}, but does not check the playback state.
     */
    private void pauseInternal(boolean fromUser) {
        mMediaPlayer.pause();
        mPrivateFlags = mPrivateFlags & ~PFLAG_VIDEO_PAUSED_BY_USER
                | (fromUser ? PFLAG_VIDEO_PAUSED_BY_USER : 0);
        onVideoStopped();
    }

    @Override
    protected void closeVideoInternal(boolean fromUser) {
        if (mMediaPlayer != null && (mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) == 0) {
            mPrivateFlags |= PFLAG_VIDEO_IS_CLOSING;

            if (getPlaybackState() != PLAYBACK_STATE_COMPLETED) {
                mSeekOnPlay = getVideoProgress();
            }
            pause(fromUser);
            abandonAudioFocus();
            mMediaPlayer.release();
            mMediaPlayer = null;
            // Not clear the PFLAG_VIDEO_INFO_RESOLVED flag
            mPrivateFlags &= ~(PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY
                    | PFLAG_SEEKING
                    | PFLAG_BUFFERING);
            // Resets below to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;

            if (mSession != null) {
                mSession.setActive(false);
                mSession.release();
                mSession = null;
            }
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
    public void seekTo(int progress, boolean fromUser) {
        if (isPlaying()) {
            seekToInternal(progress);
        } else {
            mPrivateFlags |= PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED;
            mSeekOnPlay = progress;
            play(fromUser);
            mPrivateFlags &= ~PFLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED;
        }
    }

    /**
     * Similar to {@link #seekTo(int, boolean)}, but without check to the playing state.
     */
    private void seekToInternal(int progress) {
        if ((mPrivateFlags & PFLAG_BUFFERING) == 0) {
            showLoadingView(true);
        }
        mPrivateFlags |= PFLAG_SEEKING;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Precise seek with larger performance overhead compared to the default one.
            // Slow! Really slow!
            mMediaPlayer.seekTo(progress, MediaPlayer.SEEK_CLOSEST);
        } else {
            mMediaPlayer.seekTo(progress /*, MediaPlayer.SEEK_PREVIOUS_SYNC*/);
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
        if (getPlaybackState() == PLAYBACK_STATE_COMPLETED) {
            // 1. If the video completed and the MediaPlayer object was released, we would get 0.
            // 2. The playback position from the MediaPlayer, usually, is not the duration of the video
            //    but the position at the last video key-frame when the playback is finished, in the
            //    case of which instead, here is the duration returned to avoid progress inconsistencies.
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

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public void setPlaybackSpeed(float speed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != mPlaybackSpeed) {
            mUserPlaybackSpeed = speed;
            // When video is not playing or has no tendency of to be started, we prefer recording
            // the user request to forcing the player to start at that given speed.
            if (getPlaybackState() == PLAYBACK_STATE_PREPARING || isPlaying()) {
                PlaybackParams pp = mMediaPlayer.getPlaybackParams().allowDefaults();
                pp.setSpeed(speed);
                try {
                    mMediaPlayer.setPlaybackParams(pp);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    mUserPlaybackSpeed = mPlaybackSpeed;
                    return;
                }
                // If the above fails due to an unsupported playback speed, then our speed will
                // remain unchanged. This ensures the program runs steadily.
                mPlaybackSpeed = speed;
                super.setPlaybackSpeed(speed);
            }
        }
    }

    @Override
    protected boolean isPlayerCreated() {
        return mMediaPlayer != null;
    }

    @Override
    protected boolean onPlaybackCompleted() {
        final boolean closed = super.onPlaybackCompleted();
        if (closed) {
            // Since the playback completion state deters the pause(boolean) method from being called
            // within the closeVideoInternal(boolean) method, we need this extra step to add
            // the PFLAG_VIDEO_PAUSED_BY_USER flag into mPrivateFlags to denote that the user pauses
            // (closes) the video.
            mPrivateFlags |= PFLAG_VIDEO_PAUSED_BY_USER;
            onVideoStopped();
        }
        return closed;
    }
}