/*
 * Created on 2017/09/24.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.Rational;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.liuzhenlin.slidingdrawerlayout.Utils;
import com.liuzhenlin.swipeback.SwipeBackActivity;
import com.liuzhenlin.swipeback.SwipeBackLayout;
import com.liuzhenlin.texturevideoview.AbsTextureVideoView;
import com.liuzhenlin.texturevideoview.VideoPlayerControl;
import com.liuzhenlin.texturevideoview.utils.FileUtils;
import com.liuzhenlin.texturevideoview.utils.SystemBarUtils;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.VideoDaoHelper;
import com.liuzhenlin.videos.model.Video;
import com.liuzhenlin.videos.utils.DisplayCutoutUtils;
import com.liuzhenlin.videos.utils.OSHelper;
import com.liuzhenlin.videos.utils.VideoUtils2;
import com.liuzhenlin.videos.utils.observer.OnOrientationChangeListener;
import com.liuzhenlin.videos.utils.observer.RotationObserver;
import com.liuzhenlin.videos.utils.observer.ScreenNotchSwitchObserver;
import com.liuzhenlin.videos.view.fragment.VideoOpsKt;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;

/**
 * @author 刘振林
 */
public class VideoActivity extends SwipeBackActivity {

    private static WeakReference<VideoActivity> sActivityInPiP;

    private View mStatusBarView;
    private AbsTextureVideoView mVideoView;
    private ImageView mLockUnlockOrientationButton;

    private RecyclerView mPlayList;
    private static final Object sRefreshVideoProgressPayload = new Object();
    private static final Object sHighlightSelectedItemIfExistsPayload = new Object();

    private Video[] mVideos;
    private int mVideoIndex = -1;
    private int mVideoWidth;
    private int mVideoHeight;

    private final int mStatusHeight = App.getInstance().getStatusHeight();
    private int mStatusHeightInLandscapeOfNotchSupportDevices;
    private int mNotchHeight;
    @Nullable
    private ScreenNotchSwitchObserver mNotchSwitchObserver;

    private int mPrivateFlags;

    private static final int PFLAG_SCREEN_NOTCH_SUPPORT = 1;
    private static final int PFLAG_SCREEN_NOTCH_SUPPORT_ON_EMUI = 1 << 1;
    private static final int PFLAG_SCREEN_NOTCH_SUPPORT_ON_MIUI = 1 << 2;
    private static final int PFLAG_SCREEN_NOTCH_HIDDEN = 1 << 3;

    private static final int PFLAG_DEVICE_SCREEN_ROTATION_ENABLED = 1 << 4;
    private static final int PFLAG_SCREEN_ORIENTATION_LOCKED = 1 << 5;
    private static final int PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE = 1 << 6;

    private static final int PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN = 1 << 7;

    private RotationObserver mRotationObserver;
    private OnOrientationChangeListener mOnOrientationChangeListener;
    private int mDeviceOrientation = SCREEN_ORIENTATION_PORTRAIT;
    private int mScreenOrientation = SCREEN_ORIENTATION_PORTRAIT;

    private Handler mHandler;

    private final Runnable mHideLockUnlockOrientationButtonRunnable = new Runnable() {
        @Override
        public void run() {
            showLockUnlockOrientationButton(false);
        }
    };
    private static final int DELAY_TIME_HIDE_LOCK_UNLOCK_ORIENTATION_BUTTON = 2500;

    /** The arguments to be used for Picture-in-Picture mode. */
    private PictureInPictureParams.Builder mPipParamsBuilder;

    /** A {@link BroadcastReceiver} to receive action item events from Picture-in-Picture mode. */
    private BroadcastReceiver mReceiver;

    /** Intent action for media controls from Picture-in-Picture mode. */
    private static final String ACTION_MEDIA_CONTROL = "media_control";

    /** Intent extra for media controls from Picture-in-Picture mode. */
    private static final String EXTRA_PIP_ACTION = "PiP_action";

    /** The intent extra value for play action. */
    private static final int PIP_ACTION_PLAY = 1;
    /** The intent extra value for pause action. */
    private static final int PIP_ACTION_PAUSE = 1 << 1;
    /** The intent extra value for pause action. */
    private static final int PIP_ACTION_FAST_FORWARD = 1 << 2;
    /** The intent extra value for pause action. */
    private static final int PIP_ACTION_FAST_REWIND = 1 << 3;

    /** The request code for play action PendingIntent. */
    private static final int REQUEST_PLAY = 1;
    /** The request code for pause action PendingIntent. */
    private static final int REQUEST_PAUSE = 2;
    /** The request code for fast forward action PendingIntent. */
    private static final int REQUEST_FAST_FORWARD = 3;
    /** The request code for fast rewind action PendingIntent. */
    private static final int REQUEST_FAST_REWIND = 4;

    private String mPlay;
    private String mPause;
    private String mFastForward;
    private String mFastRewind;
    private String mLockOrientation;
    private String mUnlockOrientation;
    private String mWatching;

    private View.OnLayoutChangeListener mOnPipLayoutChangeListener;
    private static final float RATIO_TOLERANCE_PIP_LAYOUT_SIZE = 5.0f / 3.0f;
    private static float sPipRatioOfProgressHeightToVideoSize;

