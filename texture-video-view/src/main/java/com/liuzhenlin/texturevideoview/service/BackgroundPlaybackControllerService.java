/*
 * Created on 2019/12/3 3:41 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview.service;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.style.TextAppearanceSpan;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.liuzhenlin.texturevideoview.InternalConsts;
import com.liuzhenlin.texturevideoview.R;
import com.liuzhenlin.texturevideoview.notification.NotificationChannelManager;
import com.liuzhenlin.texturevideoview.notification.style.DecoratedMediaCustomViewStyle;
import com.liuzhenlin.texturevideoview.utils.BitmapUtils;

import java.util.Objects;

/**
 * @author 刘振林
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BackgroundPlaybackControllerService extends Service {

    private String mPlay;
    private String mPause;
    private String mPkgName;
    /*synthetic*/ Bitmap mAppIcon;
    /*synthetic*/ int mThumbMaxWidth;
    /*synthetic*/ int mThumbMaxHeight;
    @ColorInt
    private static int sNotificationActionIconTint = -1;

    private Messenger mMessenger;

    /*synthetic*/ String mMediaTitle;
    /*synthetic*/ Bitmap mVideoThumb;
    /*synthetic*/ boolean mIsPlaying;
    /*synthetic*/ boolean mIsBuffering;
    /*synthetic*/ boolean mCanSkipToPrevious;
    /*synthetic*/ boolean mCanSkipToNext;
    /*synthetic*/ long mMediaProgress;
    /*synthetic*/ long mMediaDuration;

    /*synthetic*/ NotificationManager mNotificationManager;
    /*synthetic*/ NotificationCompat.Builder mNotificationBuilder;
    private static final int ID_NOTIFICATION = 0x7fffffff;

    private static final String EXTRA_CONTROLLER_ACTION = "extra_controllerAction";

    private static final int CONTROLLER_ACTION_PLAY = 1;
    private static final int CONTROLLER_ACTION_PAUSE = 2;
    private static final int CONTROLLER_ACTION_SKIP_TO_PREVIOUS = 3;
    private static final int CONTROLLER_ACTION_SKIP_TO_NEXT = 4;
    private static final int CONTROLLER_ACTION_CLOSE = 5;

    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int REQUEST_SKIP_TO_PREVIOUS = 3;
    private static final int REQUEST_SKIP_TO_NEXT = 4;
    private static final int REQUEST_CLOSE = 5;

    public static final int MSG_PLAY = Integer.MAX_VALUE;
    public static final int MSG_PAUSE = Integer.MAX_VALUE - 1;
    public static final int MSG_SKIP_TO_PREVIOUS = Integer.MAX_VALUE - 2;
    public static final int MSG_SKIP_TO_NEXT = Integer.MAX_VALUE - 3;
    public static final int MSG_CLOSE = Integer.MAX_VALUE - 4;

    private ControllerActionReceiver mReceiver;

    /*synthetic*/ boolean mIsForeground;

    /*synthetic*/ final Target<Bitmap> mGlideTarget = new CustomTarget<Bitmap>() {
        @Override
        public void onResourceReady(@NonNull Bitmap icon, @Nullable Transition<? super Bitmap> transition) {
            mVideoThumb = icon;
            postNotificationIfForeground();
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            mVideoThumb = mAppIcon;
            postNotificationIfForeground();
        }
    };

    private final Runnable mPostNotificationRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsForeground) {
                resetNotificationView();
                mNotificationManager.notify(ID_NOTIFICATION, mNotificationBuilder.build());
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Resources res = getResources();
        mPlay = res.getString(R.string.play);
        mPause = res.getString(R.string.pause);
        mPkgName = getPackageName();
        mAppIcon = BitmapUtils.drawableToBitmap(getApplicationInfo().loadIcon(getPackageManager()));
        mThumbMaxWidth = res.getDimensionPixelSize(R.dimen.notification_thumb_max_width);
        mThumbMaxHeight = res.getDimensionPixelSize(R.dimen.notification_thumb_max_height);
        if (sNotificationActionIconTint == -1) {
            sNotificationActionIconTint =
                    new TextAppearanceSpan(this, R.style.TextAppearance_Compat_Notification_Media)
                            .getTextColor()
                            .getDefaultColor();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = intent.getParcelableExtra(InternalConsts.EXTRA_MESSENGER);
        //noinspection unchecked
        Class<Activity> playbackActivityClass = (Class<Activity>)
                intent.getSerializableExtra(InternalConsts.EXTRA_PLAYBACK_ACTIVITY_CLASS);
        mMediaTitle = intent.getStringExtra(InternalConsts.EXTRA_MEDIA_TITLE);
        Uri mediaUri = intent.getParcelableExtra(InternalConsts.EXTRA_MEDIA_URI);
        mIsPlaying = intent.getBooleanExtra(InternalConsts.EXTRA_IS_PLAYING, false);
        mIsBuffering = intent.getBooleanExtra(InternalConsts.EXTRA_IS_BUFFERING, false);
        mCanSkipToPrevious = intent.getBooleanExtra(InternalConsts.EXTRA_CAN_SKIP_TO_PREVIOUS, false);
        mCanSkipToNext = intent.getBooleanExtra(InternalConsts.EXTRA_CAN_SKIP_TO_NEXT, false);
        mMediaProgress = intent.getLongExtra(InternalConsts.EXTRA_MEDIA_PROGRESS, 0L);
        mMediaDuration = intent.getLongExtra(InternalConsts.EXTRA_MEDIA_DURATION, 0L);

        mNotificationBuilder = new NotificationCompat.Builder(
                this, NotificationChannelManager.getPlaybackControlNotificationChannelId(this))
                .setSmallIcon(getApplicationInfo().icon)
                .setTicker(mMediaTitle)
                .setStyle(new DecoratedMediaCustomViewStyle())
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (playbackActivityClass != null) {
            Intent it = new Intent(this, playbackActivityClass)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            mNotificationBuilder.setContentIntent(PendingIntent.getActivity(this, 0, it, 0));
        }
        if (mediaUri != null) {
            Glide.with(this)
                    .asBitmap()
                    .load(mediaUri.toString())
                    .override(mThumbMaxWidth, mThumbMaxHeight)
                    .fitCenter()
                    .into(mGlideTarget);
        }
        resetNotificationView();
        startForeground(ID_NOTIFICATION, mNotificationBuilder.build());
        mIsForeground = true;

        mReceiver = new ControllerActionReceiver();
        registerReceiver(mReceiver, new IntentFilter(ControllerActionReceiver.ACTION));

        return new Binder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterReceiver(mReceiver);
        stopForeground(true);
        mIsForeground = false;
        Glide.with(this).clear(mGlideTarget);
        return false;
    }

    /*synthetic*/ void postNotificationIfForeground() {
        Handler handler = InternalConsts.getMainThreadHandler();
        handler.removeCallbacks(mPostNotificationRunnable);
        if (mIsForeground) {
            // Use the Handler to post a delayed notification and remove any previously unpublished
            // notifications that exist in the message queue, of which the main purpose is
            // to spare no effort in posting notifications one at a time in serial order so as to
            // notably reduce situations where the controller and the player state are inconsistent.
            handler.postDelayed(mPostNotificationRunnable, 100);
        }
    }

    /*synthetic*/ void resetNotificationView() {
        RemoteViews nv = createNotificationView();
        mNotificationBuilder.setCustomContentView(nv);
        mNotificationBuilder.setCustomBigContentView(nv);
    }

    private RemoteViews createNotificationView() {
        final boolean playing = mIsPlaying && !mIsBuffering;

        RemoteViews nv = new RemoteViews(
                mPkgName, R.layout.notification_background_playback_controller);

        nv.setImageViewBitmap(R.id.image_videoThumb, mVideoThumb == null ? mAppIcon : mVideoThumb);

        nv.setTextViewText(R.id.text_mediaTitle, mMediaTitle);

        setNotificationActionIconResource(nv,
                R.id.btn_toggle,
                playing ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_white_24dp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            nv.setContentDescription(R.id.btn_toggle, playing ? mPause : mPlay);
        }
        nv.setOnClickPendingIntent(R.id.btn_toggle,
                createNotificationActionIntent(
                        playing ? CONTROLLER_ACTION_PAUSE : CONTROLLER_ACTION_PLAY,
                        playing ? REQUEST_PAUSE : REQUEST_PLAY));

        nv.setViewVisibility(R.id.btn_skipPrevious, mCanSkipToPrevious ? View.VISIBLE : View.GONE);
        if (mCanSkipToPrevious) {
            setNotificationActionIconResource(
                    nv, R.id.btn_skipPrevious, R.drawable.ic_skip_previous_white_24dp);
            nv.setOnClickPendingIntent(R.id.btn_skipPrevious,
                    createNotificationActionIntent(
                            CONTROLLER_ACTION_SKIP_TO_PREVIOUS, REQUEST_SKIP_TO_PREVIOUS));
        }

        nv.setViewVisibility(R.id.btn_skipNext, mCanSkipToNext ? View.VISIBLE : View.GONE);
        if (mCanSkipToNext) {
            setNotificationActionIconResource(
                    nv, R.id.btn_skipNext, R.drawable.ic_skip_next_white_24dp);
            nv.setOnClickPendingIntent(R.id.btn_skipNext,
                    createNotificationActionIntent(
                            CONTROLLER_ACTION_SKIP_TO_NEXT, REQUEST_SKIP_TO_NEXT));
        }

        setNotificationActionIconResource(nv, R.id.btn_close, R.drawable.ic_close_white_20dp);
        nv.setOnClickPendingIntent(R.id.btn_close,
                createNotificationActionIntent(CONTROLLER_ACTION_CLOSE, REQUEST_CLOSE));

        // Chronometer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final long remaining = mMediaDuration - mMediaProgress;
            if (remaining > 0) {
                nv.setLong(R.id.countdownChronometer,
                        "setBase", SystemClock.elapsedRealtime() + remaining);
                nv.setBoolean(R.id.countdownChronometer, "setStarted", playing);
            }
        }

        return nv;
    }

    private void setNotificationActionIconResource(RemoteViews nv, int viewId, int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nv.setImageViewIcon(viewId,
                    IconCompat.createWithResource(this, resId)
                            .setTint(sNotificationActionIconTint)
                            .toIcon(this));
        } else {
            // Creates a bitmap from a tinted retrieved drawable instead,
            // for compatibility of vector drawable resource that can not be directly created
            // via BitmapFactory.decodeResource(Resources, int)
            Drawable drawable = DrawableCompat.wrap(
                    Objects.requireNonNull(AppCompatResources.getDrawable(this, resId)));
            DrawableCompat.setTint(drawable, sNotificationActionIconTint);
            Bitmap bitmap = BitmapUtils.drawableToBitmap(drawable);
            DrawableCompat.setTintList(drawable, null);
            nv.setImageViewBitmap(viewId, bitmap);
        }
    }

    private PendingIntent createNotificationActionIntent(int action, int requestCode) {
        return PendingIntent.getBroadcast(this,
                requestCode,
                new Intent(ControllerActionReceiver.ACTION)
                        .putExtra(EXTRA_CONTROLLER_ACTION, action),
                0);
    }

    /*synthetic*/ void sendMsg(int what) {
        Message msg = Message.obtain(null, what);
        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public class Binder extends android.os.Binder {

        public void onMediaTitleChange(@Nullable String title) {
            mMediaTitle = title;
            postNotificationIfForeground();
        }

        public void onMediaUriChange(@Nullable Uri uri) {
            mMediaProgress = 0L;
            mMediaDuration = 0L;

            RequestManager rm = Glide.with(BackgroundPlaybackControllerService.this);
            if (uri == null) {
                rm.clear(mGlideTarget);
            } else {
                rm
                        .asBitmap()
                        .load(uri.toString())
                        .override(mThumbMaxWidth, mThumbMaxHeight)
                        .fitCenter()
                        .into(mGlideTarget);
            }
        }

        public void onMediaDurationChanged(long progress, long duration) {
            mMediaProgress = progress;
            mMediaDuration = duration;
            postNotificationIfForeground();
        }

        public void onMediaPlay(long progress) {
            mIsPlaying = true;
            mMediaProgress = progress;
            postNotificationIfForeground();
        }

        public void onMediaPause(long progress) {
            mIsPlaying = false;
            mMediaProgress = progress;
            postNotificationIfForeground();
        }

        public void onMediaRepeat() {
            mMediaProgress = 0;
            postNotificationIfForeground();
        }

        public void onMediaBufferingStateChanged(boolean buffering, long positionMs) {
            mIsBuffering = buffering;
            mMediaProgress = positionMs;
            postNotificationIfForeground();
        }

        public void onCanSkipToPreviousChange(boolean canSkipToPrevious) {
            mCanSkipToPrevious = canSkipToPrevious;
            postNotificationIfForeground();
        }

        public void onCanSkipToNextChange(boolean canSkipToNext) {
            mCanSkipToNext = canSkipToNext;
            postNotificationIfForeground();
        }
    }

    private final class ControllerActionReceiver extends BroadcastReceiver {
        static final String ACTION =
                "action_BackgroundPlaybackControllerService$ControllerActionReceiver";

        ControllerActionReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final int action = intent.getIntExtra(EXTRA_CONTROLLER_ACTION, 0);
            switch (action) {
                case CONTROLLER_ACTION_PLAY:
                    sendMsg(MSG_PLAY);
                    break;
                case CONTROLLER_ACTION_PAUSE:
                    sendMsg(MSG_PAUSE);
                    break;
                case CONTROLLER_ACTION_SKIP_TO_PREVIOUS:
                    sendMsg(MSG_SKIP_TO_PREVIOUS);
                    break;
                case CONTROLLER_ACTION_SKIP_TO_NEXT:
                    sendMsg(MSG_SKIP_TO_NEXT);
                    break;
                case CONTROLLER_ACTION_CLOSE:
                    sendMsg(MSG_CLOSE);
                    break;
            }
        }
    }
}