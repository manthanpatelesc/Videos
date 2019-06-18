/*
 * Created on 5/27/19 8:55 AM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.content.Context;
import android.net.Uri;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

/**
 * @author 刘振林
 */
/* package-private */ final class VideoClipPlayer {
    private final Context mContext;
    private final Surface mSurface;
    private final MediaSource mMediaSource;

    private SimpleExoPlayer mExoPlayer;

    private int mSeekOnPlay;

    public VideoClipPlayer(@NonNull Context context, @NonNull Surface surface,
                           @NonNull Uri videoUri, @NonNull String userAgent,
                           @Nullable AdsMediaSource.MediaSourceFactory mediaSourceFactory) {
        mContext = context;
        mSurface = surface;
        if (mediaSourceFactory == null) {
            mediaSourceFactory = new ProgressiveMediaSource.Factory(
                    new DefaultDataSourceFactory(context, userAgent));
        }
        mMediaSource = mediaSourceFactory.createMediaSource(videoUri);
    }

    public boolean isPlaying() {
        return mExoPlayer != null && mExoPlayer.getPlayWhenReady();
    }

    public void play() {
        if (mExoPlayer != null) {
            mExoPlayer.setPlayWhenReady(true);
            if (mSeekOnPlay != 0) {
                mExoPlayer.seekTo(mSeekOnPlay);
                mSeekOnPlay = 0;
            }
        }
    }

    public void pause() {
        if (mExoPlayer != null) {
            mExoPlayer.setPlayWhenReady(false);
        }
    }

    public void seekTo(int position) {
        if (mExoPlayer != null) {
            mExoPlayer.seekTo(position);
        }
    }

    public int getCurrentPosition() {
        if (mExoPlayer != null) {
            return (int) mExoPlayer.getCurrentPosition();
        }
        return mSeekOnPlay;
    }

    public int getDuration() {
        if (mExoPlayer != null) {
            return (int) mExoPlayer.getDuration();
        }
        return (int) C.TIME_UNSET;
    }

    public void init() {
        if (mExoPlayer == null) {
            mExoPlayer = ExoPlayerFactory.newSimpleInstance(mContext);
            mExoPlayer.setVideoSurface(mSurface);
            mExoPlayer.setAudioAttributes(AbsTextureVideoView.sDefaultAudioAttrs, true);
            mExoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            mExoPlayer.prepare(mMediaSource);
        }
    }

    public void release() {
        if (mExoPlayer != null) {
            mSeekOnPlay = (int) mExoPlayer.getCurrentPosition();
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }
}
