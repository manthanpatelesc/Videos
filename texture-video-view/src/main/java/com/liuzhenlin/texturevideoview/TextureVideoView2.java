/*
 * Created on 5/7/19 9:45 AM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.liuzhenlin.texturevideoview.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.texturevideoview.utils.TimeUtil;

/**
 * A sub implementation class of {@link AbsTextureVideoView} to deal with the audio/video playback
 * logic related to the media player component through an {@link ExoPlayer} object.
 *
 * @author 刘振林
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class TextureVideoView2 extends AbsTextureVideoView {

    private static final String TAG = "TextureVideoView2";

    private SimpleExoPlayer mExoPlayer;
    /* package-private */ AdsMediaSource.MediaSourceFactory mMediaSourceFactory;

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
                // Must stop the video playback but no need for releasing the ExoPlayer here.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    pause(false);
                    break;

                // Temporarily lose the audio focus but the playback can continue.
                // The volume of the playback needs to be turned down.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    if (mExoPlayer != null) {
                        mExoPlayer.setVolume(0.5f);
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

    public TextureVideoView2(Context context) {
        this(context, null);
    }

    public TextureVideoView2(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextureVideoView2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @return the {@link AdsMediaSource.MediaSourceFactory} used for reading the media content
     */
    @Nullable
    public AdsMediaSource.MediaSourceFactory getMediaSourceFactory() {
        return mMediaSourceFactory;
    }

    /**
     * Sets a MediaSourceFactory for creating {@link MediaSource}s to play the provided
     * media stream content (if any) or `null` a {@link ProgressiveMediaSource.Factory} with
     * {@link DefaultDataSourceFactory} will be created to read the media(s).
     *
     * @param factory a subclass instance of {@link AdsMediaSource.MediaSourceFactory}
     */
    public void setMediaSourceFactory(@Nullable AdsMediaSource.MediaSourceFactory factory) {
        mMediaSourceFactory = factory;
    }

    /**
     * @return a user agent string based on the application name resolved from this view's
     * context object and the `exoplayer-core` library version, which can be used to create a
     * {@link DataSource.Factory} instance for the {@link AdsMediaSource.MediaSourceFactory} subclasses.
     */
    @NonNull
    public String getUserAgent() {
        return mUserAgent;
    }

    @Override
    public void setVideoResourceId(int resId) {
        setVideoPath(resId == 0 ? null : "rawresource:///" + resId);
    }

    @Override
    public void setVideoUri(@Nullable Uri uri) {
        if (!ObjectsCompat.equals(uri, mVideoUri)) {
            super.setVideoUri(uri);
            mVideoDuration = 0;
            mVideoDurationString = DEFAULT_STRING_VIDEO_DURATION;
            mPrivateFlags &= ~PFLAG_VIDEO_INFO_RESOLVED;
            if (mExoPlayer == null) {
                // Removes the PFLAG_VIDEO_PAUSED_BY_USER flag and resets mSeekOnPlay to 0 in case
                // the ExoPlayer was previously released and has not been initialized yet.
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
        if (mExoPlayer == null && mSurface != null && mVideoUri != null
                && (mPrivateFlags & PFLAG_VIDEO_PAUSED_BY_USER) == 0) {
            mExoPlayer = ExoPlayerFactory.newSimpleInstance(mContext);
            mExoPlayer.setVideoSurface(isPureAudioPlayback() ? null : mSurface);
            mExoPlayer.setAudioAttributes(sDefaultAudioAttrs);
            setPlaybackSpeed(mUserPlaybackSpeed);
            mExoPlayer.setRepeatMode(
                    isSingleVideoLoopPlayback() ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            mExoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    showLoadingView(playbackState == Player.STATE_BUFFERING);

                    switch (playbackState) {
                        case Player.STATE_READY:
                            if (getPlaybackState() == PLAYBACK_STATE_PREPARING) {
                                if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) == 0) {
                                    mVideoDuration = (int) mExoPlayer.getDuration();
                                    mVideoDurationString = TimeUtil.formatTimeByColon(mVideoDuration);
                                    mPrivateFlags |= PFLAG_VIDEO_INFO_RESOLVED;
                                }
                                setPlaybackState(PLAYBACK_STATE_PREPARED);
                                play(false);
                            }
                            break;

                        case Player.STATE_ENDED:
                            // For an unknown reason, the duration got from the ExoPlayer usually is
                            // one millisecond smaller than the actual one.
                            mVideoDuration = (int) mExoPlayer.getCurrentPosition();
                            onPlaybackCompleted();
                            break;
                    }
                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    if (BuildConfig.DEBUG) {
                        error.printStackTrace();
                    }
                    final int resId;
                    if (error.type == ExoPlaybackException.TYPE_SOURCE) {
                        resId = R.string.failedToLoadThisVideo;
                    } else {
                        resId = R.string.unknownErrorOccurredWhenVideoIsPlaying;
                    }
                    Toast.makeText(mContext, resId, Toast.LENGTH_SHORT).show();

                    final boolean playing = isPlaying();
                    setPlaybackState(PLAYBACK_STATE_ERROR);
                    if (playing) {
                        pauseInternal(false);
                    }
                }
            });
            mExoPlayer.addVideoListener(new com.google.android.exoplayer2.video.VideoListener() {
                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    TextureVideoView2.this.onVideoSizeChanged(width, height);
                }
            });
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

    private void startVideo() {
        if (mVideoUri != null) {
            if (mMediaSourceFactory == null) {
                mMediaSourceFactory = new ProgressiveMediaSource.Factory(
                        new DefaultDataSourceFactory(mContext, mUserAgent));
            }
            setPlaybackState(PLAYBACK_STATE_PREPARING);
            mExoPlayer.prepare(mMediaSourceFactory.createMediaSource(mVideoUri));
        } else {
            setPlaybackState(PLAYBACK_STATE_IDLE);
            mExoPlayer.stop(true);
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
        // First, resets mSeekOnPlay to 0 in case the ExoPlayer object is (being) released.
        // This ensures the video to be started at its beginning position the next time it resumes.
        mSeekOnPlay = 0;
        if (mExoPlayer != null) {
            if ((mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) != 0) {
                setPlaybackState(PLAYBACK_STATE_UNDEFINED);
            } else {
                // Not clear the PFLAG_VIDEO_INFO_RESOLVED flag
                mPrivateFlags &= ~PFLAG_VIDEO_PAUSED_BY_USER;
                pause(false);
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

        if (mExoPlayer == null) {
            // Opens the video only if this is a user request
            if (fromUser) {
                // If the video playback finished, skip to the next video if possible
                if (playbackState == PLAYBACK_STATE_COMPLETED &&
                        skipToNextIfPossible() && mExoPlayer != null) {
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
                setPlaybackState(PLAYBACK_STATE_PREPARING);
                mExoPlayer.retry();
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
                        // Ensure the player's volume is at its maximum
                        if (mExoPlayer.getVolume() != 1.0f) {
                            mExoPlayer.setVolume(1.0f);
                        }
                        mExoPlayer.setPlayWhenReady(true);
                        if (mSeekOnPlay != 0) {
                            mExoPlayer.seekTo(mSeekOnPlay);
                            mSeekOnPlay = 0;
                        } else if (playbackState == PLAYBACK_STATE_COMPLETED) {
                            mExoPlayer.seekToDefaultPosition();
                        }
                        mPrivateFlags &= ~PFLAG_VIDEO_PAUSED_BY_USER;
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
        mExoPlayer.setPlayWhenReady(false);
        mPrivateFlags = mPrivateFlags & ~PFLAG_VIDEO_PAUSED_BY_USER
                | (fromUser ? PFLAG_VIDEO_PAUSED_BY_USER : 0);
        onVideoStopped();
    }

    @Override
    protected void closeVideoInternal(boolean fromUser) {
        if (mExoPlayer != null && (mPrivateFlags & PFLAG_VIDEO_IS_CLOSING) == 0) {
            mPrivateFlags |= PFLAG_VIDEO_IS_CLOSING;

            if (getPlaybackState() != PLAYBACK_STATE_COMPLETED) {
                mSeekOnPlay = getVideoProgress();
            }
            pause(fromUser);
            abandonAudioFocus();
            mExoPlayer.release();
            mExoPlayer = null;
//            mMediaSourceFactory = null;
            // Resets the cached playback speed to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;

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
            mExoPlayer.seekTo(progress);
        } else {
            mSeekOnPlay = progress;
            play(fromUser);
        }
    }

    @Override
    public int getVideoProgress() {
        if (getPlaybackState() == PLAYBACK_STATE_COMPLETED) {
            // If the video completed and the ExoPlayer object was released, we would get 0.
            return mVideoDuration;
        }
        if (mExoPlayer != null) {
            return (int) mExoPlayer.getCurrentPosition();
        }
        return mSeekOnPlay;
    }

    @Override
    public int getVideoBufferedProgress() {
        if (mExoPlayer != null) {
            return (int) mExoPlayer.getBufferedPosition();
        }
        return 0;
    }

    @Override
    public void setPureAudioPlayback(boolean audioOnly) {
        if (audioOnly != isPureAudioPlayback()) {
            if (mExoPlayer != null) {
                mExoPlayer.setVideoSurface(audioOnly ? null : mSurface);
            }
            super.setPureAudioPlayback(audioOnly);
        }
    }

    @Override
    public void setSingleVideoLoopPlayback(boolean looping) {
        if (looping != isSingleVideoLoopPlayback()) {
            if (mExoPlayer != null) {
                mExoPlayer.setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            }
            super.setSingleVideoLoopPlayback(looping);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public void setPlaybackSpeed(float speed) {
        if (speed != mPlaybackSpeed) {
            mUserPlaybackSpeed = speed;
            if (mExoPlayer != null) {
                mExoPlayer.setPlaybackParameters(new PlaybackParameters(speed));
                mPlaybackSpeed = speed;
                super.setPlaybackSpeed(speed);
            }
        }
    }

    @Override
    protected boolean isPlayerCreated() {
        return mExoPlayer != null;
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