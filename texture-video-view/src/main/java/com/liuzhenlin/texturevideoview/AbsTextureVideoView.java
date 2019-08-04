/*
 * Created on 5/6/19 2:55 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
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
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.ParcelableSpan;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.ParcelableCompat;
import androidx.core.os.ParcelableCompatCreatorCallbacks;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorCompat;
import androidx.customview.view.AbsSavedState;
import androidx.customview.widget.ViewDragHelper;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.util.Util;
import com.liuzhenlin.texturevideoview.drawable.CircularProgressDrawable;
import com.liuzhenlin.texturevideoview.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.texturevideoview.utils.BitmapUtils;
import com.liuzhenlin.texturevideoview.utils.FileUtils;
import com.liuzhenlin.texturevideoview.utils.ScreenUtils;
import com.liuzhenlin.texturevideoview.utils.TimeUtil;
import com.liuzhenlin.texturevideoview.utils.TransitionListenerAdapter;
import com.liuzhenlin.texturevideoview.utils.Utils;
import com.liuzhenlin.texturevideoview.utils.VideoUtils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Base implementation of {@link VideoPlayerControl} to provide video playback.
 *
 * <p>This class requires the permission(s):
 * <ul>
 *   <li>{@link android.Manifest.permission#READ_EXTERNAL_STORAGE} for a local audio/video file</li>
 *   <li>{@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE} for saving captured video photos
 *       or cutout short-videos/GIFs into disk</li>
 *   <li>{@link android.Manifest.permission#INTERNET} to network based streaming content</li>
 * </ul>
 *
 * <p>When accessing this class on platform versions prior to LOLLIPOP, you ought to enable
 * vector drawables created during this view inflating the subview tree to be used within
 * {@link android.graphics.drawable.DrawableContainer} resources in your application through the
 * following code:
 * <pre>
 *     static {
 *         AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
 *     }
 * </pre>
 * Also see {@link androidx.appcompat.app.AppCompatDelegate#setCompatVectorFromResourcesEnabled(boolean)}
 * for more detailed info.
 *
 * <p>This is similar to {@link android.widget.VideoView}, but it comes with a custom control
 * containing buttons like "Play/Pause", "Skip Next", "Minimize", "Maximize", progress sliders for
 * adjusting screen brightness, volume and video progress and a {@link TextureView} used to display
 * the video frames, etc.
 *
 * <p>By default, when this view is in fullscreen mode, all of the "Skip Next" and "Choose Episode"
 * buttons are invisible to the user and even not kept in the view hierarchy for any layout purpose
 * regardless of whether or not a {@link PlayListAdapter} is set for the view displaying the playlist
 * as the data-associated logic code related to the videos should normally be maintained in some
 * specific class of you, but, if reasonable and necessary, for the former button, you can set
 * `canSkipToNext` to `true` through the code like {@code mVideoView.setCanSkipToNext(true)}, and
 * to the latter one, simply pass `true` into one of the {@link #setCanSkipToPrevious(boolean)} and
 * {@link #setCanSkipToNext(boolean)} methods to break the limit so that after clicked the button,
 * the user can have a look at the playlist and choose a preferred video from it to play.
 *
 * <P>An {@link OpCallback} usually is required for this class, which allows us to adjust the
 * brightness of the window this view is attached to, or this feature will not be enabled at all.
 *
 * <p>{@link VideoPlayerControl.OnPlaybackStateChangeListener} can be used to monitor the state of
 * the player or the current video playback.
 * {@link VideoListener} offers default/no-op implementations of each callback method, through which
 * we're able to get notified by all the events related to video playbacks we publish.<br>
 * <strong>NOTE:</strong> If possible, do avoid invoking one/some of the methods in
 * {@link VideoPlayerControl} that may cause the current playback state to change at the call site
 * of some method of the listeners above, in case unexpected result occurs though we have performed
 * some state checks before and after some of the call sites to those methods.
 *
 * <p>Using a View derived from AbsTextureVideoView is simple enough.
 * The following example demonstrates how to play a video through the class:
 * <pre>
 * public class DemoActivity extends AppCompatActivity {
 *     private AbsTextureVideoView mVideoView;
 *
 *     {@literal @}Override
 *     public void onCreate(@Nullable Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.layout.activity_demo);
 *         mVideoView = findViewById(R.id.video_view);
 *         mVideoView.setTitle("Simplest Playback Demo for AbsTextureVideoView");
 *         mVideoView.setVideoUri(getIntent().getData());
 *         // Sets fullscreenMode to true only for demonstration purpose, which, however, should normally
 *         // not be set unless the onChangeViewMode() method is called for the EventListener to perform
 *         // some changes in the layout of our Activity as we see fit.
 *         mVideoView.setFullscreenMode(true, 0);
 *         mVideoView.setVideoListener(new AbsTextureVideoView.VideoListener() {
 *             &#064;Override
 *             public void onVideoStarted() {
 *                 // no-op
 *             }
 *
 *             &#064;Override
 *             public void onVideoStopped() {
 *                 // no-op
 *             }
 *
 *             &#064;Override
 *             public void onVideoSizeChanged(int oldWidth, int oldHeight, int width, int height) {
 *                 // no-op
 *             }
 *         });
 *         mVideoView.setEventListener(new AbsTextureVideoView.EventListener() {
 *             &#064;Override
 *             public void onSkipToPrevious() {
 *                 // no-op
 *             }
 *
 *             &#064;Override
 *             public void onSkipToNext() {
 *                 // no-op
 *             }
 *
 *             &#064;Override
 *             public void onReturnClicked() {
 *                 finish();
 *             }
 *
 *             &#064;Override
 *             public void onViewModeChange(int oldMode, int newMode, boolean layoutMatches) {
 *                 // no-op
 *             }
 *
 *             &#064;Override
 *             public void onShareVideo() {
 *                 // Place the code describing how to share the video here
 *             }
 *
 *             &#064;Override
 *             public void onShareCapturedVideoPhoto(@NonNull File photo) {
 *                 FileUtils.shareFile(DemoActivity.this,
 *                         getPackageName() + ".provider", photo, "image/*");
 *             }
 *         });
 *         mVideoView.setOpCallback(new AbsTextureVideoView.OpCallback() {
 *             &#064;Override
 *             public Window getWindow() {
 *                 return DemoActivity.this.getWindow();
 *             }
 *
 *             // Optional, just returns null to use the default output directory
 *             // (the primary external storage directory concatenating with this application name).
 *             &#064;Nullable
 *             &#064;Override
 *             public String getFileOutputDirectory() {
 *                 return null;
 *             }
 *         });
 *     }
 *
 *     {@literal @}Override
 *     protected void onStart() {
 *         super.onStart();
 *         mVideoView.openVideo();
 *     }
 *
 *     {@literal @}Override
 *     protected void onStop() {
 *         super.onStop();
 *         mVideoView.closeVideo();
 *     }
 *
 *     {@literal @}Override
 *     public void onBackPressed() {
 *         if (!mVideoView.onBackPressed()) {
 *             super.onBackPressed();
 *         }
 *     }
 * }
 * </pre>
 *
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public abstract class AbsTextureVideoView extends DrawerLayout implements VideoPlayerControl,
        ViewHostEventCallback {

    /** Monitors all events related to the video playback. */
    public interface VideoListener {

        /** Called when the video is started or resumed. */
        default void onVideoStarted() {
        }

        /** Called when the video is paused or finished. */
        default void onVideoStopped() {
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
        default void onVideoSizeChanged(int oldWidth, int oldHeight, int width, int height) {
        }
    }

    /** Monitors all events related to (some of the widgets of) this view. */
    public interface EventListener {

        /** Called when a 'skip to previous' action is requested by the user. */
        void onSkipToPrevious();

        /** Called when a 'skip to next' action is requested by the user. */
        void onSkipToNext();

        /**
         * Called when the activity this view is attached to should be destroyed
         * or dialog that should be dismissed, etc.
         */
        void onReturnClicked();

        /**
         * Called when the mode of this view changes.
         *
         * @param oldMode       the old view mode, one of the constants defined with the `VIEW_MODE_` prefix
         * @param newMode       the new view mode, one of the constants defined with the `VIEW_MODE_` prefix
         * @param layoutMatches true if the layout has been adjusted to match the corresponding mode
         */
        void onViewModeChange(@ViewMode int oldMode, @ViewMode int newMode, boolean layoutMatches);

        /** Called when the video being played is about to be shared with another application. */
        void onShareVideo();

        /**
         * Called when a photo is captured for the user to share it to another app
         *
         * @param photo the captured image file of the current playing video
         */
        void onShareCapturedVideoPhoto(@NonNull File photo);
    }

    public interface OpCallback {
        /**
         * @return the window this view is currently attached to
         */
        @NonNull
        Window getWindow();

        /**
         * Returns the base directory used to store the captured video photos or cutout short-videos/GIFs.
         * <p>
         * If the returned value is nonnull, the final storage directory will be the directory
         * with `/screenshots` appended or the primary external storage directory concatenating with
         * your application name will be created (if it does not exist) as the basis.
         */
        @Nullable
        default String getFileOutputDirectory() {
            return null;
        }
    }

    public static abstract class PlayListAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
        AbsTextureVideoView videoView;
        ViewGroup drawerView;
        RecyclerView playlist;

        final OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemClick(v, playlist.getChildAdapterPosition(v));
                videoView.closeDrawer(drawerView);
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
            drawerView = (ViewGroup) recyclerView.getParent();
            videoView = (AbsTextureVideoView) drawerView.getParent();
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
         * @return true if the callback consumed the long click, false otherwise.
         */
        public boolean onItemLongClick(@NonNull View view, int position) {
            return false;
        }
    }

    private static final String TAG = "AbsTextureVideoView";

    protected int mPrivateFlags;

    /** If the controls are showing, this is marked into {@link #mPrivateFlags}. */
    private static final int PFLAG_CONTROLS_SHOWING = 1;

    /**
     * Once set, the controls will stay showing even if you call {@link #showControls(boolean)}
     * with a `false` passed into (in this case, it does nothing), and the internal will
     * manage them logically. This usually happens when user is interacting with some basic widget
     * (e.g., dragging the video progress bar or choosing a proper speed for the current player).
     */
    private static final int PFLAG_CONTROLS_SHOW_STICKILY = 1 << 1;

    /** Set by {@link #setLocked(boolean, boolean)} */
    private static final int PFLAG_LOCKED = 1 << 2;

    /** Set by {@link #setClipViewBounds(boolean)} */
    private static final int PFLAG_CLIP_VIEW_BOUNDS = 1 << 3;

    /** Set by {@link #setVideoStretchedToFitFullscreenLayout(boolean)} */
    private static final int PFLAG_VIDEO_STRETCHED_TO_FIT_FULLSCREEN_LAYOUT = 1 << 4;

    /** Set by {@link #setFullscreenMode(boolean, int)} */
    private static final int PFLAG_IN_FULLSCREEN_MODE = 1 << 5;

    /** Set via {@link #setCanSkipToPrevious(boolean)} */
    private static final int PFLAG_CAN_SKIP_TO_PREVIOUS = 1 << 6;

    /** Set via {@link #setCanSkipToNext(boolean)} */
    private static final int PFLAG_CAN_SKIP_TO_NEXT = 1 << 7;

    /** Set via {@link #setPureAudioPlayback(boolean)} */
    private static final int PFLAG_PURE_AUDIO_PLAYBACK = 1 << 8;

    /** Set via {@link #setSingleVideoLoopPlayback(boolean)} */
    private static final int PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK = 1 << 9;

    /**
     * When set, we will turn off the video playback and release the player object and
     * some other resources associated with it when the currently playing video ends.
     */
    private static final int PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS = 1 << 10;

    /** Indicates that the video info (width, height, duration, etc.) is now available. */
    protected static final int PFLAG_VIDEO_INFO_RESOLVED = 1 << 11;

    /** Indicates that the video is manually paused by the user. */
    protected static final int PFLAG_VIDEO_PAUSED_BY_USER = 1 << 12;

    /**
     * Flag indicates the video is being closed, i.e., we are releasing the player object,
     * during which we should not respond to the client such as restarting or resuming the video
     * (this may happen as we call the onVideoStopped() method of the VideoListener object in our
     * closeVideoInternal() method if this view is currently playing).
     */
    protected static final int PFLAG_VIDEO_IS_CLOSING = 1 << 13;

    @ViewMode
    private int mViewMode = VIEW_MODE_DEFAULT;

    @IntDef({
            VIEW_MODE_DEFAULT,
            VIEW_MODE_MINIMUM,
            VIEW_MODE_FULLSCREEN,
            VIEW_MODE_LOCKED_FULLSCREEN,
            VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN,
            VIEW_MODE_VIDEO_STRETCHED_LOCKED_FULLSCREEN
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface ViewMode {
    }

    /** Default mode for this view (unlocked, non-fullscreen and non-minimized) */
    public static final int VIEW_MODE_DEFAULT = 1;

    /** This view is minimized now, typically in picture-in-picture mode. */
    public static final int VIEW_MODE_MINIMUM = 2;

    /** This view is currently in fullscreen mode. */
    public static final int VIEW_MODE_FULLSCREEN = 3;

    /** This view is currently in fullscreen and locked mode. */
    public static final int VIEW_MODE_LOCKED_FULLSCREEN = 4;

    /**
     * This view is currently in fullscreen mode and the video is stretched to fit
     * the fullscreen layout.
     */
    public static final int VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN = 5;

    /**
     * This view is currently in fullscreen and locked mode and the video is stretched to fit
     * the fullscreen layout.
     */
    public static final int VIEW_MODE_VIDEO_STRETCHED_LOCKED_FULLSCREEN = 6;

    /** The amount of time till we fade out the controls. */
    private static final int TIMEOUT_SHOW_CONTROLS = 5000; // ms
    /** The amount of time till we fade out the brightness or volume frame. */
    private static final int TIMEOUT_SHOW_BRIGHTNESS_OR_VOLUME = 1000; // ms
    /** The amount of time till we fade out the view displaying the captured photo of the video. */
    private static final int TIMEOUT_SHOW_CAPTURED_PHOTO = 3000; // ms

    protected final Context mContext;
    protected final Resources mResources;

    private final ConstraintLayout mContentView;
    private final ViewGroup mDrawerView;

    private final RecyclerView mPlayList;
    private View mMoreView;

    /** Shows the video playback. */
    private final TextureView mTextureView;

    private final ViewGroup mTopControlsFrame;
    private final TextView mTitleText;
    private final View mShareButton;
    private final View mMoreButton;

    private final ImageView mLockUnlockButton;
    private final View mCameraButton;
    private final View mVideoCameraButton;

    private final ViewGroup mBrightnessOrVolumeFrame;
    private final TextView mBrightnessOrVolumeText;
    private final ProgressBar mBrightnessOrVolumeProgress;

    private final ViewGroup mBottomControlsFrame;
    private ImageView mToggleButton;
    private SeekBar mVideoSeekBar;
    // Bottom controls only in non-fullscreen mode
    private TextView mVideoProgressText;
    private TextView mVideoDurationText;
    private View mMinimizeButton;
    private View mFullscreenButton;
    // Bottom controls only in fullscreen mode
    private View mSkipNextButton;
    private TextView mVideoProgressDurationText;
    private AppCompatSpinner mSpeedSpinner;
    private View mChooseEpisodeButton;

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

    private View mCapturedPhotoView;
    private Bitmap mCapturedBitmap;
    private File mSavedPhoto;
    private AsyncTask<Void, Void, File> mSaveCapturedPhotoTask;

    private View mClipView;
    private AsyncTask<Void, Bitmap, Void> mLoadClipThumbsTask;

    private Object mSpinnerListPopup; // ListPopupWindow
    private PopupWindow mSpinnerPopup;

    /** The minimum height of the drawer views (the playlist and the 'more' view) */
    private int mDrawerViewMinimumHeight;

    /** Caches the initial `paddingTop` of the top controls frame */
    private final int mNavInitialPaddingTop;

    /** Title of the video */
    private String mTitle;

    /* package-private */ final String mAppName;
    /**
     * A user agent string based on the application name resolved from this view's context object
     * and the `exoplayer-core` library version.
     */
    /* package-private */ final String mUserAgent;

    private final String mStringPlay;
    private final String mStringPause;
    private final String mStringLock;
    private final String mStringUnlock;
    private final String mStringBrightnessFollowsSystem;
    private final String[] mSpeedsStringArray;
    private final float mSeekingViewHorizontalOffset;
    private final float mSeekingVideoThumbCornerRadius;

    @ColorInt
    protected final int mColorAccent;

    protected final int mTouchSlop;
    protected static final int DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();

    /**
     * Time interpolator used for the animator of stretching or shrinking the texture view that
     * displays the video content.
     */
    private static final Interpolator sStretchShrinkVideoInterpolator =
            new OvershootInterpolator(6.66f);

    private final OnChildTouchListener mOnChildTouchListener = new OnChildTouchListener();
    private final OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mTitleText == v) {
                if (mEventListener != null) {
                    mEventListener.onReturnClicked();
                }
            } else if (mShareButton == v) {
                if (mEventListener != null) {
                    showControls(false, false);
                    mEventListener.onShareVideo();
                }
            } else if (mMoreButton == v) {
                View view = LayoutInflater.from(mContext).inflate(
                        R.layout.drawer_view_more, mDrawerView, false);
                view.setMinimumHeight(mDrawerViewMinimumHeight);

                SwitchCompat svb = view.findViewById(R.id.bt_stretchVideo);
                SwitchCompat lsvb = view.findViewById(R.id.bt_loopSingleVideo);
                SwitchCompat papb = view.findViewById(R.id.bt_pureAudioPlayback);
                TextView whenThisEpisodeEndsText = view.findViewById(R.id.text_whenThisEpisodeEnds);
                TextView _30MinutesText = view.findViewById(R.id.text_30Minutes);
                TextView anHourText = view.findViewById(R.id.text_anHour);

                TimedOffRunnable tor = mTimedOffRunnable;
                svb.setChecked(isVideoStretchedToFitFullscreenLayout());
                lsvb.setChecked(isSingleVideoLoopPlayback());
                papb.setChecked(isPureAudioPlayback());
                whenThisEpisodeEndsText.setSelected((mPrivateFlags & PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS) != 0);
                _30MinutesText.setSelected(tor != null && tor.offTime == TimedOffRunnable.OFF_TIME_30_MINUTES);
                anHourText.setSelected(tor != null && tor.offTime == TimedOffRunnable.OFF_TIME_AN_HOUR);

                svb.setOnClickListener(this);
                lsvb.setOnClickListener(this);
                papb.setOnClickListener(this);
                whenThisEpisodeEndsText.setOnClickListener(this);
                _30MinutesText.setOnClickListener(this);
                anHourText.setOnClickListener(this);

                mMoreView = view;
                mDrawerView.addView(view);
                openDrawer(mDrawerView);

            } else if (mLockUnlockButton == v) {
                setLocked(mStringUnlock.contentEquals(v.getContentDescription()));

            } else if (mCameraButton == v) {
                showControls(true, false);
                captureVideoPhoto();

            } else if (mVideoCameraButton == v) {
                showClipView();

            } else if (mToggleButton == v) {
                toggle(true);

            } else if (mSkipNextButton == v) {
                skipToNextIfPossible();

            } else if (mFullscreenButton == v) {
                final int mode = isVideoStretchedToFitFullscreenLayout() ?
                        VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN : VIEW_MODE_FULLSCREEN;
                setViewMode(mode, false);

            } else if (mMinimizeButton == v) {
                setViewMode(VIEW_MODE_MINIMUM, false);

            } else if (mChooseEpisodeButton == v) {
                if (ViewCompat.getMinimumHeight(mPlayList) != mDrawerViewMinimumHeight) {
                    mPlayList.setMinimumHeight(mDrawerViewMinimumHeight);
                }
                mPlayList.setVisibility(VISIBLE);
                openDrawer(mDrawerView);

            } else {
                final int id = v.getId();
                if (id == R.id.bt_sharePhoto) {
                    removeCallbacks(mHideCapturedPhotoViewRunnable);
                    hideCapturedPhotoView(true);

                } else if (id == R.id.bt_stretchVideo) {
                    setVideoStretchedToFitFullscreenLayoutInternal(((Checkable) v).isChecked(), false);

                } else if (id == R.id.bt_loopSingleVideo) {
                    setSingleVideoLoopPlayback(((Checkable) v).isChecked());

                } else if (id == R.id.bt_pureAudioPlayback) {
                    setPureAudioPlayback(((Checkable) v).isChecked());

                } else if (id == R.id.text_whenThisEpisodeEnds) {
                    final boolean selected = !v.isSelected();
                    v.setSelected(selected);
                    mMoreView.findViewById(R.id.text_30Minutes).setSelected(false);
                    mMoreView.findViewById(R.id.text_anHour).setSelected(false);

                    updateTimedOffSchedule(selected, -1);

                } else if (id == R.id.text_30Minutes) {
                    final boolean selected = !v.isSelected();
                    v.setSelected(selected);
                    mMoreView.findViewById(R.id.text_whenThisEpisodeEnds).setSelected(false);
                    mMoreView.findViewById(R.id.text_anHour).setSelected(false);

                    updateTimedOffSchedule(selected, TimedOffRunnable.OFF_TIME_30_MINUTES);

                } else if (id == R.id.text_anHour) {
                    final boolean selected = !v.isSelected();
                    v.setSelected(selected);
                    mMoreView.findViewById(R.id.text_whenThisEpisodeEnds).setSelected(false);
                    mMoreView.findViewById(R.id.text_30Minutes).setSelected(false);

                    updateTimedOffSchedule(selected, TimedOffRunnable.OFF_TIME_AN_HOUR);
                }
            }
        }

        void updateTimedOffSchedule(boolean selected, int offTime) {
            switch (offTime) {
                case -1:
                    mPrivateFlags = mPrivateFlags & ~PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS
                            | (selected ? PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS : 0);
                    if (mTimedOffRunnable != null) {
                        removeCallbacks(mTimedOffRunnable);
                        mTimedOffRunnable = null;
                    }
                    break;
                case TimedOffRunnable.OFF_TIME_30_MINUTES:
                case TimedOffRunnable.OFF_TIME_AN_HOUR:
                    mPrivateFlags &= ~PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS;
                    if (selected) {
                        if (mTimedOffRunnable == null) {
                            mTimedOffRunnable = new TimedOffRunnable();
                        } else {
                            removeCallbacks(mTimedOffRunnable);
                        }
                        mTimedOffRunnable.offTime = offTime;
                        postDelayed(mTimedOffRunnable, offTime);
                    } else {
                        if (mTimedOffRunnable != null) {
                            removeCallbacks(mTimedOffRunnable);
                            mTimedOffRunnable = null;
                        }
                    }
                    break;
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
                setPlaybackSpeed(Float.parseFloat(speed.substring(0, speed.lastIndexOf('x'))));

                // Filter the non-user-triggered selection changes, so that the visibility of the
                // controls stay unchanged.
                if ((mPrivateFlags & PFLAG_CONTROLS_SHOW_STICKILY) != 0) {
                    mPrivateFlags &= ~PFLAG_CONTROLS_SHOW_STICKILY;
                    showControls(true, false);
                    checkCameraButtonsVisibilities();
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener
            = new SeekBar.OnSeekBarChangeListener() {
        int start;
        volatile int current;
        MediaMetadataRetriever mmr;
        AsyncTask<Void, Object, Void> task;
        ParcelableSpan progressTextSpan;
        ValueAnimator fadeAnimator;
        ValueAnimator translateAnimator;
        Animator.AnimatorListener animatorListener;
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
                    final boolean rtl = Utils.isLayoutRtl(mContentView);
                    final float end = !rtl && progress > start || rtl && progress < start ?
                            mSeekingViewHorizontalOffset : -mSeekingViewHorizontalOffset;
                    ValueAnimator ta;
                    translateAnimator = ta = ValueAnimator.ofFloat(0, end);
                    ta.addListener(animatorListener);
                    ta.addUpdateListener(
                            animation -> target.setTranslationX((float) animation.getAnimatedValue()));
                    ta.setDuration(DURATION);
                    ta.setRepeatMode(ValueAnimator.RESTART);
                    ta.start();
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            current = start = seekBar.getProgress();

            mPrivateFlags |= PFLAG_CONTROLS_SHOW_STICKILY;
            showControls(-1, true);
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
            if (mVideoUri != null && isInFullscreenMode() && !isPureAudioPlayback()) {
                mmr = new MediaMetadataRetriever();
                try {
                    mmr.setDataSource(mContext, mVideoUri);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    mmr.release();
                    mmr = null;
                    showSeekingTextProgress(true);
                }
                if (mmr != null) {
                    // The media contains no video content
                    if (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == null) {
                        mmr.release();
                        mmr = null;
                        showSeekingTextProgress(true);
                    } else {
                        task = new UpdateVideoThumbTask();
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        showSeekingVideoThumb(true);
                    }
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
                fadeAnimator = fa = ValueAnimator.ofFloat(0.0f, 1.0f);
                fa.addListener(animatorListener);
                fa.addUpdateListener(animation -> {
                    final float alpha = (float) animation.getAnimatedValue();
                    if (mSeekingVideoThumbText.getVisibility() == VISIBLE) {
                        mScrimView.setAlpha(alpha);
                        mSeekingVideoThumbText.setAlpha(alpha);
                    } else {
                        mSeekingTextProgressFrame.setAlpha(alpha);
                    }
                });
            } else {
                // If the fade in/out animator has not been released before we need one again,
                // reuse it to avoid unnecessary memory re-allocations.
                fadeAnimator = fa;
            }
            fa.setRepeatMode(ValueAnimator.RESTART);
            fa.start();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            final int progress = current;
            if (progress != start) seekTo(progress, true);

            mPrivateFlags &= ~PFLAG_CONTROLS_SHOW_STICKILY;
            showControls(true, false);

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

        CharSequence getProgressDurationText(int progress) {
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
        final class UpdateVideoThumbTask extends AsyncTask<Void, Object, Void> {
            static final boolean RETRIEVE_SCALED_FRAME_FROM_MMR = false;
            static final float RATIO = 0.25f;
            int last = -1;

            @Override
            protected Void doInBackground(Void... voids) {
                while (!isCancelled()) {
                    int now = current;
                    if (now == last) continue;
                    last = now;

                    View tv = mTextureView;
                    final int width = (int) (tv.getWidth() * tv.getScaleX() * RATIO + 0.5f);
                    final int height = (int) (tv.getHeight() * tv.getScaleY() * RATIO + 0.5f);

                    Bitmap thumb = null;
                    if (RETRIEVE_SCALED_FRAME_FROM_MMR
                            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        thumb = mmr.getScaledFrameAtTime(now * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height);
                    } else {
                        Bitmap tmp = mmr.getFrameAtTime(now * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if (tmp != null) {
                            thumb = BitmapUtils.createScaledBitmap(tmp, width, height, true);
                        }
                    }
                    if (thumb == null) continue;
                    thumb = BitmapUtils.createRoundCornerBitmap(
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
    private OpCallback mOpCallback;

    /** The listener for all the events related to this view we publish. */
    @Nullable
    private EventListener mEventListener;

    /** The listener for all the events related to video we publish. */
    @Nullable
    private VideoListener mVideoListener;

    @Nullable
    private List<OnPlaybackStateChangeListener> mOnPlaybackStateChangeListeners;

    protected Surface mSurface;

    /** The Uri for the video to play, set in {@link #setVideoUri(Uri)}. */
    protected Uri mVideoUri;

    protected int mVideoWidth;
    protected int mVideoHeight;

    /** How long the playback will last for. */
    protected int mVideoDuration;

    /** The string representation of the video duration. */
    protected String mVideoDurationString = DEFAULT_STRING_VIDEO_DURATION;
    protected static final String DEFAULT_STRING_VIDEO_DURATION = "00:00";

    /** The current state of the player or the playback of the video */
    @PlaybackState
    private int mPlaybackState = PLAYBACK_STATE_IDLE;

    /**
     * Caches the speed at which the player works, used on saving instance state and maybe
     * retrieved on state restore.
     */
    protected float mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
    /**
     * Caches the speed the user sets for the player at any time, even when the player has not
     * been created.
     * <p>
     * This may fail if the value is not supported by the framework.
     */
    protected float mUserPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;

    /**
     * Recording the seek position used when playback is just started.
     * <p>
     * Normally this is requested by the user (e.g., dragging the video progress bar)
     * or saved when the user leaves current UI.
     */
    protected int mSeekOnPlay;

    /** The amount of time we are stepping forward or backward for fast-forward and fast-rewind. */
    public static final int FAST_FORWARD_REWIND_INTERVAL = 15000; // ms

    private final Runnable mRefreshVideoProgressRunnable = new Runnable() {
        @Override
        public void run() {
            final int progress = getVideoProgress();
            if (isControlsShowing() && isPlaying()) {
                // Dynamic delay to keep pace with the actual progress of the video most accurately.
                postDelayed(this, 1000 - progress % 1000);
            }
            refreshVideoProgress(progress);
        }
    };

    /**
     * The ratio of the progress of the volume seek bar to the current media stream volume,
     * used to improve the smoothness of the volume progress slider, esp. when the user changes
     * its progress through horizontal screen track touches.
     */
    private static final int RATIO_VOLUME_PROGRESS_TO_VOLUME = 20;

    /** Maximum volume of the system media audio stream ({@link AudioManager#STREAM_MUSIC}) */
    private final int mMaxVolume;

    protected final AudioManager mAudioManager;

    /**
     * Default attributes for audio playback, which configure the underlying platform.
     * <p>
     * To get a {@link android.media.AudioAttributes} first accessible on api 21, simply call
     * the method {@link AudioAttributes#getAudioAttributesV21()} of this property.
     */
    protected static final AudioAttributes sDefaultAudioAttrs =
            new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MOVIE)
                    .build();

    // Used for subclasses within the same package to avoid duplicate field declarations
    /* package-private */ MediaSessionCompat mSession;
    /* package-private */ HeadsetEventsReceiver mHeadsetEventsReceiver;

    private final Runnable mHideControlsRunnable = () -> showControls(false);
    private final Runnable mHideBrightnessOrVolumeFrameRunnable = new Runnable() {
        @Override
        public void run() {
            mBrightnessOrVolumeFrame.setVisibility(GONE);
        }
    };
    private final Runnable mHideCapturedPhotoViewRunnable = () -> hideCapturedPhotoView(false);
    private final Runnable mCheckCameraButtonsVisibilitiesRunnable = this::checkCameraButtonsVisibilities;

    /**
     * Runnable used to turn off the video playback when a scheduled time point is arrived.
     */
    private TimedOffRunnable mTimedOffRunnable;

    private ViewDragHelper mDragHelper;
    private static final int FLAG_IS_OPENING = 0x2; // DrawerLayout.LayoutParams#FLAG_IS_OPENING
    private static final int FLAG_IS_CLOSING = 0x4; // DrawerLayout.LayoutParams#FLAG_IS_CLOSING

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
                // the same conditions to its original onTouchEvent() method through omitting
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
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            sLeftDraggerField = sRightDraggerField = null;
        }
        try {
            Class<LayoutParams> lpClass = LayoutParams.class;
            sOpenStateField = lpClass.getDeclaredField("openState");
            sOpenStateField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public AbsTextureVideoView(@NonNull Context context) {
        this(context, null);
    }

    public AbsTextureVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressLint("ClickableViewAccessibility")
    public AbsTextureVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(Color.BLACK);
        mContext = context;
        mResources = getResources();
        mAppName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        mUserAgent = Util.getUserAgent(context, mAppName);

        mStringPlay = mResources.getString(R.string.play);
        mStringPause = mResources.getString(R.string.pause);
        mStringLock = mResources.getString(R.string.lock);
        mStringUnlock = mResources.getString(R.string.unlock);
        mStringBrightnessFollowsSystem = mResources.getString(R.string.brightness_followsSystem);
        mSpeedsStringArray = mResources.getStringArray(R.array.speeds);
        mSeekingViewHorizontalOffset = mResources.getDimension(R.dimen.seekingViewHorizontalOffset);
        mSeekingVideoThumbCornerRadius = mResources.getDimension(R.dimen.seekingVideoThumbCornerRadius);

        mColorAccent = ContextCompat.getColor(context, R.color.colorAccent);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Inflate the content
        View.inflate(context, R.layout.view_video, this);
        mContentView = findViewById(R.id.content_videoview);
        mDrawerView = findViewById(R.id.drawer_videoview);
        mPlayList = findViewById(R.id.rv_playlist);
        mTextureView = findViewById(R.id.textureView);
        mScrimView = findViewById(R.id.scrim);
        mSeekingVideoThumbText = findViewById(R.id.text_seekingVideoThumb);
        mSeekingTextProgressFrame = findViewById(R.id.frame_seekingTextProgress);
        mSeekingProgressDurationText = findViewById(R.id.text_seeking_progress_duration);
        mSeekingProgress = findViewById(R.id.pb_seekingProgress);
        mLoadingImage = findViewById(R.id.image_loading);
        mTopControlsFrame = findViewById(R.id.frame_topControls);
        mTitleText = findViewById(R.id.text_title);
        mShareButton = findViewById(R.id.bt_share);
        mMoreButton = findViewById(R.id.bt_more);
        mLockUnlockButton = findViewById(R.id.bt_lockUnlock);
        mCameraButton = findViewById(R.id.bt_camera);
        mVideoCameraButton = findViewById(R.id.bt_videoCamera);
        mBrightnessOrVolumeFrame = findViewById(R.id.frame_brightness_or_volume);
        mBrightnessOrVolumeText = findViewById(R.id.text_brightness_or_volume);
        mBrightnessOrVolumeProgress = findViewById(R.id.pb_brightness_or_volume);
        mBottomControlsFrame = findViewById(R.id.frame_bottomControls);
        inflateBottomControls();

        mNavInitialPaddingTop = mTopControlsFrame.getPaddingTop();

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AbsTextureVideoView,
                defStyleAttr, 0);
        setTitle(ta.getString(R.styleable.AbsTextureVideoView_title));
        setVideoResourceId(ta.getResourceId(R.styleable.AbsTextureVideoView_src, 0));
        setPureAudioPlayback(ta.getBoolean(
                R.styleable.AbsTextureVideoView_pureAudioPlayback, false));
        setSingleVideoLoopPlayback(ta.getBoolean(
                R.styleable.AbsTextureVideoView_singleVideoLoopPlayback, false));
        setLocked(ta.getBoolean(R.styleable.AbsTextureVideoView_locked, false), false);
        setClipViewBounds(ta.getBoolean(R.styleable.AbsTextureVideoView_clipViewBounds, false));
        setVideoStretchedToFitFullscreenLayoutInternal(ta.getBoolean(
                R.styleable.AbsTextureVideoView_videoStretchedToFitFullscreenLayout, false), false);
        setFullscreenMode(ta.getBoolean(R.styleable.AbsTextureVideoView_fullscreen, false), 0);
        ta.recycle();

        setDrawerLockModeInternal(LOCK_MODE_LOCKED_CLOSED, mDrawerView);
        addDrawerListener(new DrawerListener() {
            int scrollState;
            float slideOffset;

            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                if (!isControlsShowing()
                        && scrollState == STATE_SETTLING
                        && slideOffset < 0.5f && slideOffset < this.slideOffset) {
                    showControls(true);
                }
                this.slideOffset = slideOffset;
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                if (newState == STATE_SETTLING && sOpenStateField != null) {
                    try {
                        final int state = sOpenStateField.getInt(mDrawerView.getLayoutParams());
                        if ((state & FLAG_IS_OPENING) != 0) {
                            showControls(false);
                        } else if ((state & FLAG_IS_CLOSING) != 0) {
                            showControls(true);
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                scrollState = newState;
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                setDrawerLockModeInternal(LOCK_MODE_UNLOCKED, drawerView);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                mPlayList.setVisibility(GONE);
                if (mMoreView != null) {
                    mDrawerView.removeView(mMoreView);
                    mMoreView = null;
                }
                setDrawerLockModeInternal(LOCK_MODE_LOCKED_CLOSED, drawerView);
            }
        });

        mContentView.setOnTouchListener(mOnChildTouchListener);
        mContentView.setTouchInterceptor(mOnChildTouchListener);

        mTitleText.setOnClickListener(mOnClickListener);
        mShareButton.setOnClickListener(mOnClickListener);
        mMoreButton.setOnClickListener(mOnClickListener);
        mLockUnlockButton.setOnClickListener(mOnClickListener);
        mCameraButton.setOnClickListener(mOnClickListener);
        mVideoCameraButton.setOnClickListener(mOnClickListener);

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

        mAudioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        Typeface tf = Typeface.createFromAsset(mResources.getAssets(), "fonts/avenirnext-medium.ttf");
        mSeekingVideoThumbText.setTypeface(tf);
        mSeekingProgressDurationText.setTypeface(tf);

        mLoadingDrawable = new CircularProgressDrawable(context);
        mLoadingDrawable.setColorSchemeColors(mColorAccent);
        mLoadingDrawable.setStrokeWidth(mResources.getDimension(R.dimen.circular_progress_stroke_width));
        mLoadingDrawable.setStrokeCap(Paint.Cap.ROUND);
        mLoadingImage.setImageDrawable(mLoadingDrawable);

        if (PackageConsts.DEBUG_LISTENER) {
            addOnPlaybackStateChangeListener((oldState, newState) ->
                    Toast.makeText(context,
                            Utils.playbackStateIntToString(oldState)
                                    + "    " + Utils.playbackStateIntToString(newState),
                            Toast.LENGTH_SHORT).show());
        }
    }

    private void inflateBottomControls() {
        ViewGroup root = mBottomControlsFrame;
        if (root.getChildCount() > 0) {
            root.removeViewAt(0);
        }

        if (isInFullscreenMode()) {
            if (isLocked()) {
                mVideoSeekBar = (SeekBar) LayoutInflater.from(mContext).inflate(
                        R.layout.bottom_controls_fullscreen_locked, root, false);
                root.addView(mVideoSeekBar);

                mVideoProgressText = null;
                mVideoDurationText = null;
                mMinimizeButton = null;
                mFullscreenButton = null;
                mSkipNextButton = null;
                mVideoProgressDurationText = null;
                mSpeedSpinner = null;
                mSpinnerListPopup = mSpinnerPopup = null;
                mChooseEpisodeButton = null;
                mToggleButton = null;
                return;
            }

            View.inflate(mContext, R.layout.bottom_controls_fullscreen, root);
            mSkipNextButton = root.findViewById(R.id.bt_skipNext);
            mVideoProgressDurationText = root.findViewById(R.id.text_videoProgressDuration);
            mSpeedSpinner = root.findViewById(R.id.spinner_speed);
            mChooseEpisodeButton = root.findViewById(R.id.bt_chooseEpisode);

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
                        // to discourage it from setting `mOutsideTouchable` to `true` for the popup
                        // in its show() method, so that the popup receives no outside touch event
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

            if (!canSkipToNext()) {
                mSkipNextButton.setVisibility(GONE);
                if (!canSkipToPrevious()) {
                    mChooseEpisodeButton.setVisibility(GONE);
                }
            }

            mSkipNextButton.setOnClickListener(mOnClickListener);
            mChooseEpisodeButton.setOnClickListener(mOnClickListener);

            mVideoProgressText = null;
            mVideoDurationText = null;
            mMinimizeButton = null;
            mFullscreenButton = null;
        } else {
            View.inflate(mContext, R.layout.bottom_controls, root);
            mVideoProgressText = root.findViewById(R.id.text_videoProgress);
            mVideoDurationText = root.findViewById(R.id.text_videoDuration);
            mMinimizeButton = root.findViewById(R.id.bt_minimize);
            mFullscreenButton = root.findViewById(R.id.bt_fullscreen);

            mMinimizeButton.setOnClickListener(mOnClickListener);
            mFullscreenButton.setOnClickListener(mOnClickListener);

            mSkipNextButton = null;
            mVideoProgressDurationText = null;
            mSpeedSpinner = null;
            mSpinnerListPopup = mSpinnerPopup = null;
            mChooseEpisodeButton = null;
        }

        mVideoSeekBar = root.findViewById(R.id.sb_video);
        mVideoSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

        mToggleButton = root.findViewById(R.id.bt_toggle);
        mToggleButton.setOnClickListener(mOnClickListener);
        adjustToggleState(isPlaying());
    }

    private void adjustToggleState(boolean playing) {
        if (!isLocked()) {
            if (playing) {
                mToggleButton.setImageResource(R.drawable.bt_pause_32dp);
                mToggleButton.setContentDescription(mStringPause);
            } else {
                mToggleButton.setImageResource(R.drawable.bt_play_32dp);
                mToggleButton.setContentDescription(mStringPlay);
            }
        }
    }

    private int indexOfPlaybackSpeed(float speed) {
        final String speedString = speed + "x";
        for (int i = 0; i < mSpeedsStringArray.length; i++) {
            if (mSpeedsStringArray[i].equals(speedString)) return i;
        }
        return -1;
    }

    /**
     * @return whether or not this view is in the foreground
     */
    /* package-private */ boolean isInForeground() {
        return getWindowVisibility() == VISIBLE;
    }

    /**
     * @return the brightness of the window this view is attached to or 0
     * if no {@link OpCallback} is set.
     */
    @Override
    public int getBrightness() {
        if (mOpCallback != null) {
            return ScreenUtils.getWindowBrightness(mOpCallback.getWindow());
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>NOTE:</strong> When changing current view's brightness, you should invoke
     * this method instead of a direct call to {@link ScreenUtils#setWindowBrightness(Window, int)}
     */
    @Override
    public void setBrightness(int brightness) {
        if (mOpCallback != null) {
            brightness = Util.constrainValue(brightness, BRIGHTNESS_FOLLOWS_SYSTEM, MAX_BRIGHTNESS);
            // Changes the brightness of the current Window
            ScreenUtils.setWindowBrightness(mOpCallback.getWindow(), brightness);
            // Sets that progress for the brightness ProgressBar
            refreshBrightnessProgress(brightness);
        }
    }

    private int volumeToProgress(int volume) {
        return volume * RATIO_VOLUME_PROGRESS_TO_VOLUME;
    }

    private int progressToVolume(int progress) {
        return (int) ((float) progress / RATIO_VOLUME_PROGRESS_TO_VOLUME + 0.5f);
    }

    @Override
    public int getVolume() {
        return mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void setVolume(int volume) {
        volume = Util.constrainValue(volume, 0, mMaxVolume);
        // Changes the system's media volume
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        // Sets that progress for the volume ProgressBar
        refreshVolumeProgress(volumeToProgress(volume));
    }

    @Override
    public void setVideoUri(@Nullable Uri uri) {
        mVideoUri = uri;
        // Hides the TextureView only if its SurfaceTexture was created upon its first drawing
        // since it had been attached to this view, or no Surface would be available for rendering
        // the video content (Further to say, according to the logic of us, there would start
        // no video at all).
        if (mSurface != null) {
            showTextureView(false);
        }
    }

    /** @see #openVideo(boolean) */
    @Override
    public void openVideo() {
        openVideo(false);
    }

    /**
     * Initialize the player object and prepare for the video playback.
     * Normally, you should invoke this method to resume video playback instead of {@link #play(boolean)}
     * whenever the Activity's restart() or resume() method is called unless the player won't
     * be released as the Activity's lifecycle changes.
     * <p>
     * <strong>NOTE:</strong> When the window this view is attached to leaves the foreground,
     * if the video has already been paused by the user, the player will not be instantiated
     * even if you call this method when this view is displayed in front of the user again and
     * only when the user manually clicks to play, will it be initialized (see {@link #play(boolean)}),
     * but you should still call this method as usual.
     *
     * @param replayIfCompleted whether to replay the video if it is over
     * @see #closeVideo()
     * @see #play(boolean)
     */
    public void openVideo(boolean replayIfCompleted) {
        if (replayIfCompleted || mPlaybackState != PLAYBACK_STATE_COMPLETED) {
            openVideoInternal();
        }
    }

    protected abstract void openVideoInternal();

    @Override
    public void closeVideo() {
        if (!isPureAudioPlayback()) {
            closeVideoInternal(false /* ignored */);
        }
    }

    /**
     * The same as {@link #closeVideo()}, but closes the video in spite of the playback mode
     * (video or audio-only)
     *
     * @param fromUser `true` if the video is turned off by the user.
     */
    protected abstract void closeVideoInternal(boolean fromUser);

    @Override
    public void fastForward(boolean fromUser) {
        seekTo(getVideoProgress() + FAST_FORWARD_REWIND_INTERVAL, fromUser);
    }

    @Override
    public void fastRewind(boolean fromUser) {
        seekTo(getVideoProgress() - FAST_FORWARD_REWIND_INTERVAL, fromUser);
    }

    @Override
    public int getVideoDuration() {
        if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
            return mVideoDuration;
        }
        return INVALID_DURATION;
    }

    @Override
    public int getVideoWidth() {
        if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
            return mVideoWidth;
        }
        return 0;
    }

    @Override
    public int getVideoHeight() {
        if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) != 0) {
            return mVideoHeight;
        }
        return 0;
    }

    @Override
    public boolean isPureAudioPlayback() {
        return (mPrivateFlags & PFLAG_PURE_AUDIO_PLAYBACK) != 0;
    }

    @CallSuper
    @Override
    public void setPureAudioPlayback(boolean audioOnly) {
        mPrivateFlags = mPrivateFlags & ~PFLAG_PURE_AUDIO_PLAYBACK
                | (audioOnly ? PFLAG_PURE_AUDIO_PLAYBACK : 0);
        showTextureView(!audioOnly);
        if (mMoreView != null) {
            Checkable toggle = mMoreView.findViewById(R.id.bt_pureAudioPlayback);
            if (audioOnly != toggle.isChecked()) {
                toggle.setChecked(audioOnly);
            }
        }
    }

    @Override
    public boolean isSingleVideoLoopPlayback() {
        return (mPrivateFlags & PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK) != 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>NOTE:</strong> This does not mean that the video played can not be changed,
     * which can be switched by the user when he/she clicks the 'skip next' button or
     * chooses another video from the playlist.
     */
    @CallSuper
    @Override
    public void setSingleVideoLoopPlayback(boolean looping) {
        mPrivateFlags = mPrivateFlags & ~PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK
                | (looping ? PFLAG_SINGLE_VIDEO_LOOP_PLAYBACK : 0);
        if (mMoreView != null) {
            Checkable toggle = mMoreView.findViewById(R.id.bt_loopSingleVideo);
            if (looping != toggle.isChecked()) {
                toggle.setChecked(looping);
            }
        }
    }

    @Override
    public float getPlaybackSpeed() {
        return isPlaying() ? mPlaybackSpeed : 0;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @CallSuper
    @Override
    public void setPlaybackSpeed(float speed) {
        if (mSpeedSpinner != null) {
            mSpeedSpinner.setSelection(indexOfPlaybackSpeed(speed), true);
        }
    }

    @PlaybackState
    @Override
    public int getPlaybackState() {
        return mPlaybackState;
    }

    protected void setPlaybackState(@PlaybackState int newState) {
        final int oldState = mPlaybackState;
        if (newState != oldState) {
            mPlaybackState = newState;
            if (mOnPlaybackStateChangeListeners != null) {
                // Since onPlaybackStateChange() is implemented by the app, it could do anything,
                // including removing itself from {@link mOnPlaybackStateChangeListeners} — and
                // that could cause problems if an iterator is used on the ArrayList.
                // To avoid such problems, just march thru the list in the reverse order.
                for (int i = mOnPlaybackStateChangeListeners.size() - 1; i >= 0; i--) {
                    mOnPlaybackStateChangeListeners.get(i).onPlaybackStateChange(oldState, newState);
                }
            }
        }
    }

    @Override
    public void addOnPlaybackStateChangeListener(@Nullable OnPlaybackStateChangeListener listener) {
        if (listener != null) {
            if (mOnPlaybackStateChangeListeners == null) {
                mOnPlaybackStateChangeListeners = new ArrayList<>(1);
            }
            if (!mOnPlaybackStateChangeListeners.contains(listener)) {
                mOnPlaybackStateChangeListeners.add(listener);
            }
        }
    }

    @Override
    public void removeOnPlaybackStateChangeListener(@Nullable OnPlaybackStateChangeListener listener) {
        if (listener != null && mOnPlaybackStateChangeListeners != null) {
            mOnPlaybackStateChangeListeners.remove(listener);
        }
    }

    protected void onVideoStarted() {
        setPlaybackState(PLAYBACK_STATE_PLAYING);

        if (!isPureAudioPlayback()) {
            showTextureView(true);
        }
        setKeepScreenOn(true);
        adjustToggleState(true);
        if (mViewMode != VIEW_MODE_MINIMUM) {
            if ((mPrivateFlags & PFLAG_CONTROLS_SHOW_STICKILY) != 0) {
                // If the PFLAG_CONTROLS_SHOW_STICKILY flag is marked into mPrivateFlags, Calling
                // showControls(true) is meaningless as this flag is a hindrance for the subsequent
                // program in that method to continue. So we need repost mRefreshVideoProgressRunnable
                // to make sure the video seek bar to be updated as the video plays.
                removeCallbacks(mRefreshVideoProgressRunnable);
                post(mRefreshVideoProgressRunnable);
            } else {
                showControls(true);
            }
        }

        if (mVideoListener != null) {
            mVideoListener.onVideoStarted();
        }
    }

    protected void onVideoStopped() {
        onVideoStopped(false /* uncompleted */);
    }

    /**
     * @param canSkipNextOnCompletion `true` if the we can skip the played video to the next one
     *                                in the playlist (if any) when the current playback ends
     */
    private void onVideoStopped(boolean canSkipNextOnCompletion) {
        final int oldState = mPlaybackState;
        final int currentState;
        if (oldState == PLAYBACK_STATE_PLAYING) {
            setPlaybackState(PLAYBACK_STATE_PAUSED);
            currentState = PLAYBACK_STATE_PAUSED;
        } else {
            currentState = oldState;
        }

        setKeepScreenOn(false);
        adjustToggleState(false);
        if (mViewMode != VIEW_MODE_MINIMUM) {
            showControls(true);
        }

        if (mVideoListener != null) {
            mVideoListener.onVideoStopped();
            // First, checks the current playback state here to see if it was changed in
            // the above call to the onVideoStopped() method of the VideoListener.
            if (currentState == mPlaybackState)
                // Then, checks whether or not the player object is released (whether the closeVideo()
                // method was called unexpectedly by the client within the same call as above).
                if (isPlayerCreated())
                    // If all of the conditions above hold, skips to the next if possible.
                    if (currentState == PLAYBACK_STATE_COMPLETED && canSkipNextOnCompletion) {
                        skipToNextIfPossible();
                    }
        }
    }

    /**
     * @return whether or not the player object is created for playing the video(s)
     */
    protected abstract boolean isPlayerCreated();

    /**
     * @return true if the video is closed, as scheduled by the user, when playback completes
     */
    protected boolean onPlaybackCompleted() {
        setPlaybackState(PLAYBACK_STATE_COMPLETED);

        if ((mPrivateFlags & PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS) != 0) {
            mPrivateFlags &= ~PFLAG_TURN_OFF_WHEN_THIS_EPISODE_ENDS;
            if (mMoreView != null) {
                mMoreView.findViewById(R.id.text_whenThisEpisodeEnds).setSelected(false);
            }

            closeVideoInternal(true);
            return true;
        } else {
            onVideoStopped(true);
            return false;
        }
    }

    protected void onVideoSizeChanged(int width, int height) {
        final int oldw = mVideoWidth;
        final int oldh = mVideoHeight;
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoListener != null) {
            mVideoListener.onVideoSizeChanged(oldw, oldh, width, height);
        }
        if (width != 0 && height != 0) requestLayout();
    }

    protected boolean skipToNextIfPossible() {
        if (mEventListener != null && mOpCallback != null && canSkipToNext()) {
            mEventListener.onSkipToNext();
            return true;
        }
        return false;
    }

    protected boolean skipToPreviousIfPossible() {
        if (mEventListener != null && mOpCallback != null && canSkipToPrevious()) {
            mEventListener.onSkipToPrevious();
            return true;
        }
        return false;
    }

    /**
     * @return true if we can skip the video played to the previous one
     */
    public boolean canSkipToPrevious() {
        return (mPrivateFlags & PFLAG_CAN_SKIP_TO_PREVIOUS) != 0;
    }

    /**
     * Sets whether or not we can skip the playing video to the previous one.
     */
    public void setCanSkipToPrevious(boolean able) {
        mPrivateFlags = mPrivateFlags & ~PFLAG_CAN_SKIP_TO_PREVIOUS
                | (able ? PFLAG_CAN_SKIP_TO_PREVIOUS : 0);
        if (mChooseEpisodeButton != null) {
            mChooseEpisodeButton.setVisibility(
                    (!able && !canSkipToNext()) ? GONE : VISIBLE);
        }
    }

    /**
     * @return true if we can skip the video played to the next one
     */
    public boolean canSkipToNext() {
        return (mPrivateFlags & PFLAG_CAN_SKIP_TO_NEXT) != 0;
    }

    /**
     * Sets whether or not we can skip the playing video to the next one.
     * <p>
     * If set to `true` and this view is currently in full screen mode, the 'Skip Next' button
     * will become visible to the user.
     */
    public void setCanSkipToNext(boolean able) {
        mPrivateFlags = mPrivateFlags & ~PFLAG_CAN_SKIP_TO_NEXT
                | (able ? PFLAG_CAN_SKIP_TO_NEXT : 0);
        if (mSkipNextButton != null) {
            mSkipNextButton.setVisibility(able ? VISIBLE : GONE);
        }
        if (mChooseEpisodeButton != null) {
            mChooseEpisodeButton.setVisibility(
                    (!able && !canSkipToPrevious()) ? GONE : VISIBLE);
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
     * Sets the listener to monitor all the events related to (some of the widgets of) this view.
     *
     * @see EventListener
     */
    public void setEventListener(@Nullable EventListener listener) {
        mEventListener = listener;
    }

    /**
     * Sets the callback to receive the operation callbacks from this view.
     *
     * @see OpCallback
     */
    public void setOpCallback(@Nullable OpCallback opCallback) {
        mOpCallback = opCallback;
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
    @Nullable
    public String getTitle() {
        return mTitle;
    }

    /**
     * Sets the title of the video to play.
     */
    public void setTitle(@Nullable String title) {
        if (!ObjectsCompat.equals(title, mTitle)) {
            mTitle = title;
            if (isInFullscreenMode()) {
                mTitleText.setText(title);
            }
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
        if (clip != isClipViewBounds()) {
            mPrivateFlags = mPrivateFlags & ~PFLAG_CLIP_VIEW_BOUNDS
                    | (clip ? PFLAG_CLIP_VIEW_BOUNDS : 0);
            if (clip) {
                ViewCompat.setBackground(this, null);
            } else {
                setBackgroundColor(Color.BLACK);
            }
        }
    }

    /**
     * @return whether the video is forced to be stretched to fit the layout size in fullscreen.
     */
    public boolean isVideoStretchedToFitFullscreenLayout() {
        return (mPrivateFlags & PFLAG_VIDEO_STRETCHED_TO_FIT_FULLSCREEN_LAYOUT) != 0;
    }

    /**
     * Sets the video to be forced to be stretched to fit the layout size in fullscreen,
     * which may be distorted if its aspect ratio is unequal to the current view's.
     * <p>
     * <strong>NOTE:</strong> If the clip view bounds flag is also set, then it always wins.
     */
    public void setVideoStretchedToFitFullscreenLayout(boolean stretched) {
        setVideoStretchedToFitFullscreenLayoutInternal(stretched, true);
    }

    private void setVideoStretchedToFitFullscreenLayoutInternal(boolean stretched, boolean checkSwitch) {
        if (stretched == isVideoStretchedToFitFullscreenLayout()) {
            return;
        }
        mPrivateFlags = mPrivateFlags & ~PFLAG_VIDEO_STRETCHED_TO_FIT_FULLSCREEN_LAYOUT
                | (stretched ? PFLAG_VIDEO_STRETCHED_TO_FIT_FULLSCREEN_LAYOUT : 0);
        if (checkSwitch && mMoreView != null) {
            Checkable toggle = mMoreView.findViewById(R.id.bt_stretchVideo);
            if (stretched != toggle.isChecked()) {
                toggle.setChecked(stretched);
            }
        }
        if (isInFullscreenMode()) {
            if (!isClipViewBounds() && mVideoWidth != 0 && mVideoHeight != 0) {
                final int width = mContentView.getWidth();
                final int height = mContentView.getHeight();
                if (!Utils.areEqualIgnorePrecisionError(
                        (float) width / height, (float) mVideoWidth / mVideoHeight)) {
                    ViewPropertyAnimatorCompat vpac = ViewCompat.animate(mTextureView);
                    vpac.withLayer()
                            .scaleX(stretched ? (float) width / mTextureView.getWidth() : 1.0f)
                            .scaleY(stretched ? (float) height / mTextureView.getHeight() : 1.0f)
                            .setInterpolator(sStretchShrinkVideoInterpolator)
                            .setDuration(500)
                            .start();
                    mTextureView.setTag(vpac);
                }
            }

            if (isLocked()) {
                if (stretched) {
                    setViewMode(VIEW_MODE_VIDEO_STRETCHED_LOCKED_FULLSCREEN, true);
                } else {
                    setViewMode(VIEW_MODE_LOCKED_FULLSCREEN, true);
                }
            } else if (stretched) {
                setViewMode(VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN, true);
            } else {
                setViewMode(VIEW_MODE_FULLSCREEN, true);
            }
        }
    }

    /**
     * @return whether this view is in fullscreen mode or not
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
            if (fullscreen == isInFullscreenMode()) {
                return;
            }

            mPrivateFlags = mPrivateFlags & ~PFLAG_IN_FULLSCREEN_MODE
                    | (fullscreen ? PFLAG_IN_FULLSCREEN_MODE : 0);
            if (fullscreen) {
                mTitleText.setText(mTitle);
                if (isControlsShowing()) {
                    mLockUnlockButton.setVisibility(VISIBLE);
                    mCameraButton.setVisibility(VISIBLE);
                    mVideoCameraButton.setVisibility(VISIBLE);
                }
            } else {
                mTitleText.setText(null);
                if (isLocked()) {
                    setLocked(false, false);
                } else {
                    mLockUnlockButton.setVisibility(GONE);
                    mCameraButton.setVisibility(GONE);
                    mVideoCameraButton.setVisibility(GONE);
                    cancelVideoPhotoCapture();
                    hideClipView(true /* usually true */);

                    // Only closes the playlist when this view is out of fullscreen mode
                    if (mMoreView == null && isDrawerVisible(mDrawerView)) {
                        closeDrawer(mDrawerView);
                    }
                }
            }
            inflateBottomControls();

            final int mode = fullscreen
                    ? isVideoStretchedToFitFullscreenLayout() ? //@formatter:off
                            VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN : VIEW_MODE_FULLSCREEN //@formatter:on
                    : VIEW_MODE_DEFAULT;
            setViewMode(mode, true);
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        final boolean clipBounds = isClipViewBounds();
        final boolean fullscreen = isInFullscreenMode();

        if (!clipBounds) {
            width -= (getPaddingLeft() + getPaddingRight());
            height -= (getPaddingTop() + getPaddingBottom());
        }
        final float aspectRatio = (float) width / height;

        if (mVideoWidth != 0 && mVideoHeight != 0) {
            final float videoAspectRatio = (float) mVideoWidth / mVideoHeight;

            final int tvw, tvh;
            if (videoAspectRatio >= aspectRatio) {
                tvw = width;
                tvh = (int) (width / videoAspectRatio + 0.5f);
            } else {
                tvw = (int) (height * videoAspectRatio + 0.5f);
                tvh = height;
            }

            if (clipBounds) {
                width = tvw;
                height = tvh;
            }

            ViewPropertyAnimatorCompat vpac = (ViewPropertyAnimatorCompat) mTextureView.getTag();
            if (vpac != null) {
                vpac.cancel();
            }
            if (fullscreen && !clipBounds && isVideoStretchedToFitFullscreenLayout()) {
                mTextureView.setScaleX((float) width / tvw);
                mTextureView.setScaleY((float) height / tvh);
            } else {
                mTextureView.setScaleX(1.0f);
                mTextureView.setScaleY(1.0f);
            }

            ViewGroup.LayoutParams tvlp = mTextureView.getLayoutParams();
            tvlp.width = tvw;
            tvlp.height = tvh;
//            mTextureView.setLayoutParams(tvlp);
        }

        ViewGroup.LayoutParams lp = mDrawerView.getLayoutParams();
        if (fullscreen) {
            // When in landscape mode, we need to make the drawer view appear to the user appropriately.
            // Its width should not occupy too much display space，so as not to affect the user
            // to preview the video content.
            if (aspectRatio > 1.0f) {
                mDrawerViewMinimumHeight = height;
                lp.width = (int) (width / 2f + 0.5f); //XXX: To make this more adaptable
            } else {
                mDrawerViewMinimumHeight = 0;
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            mDrawerViewMinimumHeight = 0;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = (int) (height * 0.88f + 0.5f);
        }
//        mDrawerView.setLayoutParams(lp);

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @SuppressLint("RtlHardcoded")
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Ensures the drawer view to be opened/closed normally during this layout change
        int state = 0;
        if (changed && sOpenStateField != null) {
            LayoutParams lp = (LayoutParams) mDrawerView.getLayoutParams();
            try {
                state = sOpenStateField.getInt(lp);
                if ((state & (FLAG_IS_OPENING | FLAG_IS_CLOSING)) != 0) {
                    if (mDragHelper == null) {
                        final int absHG = Utils.getAbsoluteHorizontalGravity(this, lp.gravity);
                        mDragHelper = (ViewDragHelper) (absHG == Gravity.LEFT ?
                                sLeftDraggerField.get(this) : sRightDraggerField.get(this));
                    }
                    mDragHelper.abort();
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        super.onLayout(changed, l, t, r, b);
        if (changed) {
            if (state != 0) {
                if ((state & FLAG_IS_OPENING) != 0) {
                    openDrawer(mDrawerView);
                } else if ((state & FLAG_IS_CLOSING) != 0) {
                    closeDrawer(mDrawerView);
                }
            }
            // Postponing checking over the visibilities of the camera buttons ensures that we can
            // correctly get the widget locations on screen so that we can decide whether or not
            // to show them and the View displaying the captured video photo.
            removeCallbacks(mCheckCameraButtonsVisibilitiesRunnable);
            post(mCheckCameraButtonsVisibilitiesRunnable);
        }
    }

    @Override
    public int getDrawerLockMode(@NonNull View drawerView) {
        LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        checkDrawerView(drawerView, Utils.getAbsoluteHorizontalGravity(this, lp.gravity));
        return getDrawerLockModeInternal(drawerView);
    }

    private int getDrawerLockModeInternal(@NonNull View drawerView) {
        return getDrawerLockMode(
                ((LayoutParams) drawerView.getLayoutParams()).gravity
                        & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK);
    }

    @Override
    public void setDrawerLockMode(int lockMode, @NonNull View drawerView) {
        LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        checkDrawerView(drawerView, Utils.getAbsoluteHorizontalGravity(this, lp.gravity));
        setDrawerLockModeInternal(lockMode, drawerView);
    }

    private void setDrawerLockModeInternal(int lockMode, @NonNull View drawerView) {
        setDrawerLockMode(lockMode,
                ((LayoutParams) drawerView.getLayoutParams()).gravity
                        & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK);
    }

    @SuppressLint("RtlHardcoded")
    private void checkDrawerView(View drawerView, int absHG) {
        if ((absHG & Gravity.LEFT) != Gravity.LEFT && (absHG & Gravity.RIGHT) != Gravity.RIGHT) {
            throw new IllegalArgumentException(
                    "View " + drawerView + " is not a drawer with appropriate layout_gravity");
        }
    }

    // Removes all pending actions
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mRefreshVideoProgressRunnable);
        removeCallbacks(mHideControlsRunnable);
        cancelVideoPhotoCapture();
        hideClipView(false);

        removeCallbacks(mHideBrightnessOrVolumeFrameRunnable);
        mBrightnessOrVolumeFrame.setVisibility(GONE);

        if (mTimedOffRunnable != null) {
            removeCallbacks(mTimedOffRunnable);
            mTimedOffRunnable = null;
        }

        // Reset playback state to IDLE when this view detaches from the hierarchy
        setPlaybackState(PLAYBACK_STATE_IDLE);
    }

    /**
     * Call this when the host of the view (Activity for instance) has detected the user's press of
     * the back key to close some widget opened or exit from the fullscreen mode.
     *
     * @return true if the back key event is handled by this view
     */
    @Override
    public boolean onBackPressed() {
        if (mClipView != null) {
            hideClipView(true);
            return true;
        } else if (isDrawerVisible(mDrawerView)) {
            closeDrawer(mDrawerView);
            return true;
        } else if (isInFullscreenMode()) {
            setViewMode(VIEW_MODE_DEFAULT, false);
            return true;
        }
        return false;
    }

    @Override
    public void onMinimizationModeChange(boolean minimized) {
        setViewMode(minimized ? VIEW_MODE_MINIMUM : VIEW_MODE_DEFAULT, true);
    }

    /**
     * @return the present mode for this view, maybe one of
     * {@link #VIEW_MODE_DEFAULT},
     * {@link #VIEW_MODE_MINIMUM},
     * {@link #VIEW_MODE_FULLSCREEN},
     * {@link #VIEW_MODE_LOCKED_FULLSCREEN},
     * {@link #VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN},
     * {@link #VIEW_MODE_VIDEO_STRETCHED_LOCKED_FULLSCREEN}
     */
    @ViewMode
    public int getViewMode() {
        return mViewMode;
    }

    private void setViewMode(@ViewMode int mode, boolean layoutMatches) {
        final int old = mViewMode;
        if (old != mode) {
            mViewMode = mode;
            if (mEventListener != null) {
                mEventListener.onViewModeChange(old, mode, layoutMatches);
            }
        }
    }

    /**
     * Returns whether or not the current view is locked.
     */
    public boolean isLocked() {
        return (mPrivateFlags & PFLAG_LOCKED) != 0;
    }

    /** @see #setLocked(boolean, boolean) */
    public void setLocked(boolean locked) {
        setLocked(locked, true);
    }

    /**
     * Sets this view to be locked or not. When it is locked, all option controls are hided
     * except for the lock toggle button and a progress bar used for indicating where
     * the current video is played and the invisible control related ops are disabled, too.
     *
     * @param locked  Whether to lock this view
     * @param animate Whether the locking or unlocking of this view should be animated.
     *                This only makes sense when this view is currently in fullscreen mode.
     */
    public void setLocked(boolean locked, boolean animate) {
        if (locked != isLocked()) {
            final boolean fullscreen = isInFullscreenMode();
            final boolean showing = isControlsShowing();
            if (fullscreen && showing) {
                if (animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Fade fade = new Fade();
                    Utils.includeChildrenForTransition(fade, mContentView,
                            mTopControlsFrame,
                            mLockUnlockButton, mCameraButton, mVideoCameraButton,
                            mBottomControlsFrame);

                    ChangeBounds cb = new ChangeBounds();
                    Utils.includeChildrenForTransition(cb, mContentView, mBottomControlsFrame);

                    TransitionManager.beginDelayedTransition(mContentView,
                            new TransitionSet().addTransition(fade).addTransition(cb));
                }
                showControls(false, false);
            }
            if (locked) {
                mPrivateFlags |= PFLAG_LOCKED;
                mLockUnlockButton.setContentDescription(mStringLock);
                mLockUnlockButton.setImageResource(R.drawable.bt_lock_24dp);
            } else {
                mPrivateFlags &= ~PFLAG_LOCKED;
                mLockUnlockButton.setContentDescription(mStringUnlock);
                mLockUnlockButton.setImageResource(R.drawable.bt_unlock_24dp);
            }
            if (fullscreen) {
                inflateBottomControls();
                if (showing) {
                    showControls(true, false);
                }

                if (locked) {
                    if (isVideoStretchedToFitFullscreenLayout()) {
                        setViewMode(VIEW_MODE_VIDEO_STRETCHED_LOCKED_FULLSCREEN, true);
                    } else {
                        setViewMode(VIEW_MODE_LOCKED_FULLSCREEN, true);
                    }
                } else if (isVideoStretchedToFitFullscreenLayout()) {
                    setViewMode(VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN, true);
                } else {
                    setViewMode(VIEW_MODE_FULLSCREEN, true);
                }
            }
        }
    }

    /**
     * @return whether the controls are currently showing or not
     */
    public boolean isControlsShowing() {
        return (mPrivateFlags & PFLAG_CONTROLS_SHOWING) != 0;
    }

    /** @see #showControls(boolean, boolean) */
    public void showControls(boolean show) {
        showControls(show, true);
    }

    /**
     * Shows the controls on screen. They will go away automatically after
     * {@value #TIMEOUT_SHOW_CONTROLS} milliseconds of inactivity.
     * If the controls are already showing, Calling this method also makes sense, as it will keep
     * the controls showing till a new {@value #TIMEOUT_SHOW_CONTROLS} ms delay is past.
     *
     * @param animate whether to fade in/out the controls smoothly or not
     */
    public void showControls(boolean show, boolean animate) {
        if ((mPrivateFlags & PFLAG_CONTROLS_SHOW_STICKILY) != 0) {
            return;
        }
        if (show) {
            if (isPlaying()) {
                showControls(TIMEOUT_SHOW_CONTROLS, animate);
            } else {
                // stay showing
                showControls(-1, animate);
            }
        } else {
            hideControls(animate);
        }
    }

    /**
     * Shows the controls on screen. They will go away automatically
     * after `timeout` milliseconds of inactivity.
     *
     * @param timeout The timeout in milliseconds. Use negative to show the controls
     *                till {@link #hideControls(boolean)} is called.
     * @param animate whether to fade in the controls smoothly or not
     */
    private void showControls(int timeout, boolean animate) {
        removeCallbacks(mHideControlsRunnable);

        if ((mPrivateFlags & PFLAG_CONTROLS_SHOWING) == 0) {
            mPrivateFlags |= PFLAG_CONTROLS_SHOWING;
            final boolean unlocked = !isLocked();
            if (animate) {
                beginControlsFadingTransition(true, unlocked);
            }
            if (unlocked) {
                mTopControlsFrame.setVisibility(VISIBLE);

                if (isDrawerVisible(mDrawerView)) closeDrawer(mDrawerView);
            }
            if (isInFullscreenMode()) {
                mLockUnlockButton.setVisibility(VISIBLE);
                if (unlocked) {
                    mCameraButton.setVisibility(VISIBLE);
                    mVideoCameraButton.setVisibility(VISIBLE);
                }
            }
            mBottomControlsFrame.setVisibility(VISIBLE);
        }

        // Cause the video progress bar to be updated even if it is already showing.
        // This happens, for example, if video is paused with the progress bar showing,
        // the user hits play.
        removeCallbacks(mRefreshVideoProgressRunnable);
        post(mRefreshVideoProgressRunnable);

        if (timeout >= 0) {
            postDelayed(mHideControlsRunnable, timeout);
        }
    }

    /**
     * Hides the controls at both ends in the vertical from the screen.
     *
     * @param animate whether to fade out the controls smoothly or not
     */
    private void hideControls(boolean animate) {
        // Removes the pending action of hiding the controls as this is being called.
        removeCallbacks(mHideControlsRunnable);

        if ((mPrivateFlags & PFLAG_CONTROLS_SHOWING) != 0) {
            mPrivateFlags &= ~PFLAG_CONTROLS_SHOWING;
            final boolean unlocked = !isLocked();
            if (animate) {
                beginControlsFadingTransition(false, unlocked);
            }
            if (unlocked) {
                mTopControlsFrame.setVisibility(GONE);
            }
            if (isInFullscreenMode()) {
                mLockUnlockButton.setVisibility(GONE);
                if (unlocked) {
                    mCameraButton.setVisibility(GONE);
                    mVideoCameraButton.setVisibility(GONE);
                    cancelVideoPhotoCapture();
                }
            }
            mBottomControlsFrame.setVisibility(GONE);
        }
    }

    private void beginControlsFadingTransition(boolean in, boolean unlocked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Transition transition = new Fade(in ? Fade.IN : Fade.OUT);
            if (unlocked) {
                Utils.includeChildrenForTransition(transition, mContentView,
                        mTopControlsFrame,
                        mLockUnlockButton, mCameraButton, mVideoCameraButton,
                        mBottomControlsFrame);
            } else {
                Utils.includeChildrenForTransition(transition, mContentView,
                        mLockUnlockButton, mCameraButton, mVideoCameraButton,
                        mBottomControlsFrame);
            }
            TransitionManager.beginDelayedTransition(mContentView, transition);
        }
    }

    private void showTextureView(boolean show) {
        if (show) {
            mTextureView.setVisibility(VISIBLE);
        } else {
            // Temporarily make the TextureView invisible. Do NOT use GONE as the Surface used to
            // render the video content will also be released when it is detached from this view
            // (the onSurfaceTextureDestroyed() method of its SurfaceTextureListener is called).
            mTextureView.setVisibility(INVISIBLE);
        }
    }

    protected void showLoadingView(boolean show) {
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

    private void checkCameraButtonsVisibilities() {
        boolean show = isControlsShowing() && isInFullscreenMode() && !isLocked();
        if (show && isSpinnerPopupShowing()) {
            final int[] location = new int[2];

            View popupRoot = mSpinnerPopup.getContentView().getRootView();
            popupRoot.getLocationOnScreen(location);
            final int popupTop = location[1];

            View camera = mVideoCameraButton;
            camera.getLocationOnScreen(location);
            final int cameraBottom = location[1] + camera.getHeight();

            if (popupTop < cameraBottom + 25f * mResources.getDisplayMetrics().density) {
                show = false;
            }
        }
        if (!show) {
            cancelVideoPhotoCapture();
        }
        final int visibility = show ? VISIBLE : GONE;
        mCameraButton.setVisibility(visibility);
        mVideoCameraButton.setVisibility(visibility);
    }

    private void hideCapturedPhotoView(boolean share) {
        if (mCapturedPhotoView != null) {
            Transition transition = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                transition = (Transition) mCapturedPhotoView.getTag();
                transition.addListener(new TransitionListenerAdapter() {
                    @Override
                    public void onTransitionEnd(Transition transition) {
                        // Recycling of the bitmap captured for the playing video MUST ONLY be done
                        // after the transition ends, in case we use a recycled bitmap for drawing.
                        mCapturedBitmap.recycle();
                        mCapturedBitmap = null;
                    }
                });
                TransitionManager.beginDelayedTransition(mContentView, transition);
            }
            mContentView.removeView(mCapturedPhotoView);
            mCapturedPhotoView = null;
            if (transition == null) {
                mCapturedBitmap.recycle();
                mCapturedBitmap = null;
            }

            if (share && mEventListener != null) {
                mEventListener.onShareCapturedVideoPhoto(mSavedPhoto);
            }
            mSavedPhoto = null;
        }
    }

    private void cancelVideoPhotoCapture() {
        Animation a = mContentView.getAnimation();
        if (a != null && a.hasStarted() && !a.hasEnded()) {
            // We call onAnimationEnd() manually to ensure it to be called immediately to restore
            // the playback state when the animation cancels and before the next animation starts
            // (if any), where the listener of the animation will be removed, so that no second call
            // will be introduced by the clearAnimation() method below.
            ((Animation.AnimationListener) mContentView.getTag()).onAnimationEnd(a);
            mContentView.clearAnimation();
        }
        if (mSaveCapturedPhotoTask != null) {
            mSaveCapturedPhotoTask.cancel(false);
            mSaveCapturedPhotoTask = null;
        }
        removeCallbacks(mHideCapturedPhotoViewRunnable);
        hideCapturedPhotoView(false);
    }

    private void captureVideoPhoto() {
        if (mSurface == null || mVideoUri == null || isPureAudioPlayback()) return;

        final int width = mTextureView.getWidth();
        final int height = mTextureView.getHeight();
        if (width == 0 || height == 0) return;

        final ViewGroup content = mContentView;

        Animation animation = content.getAnimation();
        if (animation != null && animation.hasStarted() && !animation.hasEnded()) {
            ((Animation.AnimationListener) content.getTag()).onAnimationEnd(animation);
            animation.cancel();
        }
        if (mSaveCapturedPhotoTask != null) {
            mSaveCapturedPhotoTask.cancel(false);
            mSaveCapturedPhotoTask = null;
        }

        final Bitmap bitmap = mTextureView.getBitmap(width, height);

        final float oldAspectRatio = mCapturedPhotoView == null ?
                0 : (float) mCapturedBitmap.getWidth() / mCapturedBitmap.getHeight();
        final float aspectRatio = (float) width / height;

        final boolean capturedPhotoViewValid;
        if (mCapturedPhotoView == null) {
            capturedPhotoViewValid = false;
        } else {
            removeCallbacks(mHideCapturedPhotoViewRunnable);
            if (aspectRatio >= 1 && oldAspectRatio >= 1
                    || aspectRatio < 1 && oldAspectRatio < 1) {
                capturedPhotoViewValid = true;
                mCapturedPhotoView.setVisibility(INVISIBLE);
            } else {
                capturedPhotoViewValid = false;
                hideCapturedPhotoView(false);
            }
        }

        if (animation == null) {
            animation = new AlphaAnimation(0.0f, 1.0f);
            animation.setDuration(256);
        }
        Animation.AnimationListener listener = new Animation.AnimationListener() {
            boolean playing;

            @SuppressLint("StaticFieldLeak")
            @Override
            public void onAnimationStart(Animation animation) {
                playing = isPlaying();
                if (playing) {
                    pause(true);
                }
                mSaveCapturedPhotoTask = new AsyncTask<Void, Void, File>() {
                    @SuppressLint("SimpleDateFormat")
                    @Override
                    protected File doInBackground(Void... voids) {
                        return FileUtils.saveBitmapToDisk(mContext,
                                bitmap, Bitmap.CompressFormat.PNG, 100,
                                getFileOutputDirectory() + "/screenshots",
                                mTitle + "_"
                                        + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS") //@formatter:off
                                                .format(System.currentTimeMillis()) //@formatter:on
                                        + ".png");
                    }

                    @Override
                    protected void onPostExecute(File photo) {
                        mSavedPhoto = photo;
                        if (photo == null) {
                            Toast.makeText(mContext,
                                    R.string.saveScreenshotFailed, Toast.LENGTH_SHORT).show();
                            if (capturedPhotoViewValid) {
                                hideCapturedPhotoView(false);
                            }
                        } else {
                            mCapturedBitmap = bitmap;

                            View cpv = mCapturedPhotoView;
                            if (capturedPhotoViewValid) {
                                cpv.setVisibility(VISIBLE);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    TransitionManager.beginDelayedTransition(
                                            content, (Transition) cpv.getTag());
                                }
                            } else {
                                mCapturedPhotoView = cpv = LayoutInflater.from(mContext).inflate(
                                        aspectRatio > 1
                                                ? R.layout.layout_captured_video_photo
                                                : R.layout.layout_captured_video_photo_portrait,
                                        content, false);
                            }

                            TextView shareButton = cpv.findViewById(R.id.bt_sharePhoto);
                            shareButton.setOnClickListener(mOnClickListener);

                            ImageView photoImage = cpv.findViewById(R.id.image_videoPhoto);
                            photoImage.setImageBitmap(bitmap);

                            if (!Utils.areEqualIgnorePrecisionError(aspectRatio, oldAspectRatio)) {
                                ViewGroup.LayoutParams lp = photoImage.getLayoutParams();
                                if (aspectRatio > 1) {
                                    shareButton.measure(0, 0);
                                    lp.width = shareButton.getMeasuredWidth();
                                    lp.height = (int) (lp.width / aspectRatio + 0.5f);
                                } else {
                                    // Makes the text arrange vertically
                                    final CharSequence text = shareButton.getText();
                                    final int length = text.length();
                                    final StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < length; i++) {
                                        sb.append(text.subSequence(i, i + 1));
                                        if (i < length - 1) sb.append("\n");
                                    }
                                    shareButton.setText(sb);

                                    shareButton.measure(0, 0);
                                    lp.height = shareButton.getMeasuredHeight();
                                    lp.width = (int) (lp.height * aspectRatio + 0.5f);
                                }
                                photoImage.setLayoutParams(lp);
                            }

                            if (!capturedPhotoViewValid) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    Transition transition = new Fade();
                                    Utils.includeChildrenForTransition(transition, content);
                                    TransitionManager.beginDelayedTransition(content, transition);

                                    cpv.setTag(transition);
                                }
                                content.addView(cpv);
                            }
                            postDelayed(mHideCapturedPhotoViewRunnable, TIMEOUT_SHOW_CAPTURED_PHOTO);
                        }

                        mSaveCapturedPhotoTask = null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                animation.setAnimationListener(null);
                content.setTag(null);
                if (playing) {
                    play(false);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        };
        animation.setAnimationListener(listener);
        content.setTag(listener);
        content.startAnimation(animation);
    }

    private String getFileOutputDirectory() {
        String directory = null;
        if (mOpCallback != null) {
            directory = mOpCallback.getFileOutputDirectory();
        }
        if (directory == null) {
            directory = Environment.getExternalStorageDirectory() + "/" + mAppName;
        }
        return directory;
    }

    @SuppressLint("StaticFieldLeak")
    private void showClipView() {
        // Not available when video info is waiting to be known
        if ((mPrivateFlags & PFLAG_VIDEO_INFO_RESOLVED) == 0) return;

        final int progress = getVideoProgress();
        final int duration = mVideoDuration;
        final float videoAspectRatio = (float) mVideoWidth / mVideoHeight;

        final int defaultRange = VideoClipView.DEFAULT_MAX_CLIP_DURATION
                + VideoClipView.DEFAULT_MIN_UNSELECTED_CLIP_DURATION;
        final int range; // selectable time interval in millisecond, starting with 0.
        final int rangeOffset; // first value of the above interval plus the mapped playback position.
        if (duration >= defaultRange) {
            range = defaultRange;
            final float halfOfMaxClipDuration = VideoClipView.DEFAULT_MAX_CLIP_DURATION / 2f;
            final float intervalOffset = (defaultRange - halfOfMaxClipDuration) / 2f;
            float intervalEnd = progress + halfOfMaxClipDuration + intervalOffset;
            if (intervalEnd > duration) {
                intervalEnd = duration;
            }
            rangeOffset = Math.max((int) (intervalEnd - range + 0.5f), 0);
        } else {
            range = duration;
            rangeOffset = 0;
        }

        final int[] interval = new int[2];

        final ViewGroup view;
        mClipView = view = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.layout_video_clip, mContentView, false);
        final View cutoutShortVideoButton = view.findViewById(R.id.bt_cutoutShortVideo);
        final View cutoutGifButton = view.findViewById(R.id.bt_cutoutGif);
        final View cancelButton = view.findViewById(R.id.bt_cancel);
        final View okButton = view.findViewById(R.id.bt_ok);
        final TextView vcdText = view.findViewById(R.id.text_videoclipDescription);
        final SurfaceView sv = view.findViewById(R.id.surfaceView);
        final VideoClipView vcv = view.findViewById(R.id.view_videoclip);

        cutoutShortVideoButton.setSelected(true);

        @SuppressLint("SimpleDateFormat") final OnClickListener listener = v -> {
            if (v == cutoutShortVideoButton) {
                cutoutShortVideoButton.setSelected(true);
                cutoutGifButton.setSelected(false);

            } else if (v == cutoutGifButton) {
                cutoutGifButton.setSelected(true);
                cutoutShortVideoButton.setSelected(false);

            } else if (v == cancelButton) {
                hideClipView(true);

            } else if (v == okButton) {
                hideClipView(true);

                /*
                 * Below code blocks for cutting out the desired short video or GIF.
                 * This should normally be done on a worker thread rather than the main thread that
                 * blocks the UI from updating itself till the op completes, but here we do it on
                 * the main thread just for temporary convenience as the code undoubtedly needs
                 * to be improved at some point in the future.
                 */
                final boolean cutoutShortVideo = cutoutShortVideoButton.isSelected();
                if (!cutoutShortVideo) {
                    Toast.makeText(mContext, R.string.gifClippingIsNotYetSupported, Toast.LENGTH_SHORT).show();
                    return;
                }

                final String srcPath = FileUtils.UriResolver.getPath(mContext, mVideoUri);
                if (srcPath == null) {
                    Toast.makeText(mContext, R.string.clippingFailed, Toast.LENGTH_SHORT).show();
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "", new IOException("Failed to resolve the path of the video being clipped."));
                    }
                    return;
                }

                final String destDirectory = getFileOutputDirectory()
                        + "/clips/" + (cutoutShortVideo ? "ShortVideos" : "GIFs");
                final String destName = mTitle + "_"
                        + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS") //@formatter:off
                                .format(System.currentTimeMillis()) //@formatter:on
                        + (cutoutShortVideo ? ".mp4" : ".gif");
                final String destPath = destDirectory + "/" + destName;
                File destFile = null;
                if (cutoutShortVideo) {
                    try {
                        destFile = VideoUtils.clip(srcPath, destPath, interval[0], interval[1]);
                    } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) {
                        e.printStackTrace();
                    } catch (OutOfMemoryError e) {
                        // This error occurs as we clip a video that is not in MPEG-4 format.
                        e.printStackTrace();
                    }
                } else {
                    //TODO: the logic of cutting out a GIF
                }
                if (destFile == null) {
                    Toast.makeText(mContext, R.string.clippingFailed, Toast.LENGTH_SHORT).show();

                } else if (cutoutShortVideo) {
                    FileUtils.recordMediaFileToDatabaseAndScan(mContext,
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            destFile, "video/mp4");
                    Toast.makeText(mContext,
                            mResources.getString(R.string.shortVideoHasBeenSavedTo, destName, destDirectory),
                            Toast.LENGTH_LONG).show();
                } else {
                    FileUtils.recordMediaFileToDatabaseAndScan(mContext,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            destFile, "image/gif");
                    Toast.makeText(mContext,
                            mResources.getString(R.string.gifHasBeenSavedTo, destName, destDirectory),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
        cutoutShortVideoButton.setOnClickListener(listener);
        cutoutGifButton.setOnClickListener(listener);
        cancelButton.setOnClickListener(listener);
        okButton.setOnClickListener(listener);

//        view.measure(MeasureSpec.makeMeasureSpec(mContentView.getWidth(), MeasureSpec.EXACTLY),
//                MeasureSpec.makeMeasureSpec(mContentView.getHeight(), MeasureSpec.EXACTLY));
//        sv.getLayoutParams().width = (int) (sv.getMeasuredHeight() * videoAspectRatio + 0.5f);
        ConstraintLayout.LayoutParams svlp = (ConstraintLayout.LayoutParams) sv.getLayoutParams();
        svlp.dimensionRatio = String.valueOf(videoAspectRatio);

        final SurfaceHolder holder = sv.getHolder();
        final Surface surface = holder.getSurface();
        final AdsMediaSource.MediaSourceFactory factory =
                Build.VERSION.SDK_INT >= 16 /* Jelly Bean */ && this instanceof TextureVideoView2
                        ? ((TextureVideoView2) this).mMediaSourceFactory : null;
        final VideoClipPlayer player = new VideoClipPlayer(mContext, surface, mVideoUri, mUserAgent, factory);
        final Runnable trackProgressRunnable = new Runnable() {
            @Override
            public void run() {
                final int position = player.getCurrentPosition();
                vcv.setSelection(position - rangeOffset);
                if (player.isPlaying()) {
                    if (position < interval[0] || position > interval[1]) {
                        player.seekTo(interval[0]);
                    }
                    vcv.post(this);
                }
            }
        };
        final boolean[] selectionBeingDragged = {false};
        vcv.addOnSelectionChangeListener(new VideoClipView.OnSelectionChangeListener() {
            final String seconds = mResources.getString(R.string.seconds);
            final ForegroundColorSpan colorAccentSpan = new ForegroundColorSpan(mColorAccent);

            @Override
            public void onStartTrackingTouch() {
                if (player.isPlaying()) {
                    holder.setKeepScreenOn(false);
                    player.pause();
                    vcv.removeCallbacks(trackProgressRunnable);
                }
                selectionBeingDragged[0] = true;
            }

            @Override
            public void onSelectionIntervalChange(int start, int end, boolean fromUser) {
                interval[0] = rangeOffset + start;
                interval[1] = rangeOffset + end;

                final int total = (int) (vcv.getMaximumClipDuration() / 1000f + 0.5f);
                final int selected = (int) ((end - start) / 1000f + 0.5f);
                final String s = mResources.getString(
                        R.string.canTakeUpToXSecondsXSecondsSelected, total, selected);
                final SpannableString ss = new SpannableString(s);
                ss.setSpan(colorAccentSpan,
                        s.lastIndexOf(String.valueOf(selected)),
                        s.lastIndexOf(seconds) + seconds.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                vcdText.setText(ss);
            }

            @Override
            public void onSelectionChange(int start, int end, int selection, boolean fromUser) {
                if (fromUser) {
                    player.seekTo(rangeOffset + selection);
                }
            }

            @Override
            public void onStopTrackingTouch() {
                if (surface.isValid()) {
                    holder.setKeepScreenOn(true);
                    closeVideoInternal(true /* no or little use */);
                    player.play();
                    vcv.post(trackProgressRunnable);
                }
                selectionBeingDragged[0] = false;
            }
        });
        // MUST set the durations after the above OnSelectionChangeListener was added to vcv, which
        // will ensure the onSelectionInternalChange() method to be called for the first time.
        if (range < defaultRange) {
            vcv.setMaximumClipDuration(range);
            vcv.setMinimumClipDuration(Math.min(VideoClipView.DEFAULT_MIN_CLIP_DURATION, range));
            vcv.setMinimumUnselectedClipDuration(0);
        }
        final int minClipDuration = vcv.getMinimumClipDuration();
        final int maxClipDuration = vcv.getMaximumClipDuration();
        final int minUnselectedClipDuration = vcv.getMinimumUnselectedClipDuration();
        final int totalDuration = maxClipDuration + minUnselectedClipDuration;
        final int initialSelection = progress - rangeOffset;
        final int tmpInterval = (int) (maxClipDuration / 2f + 0.5f);
        int intervalEnd = initialSelection + tmpInterval;
        if (intervalEnd > totalDuration) {
            intervalEnd = totalDuration;
        }
        int intervalStart = intervalEnd - tmpInterval;
        if (tmpInterval < minClipDuration) {
            final int diff = minClipDuration - tmpInterval;
            final int remaining = totalDuration - intervalEnd;
            if (remaining >= diff) {
                intervalEnd += diff;
            } else {
                intervalEnd = totalDuration;
                intervalStart -= diff - remaining;
            }
        }
        vcv.setSelectionInterval(intervalStart, intervalEnd);
        vcv.setSelection(initialSelection);
        vcv.post(() -> {
            final int thumbHeight = vcv.getThumbDisplayHeight();
            final float thumbWidth = thumbHeight * videoAspectRatio;
            final int thumbGalleryWidth = vcv.getThumbGalleryWidth();
            final int thumbCount = (int) (thumbGalleryWidth / thumbWidth + 0.5f);
            final int finalThumbWidth = (int) ((float) thumbGalleryWidth / thumbCount + 0.5f);
            mLoadClipThumbsTask = new AsyncTask<Void, Bitmap, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(mContext, mVideoUri);
                        if (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null) {
                            for (int i = 0; i < thumbCount && !isCancelled(); ) {
                                Bitmap frame = mmr.getFrameAtTime(
                                        (rangeOffset + ++i * range / thumbCount) * 1000L);
                                if (frame == null) {
                                    // If no frame at the specified time position is retrieved,
                                    // create a empty placeholder bitmap instead.
                                    frame = Bitmap.createBitmap(
                                            finalThumbWidth, thumbHeight, Bitmap.Config.ALPHA_8);
                                } else {
                                    frame = BitmapUtils.createScaledBitmap(
                                            frame, finalThumbWidth, thumbHeight, true);
                                }
                                publishProgress(frame);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } finally {
                        mmr.release();
                    }
                    return null;
                }

                @Override
                protected void onProgressUpdate(Bitmap... thumbs) {
                    vcv.addThumbnail(thumbs[0]);
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    mLoadClipThumbsTask = null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                player.init();
                // Seeks to the playback millisecond position mapping to the initial selection
                // as we were impossible to seek in the above OnSelectionChangeListener's
                // onSelectionChange() method when the player was not created; also we
                // have been leaving out the selection changes that are caused by the program code
                // rather than the user.
                player.seekTo(progress);
                if (!selectionBeingDragged[0]) {
                    holder.setKeepScreenOn(true);
                    // We need to make sure of the video to be closed before the clip preview starts,
                    // because the video in one of the special formats (e.g. mov) will not play if
                    // the video resource is not released in advance.
                    // This will also abandon the audio focus gained for the preceding video playback.
                    // That's why we do this just before the preview starts, for the purpose of not
                    // letting another media application have the opportunity to resume its playback.
                    closeVideoInternal(true /* no or little use */);
                    player.play();
                    vcv.post(trackProgressRunnable);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                holder.setKeepScreenOn(false);
                player.pause();
                player.release();
                vcv.removeCallbacks(trackProgressRunnable);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Transition transition = new Fade();
            Utils.includeChildrenForTransition(transition, mContentView,
                    mTopControlsFrame,
                    mLockUnlockButton, mCameraButton, mVideoCameraButton,
                    mBottomControlsFrame,
                    view);
            transition.excludeTarget(sv, true);
            TransitionManager.beginDelayedTransition(mContentView, transition);
        }
        pause(true);
        showControls(false, false);
        mContentView.addView(view);
    }

    private void hideClipView(boolean fromUser) {
        if (mClipView != null) {
            mContentView.removeView(mClipView);
            mClipView = null;
            if (mLoadClipThumbsTask != null) {
                mLoadClipThumbsTask.cancel(false);
                mLoadClipThumbsTask = null;
            }

            play(fromUser);
            showControls(true); // Make sure the controls will be showed immediately
        }
    }

    private void refreshBrightnessProgress(int progress) {
        if ((mOnChildTouchListener.touchFlags & OnChildTouchListener.TFLAG_ADJUSTING_BRIGHTNESS)
                == OnChildTouchListener.TFLAG_ADJUSTING_BRIGHTNESS) {
            final boolean brightnessFollowsSystem = progress == -1;
            mBrightnessOrVolumeText.setText(brightnessFollowsSystem
                    ? mStringBrightnessFollowsSystem
                    : mResources.getString(R.string.brightness_progress,
                    (float) progress / MAX_BRIGHTNESS * 100f));
            mBrightnessOrVolumeProgress.setProgress(brightnessFollowsSystem ? 0 : progress);
        }
    }

    private void refreshVolumeProgress(int progress) {
        if ((mOnChildTouchListener.touchFlags & OnChildTouchListener.TFLAG_ADJUSTING_VOLUME)
                == OnChildTouchListener.TFLAG_ADJUSTING_VOLUME) {
            mBrightnessOrVolumeText.setText(mResources.getString(R.string.volume_progress,
                    (float) progress / volumeToProgress(mMaxVolume) * 100f));
            mBrightnessOrVolumeProgress.setProgress(progress);
        }
    }

    private void refreshVideoProgress(int progress) {
        refreshVideoProgress(progress, true);
    }

    private void refreshVideoProgress(int progress, boolean refreshSeekBar) {
        if (!isLocked()) {
            if (isInFullscreenMode()) {
                mVideoProgressDurationText.setText(
                        mResources.getString(R.string.progress_duration,
                                TimeUtil.formatTimeByColon(progress), mVideoDurationString));
            } else {
                mVideoProgressText.setText(TimeUtil.formatTimeByColon(progress));
            }
        }
        if (mVideoSeekBar.getMax() != mVideoDuration) {
            mVideoSeekBar.setMax(mVideoDuration);
            if (mVideoDurationText != null) {
                mVideoDurationText.setText(mVideoDurationString);
            }
        }
        mVideoSeekBar.setSecondaryProgress(getVideoBufferedProgress());
        if (refreshSeekBar) {
            mVideoSeekBar.setProgress(progress);
        }
    }

    /**
     * Calling this method will cause an invocation to the video seek bar's onStopTrackingTouch()
     * if the seek bar is being dragged, so as to hide the widgets showing in that case.
     */
    @SuppressLint("ClickableViewAccessibility")
    protected void cancelDraggingVideoSeekBar() {
        MotionEvent ev = null;
        if ((mOnChildTouchListener.touchFlags & OnChildTouchListener.TFLAG_ADJUSTING_VIDEO_PROGRESS) != 0) {
            ev = Utils.obtainCancelEvent();
            mOnChildTouchListener.onTouchContent(ev);
        } else if (mVideoSeekBar.isPressed()) {
            ev = Utils.obtainCancelEvent();
            mVideoSeekBar.onTouchEvent(ev);
            // Sets an OnTouchListener for it to intercept the subsequent touch events within
            // this event stream, so that the seek bar stays not dragged.
            mVideoSeekBar.setOnTouchListener((v, event) -> {
                final int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setOnTouchListener(null);
                        return action != MotionEvent.ACTION_DOWN;
                }
                return true;
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

    private final class OnChildTouchListener implements OnTouchListener, ConstraintLayout.TouchInterceptor {

        int touchFlags;
        static final int TFLAG_STILL_DOWN_ON_POPUP = 1;
        static final int TFLAG_DOWN_ON_STATUS_BAR_AREA = 1 << 1;
        static final int TFLAG_ADJUSTING_BRIGHTNESS = 1 << 2;
        static final int TFLAG_ADJUSTING_VOLUME = 1 << 3;
        static final int TFLAG_ADJUSTING_BRIGHTNESS_OR_VOLUME =
                TFLAG_ADJUSTING_BRIGHTNESS | TFLAG_ADJUSTING_VOLUME;
        static final int TFLAG_ADJUSTING_VIDEO_PROGRESS = 1 << 4;
        static final int MASK_ADJUSTING_PROGRESS_FLAGS =
                TFLAG_ADJUSTING_BRIGHTNESS_OR_VOLUME | TFLAG_ADJUSTING_VIDEO_PROGRESS;

        // for SpeedSpinner
        float popupDownX, popupDownY;
        final Runnable postPopupOnClickedRunnable = this::onClickSpinner;

        // for ContentView
        int activePointerId = MotionEvent.INVALID_POINTER_ID;
        float downX, downY;
        float lastX, lastY;
        final GestureDetector detector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return isLocked() || isSpinnerPopupShowing();
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return isLocked() || isSpinnerPopupShowing();
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isSpinnerPopupShowing()) {
                    dismissSpinnerPopup();
                } else {
                    showControls(!isControlsShowing());
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isLocked()) {
                    return true;
                }
                if (isSpinnerPopupShowing()) {
                    dismissSpinnerPopup();
                } else {
                    toggle(true);
                }
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return isLocked() || isSpinnerPopupShowing();
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return isLocked() /*|| isSpinnerPopupShowing()*/;
            }
        });

        @Override
        public boolean shouldInterceptTouchEvent(@NonNull MotionEvent ev) {
            if (isSpinnerPopupShowing()) {
                // If the spinner's popup is showing, let content view intercept the touch events to
                // prevent the user from pressing the buttons ('play/pause', 'skip next', 'back', etc.)
                // All the things we do is for the aim that try our best to make the popup act as if
                // it was focusable.
                return true;
            }
            // No child of the content view but the 'unlock' button can receive touch events when
            // this view is locked.
            if (isLocked()) {
                final float x = ev.getX();
                final float y = ev.getY();
                final View lub = mLockUnlockButton;
                return x < lub.getLeft() || x > lub.getRight() || y < lub.getTop() || y > lub.getBottom();
            }
            return false;
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

        // Offer the speed spinner an OnClickListener as needed
        boolean onTouchSpinner(MotionEvent event) {
            switch (event.getActionMasked()) {
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
                        // Delay 100 milliseconds to let the spinner's onClick() be called before
                        // our one is called so that we can access the variables created in its show()
                        // method via reflections without any NullPointerException.
                        // This is a bit similar to the GestureDetector's onSingleTapConfirmed() method,
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
            return false; // we just need an OnClickListener, so not consume events
        }

        @SuppressLint("ClickableViewAccessibility")
        void onClickSpinner() {
            mPrivateFlags |= PFLAG_CONTROLS_SHOW_STICKILY;
            showControls(-1, true);
            checkCameraButtonsVisibilities();

            if (mSpinnerPopup == null) return;
            try {
                // Needed on platform versions >= P only
                if (sPopupDecorViewField != null) {
                    // Although this is a member field in the PopupWindow class, it is created in the
                    // popup's show() method and reset to `null` each time the popup dismisses. Thus,
                    // always retrieving it via reflection after the spinner clicked is really needed.
                    ((View) sPopupDecorViewField.get(mSpinnerPopup)).setOnTouchListener((v, event) ->
                            // This is roughly the same as the onTouchEvent() of the popup's decorView,
                            // but just returns `true` according to the same conditions on actions `down`
                            // and `outside` instead of additionally dismissing the popup as we need it
                            // to remain showing within this event stream till the up event is arrived.
                            //
                            // @see PopupWindow.PopupDecorView.onTouchEvent(MotionEvent)
                    {
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
                    });
                }

                if (sPopupOnDismissListenerField == null) return;
                // A local variable in Spinner/AppCompatSpinner class. Do NOT cache!
                // We do need to get it via reflection each time the spinner's popup window
                // shows to the user, though this may cause the program to run slightly slower.
                final PopupWindow.OnDismissListener listener =
                        (PopupWindow.OnDismissListener) sPopupOnDismissListenerField.get(mSpinnerPopup);
                // This is a little bit of a hack, but... we need to get notified when the spinner's
                // popup window dismisses, so as not to cause the controls unhiddable (even if
                // the client calls showControls(false), it does nothing for the
                // PFLAG_CONTROLS_SHOW_STICKILY flag keeps it from doing what the client wants).
                mSpinnerPopup.setOnDismissListener(() -> {
                    // First, lets the internal one get notified to release some related resources
                    listener.onDismiss();

                    // Then, do what we want (hide the controls in both the vertical ends after
                    // a delay of 5 seconds)
                    mPrivateFlags &= ~PFLAG_CONTROLS_SHOW_STICKILY;
                    showControls(true, false);
                    checkCameraButtonsVisibilities();

                    // Third, clear reference to let gc do its work
                    mSpinnerPopup.setOnDismissListener(null);
                });
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        boolean onTouchContent(MotionEvent event) {
            if (detector.onTouchEvent(event) || isLocked()) {
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
            // the navigation top inset, it is easy to make the brightness/volume progress bar showing
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

                    final boolean rtl = Utils.isLayoutRtl(mContentView);

                    final float x = event.getX(pointerIndex);
                    final float y = event.getY(pointerIndex);
                    // positive when finger swipes towards the end of horizontal
                    final float deltaX = rtl ? lastX - x : x - lastX;
                    final float deltaY = lastY - y; // positive when finger swipes up
                    lastX = x;
                    lastY = y;

                    switch (touchFlags & MASK_ADJUSTING_PROGRESS_FLAGS) {
                        case TFLAG_ADJUSTING_BRIGHTNESS: {
                            final int progress = mBrightnessOrVolumeProgress.getProgress();
                            final int newProgress = computeProgressOnTrackTouchVertically(
                                    mBrightnessOrVolumeProgress, deltaY, 1.0f);
                            if (newProgress == progress) {
                                if (progress == 0 && deltaY < 0) {
                                    setBrightness(-1);
                                }
                            } else {
                                setBrightness(newProgress);
                            }
                        }
                        break;

                        case TFLAG_ADJUSTING_VOLUME: {
                            final int progress = mBrightnessOrVolumeProgress.getProgress();
                            final int newProgress = computeProgressOnTrackTouchVertically(
                                    mBrightnessOrVolumeProgress, deltaY, 1.0f);
                            if (newProgress != progress) {
                                mAudioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, progressToVolume(newProgress), 0);
                                refreshVolumeProgress(newProgress);
                            }
                        }
                        break;

                        case TFLAG_ADJUSTING_VIDEO_PROGRESS: {
                            final int progress = mVideoSeekBar.getProgress();
                            final int newProgress = computeProgressOnTrackTouchHorizontally(
                                    mVideoSeekBar, deltaX, 0.33333334f);
                            if (newProgress != progress) {
                                mVideoSeekBar.setProgress(newProgress);
                                mOnSeekBarChangeListener.onProgressChanged(
                                        mVideoSeekBar, newProgress, true);
                            }
                        }
                        break;

                        case TFLAG_ADJUSTING_BRIGHTNESS_OR_VOLUME:
                            if (mOpCallback != null &&
                                    (!rtl && x < mContentView.getWidth() / 2
                                            || rtl && x > mContentView.getWidth() / 2)) {
                                touchFlags = touchFlags & ~TFLAG_ADJUSTING_BRIGHTNESS_OR_VOLUME
                                        | TFLAG_ADJUSTING_BRIGHTNESS;
                                removeCallbacks(mHideBrightnessOrVolumeFrameRunnable);
                                mBrightnessOrVolumeFrame.setVisibility(VISIBLE);
                                mBrightnessOrVolumeProgress.setMax(MAX_BRIGHTNESS);
                                refreshBrightnessProgress(getBrightness());
                            } else {
                                touchFlags = touchFlags & ~TFLAG_ADJUSTING_BRIGHTNESS_OR_VOLUME
                                        | TFLAG_ADJUSTING_VOLUME;
                                removeCallbacks(mHideBrightnessOrVolumeFrameRunnable);
                                mBrightnessOrVolumeFrame.setVisibility(VISIBLE);
                                mBrightnessOrVolumeProgress.setMax(volumeToProgress(mMaxVolume));
                                refreshVolumeProgress(volumeToProgress(getVolume()));
                            }
                            break;

                        default:
                            final float absDx = Math.abs(x - downX);
                            final float absDy = Math.abs(y - downY);
                            if (absDy >= absDx) {
                                if (absDy > mTouchSlop)
                                    touchFlags = touchFlags & ~MASK_ADJUSTING_PROGRESS_FLAGS
                                            | TFLAG_ADJUSTING_BRIGHTNESS_OR_VOLUME;
                            } else {
                                if (absDx > mTouchSlop) {
                                    touchFlags = touchFlags & ~MASK_ADJUSTING_PROGRESS_FLAGS
                                            | TFLAG_ADJUSTING_VIDEO_PROGRESS;
                                    if (!isControlsShowing()) {
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
                    switch (touchFlags & MASK_ADJUSTING_PROGRESS_FLAGS) {
                        case TFLAG_ADJUSTING_BRIGHTNESS:
                        case TFLAG_ADJUSTING_VOLUME:
                            postDelayed(mHideBrightnessOrVolumeFrameRunnable,
                                    TIMEOUT_SHOW_BRIGHTNESS_OR_VOLUME);
                            break;
                        case TFLAG_ADJUSTING_VIDEO_PROGRESS:
                            mOnSeekBarChangeListener.onStopTrackingTouch(mVideoSeekBar);
                            break;
                    }
                    touchFlags &= ~MASK_ADJUSTING_PROGRESS_FLAGS;
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
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

        int computeProgressOnTrackTouchHorizontally(ProgressBar progressBar, float deltaX, float sensitivity) {
            final int maxProgress = progressBar.getMax();
            final int progress = progressBar.getProgress()
                    + Math.round((float) maxProgress / mContentView.getWidth() * deltaX * sensitivity);
            return Util.constrainValue(progress, 0, maxProgress);
        }

        int computeProgressOnTrackTouchVertically(ProgressBar progressBar, float deltaY, float sensitivity) {
            final int maxProgress = progressBar.getMax();
            final int progress = progressBar.getProgress()
                    + Math.round((float) maxProgress / mContentView.getHeight() * deltaY * sensitivity);
            return Util.constrainValue(progress, 0, maxProgress);
        }
    }

    protected class SessionCallback extends MediaSessionCompat.Callback {
        private int playPauseKeyTappedTime;

        private final Runnable playPauseKeyTimeoutRunnable =
                this::handlePlayPauseKeySingleOrDoubleTapAsNeeded;

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
                            skipToPreviousIfPossible();
                            break;
                    }
                    return true;
                default:
                    // If another key is pressed within double tap timeout, consider the pending
                    // play/pause as a single/double tap to handle media keys in order.
                    handlePlayPauseKeySingleOrDoubleTapAsNeeded();
                    break;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    return skipToPreviousIfPossible();
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    return skipToNextIfPossible();
            }
            return false;
        }

        private void handlePlayPauseKeySingleOrDoubleTapAsNeeded() {
            final int tappedTime = playPauseKeyTappedTime;
            if (tappedTime == 0) return;

            playPauseKeyTappedTime = 0;
            removeCallbacks(playPauseKeyTimeoutRunnable);

            switch (tappedTime) {
                case 1:
                    toggle(true);
                    break;
                // Consider double tap as the next.
                case 2:
                    skipToNextIfPossible();
                    break;
            }
        }
    }

    private final class TimedOffRunnable implements Runnable {
        int offTime;
        static final int OFF_TIME_30_MINUTES = 30 * 60 * 1000; // ms
        static final int OFF_TIME_AN_HOUR = 60 * 60 * 1000; // ms

        @Override
        public void run() {
            mTimedOffRunnable = null;
            if (mMoreView != null) {
                switch (offTime) {
                    case OFF_TIME_30_MINUTES:
                        mMoreView.findViewById(R.id.text_30Minutes).setSelected(false);
                        break;
                    case OFF_TIME_AN_HOUR:
                        mMoreView.findViewById(R.id.text_anHour).setSelected(false);
                        break;
                }
            }
            closeVideoInternal(true);
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

        setLocked(ss.locked, false);
        setVideoStretchedToFitFullscreenLayout(ss.videoStretchedToFitFullscreenLayout);
        setFullscreenMode(ss.fullscreen, ss.navTopInset);
        setPureAudioPlayback(ss.pureAudioPlayback);
        setSingleVideoLoopPlayback(ss.looping);
        setPlaybackSpeed(ss.playbackSpeed);
        if (ss.seekOnPlay != 0) {
            // Seeks to the saved playback position for the video even if it was paused by the user
            // before, as this method is invoked after the Activity's onStart() was called, so that
            // the flag PFLAG_VIDEO_PAUSED_BY_USER makes no sense.
            seekTo(ss.seekOnPlay, false);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState ss = new SavedState(superState);

        ss.playbackSpeed = mPlaybackSpeed;
        ss.seekOnPlay = getVideoProgress();
        ss.pureAudioPlayback = isPureAudioPlayback();
        ss.looping = isSingleVideoLoopPlayback();
        ss.locked = isLocked();
        ss.videoStretchedToFitFullscreenLayout = isVideoStretchedToFitFullscreenLayout();
        ss.fullscreen = isInFullscreenMode();
        ss.navTopInset = mTopControlsFrame.getPaddingTop() - mNavInitialPaddingTop;

        return ss;
    }

    /**
     * State persisted across instances
     */
    @VisibleForTesting
    public static final class SavedState extends AbsSavedState {
        private float playbackSpeed;
        private int seekOnPlay;
        private boolean pureAudioPlayback;
        private boolean looping;
        private boolean locked;
        private boolean videoStretchedToFitFullscreenLayout;
        private boolean fullscreen;
        private int navTopInset;

        private SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in, ClassLoader loader) {
            super(in, loader);
            playbackSpeed = in.readFloat();
            seekOnPlay = in.readInt();
            pureAudioPlayback = in.readByte() != (byte) 0;
            looping = in.readByte() != (byte) 0;
            locked = in.readByte() != (byte) 0;
            videoStretchedToFitFullscreenLayout = in.readByte() != (byte) 0;
            fullscreen = in.readByte() != (byte) 0;
            navTopInset = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloat(playbackSpeed);
            dest.writeInt(seekOnPlay);
            dest.writeByte(pureAudioPlayback ? (byte) 1 : (byte) 0);
            dest.writeByte(looping ? (byte) 1 : (byte) 0);
            dest.writeByte(locked ? (byte) 1 : (byte) 0);
            dest.writeByte(videoStretchedToFitFullscreenLayout ? (byte) 1 : (byte) 0);
            dest.writeByte(fullscreen ? (byte) 1 : (byte) 0);
            dest.writeInt(navTopInset);
        }

        @SuppressWarnings("deprecation")
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