    private ProgressBar mVideoProgressInPiP;
    private static int sPipProgressMinHeight;
    private static int sPipProgressMaxHeight;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private RefreshVideoProgressInPiPTask mRefreshVideoProgressInPiPTask;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private final class RefreshVideoProgressInPiPTask {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final int progress = mVideoView.getVideoProgress();
                if (mVideoView.isPlaying()) {
                    mHandler.postDelayed(this, 1000 - progress % 1000);
                }
                mVideoProgressInPiP.setSecondaryProgress(mVideoView.getVideoBufferedProgress());
                mVideoProgressInPiP.setProgress(progress);
            }
        };

        void execute() {
            cancel();
            runnable.run();
        }

        void cancel() {
            mHandler.removeCallbacks(runnable);
        }
    }

    @Nullable
    @Override
    public Activity getPreviousActivity() {
        return App.getActivityByClassName(MainActivity.class.getName());
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mLockOrientation = getString(R.string.lockScreenOrientation);
        mUnlockOrientation = getString(R.string.unlockScreenOrientation);
        mWatching = getString(R.string.watching);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPlay = getString(R.string.play);
            mPause = getString(R.string.pause);
            mFastForward = getString(R.string.fastForward);
            mFastRewind = getString(R.string.fastRewind);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (canInitVideos(savedInstanceState)) {
            setContentView(R.layout.activity_video);
            initViews(savedInstanceState);
        } else {
            Toast.makeText(this, R.string.cannotPlayThisVideo, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private boolean canInitVideos(Bundle savedInstanceState) {
        final boolean stateRestore = savedInstanceState != null;
        Intent intent = getIntent();
        Parcelable[] parcelables = intent.getParcelableArrayExtra(Consts.KEY_VIDEOS);
        if (parcelables != null) {
            final int length = parcelables.length;
            if (length > 0) {
                mVideos = new Video[length];
                for (int i = 0; i < length; i++) {
                    Video video = (Video) parcelables[i];
                    if (stateRestore) {
                        video.setProgress(VideoDaoHelper.getInstance(this).getVideoProgress(video.getId()));
                    }
                    mVideos[i] = video;
                }
                if (stateRestore) {
                    mVideoIndex = savedInstanceState.getInt(KEY_VIDEO_INDEX);
                } else {
                    mVideoIndex = intent.getIntExtra(Consts.KEY_SELECTION, 0);
                    if (mVideoIndex < 0 || mVideoIndex >= length) {
                        mVideoIndex = 0;
                    }
                }
                return true;
            }
        } else {
            Video video = intent.getParcelableExtra(Consts.KEY_VIDEO);
            if (video == null) {
                // 解析视频地址
                Uri uri = intent.getData();
                if (uri != null) {
                    final String path = FileUtils.UriResolver.getPath(this, uri);
                    if (path != null) {
                        video = VideoDaoHelper.getInstance(this).queryVideoByPath(path);
                        if (video == null) {
                            video = new Video();
                            video.setId(Consts.NO_ID);
                            video.setName(FileUtils.getFileNameFromFilePath(path));
                            video.setPath(path);
                        }
                    }
                }
            }
            if (video != null) {
                if (stateRestore) {
                    video.setProgress(VideoDaoHelper.getInstance(this).getVideoProgress(video.getId()));
                }
                mVideos = new Video[]{video};
                mVideoIndex = stateRestore ? savedInstanceState.getInt(KEY_VIDEO_INDEX) : 0;
                return true;
            }
        }
        return false;
    }

    private void initViews(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mStatusBarView = findViewById(R.id.view_statusBar);
            ViewGroup.LayoutParams lp = mStatusBarView.getLayoutParams();
            if (lp.height != mStatusHeight) {
                lp.height = mStatusHeight;
                mStatusBarView.setLayoutParams(lp);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVideoProgressInPiP = findViewById(R.id.pbInPiP_videoProgress);
            }
        }

        mLockUnlockOrientationButton = findViewById(R.id.bt_lockUnlockOrientation);
        mLockUnlockOrientationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setScreenOrientationLocked((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_LOCKED) == 0);
            }
        });
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(KEY_IS_SCREEN_ORIENTATION_LOCKED, false)) {
                setScreenOrientationLocked(true);
            }
            mDeviceOrientation = savedInstanceState.getInt(
                    KEY_DEVICE_ORIENTATION, SCREEN_ORIENTATION_PORTRAIT);
            mScreenOrientation = savedInstanceState.getInt(
                    KEY_SCREEN_ORIENTATION, SCREEN_ORIENTATION_PORTRAIT);
            mStatusHeightInLandscapeOfNotchSupportDevices = savedInstanceState.getInt(
                    KEY_STATUS_HEIGHT_IN_LANDSCAPE_OF_NOTCH_SUPPORT_DEVICES, 0);
        }

        mVideoView = findViewById(R.id.video_view);
        if (mVideos.length > 1) {
            mVideoView.setPlayListAdapter(new VideoEpisodesAdapter());
        }
        // 确保列表滚动到所播放视频的位置
        if (savedInstanceState == null && mVideoIndex != 0) {
            notifyItemSelectionChanged(0, mVideoIndex, true);
        }
        setVideoToPlay(mVideoIndex);
        mVideoView.setVideoListener(new AbsTextureVideoView.VideoListener() {
            @Override
            public void onVideoStarted() {
                Video video = mVideos[mVideoIndex];
                final int progress = video.getProgress();
                if (progress > 0 && progress < video.getDuration()) {
                    mVideoView.seekTo(progress, false); // 恢复上次关闭此页面时播放到的位置
                    video.setProgress(0);
                }

                if (mVideoWidth == 0 && mVideoHeight == 0) {
                    mVideoWidth = mVideoView.getVideoWidth();
                    mVideoHeight = mVideoView.getVideoHeight();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mVideoProgressInPiP.setMax(mVideoView.getVideoDuration());

                    if (isInPictureInPictureMode()) {
                        // This Activity is recreated after killed by the System
                        if (mPipParamsBuilder == null) {
                            mPipParamsBuilder = new PictureInPictureParams.Builder();
                            onPictureInPictureModeChanged(true);
                        } else {
                            // We are playing the video now. In PiP mode, we want to show several
                            // action items to fast rewind, pause and fast forward the video.
                            updatePictureInPictureActions(PIP_ACTION_FAST_REWIND
                                    | PIP_ACTION_PAUSE | PIP_ACTION_FAST_FORWARD);

                            if (mRefreshVideoProgressInPiPTask != null) {
                                mRefreshVideoProgressInPiPTask.execute();
                            }
                        }
                    }
                }
            }

            @Override
            public void onVideoStopped() {
                // The video stopped or reached the playlist end. In PiP mode, we want to show some
                // action items to fast rewind, play and fast forward the video.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode()) {
                    int actions = PIP_ACTION_FAST_REWIND | PIP_ACTION_PLAY;
                    if (!(mVideoView.getPlaybackState() == VideoPlayerControl.PLAYBACK_STATE_COMPLETED
                            && !mVideoView.canSkipToNext())) {
                        actions |= PIP_ACTION_FAST_FORWARD;
                    }
                    updatePictureInPictureActions(actions);
                }
            }

            @Override
            public void onVideoSizeChanged(int oldWidth, int oldHeight, int width, int height) {
                mVideoWidth = width;
                mVideoHeight = height;

                if (width == 0 && height == 0) return;
                if (width >= height) {
                    mPrivateFlags &= ~PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE;
                    if (mScreenOrientation == SCREEN_ORIENTATION_PORTRAIT
                            && mVideoView.isInFullscreenMode()) {
                        mScreenOrientation = mDeviceOrientation == SCREEN_ORIENTATION_PORTRAIT
                                ? SCREEN_ORIENTATION_LANDSCAPE : mDeviceOrientation;
                        setRequestedOrientation(mScreenOrientation);
                        setFullscreenMode(true);
                    }
                } else {
                    mPrivateFlags |= PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE;
                    if (mScreenOrientation != SCREEN_ORIENTATION_PORTRAIT) {
                        mScreenOrientation = SCREEN_ORIENTATION_PORTRAIT;
                        setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
                        setFullscreenMode(true);
                    }
                }
            }
        });
        mVideoView.setEventListener(new AbsTextureVideoView.EventListener() {

            @Override
            public void onSkipToPrevious() {
                recordCurrVideoProgress();
                setVideoToPlay(--mVideoIndex);
                notifyItemSelectionChanged(mVideoIndex + 1, mVideoIndex, true);
            }

            @Override
            public void onSkipToNext() {
                recordCurrVideoProgress();
                setVideoToPlay(++mVideoIndex);
                notifyItemSelectionChanged(mVideoIndex - 1, mVideoIndex, true);
            }

            @Override
            public void onReturnClicked() {
                finish();
            }

            public void onViewModeChange(int oldMode, int newMode, boolean layoutMatches) {
                switch (newMode) {
                    case AbsTextureVideoView.VIEW_MODE_MINIMUM:
                        if (!layoutMatches
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                && mVideoWidth != 0 && mVideoHeight != 0) {
                            if (mPipParamsBuilder == null) {
                                mPipParamsBuilder = new PictureInPictureParams.Builder();
                            }
                            enterPictureInPictureMode(mPipParamsBuilder
                                    .setAspectRatio(new Rational(mVideoWidth, mVideoHeight))
                                    .build());
                        }
                        break;
                    case AbsTextureVideoView.VIEW_MODE_DEFAULT:
                        setFullscreenModeManually(false);
                        break;
                    case AbsTextureVideoView.VIEW_MODE_LOCKED_FULLSCREEN:
                    case AbsTextureVideoView.VIEW_MODE_VIDEO_STRETCHED_LOCKED_FULLSCREEN:
                        showLockUnlockOrientationButton(false);
                    case AbsTextureVideoView.VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN:
                    case AbsTextureVideoView.VIEW_MODE_FULLSCREEN:
                        setFullscreenModeManually(true);
                        break;
                }
            }

            @Override
            public void onShareVideo() {
                VideoOpsKt.shareVideo(VideoActivity.this, mVideos[mVideoIndex]);
            }

            @Override
            public void onShareCapturedVideoPhoto(@NonNull File photo) {
                FileUtils.shareFile(VideoActivity.this, App.getInstance().getAuthority(),
                        photo, "image/*");
            }
        });
        mVideoView.setOpCallback(new AbsTextureVideoView.OpCallback() {
            @NonNull
            @Override
            public Window getWindow() {
                return VideoActivity.this.getWindow();
            }

            @Nullable
            @Override
            public String getFileOutputDirectory() {
                return App.getAppDirectory();
            }
        });
    }

    private void setVideoToPlay(int videoIndex) {
        Video video = mVideos[videoIndex];
        mVideoView.setCanSkipToPrevious(videoIndex > 0);
        mVideoView.setCanSkipToNext(videoIndex < mVideos.length - 1);
        mVideoView.setVideoPath(video.getPath());
        mVideoView.setTitle(FileUtils.getFileTitleFromFileName(video.getName()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sActivityInPiP != null) {
            final VideoActivity activity = sActivityInPiP.get();
            if (activity != null && activity != this) {
                activity.unregisterReceiver(activity.mReceiver);
                activity.finish();
                // Clear reference
                sActivityInPiP.clear();
                sActivityInPiP = null;
            }
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPictureInPictureMode()) {
            if (mNotchSwitchObserver != null) {
                mNotchSwitchObserver.startObserver();
            }
            setAutoRotationEnabled(true);
        }
        mVideoView.openVideo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Android 4.4及以上版本，重新显示页面时状态栏会显示出来且不会自动隐藏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && mVideoView.isInFullscreenMode()) {
            showSystemBars(false);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Window window = getWindow();
        View decorView = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout dc = decorView.getRootWindowInsets().getDisplayCutout();
            if (dc != null) {
                mPrivateFlags |= PFLAG_SCREEN_NOTCH_SUPPORT;
                if (OSHelper.isEMUI()) {
                    mPrivateFlags |= PFLAG_SCREEN_NOTCH_SUPPORT_ON_EMUI;
                } else if (OSHelper.isMIUI()) {
                    mPrivateFlags |= PFLAG_SCREEN_NOTCH_SUPPORT_ON_MIUI;
                }
                mNotchHeight = dc.getSafeInsetTop();
                DisplayCutoutUtils.setLayoutInDisplayCutoutSinceP(window, true);
            }
        } else if (OSHelper.isEMUI()) {
            if (DisplayCutoutUtils.hasNotchInScreenForEMUI(this)) {
                mPrivateFlags |= PFLAG_SCREEN_NOTCH_SUPPORT | PFLAG_SCREEN_NOTCH_SUPPORT_ON_EMUI;
                mNotchHeight = DisplayCutoutUtils.getNotchSizeForEMUI(this)[1];
                DisplayCutoutUtils.setLayoutInDisplayCutoutForEMUI(window, true);
            }
        } else if (OSHelper.isColorOS()) {
            if (DisplayCutoutUtils.hasNotchInScreenForColorOS(this)) {
                mPrivateFlags |= PFLAG_SCREEN_NOTCH_SUPPORT;
                mNotchHeight = DisplayCutoutUtils.getNotchSizeForColorOS()[1];
            }
        } else if (OSHelper.isFuntouchOS()) {
            if (DisplayCutoutUtils.hasNotchInScreenForFuntouchOS(this)) {
                mPrivateFlags |= PFLAG_SCREEN_NOTCH_SUPPORT;
                mNotchHeight = DisplayCutoutUtils.getNotchHeightForFuntouchOS(this);
            }
        } else if (OSHelper.isMIUI()) {
            if (DisplayCutoutUtils.hasNotchInScreenForMIUI()) {
                mPrivateFlags |= PFLAG_SCREEN_NOTCH_SUPPORT | PFLAG_SCREEN_NOTCH_SUPPORT_ON_MIUI;
                mNotchHeight = DisplayCutoutUtils.getNotchHeightForMIUI(this);
                DisplayCutoutUtils.setLayoutInDisplayCutoutForMIUI(window, true);
            }
        }

        if (Utils.isLayoutRtl(decorView)) {
            getSwipeBackLayout().setEnabledEdges(SwipeBackLayout.EDGE_RIGHT);
        }
        // 初始化布局
        setFullscreenMode(mVideoView.isInFullscreenMode());

        mHandler = decorView.getHandler();

        final boolean notchSupportOnEMUI = (mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT_ON_EMUI) != 0;
        final boolean notchSupportOnMIUI = (mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT_ON_MIUI) != 0;
        if (notchSupportOnEMUI || notchSupportOnMIUI) {
            mNotchSwitchObserver = new ScreenNotchSwitchObserver(mHandler, this,
                    notchSupportOnEMUI, notchSupportOnMIUI) {
                @Override
                public void onNotchChange(boolean selfChange, boolean hidden) {
                    final int old = mPrivateFlags;
                    mPrivateFlags = (mPrivateFlags & ~PFLAG_SCREEN_NOTCH_HIDDEN) |
                            (hidden ? PFLAG_SCREEN_NOTCH_HIDDEN : 0);
                    if (mPrivateFlags != old) {
                        resizeVideoView();
                    }
                }
            };
            mNotchSwitchObserver.startObserver();
        }

        mRotationObserver = new RotationObserver(mHandler, this) {
            @Override
            public void onRotationChange(boolean selfChange, boolean enabled) {
                mPrivateFlags = (mPrivateFlags & ~PFLAG_DEVICE_SCREEN_ROTATION_ENABLED) |
                        (enabled ? PFLAG_DEVICE_SCREEN_ROTATION_ENABLED : 0);
            }
        };
        mOnOrientationChangeListener = new OnOrientationChangeListener(this, mDeviceOrientation) {
            @Override
            public void onOrientationChange(int orientation) {
                if (orientation != SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                    if (mVideoWidth == 0 && mVideoHeight == 0) {
                        mOnOrientationChangeListener.setOrientation(mDeviceOrientation);
                        return;
                    }
                    mDeviceOrientation = orientation;
                    changeScreenOrientationIfNeeded(orientation);
                }
            }
        };
        setAutoRotationEnabled(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 当前页面放入后台时保存视频进度
        if (!isFinishing()) {
            recordCurrVideoProgress();
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPictureInPictureMode()) {
            if (mNotchSwitchObserver != null) {
                mNotchSwitchObserver.stopObserver();
            }
            setAutoRotationEnabled(false);
        }
        mVideoView.closeVideo();
    }

    @Override
    public void finish() {
        if (mVideos != null && mVideos.length > 0) {
            if (mVideos.length == 1) {
                recordCurrVideoProgress();
                if (mVideos[0].getId() != Consts.NO_ID) {
                    setResult(Consts.RESULT_CODE_PLAY_VIDEO, new Intent().putExtra(Consts.KEY_VIDEO, mVideos[0]));
                }
            } else {
                recordCurrVideoProgress();
                setResult(Consts.RESULT_CODE_PLAY_VIDEOS, new Intent().putExtra(Consts.KEY_VIDEOS, mVideos));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // finish() does not remove the activity in PIP mode from the recents stack.
            // Only finishAndRemoveTask() does this.
            finishAndRemoveTask();
        } else {
            super.finish();
        }
    }

    private void recordCurrVideoProgress() {
        Video video = mVideos[mVideoIndex];
        video.setProgress(mVideoView.getVideoProgress());

        final long id = video.getId();
        if (id != Consts.NO_ID) {
            VideoDaoHelper.getInstance(this).setVideoProgress(id, video.getProgress());
        }
    }

    @Override
    public void onBackPressed() {
        if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sActivityInPiP != null && sActivityInPiP.get() == this) {
            sActivityInPiP.clear();
            sActivityInPiP = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacks(mHideLockUnlockOrientationButtonRunnable, null);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                && mStatusHeightInLandscapeOfNotchSupportDevices == 0) {
            mStatusHeightInLandscapeOfNotchSupportDevices = SystemBarUtils.getStatusHeight(this);
            if (mVideoView.isInFullscreenMode()) {
                mVideoView.setFullscreenMode(true, mStatusHeightInLandscapeOfNotchSupportDevices);
            }
        }
    }

    private void setScreenOrientationLocked(boolean locked) {
        if (locked) {
            mPrivateFlags |= PFLAG_SCREEN_ORIENTATION_LOCKED;
            mLockUnlockOrientationButton.setImageResource(R.drawable.ic_unlock);
            mLockUnlockOrientationButton.setContentDescription(mUnlockOrientation);
        } else {
            mPrivateFlags &= ~PFLAG_SCREEN_ORIENTATION_LOCKED;
            mLockUnlockOrientationButton.setImageResource(R.drawable.ic_lock);
            mLockUnlockOrientationButton.setContentDescription(mLockOrientation);
            // 取消锁定则将屏幕方向设为当前设备方向
            changeScreenOrientationIfNeeded(mDeviceOrientation);
        }
    }

    private void showLockUnlockOrientationButton(boolean show) {
        mLockUnlockOrientationButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setAutoRotationEnabled(boolean enabled) {
        if (enabled) {
            mRotationObserver.startObserver();
            mOnOrientationChangeListener.setEnabled(true);
        } else {
            mOnOrientationChangeListener.setEnabled(false);
            mRotationObserver.stopObserver();
        }
    }

    private void changeScreenOrientationIfNeeded(int orientation) {
        switch (mScreenOrientation) {
            case SCREEN_ORIENTATION_PORTRAIT:
                if ((mPrivateFlags & PFLAG_DEVICE_SCREEN_ROTATION_ENABLED) != 0
                        && !mVideoView.isLocked()) {
                    if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_LOCKED) != 0) {
                        final boolean fullscreen = orientation != SCREEN_ORIENTATION_PORTRAIT;
                        if (fullscreen == mVideoView.isInFullscreenMode()) {
                            return;
                        }
                        break;
                    }

                    if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) != 0) {
                        final boolean fullscreen = orientation != SCREEN_ORIENTATION_PORTRAIT;
                        if (fullscreen == mVideoView.isInFullscreenMode()) {
                            return;
                        }
                        setFullscreenMode(fullscreen);
                    } else {
                        if (orientation == SCREEN_ORIENTATION_PORTRAIT) {
                            return;
                        }
                        mScreenOrientation = orientation;
                        setRequestedOrientation(orientation);
                        setFullscreenMode(true);
                    }
                    break;
                }
                return;
            case SCREEN_ORIENTATION_LANDSCAPE:
            case SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                if (mScreenOrientation == orientation) {
                    return;
                }
                if (mVideoView.isLocked()) {
                    if (orientation == SCREEN_ORIENTATION_PORTRAIT) {
                        return;
                    }
                } else if ((mPrivateFlags & PFLAG_DEVICE_SCREEN_ROTATION_ENABLED) != 0) {
                    if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_LOCKED) != 0
                            && orientation == SCREEN_ORIENTATION_PORTRAIT) {
                        break;
                    }
                } else if (orientation == SCREEN_ORIENTATION_PORTRAIT) {
                    return;
                }

                mScreenOrientation = orientation;
                setRequestedOrientation(orientation);

                final boolean fullscreen = orientation != SCREEN_ORIENTATION_PORTRAIT;
                if (fullscreen == mVideoView.isInFullscreenMode()) {
                    //@formatter:off
                    if ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT) == 0
                            || (mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT_ON_EMUI) != 0
                                    && (mPrivateFlags & PFLAG_SCREEN_NOTCH_HIDDEN) != 0) {
                    //@formatter:on
                        if (mVideoView.isControlsShowing()) {
                            mVideoView.showControls(true, false);
                        }
                        return;
                    }
                    setFullscreenMode(fullscreen);
                    return;
                }
                setFullscreenMode(fullscreen);
                break;
        }

        showLockUnlockOrientationButton(true);
        mHandler.removeCallbacks(mHideLockUnlockOrientationButtonRunnable);
        mHandler.postDelayed(mHideLockUnlockOrientationButtonRunnable,
                DELAY_TIME_HIDE_LOCK_UNLOCK_ORIENTATION_BUTTON);
    }

    private void setFullscreenMode(boolean fullscreen) {
        // 全屏时禁用“滑动返回”
        getSwipeBackLayout().setGestureEnabled(!fullscreen);

        showSystemBars(!fullscreen);
        //@formatter:off
        mVideoView.setFullscreenMode(fullscreen,
            fullscreen && (
                   (mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT) == 0
                || (mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) == 0
                          ) ? ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT) == 0) ?
                                mStatusHeight : mStatusHeightInLandscapeOfNotchSupportDevices
                            : 0);
        //@formatter:on
        if (mVideoView.isControlsShowing()) {
            mVideoView.showControls(true, false);
        }
        resizeVideoView();

        mPrivateFlags = mPrivateFlags & ~PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN
                | (fullscreen ? PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN : 0);
    }

    private void setFullscreenModeManually(boolean fullscreen) {
        if (mVideoView.isInFullscreenMode() == fullscreen) {
            return;
        }
        if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) == 0) {
            mScreenOrientation = fullscreen ?
                    mDeviceOrientation == SCREEN_ORIENTATION_PORTRAIT
                            ? SCREEN_ORIENTATION_LANDSCAPE : mDeviceOrientation
                    : SCREEN_ORIENTATION_PORTRAIT;
            setRequestedOrientation(mScreenOrientation);
        }
        setFullscreenMode(fullscreen);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void resizeVideoView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode()) {
            setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            return;
        }

        switch (mScreenOrientation) {
            case SCREEN_ORIENTATION_PORTRAIT:
                final boolean layoutIsFullscreen = mVideoView.isInFullscreenMode();
                final boolean lastLayoutIsFullscreen =
                        (mPrivateFlags & PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN) != 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                        && (mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) != 0
                        && layoutIsFullscreen != lastLayoutIsFullscreen) {
                    TransitionManager.beginDelayedTransition(mVideoView, new ChangeBounds());
                }

                if (layoutIsFullscreen) {
                    setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    if ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT) != 0) {
                        mVideoView.setPadding(0,
                                (mPrivateFlags & PFLAG_SCREEN_NOTCH_HIDDEN) == 0 ?
                                        mNotchHeight : mStatusHeight,
                                0, 0);
                    }
                } else {
                    final int screenWidth = App.getInstance().getRealScreenWidthIgnoreOrientation();
                    // portrait w : h = 16 : 9
                    final int minLayoutHeight = (int) ((float) screenWidth / 16f * 9f + 0.5f);

                    setVideoViewSize(screenWidth, minLayoutHeight);
                    if ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT) != 0) {
                        mVideoView.setPadding(0, 0, 0, 0);
                    }
                }
                break;
            case SCREEN_ORIENTATION_LANDSCAPE:
                setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                if ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT_ON_EMUI) != 0
                        && (mPrivateFlags & PFLAG_SCREEN_NOTCH_HIDDEN) != 0) {
                    mVideoView.setPadding(0, 0, 0, 0);

                } else if ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT) != 0) {
                    mVideoView.setPadding(mNotchHeight, 0, 0, 0);
                }
                break;
            case SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                if ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT_ON_EMUI) != 0
                        && (mPrivateFlags & PFLAG_SCREEN_NOTCH_HIDDEN) != 0) {
                    mVideoView.setPadding(0, 0, 0, 0);

                } else if ((mPrivateFlags & PFLAG_SCREEN_NOTCH_SUPPORT) != 0) {
                    mVideoView.setPadding(0, 0, mNotchHeight, 0);
                }
                break;
        }
    }

    private void setVideoViewSize(int layoutWidth, int layoutHeight) {
        ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
        lp.width = layoutWidth;
        lp.height = layoutHeight;
        mVideoView.setLayoutParams(lp);
    }

    private void showSystemBars(boolean show) {
        Window window = getWindow();
        if (show) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mStatusBarView.setVisibility(View.VISIBLE);
                // 状态栏透明
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    SystemBarUtils.setStatusBackgroundColor(window, Color.TRANSPARENT);
                } else {
                    SystemBarUtils.setTranslucentStatus(window, true);
                }
                View decorView = window.getDecorView();
                int visibility = decorView.getVisibility();
                // 使view显示在状态栏底层
                visibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                // 状态栏、导航栏显现
                visibility &= ~(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                decorView.setSystemUiVisibility(visibility);
            } else {
                SystemBarUtils.showSystemBars(window, true);
            }
        } else {
            if (mStatusBarView != null) {
                mStatusBarView.setVisibility(View.GONE);
            }
            SystemBarUtils.showSystemBars(window, false);
        }
    }

    /**
     * Update the action items in Picture-in-Picture mode.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void updatePictureInPictureActions(int pipActions) {
        final List<RemoteAction> actions = new LinkedList<>();

        if ((pipActions & PIP_ACTION_FAST_REWIND) != 0) {
            actions.add(createPipAction(R.drawable.ic_fast_rewind_white_24dp,
                    mFastRewind, PIP_ACTION_FAST_REWIND, REQUEST_FAST_REWIND));
        }
        if ((pipActions & PIP_ACTION_PLAY) != 0) {
            actions.add(createPipAction(R.drawable.ic_play_white_24dp,
                    mPlay, PIP_ACTION_PLAY, REQUEST_PLAY));

        } else if ((pipActions & PIP_ACTION_PAUSE) != 0) {
            actions.add(createPipAction(R.drawable.ic_pause_white_24dp,
                    mPause, PIP_ACTION_PAUSE, REQUEST_PAUSE));
        }
        if ((pipActions & PIP_ACTION_FAST_FORWARD) != 0) {
            actions.add(createPipAction(R.drawable.ic_fast_forward_white_24dp,
                    mFastForward, PIP_ACTION_FAST_FORWARD, REQUEST_FAST_FORWARD));
        } else {
            RemoteAction action = createPipAction(R.drawable.ic_fast_forward_lightgray_24dp,
                    mFastForward, PIP_ACTION_FAST_FORWARD, REQUEST_FAST_FORWARD);
            action.setEnabled(false);
            actions.add(action);
        }

        mPipParamsBuilder.setActions(actions);

        // This is how you can update action items (or aspect ratio) for Picture-in-Picture mode.
        setPictureInPictureParams(mPipParamsBuilder.build());
    }

    /**
     * Create an pip action item in Picture-in-Picture mode.
     *
     * @param iconId      The icon to be used.
     * @param title       The title text.
     * @param pipAction   The type for the pip action. May be {@link #PIP_ACTION_PLAY},
     *                    {@link #PIP_ACTION_PAUSE},
     *                    {@link #PIP_ACTION_FAST_FORWARD},
     *                    or {@link #PIP_ACTION_FAST_REWIND}.
     * @param requestCode The request code for the {@link PendingIntent}.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private RemoteAction createPipAction(
            @DrawableRes int iconId, String title, int pipAction, int requestCode) {
        // This is the PendingIntent that is invoked when a user clicks on the action item.
        // You need to use distinct request codes for play, pause, fast forward, and fast rewind,
        // or the PendingIntent won't be properly updated.
        PendingIntent intent = PendingIntent.getBroadcast(this,
                requestCode,
                new Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_PIP_ACTION, pipAction),
                0);
        Icon icon = IconCompat.createWithResource(this, iconId).toIcon();
        return new RemoteAction(icon, title, title, intent);
    }

    @SuppressLint("SwitchIntDef")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        mVideoView.onMinimizationModeChange(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            int actions = PIP_ACTION_FAST_REWIND;
            final int playbackState = mVideoView.getPlaybackState();
            switch (playbackState) {
                case VideoPlayerControl.PLAYBACK_STATE_PLAYING:
                    actions |= PIP_ACTION_PAUSE | PIP_ACTION_FAST_FORWARD;
                    break;
                case VideoPlayerControl.PLAYBACK_STATE_COMPLETED:
                    actions |= PIP_ACTION_PLAY
                            | (mVideoView.canSkipToNext() ? PIP_ACTION_FAST_FORWARD : 0);
                    break;
                default:
                    actions |= PIP_ACTION_PLAY | PIP_ACTION_FAST_FORWARD;
                    break;
            }
            updatePictureInPictureActions(actions);

            // Starts receiving events from action items in PiP mode.
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction())) {
                        return;
                    }

                    // This is where we are called back from Picture-in-Picture action items.
                    final int action = intent.getIntExtra(EXTRA_PIP_ACTION, 0);
                    switch (action) {
                        case PIP_ACTION_PLAY:
                            mVideoView.play(true);
                            break;
                        case PIP_ACTION_PAUSE:
                            mVideoView.pause(true);
                            break;
                        case PIP_ACTION_FAST_REWIND: {
                            mVideoView.fastRewind(true);
                        }
                        break;
                        case PIP_ACTION_FAST_FORWARD: {
                            mVideoView.fastForward(true);
                        }
                        break;
                    }
                }
            };
            registerReceiver(mReceiver, new IntentFilter(ACTION_MEDIA_CONTROL));

            sActivityInPiP = new WeakReference<>(this);

            mStatusBarView.setVisibility(View.GONE);
            if (mNotchSwitchObserver != null) {
                mNotchSwitchObserver.stopObserver();
            }
            setAutoRotationEnabled(false);
            showLockUnlockOrientationButton(false);
            mVideoProgressInPiP.setVisibility(View.VISIBLE);
            mRefreshVideoProgressInPiPTask = new RefreshVideoProgressInPiPTask();
            mRefreshVideoProgressInPiPTask.execute();

            mVideoView.showControls(false, false);
            mVideoView.setClipViewBounds(true);
            resizeVideoView();

            mOnPipLayoutChangeListener = new View.OnLayoutChangeListener() {
                float cachedVideoAspectRatio;
                int cachedSize = -1;

                static final String TAG = "VideoActivityInPIP";

                /* anonymous class initializer */ {
                    if (sPipRatioOfProgressHeightToVideoSize == 0) {
                        // 1dp -> 2.75px (5.5inch  w * h = 1080 * 1920)
                        final float dp = getResources().getDisplayMetrics().density;
                        sPipRatioOfProgressHeightToVideoSize = 1.0f / (12121.2f * dp); // 1 : 33333.3 (px)
                        sPipProgressMinHeight = (int) (dp * 1.8f + 0.5f); // 5.45px -> 5px
                        sPipProgressMaxHeight = (int) (dp * 2.5f + 0.5f); // 7.375px -> 7px
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "sPipRatioOfProgressHeightToVideoSize = " + sPipRatioOfProgressHeightToVideoSize
                                    + "    " + "sPipProgressMinHeight = " + sPipProgressMinHeight
                                    + "    " + "sPipProgressMaxHeight = " + sPipProgressMaxHeight);
                        }
                    }
                }

                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (mVideoWidth == 0 || mVideoHeight == 0) return;

                    final float videoAspectRatio = (float) mVideoWidth / mVideoHeight;
                    final int width = right - left;
                    final int height = (int) (width / videoAspectRatio + 0.5f);
                    final int size = width * height;
                    final float sizeRatio = (float) size / cachedSize;

                    if (videoAspectRatio != cachedVideoAspectRatio
                            || sizeRatio > RATIO_TOLERANCE_PIP_LAYOUT_SIZE
                            || sizeRatio < 1.0f / RATIO_TOLERANCE_PIP_LAYOUT_SIZE) {
                        final int progressHeight = Math.max(sPipProgressMinHeight,
                                Math.min(sPipProgressMaxHeight,
                                        (int) (size * sPipRatioOfProgressHeightToVideoSize + 0.5f)));
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "sizeRatio = " + sizeRatio
                                    + "    " + "progressHeight = " + progressHeight
                                    + "    " + "size / 1.8dp = " + size / sPipProgressMinHeight
                                    + "    " + "size / 2.5dp = " + size / sPipProgressMaxHeight);
                        }

                        mPipParamsBuilder.setAspectRatio(
                                new Rational(width, height + progressHeight));
                        setPictureInPictureParams(mPipParamsBuilder.build());

                        cachedVideoAspectRatio = videoAspectRatio;
                        cachedSize = size;
                    }
                }
            };
            mVideoView.addOnLayoutChangeListener(mOnPipLayoutChangeListener);
        } else {
            // We are out of PiP mode. We can stop receiving events from it.
            unregisterReceiver(mReceiver);
            mReceiver = null;

            sActivityInPiP.clear();
            sActivityInPiP = null;

            mVideoView.removeOnLayoutChangeListener(mOnPipLayoutChangeListener);
            mOnPipLayoutChangeListener = null;

            mVideoView.showControls(true);
            mVideoView.setClipViewBounds(false);
            resizeVideoView();

            mStatusBarView.setVisibility(View.VISIBLE);
            if (mNotchSwitchObserver != null) {
                mNotchSwitchObserver.startObserver();
            }
            setAutoRotationEnabled(true);
            mVideoProgressInPiP.setVisibility(View.GONE);
            mRefreshVideoProgressInPiPTask.cancel();
            mRefreshVideoProgressInPiPTask = null;
        }
    }

    /**
     * 跳转到系统默认的播放器进行播放
     */
    private void playVideoByDefault() {
        final String path = mVideos[mVideoIndex].getPath();
        startActivity(new Intent(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setDataAndType(Uri.parse(path), FileUtils.getMimeTypeFromPath(path, "video/*")));
    }

    private void notifyItemSelectionChanged(int oldPosition, int position, boolean checkNewItemVisibility) {
        if (mPlayList != null) {
            RecyclerView.Adapter adapter = mPlayList.getAdapter();
            assert adapter != null;
            adapter.notifyItemChanged(oldPosition, sRefreshVideoProgressPayload);
            adapter.notifyItemChanged(oldPosition, sHighlightSelectedItemIfExistsPayload);
            adapter.notifyItemChanged(position, sRefreshVideoProgressPayload);
            adapter.notifyItemChanged(position, sHighlightSelectedItemIfExistsPayload);
            if (checkNewItemVisibility) {
                RecyclerView.LayoutManager lm = mPlayList.getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    LinearLayoutManager llm = (LinearLayoutManager) lm;
                    if (llm.findLastCompletelyVisibleItemPosition() < position) {
                        llm.scrollToPosition(position);
                    }
                } else if (lm instanceof StaggeredGridLayoutManager) {
                    StaggeredGridLayoutManager sglm = (StaggeredGridLayoutManager) lm;
                    int maxCompletelyVisiblePosition = 0;
                    for (int i : sglm.findLastCompletelyVisibleItemPositions(null)) {
                        maxCompletelyVisiblePosition = Math.max(maxCompletelyVisiblePosition, i);
                    }
                    if (maxCompletelyVisiblePosition < position) {
                        sglm.scrollToPosition(position);
                    }
                }
            }
        }
    }

    private final class VideoEpisodesAdapter
            extends AbsTextureVideoView.PlayListAdapter<VideoEpisodesAdapter.ViewHolder> {

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            mPlayList = recyclerView;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(VideoActivity.this)
                    .inflate(R.layout.item_video_play_list, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads);
            } else {
                for (Object payload : payloads) {
                    if (payload == sRefreshVideoProgressPayload) {
                        Video video = mVideos[position];
                        if (position == mVideoIndex) {
                            holder.videoProgressDurationText.setText(mWatching);
                        } else {
                            holder.videoProgressDurationText.setText(
                                    VideoUtils2.concatVideoProgressAndDuration(
                                            video.getProgress(), video.getDuration()));
                        }
                    } else if (payload == sHighlightSelectedItemIfExistsPayload) {
                        highlightSelectedItemIfExists(holder, position);
                    }
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            highlightSelectedItemIfExists(holder, position);

            Video video = mVideos[position];
            VideoUtils2.loadVideoThumbnail(holder.videoImage, video);
            holder.videoNameText.setText(video.getName());
            if (position == mVideoIndex) {
                holder.videoProgressDurationText.setText(mWatching);
            } else {
                holder.videoProgressDurationText.setText(
                        VideoUtils2.concatVideoProgressAndDuration(video.getProgress(), video.getDuration()));
            }
        }

        @Override
        public int getItemCount() {
            return mVideos.length;
        }

        void highlightSelectedItemIfExists(ViewHolder holder, int position) {
            final boolean selected = position == mVideoIndex;
            holder.itemView.setSelected(selected);
            holder.videoNameText.setTextColor(selected ? Consts.COLOR_ACCENT : Color.WHITE);
            holder.videoProgressDurationText.setTextColor(
                    selected ? Consts.COLOR_ACCENT : 0x80FFFFFF);
        }

        @Override
        public void onItemClick(@NonNull View view, int position) {
            if (mVideoIndex == position) {
                Toast.makeText(VideoActivity.this, R.string.theVideoIsPlaying, Toast.LENGTH_SHORT).show();
            } else {
                recordCurrVideoProgress();
                setVideoToPlay(position);

                final int oldIndex = mVideoIndex;
                mVideoIndex = position;
                notifyItemSelectionChanged(oldIndex, position, false);
            }
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView videoImage;
            final TextView videoNameText;
            final TextView videoProgressDurationText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                videoImage = itemView.findViewById(R.id.image_video);
                videoNameText = itemView.findViewById(R.id.text_videoName);
                videoProgressDurationText = itemView.findViewById(R.id.text_videoProgressAndDuration);
            }
        }
    }

    // --------------- Saved Instance State ------------------------

    private static final String KEY_IS_SCREEN_ORIENTATION_LOCKED = "kisol";
    private static final String KEY_DEVICE_ORIENTATION = "kdo";
    private static final String KEY_SCREEN_ORIENTATION = "kso";
    private static final String KEY_STATUS_HEIGHT_IN_LANDSCAPE_OF_NOTCH_SUPPORT_DEVICES = "kshilonsd";
    private static final String KEY_VIDEO_INDEX = "kvi";

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_SCREEN_ORIENTATION_LOCKED,
                (mPrivateFlags & PFLAG_SCREEN_ORIENTATION_LOCKED) != 0);
        outState.putInt(KEY_DEVICE_ORIENTATION, mDeviceOrientation);
        outState.putInt(KEY_SCREEN_ORIENTATION, mScreenOrientation);
        outState.putInt(KEY_STATUS_HEIGHT_IN_LANDSCAPE_OF_NOTCH_SUPPORT_DEVICES,
                mStatusHeightInLandscapeOfNotchSupportDevices);
        outState.putInt(KEY_VIDEO_INDEX, mVideoIndex);
    }
}