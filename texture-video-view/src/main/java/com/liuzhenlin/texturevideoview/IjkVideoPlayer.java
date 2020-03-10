/*
 * Created on 2020-3-7 2:36:03 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Messenger;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RestrictTo;

import com.google.android.material.snackbar.Snackbar;
import com.liuzhenlin.texturevideoview.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.texturevideoview.receiver.MediaButtonEventHandler;
import com.liuzhenlin.texturevideoview.receiver.MediaButtonEventReceiver;
import com.liuzhenlin.texturevideoview.utils.Utils;

import java.io.IOException;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * A sub implementation class of {@link VideoPlayer} to deal with the audio/video playback logic
 * related to the media player component through a {@link IjkMediaPlayer} object.
 *
 * @author 刘振林
 */
public class IjkVideoPlayer extends VideoPlayer {

    private static final String TAG = "IjkVideoPlayer";

    /**
     * Flag used to indicate that the volume of the video is auto-turned down by the system
     * when the player temporarily loses the audio focus.
     */
    private static final int $FLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY = 1 << 31;

    /**
     * Flag indicates that a position seek request happens when the video is not playing.
     */
    private static final int $FLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED = 1 << 30;

    /**
     * If true, IjkPlayer is moving the media to some specified time position
     */
    private static final int $FLAG_SEEKING = 1 << 29;

    /**
     * If true, IjkPlayer is temporarily pausing playback internally in order to buffer more data.
     */
    private static final int $FLAG_BUFFERING = 1 << 28;

    private IjkMediaPlayer mIjkPlayer;
    private Surface mSurface;

    /** Rotation degrees of the played video source */
    private int mVideoRotation;

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
                    if ((mInternalFlags & $FLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY) != 0) {
                        mInternalFlags &= ~$FLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                        mIjkPlayer.setVolume(1.0f, 1.0f);
                    }
                    play(false);
                    break;

                // Loss of audio focus of unknown duration.
                // This usually happens when the user switches to another audio/video application
                // that causes our view to stop playing, so the video can be thought of as
                // being paused/closed by the user.
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mVideoView != null && mVideoView.isInForeground()) {
                        // If the view is still in the foreground, pauses the video only.
                        pause(true);
                    } else {
                        // But if this occurs during background playback, we must close the video
                        // to release the resources associated with it.
                        closeVideoInternal(true);
                    }
                    break;

                // Temporarily lose the audio focus and will probably gain it again soon.
                // Must stop the video playback but no need for releasing the IjkPlayer here.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    pause(false);
                    break;

                // Temporarily lose the audio focus but the playback can continue.
                // The volume of the playback needs to be turned down.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mInternalFlags |= $FLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                    mIjkPlayer.setVolume(0.5f, 0.5f);
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

//    private static HttpProxyCacheServer sCacheServer;

