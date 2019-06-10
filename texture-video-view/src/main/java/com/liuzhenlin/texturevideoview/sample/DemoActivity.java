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

import com.liuzhenlin.texturevideoview.AbsTextureVideoView;
import com.liuzhenlin.texturevideoview.R;
import com.liuzhenlin.texturevideoview.utils.FileUtils;

import java.io.File;

/**
 * @author 刘振林
 */
public class DemoActivity extends AppCompatActivity {
    private AbsTextureVideoView mVideoView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        mVideoView = findViewById(R.id.video_view);
        mVideoView.setTitle("Simplest Playback Demo for AbsTextureVideoView");
        mVideoView.setVideoUri(getIntent().getData());
        // Sets fullscreenMode to true only for demonstration purpose, which, however, should normally
        // not be set unless the onChangeViewMode() method is called for the EventListener to perform
        // some changes in the layout of our Activity as we see fit.
        mVideoView.setFullscreenMode(true, 0);
        mVideoView.setVideoListener(new AbsTextureVideoView.VideoListener() {
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
        mVideoView.setEventListener(new AbsTextureVideoView.EventListener() {
            @Override
            public void onSkipToPrevious() {
                // no-op
            }

            @Override
            public void onSkipToNext() {
                // no-op
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
        mVideoView.setOpCallback(new AbsTextureVideoView.OpCallback() {
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
        mVideoView.openVideo();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mVideoView.closeVideo();
    }

    @Override
    public void onBackPressed() {
        if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }
}
