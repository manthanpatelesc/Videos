/*
 * Created on 18-9-16 下午4:09.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.os.ParcelableCompat;
import androidx.core.os.ParcelableCompatCreatorCallbacks;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.view.AbsSavedState;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liuzhenlin.texturevideoview.drawable.CircularProgressDrawable;
import com.liuzhenlin.texturevideoview.receiver.HeadsetButtonReceiver;
import com.liuzhenlin.texturevideoview.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.texturevideoview.receiver.VolumeReceiver;
import com.liuzhenlin.texturevideoview.utils.BitmapUtil;
import com.liuzhenlin.texturevideoview.utils.ScreenUtils;
import com.liuzhenlin.texturevideoview.utils.TimeUtil;
import com.liuzhenlin.texturevideoview.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Provides video playback.
 * <p>
 * This is similar to {@link VideoView}, but it comes with a custom control containing
 * 1) buttons like "Go Back", "Play/Pause", "Minimize", "Maximize",
 * 2) progress sliders for adjusting screen brightness, volume and video progress and
 * 3) a {@link TextureView} used to display the video.
 *
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class TextureVideoView extends DrawerLayout {

    /** Monitors all events related to the video playback. */
    public static abstract class VideoListener {
        /** Called when the video is started or resumed. */
        public void onVideoStarted() {
        }

        /** Called when the video is paused or finished. */
        public void onVideoStopped() {
        }

        /** Called when a "skip to previous" action is requested by the user. */
        public void onSkipToPrevious() {
        }

        /** Called when a "skip to next" action is requested by the user. */
        public void onSkipToNext() {
        }

        /** Called when this view should be maximized (e.g., turning into fullscreen mode). */
        public void onMaximizeVideo() {
        }

        /** Called when this view should be minimized (e.g., becoming picture-in-picture mode). */
        public void onMinimizeVideo() {
        }

        /**
         * Called to indicate the video size (width and height) or 0 if there was no video or
         * the value was not determined yet.
         * <p>
         * This is useful for deciding whether to perform some layout changes
         *
         * @param oldWidth  the previous width of the video
         * @param oldHeight the previous height of the video
         * @param width     the new width of the video
         * @param height    the new height of the video
         */
        public void onVideoSizeChanged(int oldWidth, int oldHeight, int width, int height) {
        }
    }

    public static abstract class OpCallback {
        /**
         * @return `true` if the activity to which this view belongs is currently in
         * picture-in-picture mode.
         * @see Activity#isInPictureInPictureMode()
         */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isInPictureInPictureMode() {
            return false;
        }

        /**
         * @return the window this view is currently attached to
         */
        @NonNull
        public abstract Window getWindow();

        /**
         * @return whether the player can start a video that precedes the current one (if any)
         * in the playlist.
         */
        public boolean canSkipToPrevious() {
            return false;
        }

        /**
         * @return whether the player can start a video that follows the current one (if any)
         * in the playlist.
         */
        public boolean canSkipToNext() {
            return false;
        }
    }

    public interface OnReturnClickListener {
        /**
         * Called when the activity this view is attached to should be destroyed
         * or dialog that should be dismissed, etc.
         */
        void onReturnClick();
    }

    public static abstract class PlayListAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
        TextureVideoView videoView;
        RecyclerView playlist;

        final OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemClick(v, playlist.getChildAdapterPosition(v));
                videoView.closeDrawer(playlist);
            }
        };
        final OnLongClickListener onLongClickListener = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return onItemLongClick(v, playlist.getChildAdapterPosition(v));
            }
        };

        @CallSuper
        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            playlist = recyclerView;
            videoView = (TextureVideoView) recyclerView.getParent();
        }

        @CallSuper
        @Override
        public void onViewAttachedToWindow(@NonNull VH holder) {
            holder.itemView.setOnClickListener(onClickListener);
            holder.itemView.setOnLongClickListener(onLongClickListener);
        }

        @CallSuper
        @Override
        public void onViewDetachedFromWindow(@NonNull VH holder) {
            holder.itemView.setOnClickListener(null);
            holder.itemView.setOnLongClickListener(null);
        }

        /**
         * Callback method to be invoked when an item in the RecyclerView has been clicked.
         *
         * @param view     The itemView that was clicked.
         * @param position The position of the view in the adapter
         */
        public void onItemClick(@NonNull View view, int position) {
        }

        /**
         * Callback method to be invoked when an item in the RecyclerView has been clicked and held.
         *
         * @param view     The itemView that was clicked and held.
         * @param position The position of the view in the list
         * @return `true` if the callback consumed the long click, false otherwise.
         */
        public boolean onItemLongClick(@NonNull View view, int position) {
            return false;
        }
    }

    private static final String TAG = "TextureVideoView";

    private int mPrivateFlags;

    /**
     * Indicates that the player is currently preparing for the video playback asynchronously.
     */
    private static final int PFLAG_PLAYER_IS_PREPARING = 1;

    /** Indicates that the video info (width, height, duration, etc.) is now available. */
    private static final int PFLAG_VIDEO_INFO_RESOLVED = 1 << 1;

    /** Indicates that the video is currently playing (the player is running, too). */
    private static final int PFLAG_VIDEO_IS_PLAYING = 1 << 2;

    /** Indicates that an error occurs while the video is playing. */
    private static final int PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO = 1 << 3;

    /** Indicates that the video is manually paused by the user. */
    private static final int PFLAG_VIDEO_PAUSED_BY_USER = 1 << 4;

    /** Indicates that the end of the video is reached. */
    private static final int PFLAG_VIDEO_PLAYBACK_COMPLETED = 1 << 5;

    /**
     * Flag used to indicates that the volume of the video is auto-turned down by the system
     * when the player temporarily loses the audio focus.
     */
    private static final int PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY = 1 << 6;

    /** If the top and bottom controls are showing, this is marked. */
    private static final int PFLAG_TOP_BOTTOM_CONTROLS_SHOWING = 1 << 7;

    /**
     * Once set, the top and bottom controls will stay showing even if you call
     * {@link #showTopAndBottomControls(boolean)} with a `false` passed into (in this case,
     * it does nothing), and the internal will manage them logically. This usually happens
     * when user is interacting with some basic widget (e.g. dragging the video progress bar or
     * choosing a proper speed for the current player).
     */
    private static final int PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY = 1 << 8;

    /**
     * Flag indicates whether or not the top and bottom controls are shown before we open the playlist
     * through animator or it is opened.
     */
    private static final int PFLAG_TOP_BOTTOM_CONTROLS_SHOWN_BEFORE_OPENING_PLAYLIST = 1 << 9;

    /**
     * If set, we will clip the bounds of this view to make it fit the width and height
     * of the video, or we fill the remaining area with black bars.
     */
    private static final int PFLAG_CLIP_VIEW_BOUNDS = 1 << 10;

    /** If set, this view is currently in fullscreen mode. */
    private static final int PFLAG_IN_FULLSCREEN_MODE = 1 << 11;

    /** The amount of time till we fade out the brightness or volume SeekBar. */
    private static final int TIMEOUT_SHOW_BRIGHTNESS_OR_VOLUME = 2500; // ms
    /** The amount of time till we fade out the top and bottom controls. */
    private static final int TIMEOUT_SHOW_TOP_AND_BOTTOM_CONTROLS = 5000; // ms

    protected final Context mContext;
    protected final Resources mResources;

    private final ConstraintLayout mContentView;
    private final RecyclerView mPlayList;

    /** Shows the video playback. */
    private final TextureView mTextureView;

    private final ViewGroup mTopControlsFrame;
    private final TextView mTitleText;

    private final TextView mBrightnessText;
    private final TextView mBrightnessValueText;
    private final VerticalSeekBar mBrightnessSeekBar;

    private final TextView mVolumeText;
    private final TextView mVolumeValueText;
    private final VerticalSeekBar mVolumeSeekBar;

    private final ViewGroup mBottomControlsFrame;
    private ImageView mToggleButton;
    private SeekBar mVideoSeekBar;
    // Bottom Controls only in non-fullscreen mode
    private TextView mVideoProgressText;
    private TextView mVideoDurationText;
    private ImageView mMinimizeButton;
    private ImageView mFullscreenButton;
    // Bottom Controls only in fullscreen mode
    private ImageView mSkipNextButton;
    private TextView mVideoProgressDurationText;
    private AppCompatSpinner mSpeedSpinner;
    private TextView mChooseEpisodeButton;

    /**
     * Scrim view with a 33.3% black background shows on our TextureView to obscure primary
     * video frames while the video thumb text is visible to the user.
     */
    private final View mScrimView;
    private final TextView mSeekingVideoThumbText;

    private final ViewGroup mSeekingTextProgressFrame;
    private final TextView mSeekingProgressDurationText;
    private final ProgressBar mSeekingProgress;

    private final ImageView mLoadingImage;
    private final CircularProgressDrawable mLoadingDrawable;

    private Object mSpinnerListPopup; // ListPopupWindow
    private PopupWindow mSpinnerPopup;

    private final int mNavInitialPaddingTop;

    private final String mStringPlay;
    private final String mStringPause;
    private final String[] mSpeedsStringArray;
    private final float mSeekingViewHorizontalOffset;
    private final float mSeekingVideoThumbCornerRadius;

    @ColorInt
    protected final int mColorAccent;
    private static final int[] THEME_ATTRS = {R.attr.colorAccent};

    protected final int mTouchSlop;
    protected static final int DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();

    private final OnChildTouchListener mOnChildTouchListener = new OnChildTouchListener();
    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mTitleText == v) {
                if (mOnReturnClickListener != null) {
                    mOnReturnClickListener.onReturnClick();
                }
            } else if (mToggleButton == v) {
                toggle();

            } else if (mSkipNextButton == v) {
                if (mVideoListener != null &&
                        mOpCallback != null && mOpCallback.canSkipToNext()) {
                    mVideoListener.onSkipToNext();
                }
            } else if (mFullscreenButton == v) {
                if (mVideoListener != null) {
                    mVideoListener.onMaximizeVideo();
                }
            } else if (mMinimizeButton == v) {
                if (mVideoListener != null) {
                    mVideoListener.onMinimizeVideo();
                }
            } else if (mChooseEpisodeButton == v) {
                openDrawer(mPlayList);
            }
        }
    };
    private final AdapterView.OnItemSelectedListener mOnItemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (mSpeedSpinner == parent
                    && view instanceof TextView /* This may be null during state restore */) {
                TextView tv = (TextView) view;

                ViewGroup.LayoutParams lp = tv.getLayoutParams();
                lp.width = parent.getWidth();
                lp.height = parent.getHeight();
                tv.setLayoutParams(lp);

                final String speed = tv.getText().toString();
                setPlaybackSpeed(Float.parseFloat(speed.substring(0, speed.lastIndexOf('x'))), false);

                // Filter the non-user-triggered selection changes, so that the visibility of the
                // top and bottom controls stay unchanged.
                if ((mPrivateFlags & PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY) != 0) {
                    mPrivateFlags &= ~PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY;
                    showTopAndBottomControls(true);
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    private final VerticalSeekBar.OnVerticalSeekBarChangeListener mOnVerticalSeekBarChangeListener
            = new VerticalSeekBar.OnVerticalSeekBarChangeListener() {
        @Override
        public void onVerticalProgressChanged(VerticalSeekBar verticalSeekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                return;
            }
            if (mBrightnessSeekBar == verticalSeekBar) {
                refreshBrightnessProgress(progress, false);
                showBrightnessSeekBar(true);

                ScreenUtils.setWindowBrightness(mOpCallback.getWindow(), progress);

                // volume SeekBar
            } else {
                refreshVolumeProgress(progress, false);
                showVolumeSeekBar(true);

                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progressToVolume(progress), 0);
            }
        }

        @Override
        public void onStartVerticalTrackingTouch(VerticalSeekBar verticalSeekBar) {
        }

        @Override
        public void onStopVerticalTrackingTouch(VerticalSeekBar verticalSeekBar) {
        }
    };
    private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener
            = new SeekBar.OnSeekBarChangeListener() {
        int start;
        volatile int current;
        MediaMetadataRetriever mmr;
        UpdateVideoThumbTask task;
        ForegroundColorSpan progressTextSpan;
        ValueAnimator fadeAnimator;
        ValueAnimator translateAnimator;
        ValueAnimator.AnimatorListener animatorListener;
        static final int DURATION = 800; // ms

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                current = progress;
                if (mmr == null) {
                    mSeekingProgressDurationText.setText(getProgressDurationText(progress));
                    mSeekingProgress.setProgress(progress);
                }
                refreshVideoProgress(progress, false);

                if (translateAnimator == null) {
                    final View target = mmr == null ? mSeekingTextProgressFrame : mSeekingVideoThumbText;
                    translateAnimator = ValueAnimator.ofFloat(0,
                            progress > start ? mSeekingViewHorizontalOffset : -mSeekingViewHorizontalOffset);
                    translateAnimator.addListener(animatorListener);
                    translateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            target.setTranslationX((float) animation.getAnimatedValue());
                        }
                    });
                    translateAnimator.setDuration(DURATION);
                    translateAnimator.setRepeatMode(ValueAnimator.RESTART);
                    translateAnimator.start();
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            current = start = seekBar.getProgress();

            mPrivateFlags |= PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY;
            showTopAndBottomControls(-1);
            // Do not refresh the video progress bar with the current playback position
            // while the user is dragging it.
            removeCallbacks(mRefreshVideoProgressRunnable);

            Animator.AnimatorListener listener = animatorListener;
            ValueAnimator fa = fadeAnimator;
            if (fa != null) {
                // hide the currently showing view (mSeekingVideoThumbText/mSeekingTextProgressFrame)
                fa.end();
            }
            if (translateAnimator != null) {
                // Reset horizontal translation to 0 for the just hidden view
                translateAnimator.end();
            }
            animatorListener = listener; // Reuse the animator listener if it is not recycled
            // Decide which view to show
            if (mVideoUri != null && isInFullscreenMode()) {
                if (mmr == null) {
                    mmr = new MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(mContext, mVideoUri);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        mmr.release();
                        mmr = null;
                        showSeekingTextProgress(true);
                        return;
                    }

                    task = new UpdateVideoThumbTask();
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    showSeekingVideoThumb(true);
                }
            } else {
                showSeekingTextProgress(true);
            }
            // Start the fade in animation
            if (fa == null) {
                if (animatorListener == null) {
                    animatorListener = new AnimatorListenerAdapter() {
                        // Override for compatibility with APIs below 26.
                        // This will not get called on platforms O and higher.
                        @Override
                        public void onAnimationStart(Animator animation) {
                            onAnimationStart(animation, isReverse(animation));
                        }

                        // Override for compatibility with APIs below 26.
                        // This will not get called on platforms O and higher.
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            onAnimationEnd(animation, isReverse(animation));
                        }

                        boolean isReverse(Animator animation) {
                            // When reversing, the animation's repeat mode was set to REVERSE
                            // before it started.
                            return ((ValueAnimator) animation).getRepeatMode() == ValueAnimator.REVERSE;
                        }

                        @TargetApi(Build.VERSION_CODES.O)
                        @Override
                        public void onAnimationStart(Animator animation, boolean isReverse) {
                            final boolean isThumbVisible = mSeekingVideoThumbText.getVisibility() == VISIBLE;
                            final boolean isFadeAnimation = animation == fadeAnimator;

                            Animator other = isFadeAnimation ? translateAnimator : fadeAnimator;
                            if (other == null || !other.isRunning()) {
                                updateLayer(LAYER_TYPE_HARDWARE, isThumbVisible);
                            }

                            if (isFadeAnimation) {
                                animation.setDuration(isReverse || isThumbVisible ?
                                        DURATION : (long) (DURATION * 2f / 3f + 0.5f));
                            }
                        }

                        @TargetApi(Build.VERSION_CODES.O)
                        @Override
                        public void onAnimationEnd(Animator animation, boolean isReverse) {
                            final boolean isThumbVisible = mSeekingVideoThumbText.getVisibility() == VISIBLE;
                            final boolean isFadeAnimation = animation == fadeAnimator;

                            Animator other = isFadeAnimation ? translateAnimator : fadeAnimator;
                            if (other == null || !other.isRunning()) {
                                updateLayer(LAYER_TYPE_NONE, isThumbVisible);
                            }

                            if (isReverse) {
                                if (isFadeAnimation) {
                                    fadeAnimator = null;
                                } else {
                                    translateAnimator = null;
                                }
                                if (fadeAnimator == null && translateAnimator == null) {
                                    animatorListener = null;
                                    if (isThumbVisible) {
                                        recycleVideoThumb();
                                        // Clear the text to make sure it doesn't show anything the next time
                                        // it appears, otherwise a separate text would be displayed on it,
                                        // which we do not want.
                                        mSeekingVideoThumbText.setText("");
                                        showSeekingVideoThumb(false);
                                    } else {
                                        showSeekingTextProgress(false);
                                    }
                                }
                            }
                        }

                        void updateLayer(int layerType, boolean isThumbVisible) {
                            if (isThumbVisible) {
                                mSeekingVideoThumbText.setLayerType(layerType, null);
                                if (ViewCompat.isAttachedToWindow(mSeekingVideoThumbText)) {
                                    mSeekingVideoThumbText.buildLayer();
                                }
                                mScrimView.setLayerType(layerType, null);
                                if (ViewCompat.isAttachedToWindow(mScrimView)) {
                                    mScrimView.buildLayer();
                                }
                            } else {
//                                mSeekingTextProgressFrame.setLayerType(layerType, null);
//                                if (ViewCompat.isAttachedToWindow(mSeekingTextProgressFrame)) {
//                                    mSeekingTextProgressFrame.buildLayer();
//                                }
                            }
                        }
                    };
                }
                fadeAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
                fadeAnimator.addListener(animatorListener);
                fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        final float alpha = (float) animation.getAnimatedValue();
                        if (mSeekingVideoThumbText.getVisibility() == VISIBLE) {
                            mScrimView.setAlpha(alpha);
                            mSeekingVideoThumbText.setAlpha(alpha);
                        } else {
                            mSeekingTextProgressFrame.setAlpha(alpha);
                        }
                    }
                });
                fadeAnimator.setRepeatMode(ValueAnimator.RESTART);
                fadeAnimator.start();
            } else {
                // If the fade in/out animator has not been released before we need one again,
                // reuse it to avoid unnecessary memory re-allocations.
                fadeAnimator = fa;
                fa.setRepeatMode(ValueAnimator.RESTART);
                fa.start();
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            final int progress = current;
            if (progress != start) seekTo(progress);

            mPrivateFlags &= ~PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY;
            showTopAndBottomControls(true);

            if (mmr != null) {
                task.cancel(false);
                task = null;
                mmr.release();
                mmr = null;
            }
            if (translateAnimator != null) {
                translateAnimator.setRepeatMode(ValueAnimator.REVERSE);
                translateAnimator.reverse();
            }
            fadeAnimator.setRepeatMode(ValueAnimator.REVERSE);
            fadeAnimator.reverse();
        }

        void showSeekingVideoThumb(boolean show) {
            if (show) {
                mScrimView.setVisibility(VISIBLE);
                mSeekingVideoThumbText.setVisibility(VISIBLE);
            } else {
                mScrimView.setVisibility(GONE);
                mSeekingVideoThumbText.setVisibility(GONE);
            }
        }

        void showSeekingTextProgress(boolean show) {
            if (show) {
                final int progress = current;
                mSeekingProgressDurationText.setText(getProgressDurationText(progress));
                mSeekingProgress.setMax(mVideoDuration);
                mSeekingProgress.setProgress(progress);
                mSeekingTextProgressFrame.setVisibility(VISIBLE);
            } else {
                mSeekingTextProgressFrame.setVisibility(GONE);
            }
        }

        SpannableString getProgressDurationText(int progress) {
            if (progressTextSpan == null) {
                progressTextSpan = new ForegroundColorSpan(mColorAccent);
            }
            final String ps = TimeUtil.formatTimeByColon(progress);
            final SpannableString ss = new SpannableString(
                    mResources.getString(R.string.progress_duration, ps, mVideoDurationString));
            ss.setSpan(progressTextSpan, 0, ps.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ss;
        }

        void recycleVideoThumb() {
            Drawable thumb = mSeekingVideoThumbText.getCompoundDrawables()[3];
            // Removes the drawable that holds a reference to the bitmap to be recycled,
            // in case we still use the recycled bitmap on the next drawing of the TextView.
            mSeekingVideoThumbText.setCompoundDrawables(null, null, null, null);
            if (thumb instanceof BitmapDrawable) {
                ((BitmapDrawable) thumb).getBitmap().recycle();
            }
        }

        @SuppressLint("StaticFieldLeak")
        class UpdateVideoThumbTask extends AsyncTask<Void, Object, Void> {
            static final float RATIO = 0.25f;
            int last = -1;

            @Override
            protected Void doInBackground(Void... voids) {
                while (!isCancelled()) {
                    int now = current;
                    if (now == last) continue;
                    last = now;

                    final int width = (int) (mTextureView.getWidth() * RATIO + 0.5f);
                    final int height = (int) (mTextureView.getHeight() * RATIO + 0.5f);

                    Bitmap thumb = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        thumb = mmr.getScaledFrameAtTime(now * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height);
                    } else {
                        Bitmap tmp = mmr.getFrameAtTime(now * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if (tmp != null) {
                            thumb = BitmapUtil.createScaledBitmap(tmp, width, height, true);
                        }
                    }
                    if (thumb == null) continue;
                    thumb = BitmapUtil.createRoundCornerBitmap(
                            thumb, mSeekingVideoThumbCornerRadius, true);

                    publishProgress(getProgressDurationText(now), new BitmapDrawable(mResources, thumb));
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Object... objs) {
                recycleVideoThumb();
                mSeekingVideoThumbText.setText((CharSequence) objs[0]);
                mSeekingVideoThumbText.setCompoundDrawablesWithIntrinsicBounds(
                        null, null, null, (Drawable) objs[1]);
            }
        }
    };

    @Nullable
    private OnReturnClickListener mOnReturnClickListener;

    @Nullable
    private OpCallback mOpCallback;

    /** The listener for all the events related to video we publish. */
    @Nullable
    private VideoListener mVideoListener;

    private MediaPlayer mMediaPlayer;
    private Surface mSurface;

    /** The Uri for the video to play. */
    @Nullable
    private Uri mVideoUri;

    private int mVideoWidth;
    private int mVideoHeight;

    /** How long the playback will last for. */
    private int mVideoDuration;
    /** No duration is available (for example, the video info has not been resolved). */
    public static final int INVALID_DURATION = -1;

    /** The string representation of the video duration. */
    private String mVideoDurationString = DEFAULT_STRING_VIDEO_DURATION;
    private static final String DEFAULT_STRING_VIDEO_DURATION = "00:00";

    /**
     * Caches the speed at which the player works, used on saving instance state and maybe
     * retrieved on state restore.
     */
    private float mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
    /**
     * Caches the speed the user sets for the player at any time, even when the player has not
     * been created. This may fail if the value is not supported by the framework.
     */
    private float mUserPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;

    public static final float DEFAULT_PLAYBACK_SPEED = 1.0f;

    /**
     * How much of the network-based video has been buffered from the media stream received
     * through progressive HTTP download
     */
    private int mBuffering;

    /**
     * Recording the seek position used when playback is just started.
     * Normally this is requested by the user (e.g., dragging the video progress bar)
     * or saved when the user leaves current UI.
     */
    private int mSeekOnPlay;

    /** The amount of time we are stepping forward or backward for fast-forward and fast-rewind. */
    private static final int FAST_FORWARD_REWIND_INTERVAL = 15000; // ms

    private final Runnable mRefreshVideoProgressRunnable = new Runnable() {
        @Override
        public void run() {
            final int progress = getVideoProgress();
            if (isTopAndBottomControlsShown() && isPlaying()) {
                // Dynamic delay to keep pace with the actual progress of the video most accurately.
                postDelayed(this, 1000 - progress % 1000);
            }
            refreshVideoProgress(progress);
        }
    };

    /** Maximum brightness of the window */
    public static final int MAX_BRIGHTNESS = 255;

    /**
     * The ratio of the progress of the volume seek bar to the current media stream volume,
     * used to improve the smoothness of the volume progress slider, esp. when the user changes
     * its progress though horizontal screen track touches.
     */
    private static final int RATIO_VOLUME_PROGRESS_TO_VOLUME = 20;

    private final AudioManager mAudioManager;
    private final AudioAttributes mAudioAttrs;
    private final AudioFocusRequest mAudioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                // Audio focus gained
                case AudioManager.AUDIOFOCUS_GAIN:
                    play();
                    break;

                // Loss of audio focus of unknown duration. But releasing the MediaPlayer
                // is not needed as the view is still showing to the user.
                case AudioManager.AUDIOFOCUS_LOSS:
                    pause(false);
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
    private final VolumeReceiver mVolumeReceiver;

    private final MediaSessionCompat mSession;
    private final HeadsetEventsReceiver mHeadsetEventsReceiver;

    private final Runnable mHideBrightnessSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            showBrightnessSeekBar(false);
        }
    };
    private final Runnable mHideVolumeSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            showVolumeSeekBar(false);
        }
    };
    private final Runnable mHideTopAndBottomControlsRunnable = new Runnable() {
        @Override
        public void run() {
            showTopAndBottomControls(false);
        }
    };

    private ViewDragHelper mDragHelper;
    private int mOpenStateInLayout;
    private static final int FLAG_IS_OPENING = 0x2; // DrawerLayout.LayoutParams#FLAG_IS_OPENING
    private static final int FLAG_IS_CLOSING = 0x4; // DrawerLayout.LayoutParams#FLAG_IS_CLOSING
    private final Runnable mOpenOrClosePlayListRunnable = new Runnable() {
        @Override
        public void run() {
            if ((mOpenStateInLayout & FLAG_IS_OPENING) != 0) {
                openDrawer(mPlayList);
            } else if ((mOpenStateInLayout & FLAG_IS_CLOSING) != 0) {
                closeDrawer(mPlayList);
            }
            mOpenStateInLayout = 0;
        }
    };

    private static Field sLeftDraggerField;
    private static Field sRightDraggerField;
    private static Field sOpenStateField;

    private static Field sListPopupField;
    private static Field sPopupField;
    private static Field sPopupDecorViewField;
    private static Field sForceIgnoreOutsideTouchField;
    private static Field sPopupOnDismissListenerField;

    static {
        try {
            // The AppCompatSpinner class will automatically be used when we use {@link Spinner}
            // in our layouts. But on Marshmallow and above, if we retrieve the `mPopup` field from
            // the AppCompatSpinner class, we will get `null` instead of a runtime instance of
            // ListPopupWindow as that field with the same name as the one declared in the Spinner
            // class will not be initialized in the constructor of the class it is declared in at all.
            Class<?> spinnerClass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                    Spinner.class : AppCompatSpinner.class;
            sListPopupField = spinnerClass.getDeclaredField("mPopup");
            sListPopupField.setAccessible(true);

            Class<?> listPopupClass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? android.widget.ListPopupWindow.class
                    : androidx.appcompat.widget.ListPopupWindow.class;
            sPopupField = listPopupClass.getDeclaredField("mPopup");
            sPopupField.setAccessible(true);

            try {
                // On platforms after O MR1, we can not use reflections to access the internal hidden
                // fields and methods, so use the slower processing logic that set a touch listener
                // for the popup's decorView and consume the `down` and `outside` events according to
                // the same conditions to its original `onTouchEvent()` method through omitting
                // the invocations to the popup's dismiss() method, so that the popup remains showing
                // within an outside touch event stream till the up event is reached, in which, instead,
                // we will dismiss it manually.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    //noinspection JavaReflectionMemberAccess
                    sPopupDecorViewField = PopupWindow.class.getDeclaredField("mDecorView");
                    sPopupDecorViewField.setAccessible(true);
                } else {
                    // @see ListPopupWindow#setForceIgnoreOutsideTouch() — public hidden method
                    //                                                   — restricted to internal use only
                    // @see ListPopupWindow#show()
                    sForceIgnoreOutsideTouchField = listPopupClass.getDeclaredField("mForceIgnoreOutsideTouch");
                    sForceIgnoreOutsideTouchField.setAccessible(true);
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

            try {
                //noinspection JavaReflectionMemberAccess
                sPopupOnDismissListenerField = PopupWindow.class.getDeclaredField("mOnDismissListener");
                sPopupOnDismissListenerField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            sListPopupField = sPopupField = null;
        }

        try {
            Class<DrawerLayout> drawerLayoutClass = DrawerLayout.class;
            sLeftDraggerField = drawerLayoutClass.getDeclaredField("mLeftDragger");
            sLeftDraggerField.setAccessible(true);
            sRightDraggerField = drawerLayoutClass.getDeclaredField("mRightDragger");
            sRightDraggerField.setAccessible(true);

            Class<LayoutParams> lpClass = LayoutParams.class;
            sOpenStateField = lpClass.getDeclaredField("openState");
            sOpenStateField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            sLeftDraggerField = sRightDraggerField = null;
            sOpenStateField = null;
        }
    }

    /* class initializer */ {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAudioAttrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
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
        this(context, null);
    }

    public TextureVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextureVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(Color.BLACK);
        mContext = context;
        mResources = getResources();
        mStringPlay = mResources.getString(R.string.play);
        mStringPause = mResources.getString(R.string.pause);
        mSpeedsStringArray = mResources.getStringArray(R.array.speeds);
        mSeekingViewHorizontalOffset = mResources.getDimension(R.dimen.seekingViewHorizontalOffset);
        mSeekingVideoThumbCornerRadius = mResources.getDimension(R.dimen.seekingVideoThumbCornerRadius);

        TypedArray a = context.obtainStyledAttributes(THEME_ATTRS);
        mColorAccent = a.getColor(0, Color.WHITE);
        a.recycle();

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Inflate the content
        inflate(context, R.layout.view_video, this);
        mContentView = findViewById(R.id.content_videoview);
        mPlayList = findViewById(R.id.recycler_playlist);
        mTextureView = findViewById(R.id.textureView);
        mScrimView = findViewById(R.id.scrim);
        mTopControlsFrame = findViewById(R.id.frame_topControls);
        mTitleText = findViewById(R.id.text_title);
        mBrightnessText = findViewById(R.id.text_brightness);
        mBrightnessValueText = findViewById(R.id.text_brightnessValue);
        mBrightnessSeekBar = findViewById(R.id.sb_brightness);
        mVolumeText = findViewById(R.id.text_volume);
        mVolumeValueText = findViewById(R.id.text_volumeValue);
        mVolumeSeekBar = findViewById(R.id.sb_volume);
        mSeekingVideoThumbText = findViewById(R.id.text_seekingVideoThumb);
        mSeekingTextProgressFrame = findViewById(R.id.frame_seekingTextProgress);
        mSeekingProgressDurationText = findViewById(R.id.text_seeking_progress_duration);
        mSeekingProgress = findViewById(R.id.pb_seekingProgress);
        mLoadingImage = findViewById(R.id.image_loading);
        mBottomControlsFrame = findViewById(R.id.frame_bottomControls);
        inflateBottomControls();

        mNavInitialPaddingTop = mTopControlsFrame.getPaddingTop();

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.TextureVideoView,
                defStyleAttr, 0);
        setTitle(ta.getString(R.styleable.TextureVideoView_title));
        setVideoResourceId(ta.getResourceId(R.styleable.TextureVideoView_src, 0));
        setClipViewBounds(ta.getBoolean(R.styleable.TextureVideoView_clipViewBounds, false));
        setFullscreenMode(ta.getBoolean(R.styleable.TextureVideoView_inFullscreenMode, false), 0);
        ta.recycle();

        addDrawerListener(new SimpleDrawerListener() {
            @Override
            public void onDrawerStateChanged(int newState) {
                if (newState == STATE_SETTLING && isTopAndBottomControlsShown() && sOpenStateField != null) {
                    try {
                        final int state = sOpenStateField.getInt(mPlayList.getLayoutParams());
                        if ((state & FLAG_IS_OPENING) != 0) {
                            showTopAndBottomControls(false);
                            mPrivateFlags |= PFLAG_TOP_BOTTOM_CONTROLS_SHOWN_BEFORE_OPENING_PLAYLIST;
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                if (isTopAndBottomControlsShown()) {
                    showTopAndBottomControls(false);
                    mPrivateFlags |= PFLAG_TOP_BOTTOM_CONTROLS_SHOWN_BEFORE_OPENING_PLAYLIST;
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                if ((mPrivateFlags & PFLAG_TOP_BOTTOM_CONTROLS_SHOWN_BEFORE_OPENING_PLAYLIST) != 0) {
                    showTopAndBottomControls(true);
                    mPrivateFlags &= ~PFLAG_TOP_BOTTOM_CONTROLS_SHOWN_BEFORE_OPENING_PLAYLIST;
                }
            }
        });

        //noinspection all (ClickableViewAccessibility)
        mContentView.setOnTouchListener(mOnChildTouchListener);
        mContentView.setTouchInterceptor(mOnChildTouchListener);

        mTitleText.setOnClickListener(mOnClickListener);

        // Prepare video playback
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mSurface = new Surface(surface);
                openVideo();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeVideo();
                mSurface.release();
                mSurface = null;
                return true;
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        mBrightnessSeekBar.setOnVerticalSeekBarChangeListener(mOnVerticalSeekBarChangeListener);
        mVolumeSeekBar.setOnVerticalSeekBarChangeListener(mOnVerticalSeekBarChangeListener);

        mBrightnessSeekBar.setMax(MAX_BRIGHTNESS);
        mBrightnessSeekBar.setEnabled(false);

        mAudioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        mVolumeSeekBar.setMax(RATIO_VOLUME_PROGRESS_TO_VOLUME *
                mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)); // 20 * 15 = 300
        mVolumeReceiver = new VolumeReceiver(context) {
            @Override
            public void onMusicVolumeChange(int prevolume, int volume) {
                mVolumeSeekBar.setEnabled(true);
                refreshVolumeProgress(volumeToProgress(volume));
            }

            @Override
            public void onRingerModeChange(int mode) {
                final int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                switch (mode) {
                    case AudioManager.RINGER_MODE_NORMAL:
                        mVolumeSeekBar.setEnabled(true);
                        if (volume != getVolume()) {
                            refreshVolumeProgress(volumeToProgress(volume));
                        }
                        break;
                    case AudioManager.RINGER_MODE_SILENT:
                    case AudioManager.RINGER_MODE_VIBRATE:
                        if (volume == 0) {
                            mVolumeSeekBar.setEnabled(false);
                        }
                        break;
                }
            }
        };

        Typeface tf = Typeface.createFromAsset(mResources.getAssets(), "fonts/avenirnext-medium.ttf");
        mSeekingVideoThumbText.setTypeface(tf);
        mSeekingProgressDurationText.setTypeface(tf);

        mLoadingDrawable = new CircularProgressDrawable(context);
        mLoadingDrawable.setColorSchemeColors(mColorAccent);
        mLoadingDrawable.setStrokeWidth(mResources.getDimension(R.dimen.circular_progress_stroke_width));
        mLoadingDrawable.setStrokeCap(Paint.Cap.ROUND);
        mLoadingImage.setImageDrawable(mLoadingDrawable);

        mSession = new MediaSessionCompat(context, TAG,
                new ComponentName(context, HeadsetButtonReceiver.class), null);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
        mSession.setCallback(new SessionCallback());
        mHeadsetEventsReceiver = new HeadsetEventsReceiver(context) {
            @Override
            public void onHeadsetPluggedOutOrBluetoothDisconnected() {
                pause(true);
            }
        };
    }

    private void inflateBottomControls() {
        if (mBottomControlsFrame.getChildCount() > 0) {
            mBottomControlsFrame.removeViewAt(0);
        }

        View view;
        if (isInFullscreenMode()) {
            view = View.inflate(mContext, R.layout.bottom_controls_fullscreen, mBottomControlsFrame);
            mSkipNextButton = view.findViewById(R.id.bt_skipNext);
            mVideoProgressDurationText = view.findViewById(R.id.text_videoProgressDuration);
            mSpeedSpinner = view.findViewById(R.id.spinner_speed);
            mChooseEpisodeButton = view.findViewById(R.id.bt_chooseEpisode);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    mContext, R.layout.item_speed_spinner, mSpeedsStringArray);
            adapter.setDropDownViewResource(R.layout.dropdown_item_speed_spinner);

            mSpeedSpinner.setOnItemSelectedListener(mOnItemSelectedListener);
            mSpeedSpinner.setPopupBackgroundResource(R.color.bg_popup);
            mSpeedSpinner.setAdapter(adapter);
            mSpeedSpinner.setSelection(indexOfPlaybackSpeed(mPlaybackSpeed), false);
            mSpeedSpinner.setOnTouchListener(mOnChildTouchListener);

            if (sListPopupField != null && sPopupField != null) {
                try {
                    mSpinnerListPopup = sListPopupField.get(mSpeedSpinner);
                    // Works on platforms prior to P
                    if (sForceIgnoreOutsideTouchField != null) {
                        // Sets the field `mForceIgnoreOutsideTouch` of the ListPopupWindow to `true`
                        // to discourage it from setting `outsideTouchable` to `true` for the popup
                        // in its `show()` method, so that the popup receives no outside touch event
                        // to dismiss itself.
                        sForceIgnoreOutsideTouchField.setBoolean(mSpinnerListPopup, true);
                    }
                    mSpinnerPopup = (PopupWindow) sPopupField.get(mSpinnerListPopup);
                    // This popup window reports itself as focusable so that it can intercept the
                    // back button. However, if we are currently in fullscreen mode, what will the
                    // aftereffect be？Yeah，the system bars will become visible to the user and
                    // even affect the user to choose a reasonable speed for the player.
                    // For all of the reasons, we're supposed to prevent it from doing that.
                    mSpinnerPopup.setFocusable(false);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            checkSkipNextAndChooseEpisodeWidgetsVisibilities();

            mSkipNextButton.setOnClickListener(mOnClickListener);
            mChooseEpisodeButton.setOnClickListener(mOnClickListener);

            mVideoProgressText = null;
            mVideoDurationText = null;
            mMinimizeButton = null;
            mFullscreenButton = null;
        } else {
            view = View.inflate(mContext, R.layout.bottom_controls, mBottomControlsFrame);
            mVideoProgressText = view.findViewById(R.id.text_videoProgress);
            mVideoDurationText = view.findViewById(R.id.text_videoDuration);
            mMinimizeButton = view.findViewById(R.id.bt_minimize);
            mFullscreenButton = view.findViewById(R.id.bt_fullscreen);

            mMinimizeButton.setOnClickListener(mOnClickListener);
            mFullscreenButton.setOnClickListener(mOnClickListener);

            mSkipNextButton = null;
            mVideoProgressDurationText = null;
            mSpeedSpinner = null;
            mSpinnerListPopup = mSpinnerPopup = null;
            mChooseEpisodeButton = null;
        }

        mVideoSeekBar = view.findViewById(R.id.sb_video);
        mVideoSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

        mToggleButton = view.findViewById(R.id.bt_toggle);
        mToggleButton.setOnClickListener(mOnClickListener);
        adjustToggleState(isPlaying());
    }

    private void adjustToggleState(boolean playing) {
        if (playing) {
            mToggleButton.setImageResource(R.drawable.bt_pause_33dp);
            mToggleButton.setContentDescription(mStringPause);
        } else {
            mToggleButton.setImageResource(R.drawable.bt_play_33dp);
            mToggleButton.setContentDescription(mStringPlay);
        }
    }

    private int indexOfPlaybackSpeed(float speed) {
        final String speedString = speed + "x";
        for (int i = 0; i < mSpeedsStringArray.length; i++) {
            if (mSpeedsStringArray[i].equals(speedString)) return i;
        }
        return -1;
    }

    private void checkSkipNextAndChooseEpisodeWidgetsVisibilities() {
        if (isInFullscreenMode()) {
            final boolean canSkipToPrevious = mOpCallback != null && mOpCallback.canSkipToPrevious();
            final boolean canSKipToNext = mOpCallback != null && mOpCallback.canSkipToNext();
            mSkipNextButton.setVisibility(canSKipToNext ? VISIBLE : GONE);
            if ((canSkipToPrevious || canSKipToNext)) {
                mChooseEpisodeButton.setVisibility(VISIBLE);
                setDrawerLockModeInternal(LOCK_MODE_UNLOCKED, mPlayList);
            } else {
                mChooseEpisodeButton.setVisibility(GONE);
                setDrawerLockModeInternal(LOCK_MODE_LOCKED_CLOSED, mPlayList);
            }
        } else {
            setDrawerLockModeInternal(LOCK_MODE_LOCKED_CLOSED, mPlayList);
        }
    }

    /**
     * Sets the listener to monitor video events.
     *
     * @see VideoListener
     */
    public void setVideoListener(@Nullable VideoListener listener) {
        mVideoListener = listener;
    }

    /**
     * Sets the listener to receive the operation callbacks from this view.
     *
     * @see OpCallback
     */
    public void setOpCallback(@Nullable OpCallback opCallback) {
        mOpCallback = opCallback;
        if (mOpCallback == null) {
            mBrightnessSeekBar.setEnabled(false);
        } else {
            mBrightnessSeekBar.setEnabled(true);
            refreshBrightnessProgress(getBrightness());
        }
    }

    /**
     * Sets the listener to perform a go-back action — that is this view should be detached from
     * the window it is attached to.
     *
     * @see OnReturnClickListener
     */
    public void setOnReturnClickListener(@Nullable OnReturnClickListener listener) {
        mOnReturnClickListener = listener;
    }

    /**
     * @param <VH> A class that extends {@link RecyclerView.ViewHolder} that was used by the adapter.
     * @return the RecyclerView adapter for the video playlist or `null` if not set
     */
    @Nullable
    public <VH extends RecyclerView.ViewHolder> PlayListAdapter<VH> getPlayListAdapter() {
        //noinspection unchecked
        return (PlayListAdapter<VH>) mPlayList.getAdapter();
    }

    /**
     * Sets an adapter for the RecyclerView that displays the video playlist
     *
     * @param adapter see {@link PlayListAdapter}
     * @param <VH>    A class that extends {@link RecyclerView.ViewHolder} that will be used by the adapter.
     */
    public <VH extends RecyclerView.ViewHolder> void setPlayListAdapter(@Nullable PlayListAdapter<VH> adapter) {
        if (adapter != null && mPlayList.getLayoutManager() == null) {
            mPlayList.setLayoutManager(new LinearLayoutManager(mContext));
            mPlayList.addItemDecoration(
                    new DividerItemDecoration(mContext, DividerItemDecoration.VERTICAL));
            mPlayList.setHasFixedSize(true);
        }
        mPlayList.setAdapter(adapter);
    }

    /**
     * @return title of the video.
     */
    public String getTitle() {
        return mTitleText.getText().toString();
    }

    /**
     * Sets the title of the video to play.
     */
    public void setTitle(String title) {
        mTitleText.setText(title);
    }

    /**
     * Sets the raw resource ID of the video to play.
     */
    public void setVideoResourceId(@RawRes int resId) {
        setVideoPath(resId == 0 ?
                null : "android.resource://" + mContext.getPackageName() + File.separator + resId);
    }

    /**
     * Sets the file path of the video to play
     */
    public void setVideoPath(@Nullable String path) {
        setVideoUri(path == null ? null : Uri.parse(path));
    }

    /**
     * Sets the Uri for the video to play
     */
    public void setVideoUri(@Nullable Uri uri) {
        if (!ObjectsCompat.equals(uri, mVideoUri)) {
            mVideoUri = uri;
            mVideoDuration = 0;
            mVideoDurationString = DEFAULT_STRING_VIDEO_DURATION;
            mPrivateFlags &= ~PFLAG_VIDEO_INFO_RESOLVED;
            if (mMediaPlayer == null) {
                // Removes the PLFAG_VIDEO_PLAYBACK_COMPLETED flag and resets mSeekOnPlay to 0
                // in case the MediaPlayer was previously released and has not been initialized yet.
                mPrivateFlags &= ~PFLAG_VIDEO_PLAYBACK_COMPLETED;
                mSeekOnPlay = 0;
                openVideoInternal();
            } else {
                restartVideo();
            }
        }
    }

    /**
     * @return the brightness of the window this view is attached to or 0
     * if no {@link OpCallback} is set.
     */
    @IntRange(from = 0, to = MAX_BRIGHTNESS)
    public int getBrightness() {
        if (mOpCallback != null) {
            return ScreenUtils.getWindowBrightness(mOpCallback.getWindow());
        }
        return 0;
    }

    /**
     * Sets the brightness for the window to which this view is attached
     * <p>
     * <strong>NOTE:</strong> When changing current view's brightness, you should invoke
     * this method instead of a direct call to {@link ScreenUtils#setWindowBrightness(Window, int)}
     */
    public void setBrightness(@IntRange(from = 1, to = MAX_BRIGHTNESS) int brightness) {
        if (mOpCallback != null) {
            // Changes the brightness of the current Window
            if (getBrightness() != brightness) {
                ScreenUtils.setWindowBrightness(mOpCallback.getWindow(), brightness);
            }
            // Sets that progress for the brightness SeekBar
            refreshBrightnessProgress(brightness);
//            // Shows the brightness SeekBar
//            if (mOpCallback == null || !mOpCallback.isInPictureInPictureMode()) {
//                showBrightnessSeekBar(true);
//            }
        }
    }

    private int volumeToProgress(int volume) {
        return volume * RATIO_VOLUME_PROGRESS_TO_VOLUME;
    }

    private int progressToVolume(int progress) {
        return (int) ((float) progress / RATIO_VOLUME_PROGRESS_TO_VOLUME + 0.5f);
    }

    /**
     * @return the current volume of the media, maybe 0 if the mode is silent or vibration
     */
    @IntRange(from = 0, to = 15)
    public int getVolume() {
        return mVolumeSeekBar.isEnabled() ? progressToVolume(mVolumeSeekBar.getProgress()) : 0;
    }

    /**
     * Sets the media volume of the system used in this player
     */
    public void setVolume(@IntRange(from = 0, to = 15) int volume) {
        if (getVolume() != volume) {
            // Changes the system's media volume
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
//            // Sets that progress for the volume SeekBar
//            refreshVolumeProgress(volumeToProgress(volume));
//            // Shows the volume SeekBar
//            if (mOpCallback == null || !mOpCallback.isInPictureInPictureMode()) {
//                showVolumeSeekBar(true);
//            }
        }
    }

    /**
     * @return <code>true</code> if the bounds of this view is clipped to adapt for the video
     */
    public boolean isClipViewBounds() {
        return (mPrivateFlags & PFLAG_CLIP_VIEW_BOUNDS) != 0;
    }

    /**
     * Sets whether this view should crop its border to fit the aspect ratio of the video.
     * <p>
     * <strong>NOTE:</strong> After invoking this method, you may need to directly call
     * {@link #requestLayout()} to refresh the layout or do that via an implicit invocation
     * which will call it internally (such as {@link #setLayoutParams(ViewGroup.LayoutParams)}).
     *
     * @param clip If true, the bounds of this view will be clipped;
     *             otherwise, black bars will be filled to the view's remaining area.
     */
    public void setClipViewBounds(boolean clip) {
        if (isClipViewBounds() == clip) {
            return;
        }
        mPrivateFlags = mPrivateFlags & ~PFLAG_CLIP_VIEW_BOUNDS
                | (clip ? PFLAG_CLIP_VIEW_BOUNDS : 0);
        if (clip) {
            ViewCompat.setBackground(this, null);
        } else {
            setBackgroundColor(Color.BLACK);
        }
    }

    /**
     * @return whether this view is in fullscreen mode or not
     * @see #setFullscreenMode(boolean, int)
     */
    public boolean isInFullscreenMode() {
        return (mPrivateFlags & PFLAG_IN_FULLSCREEN_MODE) != 0;
    }

    /**
     * Sets this view to put it into fullscreen mode or not.
     * If set, minimize and fullscreen buttons will disappear (visibility set to {@link #GONE}) and
     * the specified padding `navTopInset` will be inserted at the top of the top controls' frame.
     * <p>
     * <strong>NOTE:</strong> This method does not resize the view as the system bars and
     * the screen orientation may need to be adjusted simultaneously, meaning that you should
     * implement your own logic to resize it.
     *
     * @param fullscreen  Whether this view should go into fullscreen mode.
     * @param navTopInset The downward offset of the navigation widget relative to its initial
     *                    position. Normally, when setting fullscreen mode, you need to move it
     *                    down a proper distance, so that it appears below the status bar.
     */
    public void setFullscreenMode(boolean fullscreen, int navTopInset) {
        try {
            if (isInFullscreenMode() == fullscreen) {
                return;
            }

            mPrivateFlags = mPrivateFlags & ~PFLAG_IN_FULLSCREEN_MODE
                    | (fullscreen ? PFLAG_IN_FULLSCREEN_MODE : 0);
            inflateBottomControls();
        } finally {
            final int paddingTop = mNavInitialPaddingTop + navTopInset;
            if (mTopControlsFrame.getPaddingTop() != paddingTop) {
                mTopControlsFrame.setPadding(
                        mTopControlsFrame.getPaddingLeft(),
                        paddingTop,
                        mTopControlsFrame.getPaddingRight(),
                        mTopControlsFrame.getPaddingBottom());
            }
        }
    }

    @Override
    public int getDrawerLockMode(@NonNull View drawerView) {
        checkDrawerView(drawerView,
                ((LayoutParams) drawerView.getLayoutParams()).gravity
                        & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK);
        return getDrawerLockModeInternal(drawerView);
    }

    private int getDrawerLockModeInternal(@NonNull View drawerView) {
        return getDrawerLockMode(
                ((LayoutParams) drawerView.getLayoutParams()).gravity
                        & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK);
    }

    @Override
    public void setDrawerLockMode(int lockMode, @NonNull View drawerView) {
        checkDrawerView(drawerView,
                ((LayoutParams) drawerView.getLayoutParams()).gravity
                        & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK);
        setDrawerLockModeInternal(lockMode, drawerView);
    }

    private void setDrawerLockModeInternal(int lockMode, @NonNull View drawerView) {
        final int hg = ((LayoutParams) drawerView.getLayoutParams()).gravity
                & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        if (getDrawerLockMode(hg) != lockMode) {
            setDrawerLockMode(lockMode, hg);
        }
    }

    @SuppressLint("RtlHardcoded")
    private void checkDrawerView(View drawerView, int horizontalGravity) {
        final int ld = ViewCompat.getLayoutDirection(this);
        final int absHG = GravityCompat.getAbsoluteGravity(horizontalGravity, ld);
        if ((absHG & Gravity.LEFT) != Gravity.LEFT && (absHG & Gravity.RIGHT) != Gravity.RIGHT) {
            throw new IllegalArgumentException(
                    "View " + drawerView + " is not a drawer with appropriate layout_gravity");
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            final float videoAspectRatio = (float) mVideoWidth / mVideoHeight;

            final boolean clipBounds = isClipViewBounds();

            int width = widthSize;
            int height = heightSize;
            if (!clipBounds) {
                final int horizontalPaddings = getPaddingLeft() + getPaddingRight();
                final int verticalPaddings = getPaddingTop() + getPaddingBottom();
                width -= horizontalPaddings;
                height -= verticalPaddings;
            }
            final float aspectRatio = (float) width / height;

            ViewGroup.LayoutParams rvlp = mPlayList.getLayoutParams();
            // When in landscape mode, we need to make the playlist appear to the user appropriately.
            // Its width should not occupy too much display space，so as not to affect the user
            // to preview the video content.
            if (isInFullscreenMode() && aspectRatio > 1.0f) {
                //XXX: to make this more adaptable
                rvlp.width = (int) (width / 2f + 0.5f);
            } else {
                rvlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            }

            ConstraintLayout.LayoutParams tvlp = (ConstraintLayout.LayoutParams) mTextureView.getLayoutParams();
            if (videoAspectRatio >= aspectRatio) {
                tvlp.width = width;
                tvlp.height = (int) (width / videoAspectRatio + 0.5f);
            } else {
                tvlp.width = (int) (height * videoAspectRatio + 0.5f);
                tvlp.height = height;
            }

            if (clipBounds) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(tvlp.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(tvlp.height, MeasureSpec.EXACTLY));
                return;
            }
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && sOpenStateField != null) {
            LayoutParams lp = (LayoutParams) mPlayList.getLayoutParams();
            try {
                mOpenStateInLayout = sOpenStateField.getInt(lp);
                if ((mOpenStateInLayout & (FLAG_IS_OPENING | FLAG_IS_CLOSING)) != 0) {
                    if (mDragHelper == null) {
                        final int hg = lp.gravity & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK;
                        final int ld = ViewCompat.getLayoutDirection(this);
                        final int absHG = GravityCompat.getAbsoluteGravity(hg, ld);
                        //noinspection all (RtlHardcoded)
                        mDragHelper = (ViewDragHelper) (absHG == Gravity.LEFT ?
                                sLeftDraggerField.get(this) : sRightDraggerField.get(this));
                    }
                    // Delays the running animation to ensure the playlist will open or close normally
                    mDragHelper.abort();
                    removeCallbacks(mOpenOrClosePlayListRunnable);
                    post(mOpenOrClosePlayListRunnable);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        super.onLayout(changed, l, t, r, b);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mVolumeReceiver.register();
        mHeadsetEventsReceiver.register(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        mSession.setActive(true);

        // Initialize the volume SeekBar according to the current volume stream info.
        final int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        refreshVolumeProgress(volumeToProgress(volume));
        mVolumeSeekBar.setEnabled(!(volume == 0
                && mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVolumeReceiver.unregister();
        mHeadsetEventsReceiver.unregister();
        mSession.setActive(false);

        // Removes all pending actions
        removeCallbacks(mRefreshVideoProgressRunnable);
        removeCallbacks(mHideBrightnessSeekBarRunnable);
        removeCallbacks(mHideVolumeSeekBarRunnable);
        removeCallbacks(mHideTopAndBottomControlsRunnable);
        removeCallbacks(mOpenOrClosePlayListRunnable);
    }

    /**
     * Initialize the MediaPlayer object and prepare for the video playback.
     * Normally, you should invoke this method to resume video playback instead of {@link #play()}
     * whenever the Activity's restart() or resume() method is called unless the player won't
     * be released as the Activity's lifecycle changes.
     * <p>
     * <strong>NOTE:</strong> When the window this view is attached to leaves the foreground,
     * if the video has already been paused by the user, the MediaPlayer will not be instantiated
     * even if you call this method when this view is displayed in front of the user again and
     * only when the user manually clicks to play, will it be initialized (see {@link #play()}),
     * but you should still call this method as usual.
     *
     * @param replayIfCompleted whether to replay the video if it is over
     * @see #closeVideo()
     * @see #play()
     */
    public void openVideo(boolean replayIfCompleted) {
        if (replayIfCompleted || !isPlaybackCompleted()) {
            openVideoInternal();
        }
    }

    /** @see #openVideo(boolean) */
    public void openVideo() {
        openVideo(false);
    }

    private void openVideoInternal() {
        if (mMediaPlayer == null && mSurface != null && mVideoUri != null
                && (mPrivateFlags & PFLAG_VIDEO_PAUSED_BY_USER) == 0) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setSurface(mSurface);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mMediaPlayer.setAudioAttributes(mAudioAttrs);
            } else {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mPrivateFlags &= ~PFLAG_PLAYER_IS_PREPARING;
                    mPrivateFlags |= PFLAG_VIDEO_INFO_RESOLVED;
                    mVideoDuration = mp.getDuration();
                    mVideoDurationString = TimeUtil.formatTimeByColon(mVideoDuration);
                    showLoadingView(false);
                    play();
                }
            });
            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    showLoadingView(false);
                }
            });
            mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mBuffering = (int) (mVideoDuration * percent / 100f + 0.5f);
                    mVideoSeekBar.setSecondaryProgress(mBuffering);
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Error occurred while playing video:" +
                                " what= " + what + "; extra= " + extra);
                    }
                    showVideoErrorMsg(extra);
                    mPrivateFlags |= PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO;
                    mPrivateFlags &= ~(PFLAG_PLAYER_IS_PREPARING
                            | PFLAG_VIDEO_PLAYBACK_COMPLETED);
                    pause(false);
                    return true;
                }
            });
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mPrivateFlags |= PFLAG_VIDEO_PLAYBACK_COMPLETED;
                    onVideoStopped();
                }
            });
            mMediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    final int oldw = mVideoWidth;
                    final int oldh = mVideoHeight;
                    mVideoWidth = width;
                    mVideoHeight = height;
                    if (mVideoListener != null) {
                        mVideoListener.onVideoSizeChanged(oldw, oldh, width, height);
                    }
                    if (width != 0 && height != 0) requestLayout();
                }
            });

            startVideo();
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
            } catch (IOException e) {
                e.printStackTrace();
                //noinspection all (InlinedApi)
                showVideoErrorMsg(MediaPlayer.MEDIA_ERROR_IO);
                showLoadingView(false); // in case it is already showing
            }
        } else {
            showLoadingView(false);
        }
        cancelDraggingVideoSeekBar();
    }

    /**
     * Restarts playback of the video.
     */
    public void restartVideo() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            // Clear all play flags except for PFLAG_VIDEO_INFO_RESOLVED
            mPrivateFlags &= ~(PFLAG_PLAYER_IS_PREPARING
                    | PFLAG_VIDEO_IS_PLAYING
                    | PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO
                    | PFLAG_VIDEO_PAUSED_BY_USER
                    | PFLAG_VIDEO_PLAYBACK_COMPLETED);
            mSeekOnPlay = 0;
            // Resets below to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;
            startVideo();
        }
    }

    /**
     * Checks whether the MediaPlayer is playing.
     *
     * @return `true` if currently playing, `false` otherwise
     */
    public boolean isPlaying() {
        return (mPrivateFlags & PFLAG_VIDEO_IS_PLAYING) != 0;
    }

    public void toggle() {
        if (isPlaying()) {
            pause(true);
        } else {
            play();
        }
    }

    /**
     * Starts or resumes playback.
     * If previously paused, playback will continue from where it was paused.
     * If never started before, playback will start at the beginning.
     *
     * @see #pause(boolean)
     */
    public void play() {
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
            // Re-prepareAsync after error occurred
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;
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
                    mMediaPlayer.start();
                    mPrivateFlags |= PFLAG_VIDEO_IS_PLAYING;
                    mPrivateFlags &= ~(PFLAG_VIDEO_PAUSED_BY_USER | PFLAG_VIDEO_PLAYBACK_COMPLETED);
                    // Ensure the player's volume is at its maximum
                    if ((mPrivateFlags & PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY) != 0) {
                        mPrivateFlags &= ~PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY;
                        mMediaPlayer.setVolume(1.0f, 1.0f);
                    }
                    if (mUserPlaybackSpeed != mPlaybackSpeed) {
                        setPlaybackSpeed(mUserPlaybackSpeed, true);
                    }
                    if (mSeekOnPlay != 0) {
                        seekTo(mSeekOnPlay);
                        mSeekOnPlay = 0;
                    }
                    // Dispatch to listener when playback starts
                    if (mVideoListener != null) {
                        mVideoListener.onVideoStarted();
                    }
                    setKeepScreenOn(true);
                    adjustToggleState(true);
                    if (mOpCallback == null || !mOpCallback.isInPictureInPictureMode()) {
                        showTopAndBottomControls(true);
                    }
                    break;

                case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                    // do nothing
                    break;
            }
        }
    }

    /**
     * Pauses playback. Call {@link #play()} to resume.
     *
     * @param fromUser whether this interaction is triggered by the user
     * @see #play()
     */
    public void pause(boolean fromUser) {
        if (isPlaying()) {
            mMediaPlayer.pause();
            mPrivateFlags = mPrivateFlags & ~PFLAG_VIDEO_PAUSED_BY_USER
                    | (fromUser ? PFLAG_VIDEO_PAUSED_BY_USER : 0);
            onVideoStopped();
        }
    }

    /**
     * Pauses playback and releases resources associated with the MediaPlayer object.
     * Usually, whenever an Activity of an application is paused (its onPaused() method is called),
     * or stopped (its onStop() method is called), this method should be invoked to release
     * the MediaPlayer object, unless the application has a special need to keep the object around.
     *
     * @see #openVideo()
     * @see #openVideo(boolean)
     */
    public void closeVideo() {
        if (mMediaPlayer != null) {
            if (!isPlaybackCompleted()) {
                mSeekOnPlay = getVideoProgress();
            }
            pause(false);
            abandonAudioFocus();
            mMediaPlayer.release();
            mMediaPlayer = null;
            // Clear all play flags except PFLAG_VIDEO_PLAYBACK_COMPLETED and PFLAG_VIDEO_INFO_RESOLVED
            mPrivateFlags &= ~(PFLAG_PLAYER_IS_PREPARING
                    | PFLAG_ERROR_OCCURRED_WHILE_PLAYING_VIDEO
                    | PFLAG_VIDEO_VOLUME_TURNED_DOWN_AUTOMATICALLY);
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;
        }
        showLoadingView(false);
        cancelDraggingVideoSeekBar();
    }

    private void onVideoStopped() {
        mPrivateFlags &= ~PFLAG_VIDEO_IS_PLAYING;
        if (mVideoListener != null) {
            mVideoListener.onVideoStopped();
        }
        setKeepScreenOn(false);
        adjustToggleState(false);
        if (mOpCallback == null || !mOpCallback.isInPictureInPictureMode()) {
            showTopAndBottomControls(true);
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        } else {
            mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
        }
    }

    /**
     * @return the current playback speed of the video
     */
    public float getPlaybackSpeed() {
        return isPlaying() ? mPlaybackSpeed : 0;
    }

    /** Sets the speed for the video player */
    private void setPlaybackSpeed(float speed, boolean checkSpinnerSelection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mPlaybackSpeed != speed) {
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

                if (checkSpinnerSelection && mSpeedSpinner != null) {
                    mSpeedSpinner.setSelection(indexOfPlaybackSpeed(speed), true);
                }
            }
        }
    }

    /** Skips video to the specified time position. */
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
            mSeekOnPlay = progress;
            play();
        }
    }

    /** Fast-forward the video. */
    public void fastForward() {
        seekTo(getVideoProgress() + FAST_FORWARD_REWIND_INTERVAL);
    }

    /** Fast-rewind the video. */
    public void fastRewind() {
        seekTo(getVideoProgress() - FAST_FORWARD_REWIND_INTERVAL);
    }

    /**
     * @return <code>true</code> if the video playback is finished
     */
    public boolean isPlaybackCompleted() {
        return (mPrivateFlags & PFLAG_VIDEO_PLAYBACK_COMPLETED) != 0;
    }

    /**
     * @return whether or not the video is prepared for the player
     */
    private boolean isVideoPrepared() {
        return mMediaPlayer != null && (mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0;
    }

    /**
     * @return the current playback position of the video.
     */
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

    /**
     * Gets the duration of the video.
     *
     * @return the duration in milliseconds, if no duration is available (the duration is
     * not determined yet), then {@value INVALID_DURATION} is returned.
     */
    public int getVideoDuration() {
        if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
            return mVideoDuration;
        }
        return INVALID_DURATION;
    }

    /**
     * @return the width of the video, or 0 if there is no video or the width has not been
     * determined yet.
     */
    public int getVideoWidth() {
        if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
            return mVideoWidth;
        }
        return 0;
    }

    /**
     * @return the height of the video, or 0 if there is no video or the height has not been
     * determined yet.
     */
    public int getVideoHeight() {
        if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
            return mVideoHeight;
        }
        return 0;
    }

    public boolean isBrightnessSeekBarShown() {
        return mBrightnessSeekBar.getVisibility() == VISIBLE;
    }

    public void showBrightnessSeekBar(boolean show) {
        removeCallbacks(mHideBrightnessSeekBarRunnable);
        if (show) {
            postDelayed(mHideBrightnessSeekBarRunnable, TIMEOUT_SHOW_BRIGHTNESS_OR_VOLUME);
            if (isBrightnessSeekBarShown()) {
                return;
            }
        } else if (!isBrightnessSeekBarShown()) {
            return;
        }
        final int visibility = show ? VISIBLE : INVISIBLE;
        mBrightnessText.setVisibility(visibility);
        mBrightnessSeekBar.setVisibility(visibility);
        mBrightnessValueText.setVisibility(visibility);
    }

    public boolean isVolumeSeekBarShown() {
        return mVolumeSeekBar.getVisibility() == VISIBLE;
    }

    public void showVolumeSeekBar(boolean show) {
        removeCallbacks(mHideVolumeSeekBarRunnable);
        if (show) {
            postDelayed(mHideVolumeSeekBarRunnable, TIMEOUT_SHOW_BRIGHTNESS_OR_VOLUME);
            if (isVolumeSeekBarShown()) {
                return;
            }
        } else if (!isVolumeSeekBarShown()) {
            return;
        }
        final int visibility = show ? VISIBLE : INVISIBLE;
        mVolumeText.setVisibility(visibility);
        mVolumeSeekBar.setVisibility(visibility);
        mVolumeValueText.setVisibility(visibility);
    }

    public boolean isTopAndBottomControlsShown() {
        return (mPrivateFlags & PFLAG_TOP_BOTTOM_CONTROLS_SHOWING) != 0;
    }

    public void showTopAndBottomControls(boolean show) {
        if ((mPrivateFlags & PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY) != 0) {
            return;
        }
        if (show) {
            if (isPlaying()) {
                showTopAndBottomControls(TIMEOUT_SHOW_TOP_AND_BOTTOM_CONTROLS);
            } else {
                // stay showing
                showTopAndBottomControls(-1);
            }
        } else {
            hideTopAndBottomControls();
        }
    }

    /**
     * Shows top and bottom controls on screen. They will go away automatically
     * after `timeout` milliseconds of inactivity.
     *
     * @param timeout The timeout in milliseconds. Use negative to show the controls
     *                till {@link #hideTopAndBottomControls()} is called.
     */
    private void showTopAndBottomControls(int timeout) {
        removeCallbacks(mHideTopAndBottomControlsRunnable);

        if ((mPrivateFlags & PFLAG_TOP_BOTTOM_CONTROLS_SHOWING) == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                TransitionManager.beginDelayedTransition(mTopControlsFrame, new Fade(Fade.IN));
                TransitionManager.beginDelayedTransition(mBottomControlsFrame, new Fade(Fade.IN));
            }
            mTopControlsFrame.setVisibility(VISIBLE);
            mBottomControlsFrame.setVisibility(VISIBLE);
            mPrivateFlags |= PFLAG_TOP_BOTTOM_CONTROLS_SHOWING;

            if (isDrawerVisible(mPlayList)) closeDrawer(mPlayList);
        }
        // Always perform this check to decide whether or not to display the button(s).
        // If the controls are already showing, this will ensure the `skip next` to be hided
        // immediately after the user skips to the last video in the playlist.
        checkSkipNextAndChooseEpisodeWidgetsVisibilities();

        // Cause the video progress bar to be updated even if it is already showing.
        // This happens, for example, if video is paused with the progress bar showing,
        // the user hits play.
        removeCallbacks(mRefreshVideoProgressRunnable);
        post(mRefreshVideoProgressRunnable);

        if (timeout >= 0) {
            postDelayed(mHideTopAndBottomControlsRunnable, timeout);
        }
    }

    /**
     * Hides the controls at both ends in the vertical from the screen.
     */
    private void hideTopAndBottomControls() {
        // Removes the pending action of hiding top and bottom controls as this is being called.
        removeCallbacks(mHideTopAndBottomControlsRunnable);

        if ((mPrivateFlags & PFLAG_TOP_BOTTOM_CONTROLS_SHOWING) != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                TransitionManager.beginDelayedTransition(mTopControlsFrame, new Fade(Fade.OUT));
                TransitionManager.beginDelayedTransition(mBottomControlsFrame, new Fade(Fade.OUT));
            }
            mTopControlsFrame.setVisibility(INVISIBLE);
            mBottomControlsFrame.setVisibility(INVISIBLE);
            mPrivateFlags &= ~PFLAG_TOP_BOTTOM_CONTROLS_SHOWING;
        }
    }

    private void showLoadingView(boolean show) {
        if (show) {
            if (mLoadingImage.getVisibility() != VISIBLE) {
                mLoadingImage.setVisibility(VISIBLE);
                mLoadingDrawable.start();
            }
        } else if (mLoadingImage.getVisibility() != GONE) {
            mLoadingImage.setVisibility(GONE);
            mLoadingDrawable.stop();
        }
    }

    private void refreshBrightnessProgress(int progress) {
        refreshBrightnessProgress(progress, true);
    }

    private void refreshBrightnessProgress(int progress, boolean refreshSeekBar) {
        if (refreshSeekBar) {
            mBrightnessSeekBar.setProgress(progress);
        }
        mBrightnessValueText.setText(mResources.getString(R.string.progress,
                (float) progress / MAX_BRIGHTNESS * 100f));
    }

    private void refreshVolumeProgress(int progress) {
        refreshVolumeProgress(progress, true);
    }

    private void refreshVolumeProgress(int progress, boolean refreshSeekBar) {
        if (refreshSeekBar) {
            mVolumeSeekBar.setProgress(progress);
        }
        mVolumeValueText.setText(mResources.getString(R.string.progress,
                (float) progress / mVolumeSeekBar.getMax() * 100f));
    }

    private void refreshVideoProgress(int progress) {
        refreshVideoProgress(progress, true);
    }

    private void refreshVideoProgress(int progress, boolean refreshSeekBar) {
        if (isInFullscreenMode()) {
            mVideoProgressDurationText.setText(
                    mResources.getString(R.string.progress_duration,
                            TimeUtil.formatTimeByColon(progress), mVideoDurationString));
        } else {
            mVideoProgressText.setText(TimeUtil.formatTimeByColon(progress));
        }
        if (mVideoSeekBar.getMax() != mVideoDuration) {
            mVideoSeekBar.setMax(mVideoDuration);
            if (mVideoDurationText != null) {
                mVideoDurationText.setText(mVideoDurationString);
            }
        }
        if (mVideoSeekBar.getSecondaryProgress() != mBuffering) {
            mVideoSeekBar.setSecondaryProgress(mBuffering);
        }
        if (refreshSeekBar) {
            mVideoSeekBar.setProgress(progress);
        }
    }

    /**
     * Calling this method will cause an invocation to the video seek bar's `onStopTrackingTouch`
     * if the seek bar is being dragged, so as to hide the widgets showing in that case.
     */
    private void cancelDraggingVideoSeekBar() {
        MotionEvent ev = null;
        if ((mOnChildTouchListener.touchFlags & OnChildTouchListener.TFLAG_ADJUSTING_VIDEO_PROGRESS) != 0) {
            ev = Utils.obtainCancelEvent();
            mOnChildTouchListener.onTouchContent(ev);
        } else if (mVideoSeekBar.isPressed()) {
            ev = Utils.obtainCancelEvent();
            mVideoSeekBar.onTouchEvent(ev);
            // Sets an `onTouchListener` for it to intercept the subsequent touch events within
            // this event stream, so that the seek bar stays not dragged.
            //noinspection all (ClickableViewAccessibility)
            mVideoSeekBar.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    final int action = event.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            //noinspection all (ClickableViewAccessibility)
                            v.setOnTouchListener(null);
                            return action != MotionEvent.ACTION_DOWN;
                    }
                    return true;
                }
            });
        }
        if (ev != null) ev.recycle();
    }

    private boolean isSpinnerPopupShowing() {
        return mSpinnerPopup != null && mSpinnerPopup.isShowing();
    }

    private void dismissSpinnerPopup() {
        if (mSpinnerListPopup != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ((android.widget.ListPopupWindow) mSpinnerListPopup).dismiss();
            } else {
                ((androidx.appcompat.widget.ListPopupWindow) mSpinnerListPopup).dismiss();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // When the popup of the speed spinner is showing, do not allow the playlist to be slid out.
        if (isSpinnerPopupShowing()) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private class OnChildTouchListener implements OnTouchListener, ConstraintLayout.TouchInterceptor {

        int touchFlags;
        static final int TFLAG_STILL_DOWN_ON_POPUP = 1;
        static final int TFLAG_DOWN_ON_STATUS_BAR_AREA = 1 << 1;
        static final int TFLAG_ADJUSTING_SCREEN_BRIGHTNESS = 1 << 2;
        static final int TFLAG_ADJUSTING_VOLUME = 1 << 3;
        static final int TFLAG_ADJUSTING_VERTICAL_SEEK_BARS =
                TFLAG_ADJUSTING_SCREEN_BRIGHTNESS | TFLAG_ADJUSTING_VOLUME;
        static final int TFLAG_ADJUSTING_VIDEO_PROGRESS = 1 << 4;
        static final int MASK_ADJUSTING_SEEK_BAR_FLAGS =
                TFLAG_ADJUSTING_VERTICAL_SEEK_BARS | TFLAG_ADJUSTING_VIDEO_PROGRESS;

        // for SpeedSpinner
        float popupDownX, popupDownY;
        final Runnable postPopupOnClickedRunnable = new Runnable() {
            @Override
            public void run() {
                onClickSpinner();
            }
        };

        // for ContentView
        int activePointerId = ViewDragHelper.INVALID_POINTER;
        float downX, downY;
        float lastX, lastY;
        final GestureDetector detector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return isSpinnerPopupShowing();
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return isSpinnerPopupShowing();
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isSpinnerPopupShowing()) {
                    dismissSpinnerPopup();
                } else {
                    showTopAndBottomControls(!isTopAndBottomControlsShown());
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isSpinnerPopupShowing()) {
                    dismissSpinnerPopup();
                } else {
                    toggle();
                }
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return isSpinnerPopupShowing();
            }

//            @Override
//            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//                return isSpinnerPopupShowing();
//            }
        });

        @Override
        public boolean shouldInterceptTouchEvent(@NonNull MotionEvent ev) {
            // If the spinner's popup is showing, let content view intercept the touch events to
            // prevent the user from pressing the buttons ('play/pause', 'skip next', 'back', etc.)
            // All the things we do is for the aim that try our best to make the popup act as if
            // it was focusable.
            return isSpinnerPopupShowing();
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (v == mContentView) {
                return onTouchContent(event);
            } else if (v == mSpeedSpinner) {
                return onTouchSpinner(event);
            }
            return false;
        }

        // Offer the speed spinner an `onClickListener` as needed
        boolean onTouchSpinner(MotionEvent event) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    popupDownX = event.getX();
                    popupDownY = event.getY();
                    touchFlags |= TFLAG_STILL_DOWN_ON_POPUP;
                    removeCallbacks(postPopupOnClickedRunnable);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    touchFlags &= ~TFLAG_STILL_DOWN_ON_POPUP;
                    removeCallbacks(postPopupOnClickedRunnable);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if ((touchFlags & TFLAG_STILL_DOWN_ON_POPUP) != 0) {
                        final float absDx = Math.abs(event.getX() - popupDownX);
                        final float absDy = Math.abs(event.getY() - popupDownY);
                        if (absDx * absDx + absDy * absDy > mTouchSlop * mTouchSlop) {
                            touchFlags &= ~TFLAG_STILL_DOWN_ON_POPUP;
                            removeCallbacks(postPopupOnClickedRunnable);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if ((touchFlags & TFLAG_STILL_DOWN_ON_POPUP) != 0) {
                        touchFlags &= ~TFLAG_STILL_DOWN_ON_POPUP;
                        // Delay 100 milliseconds to let the spinner's `onClick` be called before
                        // our one is called so that we can access the variables created in its `show()`
                        // method via reflections without any NullPointerException.
                        // This is a bit similar to the GestureDetector's onSingleTapConfirmed method,
                        // but not so rigorous as our logic processing is lightweight and effective
                        // enough in this use case.
                        postDelayed(postPopupOnClickedRunnable, 100);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    touchFlags &= ~TFLAG_STILL_DOWN_ON_POPUP;
                    removeCallbacks(postPopupOnClickedRunnable);
                    break;
            }
            return false; // we just need an `onClickListener`, so not consume events
        }

        void onClickSpinner() {
            mPrivateFlags |= PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY;
            showTopAndBottomControls(-1);

            if (mSpinnerPopup == null) return;
            try {
                // Needed on platform versions >= P only
                if (sPopupDecorViewField != null) {
                    // Although this is a member field in the PopupWindow class, it is created in the
                    // popup's `show()` method and reset to `null` each time the popup dismisses. Thus,
                    // always retrieving it via reflection after the spinner clicked is really needed.
                    ((View) sPopupDecorViewField.get(mSpinnerPopup)).setOnTouchListener(new OnTouchListener() {
                        // This is roughly the same as the `onTouchEvent` of the popup's decorView,
                        // but just returns `true` according to the same conditions on actions `down`
                        // and `outside` instead of additionally dismissing the popup as we need it
                        // to remain showing within this event stream till the up event is arrived.
                        //
                        // @see PopupWindow.PopupDecorView.onTouchEvent(MotionEvent)
                        @SuppressLint("ClickableViewAccessibility")
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    final float x = event.getX();
                                    final float y = event.getY();
                                    if (x < 0 || x >= v.getWidth() || y < 0 || y >= v.getHeight()) {
                                        // no dismiss()
                                        return true;
                                    }
                                    break;
                                case MotionEvent.ACTION_OUTSIDE:
                                    // no dismiss()
                                    return true;
                            }
                            return false;
                        }
                    });
                }

                if (sPopupOnDismissListenerField == null) return;
                // A local variable in Spinner/AppCompatSpinner class. Do NOT cache!
                // We do need to get it via reflection each time the spinner's popup window
                // shows to the user, though this may cause the program to run slightly slower.
                final PopupWindow.OnDismissListener listener =
                        (PopupWindow.OnDismissListener) sPopupOnDismissListenerField.get(mSpinnerPopup);
                // This is a little bit of a hack, but... we need to get notified when the spinner's
                // popup window dismisses, so as not to cause the top and bottom controls unhiddable
                // (even if the client calls `showTopAndBottomControls(false)`, it does nothing for
                // the `PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY` flag keeps it from doing what
                // the client wants).
                mSpinnerPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        // First, lets the internal one get notified to release some related resources
                        listener.onDismiss();

                        // Then, do what we want (hide the controls in both the vertical ends after
                        // a delay of 5 seconds)
                        mPrivateFlags &= ~PFLAG_TOP_BOTTOM_CONTROLS_SHOW_STICKILY;
                        showTopAndBottomControls(true);

                        // Third, clear reference to let gc do its work
                        mSpinnerPopup.setOnDismissListener(null);
                    }
                });
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        boolean onTouchContent(MotionEvent event) {
            if (detector.onTouchEvent(event)) {
                return true;
            }

            final int action = event.getAction();

            if (isSpinnerPopupShowing()) {
                if (action == MotionEvent.ACTION_UP) {
                    dismissSpinnerPopup();
                }
                return true;
            }

            // In fullscreen mode, if the y coordinate of the initial `down` event is less than
            // the navigation top inset, it is easy to make the volume/brightness seek bar showing
            // while the user is pulling down the status bar, of which, however, the user may have
            // no tendency. In that case, to avoid touch conflicts, we just return `true` instead.
            if (isInFullscreenMode()) {
                if (action == MotionEvent.ACTION_DOWN) {
                    final int navTopInset = mTopControlsFrame.getPaddingTop() - mNavInitialPaddingTop;
                    touchFlags = touchFlags & ~TFLAG_DOWN_ON_STATUS_BAR_AREA
                            | (event.getY() <= navTopInset ? TFLAG_DOWN_ON_STATUS_BAR_AREA : 0);
                }
                if ((touchFlags & TFLAG_DOWN_ON_STATUS_BAR_AREA) != 0) return true;
            }

            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    final int actionIndex = event.getActionIndex();
                    lastX = downX = event.getX(actionIndex);
                    lastY = downY = event.getY(actionIndex);
                    activePointerId = event.getPointerId(actionIndex);
                    break;

                case MotionEvent.ACTION_MOVE:
                    final int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex < 0) {
                        Log.e(TAG, "Error processing slide; pointer index for id "
                                + activePointerId + " not found. Did any MotionEvents get skipped?");
                        return false;
                    }

                    final boolean rtl = ViewCompat.getLayoutDirection(mContentView) == ViewCompat.LAYOUT_DIRECTION_RTL;

                    final float x = event.getX(pointerIndex);
                    final float y = event.getY(pointerIndex);
                    // positive when finger swipes towards the end of horizontal
                    final float deltaX = rtl ? lastX - x : x - lastX;
                    final float deltaY = lastY - y; // positive when finger swipes up
                    lastX = x;
                    lastY = y;

                    switch (touchFlags & MASK_ADJUSTING_SEEK_BAR_FLAGS) {
                        case TFLAG_ADJUSTING_SCREEN_BRIGHTNESS: {
                            if (mOpCallback == null) {
                                showBrightnessSeekBar(true);
                                break;
                            }

                            final int progress = mBrightnessSeekBar.getProgress();
                            final int newProgress = computeVerticalSeekBarProgress(
                                    mBrightnessSeekBar, deltaY, 1.0f);
                            if (newProgress == progress) {
                                showBrightnessSeekBar(true);
                            } else {
                                mBrightnessSeekBar.setProgress(newProgress);
                                mOnVerticalSeekBarChangeListener.onVerticalProgressChanged(
                                        mBrightnessSeekBar, newProgress, true);
                            }
                        }
                        break;

                        case TFLAG_ADJUSTING_VOLUME: {
                            final int progress = mVolumeSeekBar.getProgress();
                            final int newProgress = computeVerticalSeekBarProgress(
                                    mVolumeSeekBar, deltaY, 1.0f);
                            if (newProgress == progress) {
                                showVolumeSeekBar(true);
                            } else {
                                mVolumeSeekBar.setEnabled(true);
                                mVolumeSeekBar.setProgress(newProgress);
                                mOnVerticalSeekBarChangeListener.onVerticalProgressChanged(
                                        mVolumeSeekBar, newProgress, true);
                            }
                        }
                        break;

                        case TFLAG_ADJUSTING_VIDEO_PROGRESS: {
                            final int progress = mVideoSeekBar.getProgress();
                            final int newProgress = computeSeekBarProgress(
                                    mVideoSeekBar, deltaX, 0.33333334f);
                            if (newProgress != progress) {
                                mVideoSeekBar.setProgress(newProgress);
                                mOnSeekBarChangeListener.onProgressChanged(
                                        mVideoSeekBar, newProgress, true);
                            }
                        }
                        break;

                        case TFLAG_ADJUSTING_VERTICAL_SEEK_BARS:
                            if (!rtl && x >= mContentView.getWidth() / 2
                                    || rtl && x <= mContentView.getWidth() / 2) {
                                touchFlags = touchFlags & ~TFLAG_ADJUSTING_VERTICAL_SEEK_BARS
                                        | TFLAG_ADJUSTING_VOLUME;
                                mOnVerticalSeekBarChangeListener.onStartVerticalTrackingTouch(mVolumeSeekBar);
                            } else {
                                touchFlags = touchFlags & ~TFLAG_ADJUSTING_VERTICAL_SEEK_BARS
                                        | TFLAG_ADJUSTING_SCREEN_BRIGHTNESS;
                                mOnVerticalSeekBarChangeListener.onStartVerticalTrackingTouch(mBrightnessSeekBar);
                            }
                            break;

                        default:
                            final float absDx = Math.abs(x - downX);
                            final float absDy = Math.abs(y - downY);
                            if (absDy >= absDx) {
                                if (absDy > mTouchSlop)
                                    touchFlags = touchFlags & ~MASK_ADJUSTING_SEEK_BAR_FLAGS
                                            | TFLAG_ADJUSTING_VERTICAL_SEEK_BARS;
                            } else {
                                if (absDx > mTouchSlop) {
                                    touchFlags = touchFlags & ~MASK_ADJUSTING_SEEK_BAR_FLAGS
                                            | TFLAG_ADJUSTING_VIDEO_PROGRESS;
                                    if (!isTopAndBottomControlsShown()) {
                                        mVideoSeekBar.setProgress(getVideoProgress());
                                    }
                                    mOnSeekBarChangeListener.onStartTrackingTouch(mVideoSeekBar);
                                }
                            }
                            break;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    onSecondaryPointerUp(event);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    switch (touchFlags & MASK_ADJUSTING_SEEK_BAR_FLAGS) {
                        case TFLAG_ADJUSTING_SCREEN_BRIGHTNESS:
                            mOnVerticalSeekBarChangeListener.onStopVerticalTrackingTouch(mBrightnessSeekBar);
                            break;
                        case TFLAG_ADJUSTING_VOLUME:
                            mOnVerticalSeekBarChangeListener.onStopVerticalTrackingTouch(mVolumeSeekBar);
                            break;
                        case TFLAG_ADJUSTING_VIDEO_PROGRESS:
                            mOnSeekBarChangeListener.onStopTrackingTouch(mVideoSeekBar);
                            break;
                    }
                    touchFlags &= ~MASK_ADJUSTING_SEEK_BAR_FLAGS;
                    activePointerId = ViewDragHelper.INVALID_POINTER;
                    break;
            }
            return true;
        }

        private void onSecondaryPointerUp(MotionEvent ev) {
            final int pointerIndex = ev.getActionIndex();
            final int pointerId = ev.getPointerId(pointerIndex);
            if (pointerId == activePointerId) {
                // This was our active pointer going up.
                // Choose a new active pointer and adjust accordingly.
                final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                activePointerId = ev.getPointerId(newPointerIndex);
                lastX = downX = ev.getX(newPointerIndex);
                lastY = downY = ev.getY(newPointerIndex);
            }
        }

        int computeSeekBarProgress(SeekBar seekBar, float deltaX, float sensitivity) {
            final int maxProgress = seekBar.getMax();
            final int progress = seekBar.getProgress()
                    + Math.round((float) maxProgress / mContentView.getWidth() * deltaX * sensitivity);
            return Math.max(0, Math.min(progress, maxProgress));
        }

        int computeVerticalSeekBarProgress(
                VerticalSeekBar verticalSeekBar, float deltaY, float sensitivity) {
            final int maxProgress = verticalSeekBar.getMax();
            final int progress = verticalSeekBar.getProgress()
                    + Math.round((float) maxProgress / mContentView.getHeight() * deltaY * sensitivity);
            return Math.max(0, Math.min(progress, maxProgress));
        }
    }

    private class SessionCallback extends MediaSessionCompat.Callback {
        int playPauseKeyTappedTime;

        final Runnable playPauseKeyTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                handlePlayPauseKeySingleOrDoubleTapAsNeeded();
            }
        };

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            KeyEvent keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (keyEvent == null || keyEvent.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            final int keyCode = keyEvent.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    if (keyEvent.getRepeatCount() > 0) {
                        // Consider long-press as a single tap.
                        handlePlayPauseKeySingleOrDoubleTapAsNeeded();

                    } else switch (playPauseKeyTappedTime) {
                        case 0:
                            playPauseKeyTappedTime = 1;
                            postDelayed(playPauseKeyTimeoutRunnable, DOUBLE_TAP_TIMEOUT);
                            break;

                        case 1:
                            playPauseKeyTappedTime = 2;
                            removeCallbacks(playPauseKeyTimeoutRunnable);
                            postDelayed(playPauseKeyTimeoutRunnable, DOUBLE_TAP_TIMEOUT);
                            break;

                        case 2:
                            playPauseKeyTappedTime = 0;
                            removeCallbacks(playPauseKeyTimeoutRunnable);

                            // Consider triple tap as the previous.
                            if (mVideoListener != null &&
                                    mOpCallback != null && mOpCallback.canSkipToPrevious()) {
                                mVideoListener.onSkipToPrevious();
                            }
                            break;
                    }
                    return true;
                default:
                    // If another key is pressed within double tap timeout, consider the pending
                    // play/pause as a single/double tap to handle media keys in order.
                    handlePlayPauseKeySingleOrDoubleTapAsNeeded();
                    break;
            }

            return false;
        }

        void handlePlayPauseKeySingleOrDoubleTapAsNeeded() {
            final int tappedTime = playPauseKeyTappedTime;
            if (tappedTime == 0) return;

            playPauseKeyTappedTime = 0;
            removeCallbacks(playPauseKeyTimeoutRunnable);

            switch (tappedTime) {
                case 1:
                    toggle();
                    break;
                // Consider double tap as the next.
                case 2:
                    if (mVideoListener != null &&
                            mOpCallback != null && mOpCallback.canSkipToNext()) {
                        mVideoListener.onSkipToNext();
                    }
                    break;
            }
        }
    }

    // --------------- Saved Instance State ------------------------

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        setClipViewBounds(ss.clipViewBounds);
        setFullscreenMode(ss.isInFullscreenMode, ss.navTopInset);
        if (ss.seekOnPlay != 0) {
            // Seeks to the saved playback position for the video even if it was paused by the user
            // before, as this method is invoked after the Activity's onStart() was called, so that
            // the flag PFLAG_VIDEO_PAUSED_BY_USER makes no sense.
            seekTo(ss.seekOnPlay);
        }
        setPlaybackSpeed(ss.playbackSpeed, true);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState ss = new SavedState(superState);

        ss.playbackSpeed = mPlaybackSpeed;
        ss.seekOnPlay = getVideoProgress();
        ss.clipViewBounds = isClipViewBounds();
        ss.isInFullscreenMode = isInFullscreenMode();
        ss.navTopInset = mTopControlsFrame.getPaddingTop() - mNavInitialPaddingTop;

        return ss;
    }

    /**
     * State persisted across instances
     */
    @SuppressWarnings({"WeakerAccess", "deprecation"})
    protected static class SavedState extends AbsSavedState {
        float playbackSpeed;
        int seekOnPlay;
        boolean clipViewBounds;
        boolean isInFullscreenMode;
        int navTopInset;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel in, ClassLoader loader) {
            super(in, loader);
            playbackSpeed = in.readFloat();
            seekOnPlay = in.readInt();
            clipViewBounds = in.readByte() != (byte) 0;
            isInFullscreenMode = in.readByte() != (byte) 0;
            navTopInset = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloat(playbackSpeed);
            dest.writeInt(seekOnPlay);
            dest.writeByte(clipViewBounds ? (byte) 1 : (byte) 0);
            dest.writeByte(isInFullscreenMode ? (byte) 1 : (byte) 0);
            dest.writeInt(navTopInset);
        }

        public static final Creator<SavedState> CREATOR = ParcelableCompat.newCreator(
                new ParcelableCompatCreatorCallbacks<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                });
    }
}