//    private HttpProxyCacheServer getCacheServer() {
//        if (sCacheServer == null) {
//            sCacheServer = new HttpProxyCacheServer.Builder(mContext)
//                    .cacheDirectory(new File(getBaseVideoCacheDirectory(), "sm"))
//                    .maxCacheSize(DEFAULT_MAXIMUM_CACHE_SIZE)
//                    .build();
//        }
//        return sCacheServer;
//    }

    public IjkVideoPlayer(@NonNull Context context) {
        super(context);
    }

    @Override
    public void setVideoResourceId(@RawRes int resId) {
        Log.e(TAG, "",
                new UnsupportedOperationException("Play via raw resource id is not yet supported"));
    }

    @Override
    public void setVideoUri(@Nullable Uri uri) {
        final Uri oldUri = mVideoUri;
        final int videoRotation = mVideoRotation;
        mVideoRotation = 0;
        super.setVideoUri(uri);
        if (oldUri == uri) {
            mVideoRotation = videoRotation;
        }
    }

    @Override
    protected boolean isPlayerCreated() {
        return mIjkPlayer != null;
    }

    @Override
    protected void onVideoSurfaceChanged(@Nullable Surface surface) {
        if (mIjkPlayer != null) {
            mSurface = surface;
            mIjkPlayer.setSurface(surface);
        }
    }

    @Override
    protected void openVideoInternal(@Nullable Surface surface) {
        if (mIjkPlayer == null && mVideoUri != null
                && !(mVideoView != null && surface == null)
                && (mInternalFlags & $FLAG_VIDEO_PAUSED_BY_USER) == 0) {
            mSurface = surface;
            mIjkPlayer = new IjkMediaPlayer();
            resetIjkPlayerParams();
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                mMediaPlayer.setAudioAttributes(sDefaultAudioAttrs.getAudioAttributesV21());
//            } else {
//                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
//            }
            mIjkPlayer.setOnPreparedListener(mp -> {
                if (mVideoView != null) {
                    mVideoView.showLoadingView(false);
                }
                if ((mInternalFlags & $FLAG_VIDEO_DURATION_DETERMINED) == 0) {
                    final int duration = (int) mp.getDuration();
                    onVideoDurationDetermined(duration == 0 ? UNKNOWN_DURATION : duration);
                    mInternalFlags |= $FLAG_VIDEO_DURATION_DETERMINED;
                }
                setPlaybackState(PLAYBACK_STATE_PREPARED);
                play(false);
            });
            mIjkPlayer.setOnVideoSizeChangedListener((mp, width, height, sarNum, sarDen) -> {
                final boolean videoSwapped = mVideoRotation == 90 || mVideoRotation == 270;
                if (videoSwapped) {
                    int swap = width;
                    //noinspection SuspiciousNameCombination
                    width = height;
                    height = swap;
                }
                final float pixelWidthHeightRatio = (float) sarNum / sarDen;
                if (pixelWidthHeightRatio > 0.0f && pixelWidthHeightRatio != 1.0f) {
                    width = (int) (width * pixelWidthHeightRatio + 0.5f);
                }
                onVideoSizeChanged(width, height);
            });
            mIjkPlayer.setOnSeekCompleteListener(mp -> {
                mInternalFlags &= ~$FLAG_SEEKING;
                if ((mInternalFlags & $FLAG_BUFFERING) == 0) {
                    if (mVideoView != null)
                        mVideoView.showLoadingView(false);
                }
                onVideoSeekProcessed();
            });
            mIjkPlayer.setOnInfoListener((mp, what, extra) -> {
                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                        mInternalFlags |= $FLAG_BUFFERING;
                        if ((mInternalFlags & $FLAG_SEEKING) == 0) {
                            if (mVideoView != null)
                                mVideoView.showLoadingView(true);
                        }
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                        mInternalFlags &= ~$FLAG_BUFFERING;
                        if ((mInternalFlags & $FLAG_SEEKING) == 0) {
                            if (mVideoView != null)
                                mVideoView.showLoadingView(false);
                        }
                        break;
                    case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                        mVideoRotation = extra;
                        if (extra == 90 || extra == 270) {
                            final int videoWidth = mp.getVideoWidth();
                            final int videoHeight = mp.getVideoHeight();
                            if (videoWidth != 0 || videoHeight != 0) {
                                //noinspection SuspiciousNameCombination
                                onVideoSizeChanged(videoHeight, videoWidth);
                            }
                        }
                        break;
                }
                return false;
            });
            mIjkPlayer.setOnBufferingUpdateListener((mp, percent)
                    -> mBuffering = clampedPositionMs((int) (mVideoDuration * percent / 100f + 0.5f)));
            mIjkPlayer.setOnErrorListener((mp, what, extra) -> {
                if (InternalConsts.DEBUG) {
                    Log.e(TAG, "Error occurred while playing video:" +
                            " what= " + what + "; extra= " + extra);
                }
                showVideoErrorMsg(extra);

                if (mVideoView != null) {
                    mVideoView.showLoadingView(false);
                }
                final boolean playing = isPlaying();
                setPlaybackState(PLAYBACK_STATE_ERROR);
                if (playing) {
                    pauseInternal(false);
                }
                return true;
            });
            mIjkPlayer.setOnCompletionListener(mp -> {
                if (isSingleVideoLoopPlayback()) {
                    mp.start();
                    onVideoRepeat();
                } else {
                    onPlaybackCompleted();
                }
            });
            startVideo();

            MediaButtonEventReceiver.setMediaButtonEventHandler(
                    new MediaButtonEventHandler(new Messenger(new MsgHandler(this))));
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
            case IMediaPlayer.MEDIA_ERROR_IO:
                stringRes = R.string.failedToLoadThisVideo;
                break;
            case IMediaPlayer.MEDIA_ERROR_UNSUPPORTED:
            case IMediaPlayer.MEDIA_ERROR_MALFORMED:
                stringRes = R.string.videoInThisFormatIsNotSupported;
                break;
            case IMediaPlayer.MEDIA_ERROR_TIMED_OUT:
                stringRes = R.string.loadTimeout;
                break;
            default:
                stringRes = R.string.unknownErrorOccurredWhenVideoIsPlaying;
                break;
        }
        if (mVideoView != null) {
            Utils.showUserCancelableSnackbar(mVideoView, stringRes, Snackbar.LENGTH_SHORT);
        } else {
            Toast.makeText(mContext, stringRes, Toast.LENGTH_SHORT).show();
        }
    }

    private void startVideo() {
        if (mVideoUri != null) {
            try {
                mIjkPlayer.setDataSource(mContext, mVideoUri);
//                final String url = mVideoUri.toString();
//                if (URLUtils.isNetworkUrl(url)) {
//                    mIjkPlayer.setDataSource(getCacheServer().getProxyUrl(url));
//                } else {
//                    mIjkPlayer.setDataSource(mContext, mVideoUri);
//                }
                if (mVideoView != null) {
                    mVideoView.showLoadingView(true);
                }
                setPlaybackState(PLAYBACK_STATE_PREPARING);
                mIjkPlayer.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
                showVideoErrorMsg(IMediaPlayer.MEDIA_ERROR_IO);
                if (mVideoView != null) {
                    mVideoView.showLoadingView(false); // in case it is already showing
                }
                setPlaybackState(PLAYBACK_STATE_ERROR);
            }
        } else {
            if (mVideoView != null) {
                mVideoView.showLoadingView(false);
            }
            setPlaybackState(PLAYBACK_STATE_IDLE);
        }
        if (mVideoView != null) {
            mVideoView.cancelDraggingVideoSeekBar();
        }
    }

    private void resetIjkPlayerParams() {
        mIjkPlayer.setSurface(mSurface);
        mIjkPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        // Add hw acceleration media options to use hw decoder
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
        // Enable accurate seek & fast seek
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);
        mIjkPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
