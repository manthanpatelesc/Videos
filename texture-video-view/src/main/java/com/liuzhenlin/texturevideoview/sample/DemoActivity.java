/*
 * Created on 5/18/19 11:44 AM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview.sample;

import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.liuzhenlin.texturevideoview.IVideoPlayer;
import com.liuzhenlin.texturevideoview.R;
import com.liuzhenlin.texturevideoview.SystemVideoPlayer;
import com.liuzhenlin.texturevideoview.TextureVideoView;
import com.liuzhenlin.texturevideoview.VideoPlayer;
import com.liuzhenlin.texturevideoview.utils.FileUtils;

import java.io.File;

/**
 * @author 刘振林
 */
public class DemoActivity extends AppCompatActivity {
    private TextureVideoView mVideoView;
    private VideoPlayer mVideoPlayer;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        // First, interrelates TextureVideoView with VideoPlayer
        mVideoView = findViewById(R.id.videoview);
        mVideoPlayer = new SystemVideoPlayer(this);
        mVideoPlayer.setVideoView(mVideoView);
        mVideoView.setVideoPlayer(mVideoPlayer);

        mVideoView.setTitle("Simplest Playback Demo for TextureVideoView");
        mVideoPlayer.setVideoUri(getIntent().getData());
        // Sets fullscreenMode to true only for demonstration purpose, which, however, should
        // normally not be set unless the onViewModeChange() method is called for the EventListener
        // to perform some changes in the layout of our Activity as we see fit.
        mVideoView.setFullscreenMode(true, 0);
        mVideoPlayer.addVideoListener(new IVideoPlayer.VideoListener() {
            @Override
            public void onVideoStarted() {
                // no-op
            }

            @Override
            public void onVideoStopped() {
                // no-op
            }

            @Override
            public void onVideoSizeChanged(int oldWidth, int oldHeight, int width, int height) {
                // no-op
            }
        });
        mVideoPlayer.setOnSkipPrevNextListener(new VideoPlayer.OnSkipPrevNextListener() {
            @Override
            public void onSkipToPrevious() {
                // no-op
            }

            @Override
            public void onSkipToNext() {
                // no-op
            }
        });
        mVideoView.setEventListener(new TextureVideoView.EventListener() {
            @Override
            public void onPlayerChange(@Nullable VideoPlayer videoPlayer) {
                mVideoPlayer = videoPlayer;
            }

            @Override
            public void onReturnClicked() {
                finish();
            }

            @Override
            public void onViewModeChange(int oldMode, int newMode, boolean layoutMatches) {
                // no-op
            }

            @Override
            public void onShareVideo() {
                // Place the code describing how to share the video here
            }

            @Override
            public void onShareCapturedVideoPhoto(@NonNull File photo) {
                FileUtils.shareFile(DemoActivity.this,
                        getPackageName() + ".provider", photo, "image/*");
            }
        });
        mVideoView.setOpCallback(new TextureVideoView.OpCallback() {
            @NonNull
            @Override
            public Window getWindow() {
                return DemoActivity.this.getWindow();
            }

            // Optional, just returns null to use the default output directory
            // (the primary external storage directory concatenating with this application name).
            @Nullable
            @Override
            public String getFileOutputDirectory() {
                return null;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mVideoPlayer.openVideo();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mVideoPlayer.closeVideo();
    }

    @Override
    public void onBackPressed() {
        if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }
}