//        mIjkPlayer.setLooping(isSingleVideoLoopPlayback());
        if (mUserPlaybackSpeed != mPlaybackSpeed) {
            setPlaybackSpeed(mUserPlaybackSpeed);
        }
    }

    private void resetIjkPlayer() {
        mIjkPlayer.setSurface(null);
        mIjkPlayer.stop();
        mIjkPlayer.reset();
        resetIjkPlayerParams();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>NOTE: </strong> If this method is called during the video being closed, it does
     * nothing other than setting the playback state to {@link #PLAYBACK_STATE_UNDEFINED}, so as
     * not to suppress the next call to the {@link #openVideo())} method if the current playback state
     * is {@link #PLAYBACK_STATE_COMPLETED}, and the state is usually needed to be updated in
     * this call, too. Thus for all of the above reasons, it is the best to switch over to.
     */
    @Override
    public void restartVideo() {
        // First, resets mSeekOnPlay to 0 in case the IjkPlayer object is (being) released.
        // This ensures the video to be started at its beginning position the next time it resumes.
        mSeekOnPlay = 0;
        if (mIjkPlayer != null) {
            if ((mInternalFlags & $FLAG_VIDEO_IS_CLOSING) != 0) {
                setPlaybackState(PLAYBACK_STATE_UNDEFINED);
            } else {
                // Not clear the $FLAG_VIDEO_DURATION_DETERMINED flag
                mInternalFlags &= ~($FLAG_VIDEO_PAUSED_BY_USER
                        | $FLAG_CAN_GET_ACTUAL_POSITION_FROM_PLAYER
                        | $FLAG_SEEKING
                        | $FLAG_BUFFERING);
                pause(false);
                // Resets below to prepare for the next resume of the video player
                mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
                mBuffering = 0;
                resetIjkPlayer();
                startVideo();
            }
        }
    }

    @Override
    public void play(boolean fromUser) {
        if ((mInternalFlags & $FLAG_VIDEO_IS_CLOSING) != 0) {
            // In case the video playback is closing
            return;
        }

        final int playbackState = getPlaybackState();

        if (mIjkPlayer == null) {
            // Opens the video only if this is a user request
            if (fromUser) {
                // If the video playback finished, skip to the next video if possible
                if (playbackState == PLAYBACK_STATE_COMPLETED && !isSingleVideoLoopPlayback() &&
                        skipToNextIfPossible() && mIjkPlayer != null) {
                    return;
                }

                mInternalFlags &= ~$FLAG_VIDEO_PAUSED_BY_USER;
                openVideo(true);
            } else {
                Log.e(TAG, "Cannot start playback programmatically before the video is opened.");
            }
            return;
        }

        if (!fromUser && (mInternalFlags & $FLAG_VIDEO_PAUSED_BY_USER) != 0) {
            Log.e(TAG, "Cannot start playback programmatically after it was paused by user.");
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
                mInternalFlags &= ~($FLAG_SEEKING | $FLAG_BUFFERING);
                mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
                mBuffering = 0;
                if ((mInternalFlags & $FLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED) == 0) {
                    // Record the current playback position only if there is no external program code
                    // requesting a position seek in this case.
                    mSeekOnPlay = getVideoProgress();
                }
                mInternalFlags &= ~$FLAG_CAN_GET_ACTUAL_POSITION_FROM_PLAYER;
                resetIjkPlayer();
                startVideo();
                break;

            case PLAYBACK_STATE_COMPLETED:
                if (!isSingleVideoLoopPlayback() &&
                        skipToNextIfPossible() && getPlaybackState() != PLAYBACK_STATE_COMPLETED) {
                    break;
                }
                // Starts the video only if we have prepared it for the player
            case PLAYBACK_STATE_PREPARED:
            case PLAYBACK_STATE_PAUSED:
                //@formatter:off
                final int result = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                      ? mAudioManager.requestAudioFocus(mAudioFocusRequest)
                      : mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                              AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                //@formatter:on
                switch (result) {
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        if (InternalConsts.DEBUG) {
                            Log.w(TAG, "Failed to request audio focus");
                        }
                        // Starts to play video even if the audio focus is not gained, but it is
                        // best not to happen.
                    case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                        mIjkPlayer.start();
                        // Position seek each time works correctly only if the player engine is started
                        if (mSeekOnPlay != 0) {
                            seekToInternal(mSeekOnPlay);
                            mSeekOnPlay = 0;
                        }
                        // Ensure the player's volume is at its maximum
                        if ((mInternalFlags & $FLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY) != 0) {
                            mInternalFlags &= ~$FLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                            mIjkPlayer.setVolume(1.0f, 1.0f);
                        }
                        mInternalFlags &= ~$FLAG_VIDEO_PAUSED_BY_USER;
                        mInternalFlags |= $FLAG_CAN_GET_ACTUAL_POSITION_FROM_PLAYER;
                        onVideoStarted();

                        // Register MediaButtonEventReceiver every time the video starts, which
                        // will ensure it to be the sole receiver of MEDIA_BUTTON intents
                        mAudioManager.registerMediaButtonEventReceiver(sMediaButtonEventReceiverComponent);
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
        mIjkPlayer.pause();
        mInternalFlags = mInternalFlags & ~$FLAG_VIDEO_PAUSED_BY_USER
                | (fromUser ? $FLAG_VIDEO_PAUSED_BY_USER : 0);
        onVideoStopped();
    }

    @Override
    protected void closeVideoInternal(boolean fromUser) {
        if (mIjkPlayer != null && (mInternalFlags & $FLAG_VIDEO_IS_CLOSING) == 0) {
            mInternalFlags |= $FLAG_VIDEO_IS_CLOSING;

            if (getPlaybackState() != PLAYBACK_STATE_COMPLETED) {
                mSeekOnPlay = getVideoProgress();
            }
            pause(fromUser);
            abandonAudioFocus();
            releaseIjkPlayer();
            // Not clear the $FLAG_VIDEO_DURATION_DETERMINED flag
            mInternalFlags &= ~($FLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY
                    | $FLAG_SEEKING
                    | $FLAG_BUFFERING
                    | $FLAG_CAN_GET_ACTUAL_POSITION_FROM_PLAYER);
            // Resets below to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;

            mHeadsetEventsReceiver.unregister();
            mHeadsetEventsReceiver = null;

            mInternalFlags &= ~$FLAG_VIDEO_IS_CLOSING;

            if (mVideoView != null)
                mVideoView.showLoadingView(false);
        }
        if (mVideoView != null) {
            mVideoView.cancelDraggingVideoSeekBar();
        }
    }

    private void releaseIjkPlayer() {
        mSurface = null;
        mIjkPlayer.setSurface(null);
        mIjkPlayer.stop();
        mIjkPlayer.release();
        mIjkPlayer = null;
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        } else {
            mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
        }
    }

    @Override
    public void seekTo(int positionMs, boolean fromUser) {
        positionMs = clampedPositionMs(positionMs);
        if (isPlaying()) {
            seekToInternal(positionMs);
        } else {
            mInternalFlags |= $FLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED;
            mSeekOnPlay = positionMs;
            play(fromUser);
            mInternalFlags &= ~$FLAG_SEEK_POSITION_WHILE_VIDEO_PAUSED;
        }
    }

    /**
     * Similar to {@link #seekTo(int, boolean)}, but without check to the playing state.
     */
    private void seekToInternal(int positionMs) {
        if ((mInternalFlags & $FLAG_BUFFERING) == 0) {
            if (mVideoView != null)
                mVideoView.showLoadingView(true);
        }
        mInternalFlags |= $FLAG_SEEKING;
        mIjkPlayer.seekTo(positionMs);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // Precise seek with larger performance overhead compared to the default one.
//            // Slow! Really slow!
//            mMediaPlayer.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST);
//        } else {
//            mMediaPlayer.seekTo(positionMs /*, MediaPlayer.SEEK_PREVIOUS_SYNC*/);
//        }
    }

    @Override
    public int getVideoProgress() {
        if (getPlaybackState() == PLAYBACK_STATE_COMPLETED) {
            // 1. If the video completed and the IjkPlayer object was released, we would get 0.
            // 2. The playback position from the IjkPlayer, usually, is not the duration of the video
            // but the position at the last video key-frame when the playback is finished, in the
            // case of which instead, here is the duration returned to avoid progress inconsistencies.
            return mVideoDuration;
        }
        if ((mInternalFlags & $FLAG_CAN_GET_ACTUAL_POSITION_FROM_PLAYER) != 0) {
            return clampedPositionMs((int) mIjkPlayer.getCurrentPosition());
        }
        return mSeekOnPlay;
    }

    @Override
    public int getVideoBufferProgress() {
        return mBuffering;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public void setPlaybackSpeed(float speed) {
        if (speed != mPlaybackSpeed) {
            mUserPlaybackSpeed = speed;
            if (mIjkPlayer != null) {
                mIjkPlayer.setSpeed(speed);
                super.setPlaybackSpeed(speed);
            }
        }
    }

//    @Override
//    public void setSingleVideoLoopPlayback(boolean looping) {
//        if (looping != isSingleVideoLoopPlayback()) {
//            if (mIjkPlayer != null) {
//                mIjkPlayer.setLooping(looping);
//            }
//            super.setSingleVideoLoopPlayback(looping);
//        }
//    }

    @Override
    protected boolean onPlaybackCompleted() {
        final boolean closed = super.onPlaybackCompleted();
        if (closed) {
            // Since the playback completion state deters the pause(boolean) method from being called
            // within the closeVideoInternal(boolean) method, we need this extra step to add
            // the $FLAG_VIDEO_PAUSED_BY_USER flag into mInternalFlags to denote that the user pauses
            // (closes) the video.
            mInternalFlags |= $FLAG_VIDEO_PAUSED_BY_USER;
            onVideoStopped();
        }
        return closed;
    }
}
