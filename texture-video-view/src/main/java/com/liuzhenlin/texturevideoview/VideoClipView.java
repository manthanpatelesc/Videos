/*
 * Created on 5/19/19 10:43 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Util;
import com.liuzhenlin.texturevideoview.utils.BitmapUtils;
import com.liuzhenlin.texturevideoview.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author 刘振林
 */
public class VideoClipView extends FrameLayout {
    private static final String TAG = "VideoClipView";

    private final RecyclerView mThumbsGallery;
    private final ThumbsAdapter mThumbsAdapter = new ThumbsAdapter();

    private final Drawable mClipBackwards;
    private final Drawable mClipBackwardsDark;
    private final Drawable mClipForward;
    private final Drawable mClipForwardDark;

    private final int mDrawableWidth;
    private final int mDrawableHeight;

    private Drawable mLeftDrawable;
    private Drawable mRightDrawable;

    /***/
    private float mLeftDrawableOffset; // [0, 1)
    /***/
    private float mRightDrawableOffset; // [0, 1)

    /** Minimum spacing between 'Clip Backwards' and 'Clip Forward' buttons. */
    private float mMinimumClipBackwardsForwardGap;
    /** Maximum spacing between 'Clip Backwards' and 'Clip Forward' buttons. */
    private float mMaximumClipBackwardsForwardGap;

    public static final int DEFAULT_MIN_CLIP_DURATION = 3 * 1000; // ms
    public static final int DEFAULT_MAX_CLIP_DURATION = 120 * 1000; // ms
    public static final int DEFAULT_MIN_UNSELECTED_CLIP_DURATION = 8 * 1000; // ms

    /**
     * @see #getMinimumClipDuration()
     * @see #setMinimumClipDuration(int)
     */
    private int mMinimumClipDuration = DEFAULT_MIN_CLIP_DURATION;
    /**
     * @see #getMaximumClipDuration()
     * @see #setMaximumClipDuration(int)
     */
    private int mMaximumClipDuration = DEFAULT_MAX_CLIP_DURATION;
    /**
     * @see #getMinimumUnselectedClipDuration()
     * @see #setMinimumUnselectedClipDuration(int)
     */
    private int mMinimumUnselectedClipDuration = DEFAULT_MIN_UNSELECTED_CLIP_DURATION;

    private final int[] mSelectionInterval = sNoSelectionInterval.clone();
    private static final int[] sNoSelectionInterval = {0, 0};

    protected final float mDip;
    protected final float mTouchSlop;

    private final Paint mFrameBarPaint;
    private final int mFrameBarHeight;
    private final int mFrameBarColor;
    private final int mFrameBarDarkColor;

    private final Paint mProgressPaint;
    private final float mProgressStrokeWidth;
    private final float mProgressHeaderFooterStrokeWidth;
    private final float mProgressHeaderFooterLength;

    /** The selected millisecond position as a percentage of the selectable time interval. */
    private float mProgressPercent; // [0, 1]

    /**
     * Offset between the touch point x coordinate and the horizontal center position of the
     * progress cursor while it is being dragged by the user.
     */
    private float mProgressMoveOffset = Float.NaN;

    /**
     * True if the drawable offsets have been determined at least once from the layout direction.
     */
    private boolean mDrawableOffsetsResolved;

    private boolean mFirstLayout = true;
    private boolean mInLayout;

    private int mActivePointerId;
    private float mDownX;
    private float mDownY;
    private final float[] mTouchX = new float[2];
    private final float[] mTouchY = new float[2];

    private int mTouchFlags;
    private static final int TFLAG_LEFT_DRAWABLE_BEING_DRAGGED = 1;
    private static final int TFLAG_RIGHT_DRAWABLE_BEING_DRAGGED = 1 << 1;
    private static final int TFLAG_DRAWABLE_BEING_DRAGGED = 0b0011;
    private static final int TFLAG_PROGRESS_BEING_DRAGGED = 1 << 2;
    private static final int TOUCH_MASK = 0b0111;

    private List<OnSelectionChangeListener> mOnSelectionChangeListeners;

    /**
     * Lister for monitoring all the changes to the selection or selection interval of the video clip.
     */
    public interface OnSelectionChangeListener {
        /**
         * Notification that the user has started a touch gesture that could change the current
         * selection or/and selection interval of the video clip.
         */
        default void onStartTrackingTouch() {
        }

        /**
         * Gets notified when the selection interval of the video clip changes.
         *
         * @param start start position in millisecond of the selected time interval
         * @param end   end position in millisecond of the selected time interval
         */
        default void onSelectionIntervalChange(int start, int end) {
        }

        /**
         * Gets notified when the selection of the video clip changes.
         *
         * @param start     start position in millisecond of the selected time interval
         * @param end       end position in millisecond of the selected time interval
         * @param selection position of the selected millisecond within the selection interval
         * @param fromUser  true if the selection change was initiated by the user
         */
        default void onSelectionChange(int start, int end, int selection, boolean fromUser) {
        }

        /**
         * Notification that the user has finished a touch gesture that could have changed the
         * selection or/and selection interval of the video clip.
         */
        default void onStopTrackingTouch() {
        }
    }

    public void addOnSelectionChangeListener(@NonNull OnSelectionChangeListener listener) {
        if (mOnSelectionChangeListeners == null) {
            mOnSelectionChangeListeners = new ArrayList<>(1);
        }
        if (!mOnSelectionChangeListeners.contains(listener)) {
            mOnSelectionChangeListeners.add(listener);
        }
    }

    public void removeOnSelectionChangeListener(@Nullable OnSelectionChangeListener listener) {
        if (hasOnSelectionChangeListener()) {
            mOnSelectionChangeListeners.remove(listener);
        }
    }

    public void clearOnSelectionChangeListeners() {
        if (hasOnSelectionChangeListener()) {
            mOnSelectionChangeListeners.clear();
        }
    }

    private boolean hasOnSelectionChangeListener() {
        return mOnSelectionChangeListeners != null && !mOnSelectionChangeListeners.isEmpty();
    }

    private void notifyListenersWhenSelectionDragStarts() {
        if (hasOnSelectionChangeListener()) {
            // Since onStartTrackingTouch() is implemented by the app, it could do anything,
            // including removing itself from {@link mOnSelectionChangeListeners} – and that could
            // cause problems if an iterator is used on the ArrayList {@link mOnSelectionChangeListeners}.
            // To avoid such problems, just march thru the list in the reverse order.
            for (int i = mOnSelectionChangeListeners.size() - 1; i >= 0; i--) {
                mOnSelectionChangeListeners.get(i).onStartTrackingTouch();
            }
        }
    }

    private void notifyListenersWhenSelectionDragStops() {
        if (hasOnSelectionChangeListener()) {
            // Since onStopTrackingTouch() is implemented by the app, it could do anything,
            // including removing itself from {@link mOnSelectionChangeListeners} – and that could
            // cause problems if an iterator is used on the ArrayList {@link mOnSelectionChangeListeners}.
            // To avoid such problems, just march thru the list in the reverse order.
            for (int i = mOnSelectionChangeListeners.size() - 1; i >= 0; i--) {
                mOnSelectionChangeListeners.get(i).onStopTrackingTouch();
            }
        }
    }

    private void notifyListenersOfSelectionIntervalChange() {
        if (hasOnSelectionChangeListener()) {
            final int[] interval = mSelectionInterval;
            getSelectionInterval(interval);
            // Since onSelectionIntervalChange() is implemented by the app, it could do anything,
            // including removing itself from {@link mOnSelectionChangeListeners} – and that could
            // cause problems if an iterator is used on the ArrayList {@link mOnSelectionChangeListeners}.
            // To avoid such problems, just march thru the list in the reverse order.
            for (int i = mOnSelectionChangeListeners.size() - 1; i >= 0; i--) {
                mOnSelectionChangeListeners.get(i).onSelectionIntervalChange(interval[0], interval[1]);
            }
        }
    }

    private void notifyListenersOfSelectionChange(boolean fromUser) {
        if (hasOnSelectionChangeListener()) {
            final int[] interval = mSelectionInterval;
            getSelectionInterval(interval);
            final int selection = getSelection();
            // Since onSelectionChange() is implemented by the app, it could do anything,
            // including removing itself from {@link mOnSelectionChangeListeners} – and that could
            // cause problems if an iterator is used on the ArrayList {@link mOnSelectionChangeListeners}.
            // To avoid such problems, just march thru the list in the reverse order.
            for (int i = mOnSelectionChangeListeners.size() - 1; i >= 0; i--) {
                mOnSelectionChangeListeners.get(i).onSelectionChange(
                        interval[0], interval[1], selection, fromUser);
            }
        }
    }

    public VideoClipView(@NonNull Context context) {
        this(context, null);
    }

    public VideoClipView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoClipView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDip = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.getTouchSlop() * mDip;
        mFrameBarHeight = (int) (2.5f * mDip + 0.5f);
        mProgressStrokeWidth = 3.0f * mDip;
        mProgressHeaderFooterStrokeWidth = 1.8f * mDip;
        mProgressHeaderFooterLength = 8.0f * mDip;

        mClipBackwards = ContextCompat.getDrawable(context, R.drawable.ic_clip_backwards);
        mClipBackwardsDark = ContextCompat.getDrawable(context, R.drawable.ic_clip_backwards_dark);
        mClipForward = ContextCompat.getDrawable(context, R.drawable.ic_clip_forward);
        mClipForwardDark = ContextCompat.getDrawable(context, R.drawable.ic_clip_forward_dark);

        mDrawableWidth = mClipBackwards.getIntrinsicWidth();
        mDrawableHeight = mClipBackwards.getIntrinsicHeight();

        mFrameBarColor = BitmapUtils.getDominantColorOrThrow(BitmapUtils.drawableToBitmap(mClipBackwards));
        mFrameBarDarkColor = BitmapUtils.getDominantColorOrThrow(BitmapUtils.drawableToBitmap(mClipBackwardsDark));

        mFrameBarPaint = new Paint();
        mFrameBarPaint.setStyle(Paint.Style.FILL);

        mProgressPaint = new Paint();
        mProgressPaint.setColor(ContextCompat.getColor(context, R.color.colorAccent));
        mProgressPaint.setStyle(Paint.Style.STROKE);

        final int verticalEndPadding = (int) (4.0f * mDip + 0.5f);
        super.setPadding(mDrawableWidth, verticalEndPadding, mDrawableWidth, verticalEndPadding);

        View.inflate(context, R.layout.view_videoclip, this);
        mThumbsGallery = findViewById(R.id.rv_videoclip_thumbs);
        mThumbsGallery.setMinimumHeight(mDrawableHeight);
        mThumbsGallery.setHasFixedSize(true);
        mThumbsGallery.setAdapter(mThumbsAdapter);

        if (BuildConfig.DEBUG) {
            addOnSelectionChangeListener(new OnSelectionChangeListener() {
                @Override
                public void onStartTrackingTouch() {
                    Log.d(TAG, "---------- onStartTrackingTouch ----------");
                }

                @Override
                public void onSelectionIntervalChange(int start, int end) {
                    Log.d(TAG, "onSelectionIntervalChange: " + start + "    " + end);
                }

                @Override
                public void onSelectionChange(int start, int end, int selection, boolean fromUser) {
                    Log.d(TAG, "onSelectionChange: " +
                            start + "    " + end + "    " + selection + "    " + fromUser);
                }

                @Override
                public void onStopTrackingTouch() {
                    Log.d(TAG, "---------- onStopTrackingTouch ----------");
                }
            });
        }
    }

    /** @return minimum lasting time for a selected clip */
    public int getMinimumClipDuration() {
        return mMinimumClipDuration;
    }

    /** Sets the minimum lasting time for a selected clip. */
    public void setMinimumClipDuration(int duration) {
        if (mMinimumClipDuration != duration) {
            mMinimumClipDuration = duration;
            onSetClipDuration();
        }
    }

    /** @return maximum lasting time for a selected clip */
    public int getMaximumClipDuration() {
        return mMaximumClipDuration;
    }

    /** Sets the maximum lasting time for a selected clip. */
    public void setMaximumClipDuration(int duration) {
        if (mMaximumClipDuration != duration) {
            mMaximumClipDuration = duration;
            onSetClipDuration();
        }
    }

    /** @return minimum duration for the clip(s) outside the selected time interval */
    public int getMinimumUnselectedClipDuration() {
        return mMinimumUnselectedClipDuration;
    }

    /** Sets the minimum duration of the clip(s) outside the selected time interval. */
    public void setMinimumUnselectedClipDuration(int duration) {
        if (mMinimumUnselectedClipDuration != duration) {
            mMinimumUnselectedClipDuration = duration;
            onSetClipDuration();
        }
    }

    private void onSetClipDuration() {
        resolveDrawableOffsets();
        resetProgressPercent(false);

        final boolean laidout = ViewCompat.isLaidOut(this);
        if (!laidout && !mFirstLayout || laidout && !mInLayout) {
            requestLayout();
            invalidate();
        }
    }

    private void resolveDrawableOffsets() {
        final int[] oldInterval = mSelectionInterval.clone();
        // Not to take the selection interval upon the first resolution of the offsets to ensure
        // the OnSelectionChangeListeners to get notified for the interval change.
        if (!Arrays.equals(oldInterval, sNoSelectionInterval)) {
            getSelectionInterval(oldInterval);
        }

        // Prefers assuming a video clip selected with half its maximum duration the first time
        // the view shows to the user to choosing another one using the minimum period of time.
        final float percent = 1.0f -
                Math.max(mMinimumClipDuration, mMaximumClipDuration / 2f)
                        / (mMaximumClipDuration + mMinimumUnselectedClipDuration);
        if (Utils.isLayoutRtl(this)) {
            mLeftDrawableOffset = percent;
            mRightDrawableOffset = 0;
        } else {
            mLeftDrawableOffset = 0;
            mRightDrawableOffset = percent;
        }
        mDrawableOffsetsResolved = true;

        getSelectionInterval(mSelectionInterval);
        if (!Arrays.equals(mSelectionInterval, oldInterval)) {
            notifyListenersOfSelectionIntervalChange();
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        // no-op
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        // no-op
    }

    @Override
    public void setClipToPadding(boolean clipToPadding) {
        // no-op
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        resolveDrawableOffsets();
        resetProgressPercent(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mInLayout = true;
        super.onLayout(changed, left, top, right, bottom);
        mInLayout = false;
        mFirstLayout = false;

        mMaximumClipBackwardsForwardGap = mThumbsGallery.getWidth() *
                (float) mMaximumClipDuration / (mMaximumClipDuration + mMinimumUnselectedClipDuration);
        mMinimumClipBackwardsForwardGap = mMaximumClipBackwardsForwardGap *
                (float) mMinimumClipDuration / mMaximumClipDuration;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawFrame(canvas);
        // Skip drawing the progress cursor if some drawable is being dragged
        if ((mTouchFlags & TFLAG_DRAWABLE_BEING_DRAGGED) == 0) {
            drawProgressCursor(canvas);
        }
    }

    private void drawFrame(Canvas canvas) {
        final int width = getWidth();
        final int childTop = getPaddingTop();
        final int childBottom = getHeight() - getPaddingBottom();

        final boolean dark;
        final float range = width - mDrawableWidth * 2;
        final float leftDrawableOffset = range * mLeftDrawableOffset;
        final float rightDrawableOffset = range * mRightDrawableOffset;
        final float framebarLeft = mDrawableWidth + leftDrawableOffset;
        final float framebarRight = width - mDrawableWidth - rightDrawableOffset;
        final float framebarWidth = framebarRight - framebarLeft;
        final int leftDrawableLeft = (int) (leftDrawableOffset + 0.5f);
        final int leftDrawableRight = (int) (framebarLeft + 0.5f);
        final int rightDrawableLeft = (int) (framebarRight + 0.5f);
        final int rightDrawableRight = (int) (width - rightDrawableOffset + 0.5f);

        final boolean rtl = Utils.isLayoutRtl(this);
        Drawable leftDrawable = rtl ? mClipForward : mClipBackwards;
        Drawable rightDrawable = rtl ? mClipBackwards : mClipForward;
        if (framebarWidth == mMinimumClipBackwardsForwardGap
                || framebarWidth == mMaximumClipBackwardsForwardGap) {
            dark = true;
            leftDrawable = rtl ? mClipForwardDark : mClipBackwardsDark;
            rightDrawable = rtl ? mClipBackwardsDark : mClipForwardDark;
        } else {
            dark = false;
        }
        mLeftDrawable = leftDrawable;
        mRightDrawable = rightDrawable;

        leftDrawable.setBounds(leftDrawableLeft, childTop, leftDrawableRight, childBottom);
        rightDrawable.setBounds(rightDrawableLeft, childTop, rightDrawableRight, childBottom);
        // Draw left & right drawables
        leftDrawable.draw(canvas);
        rightDrawable.draw(canvas);

        // Draw top & bottom frame bars
        mFrameBarPaint.setColor(dark ? mFrameBarDarkColor : mFrameBarColor);
        canvas.drawRect(leftDrawableRight, childTop,
                rightDrawableLeft, childTop + mFrameBarHeight, mFrameBarPaint);
        canvas.drawRect(leftDrawableRight, childBottom - mFrameBarHeight,
                rightDrawableLeft, childBottom, mFrameBarPaint);
    }

    private void drawProgressCursor(Canvas canvas) {
        final int height = getHeight();

        final float progressCenterX = progressPercentToProgressCenterX(mProgressPercent);
        //noinspection SuspiciousNameCombination
        final float progressTop = mProgressHeaderFooterStrokeWidth;
        final float progressBottom = height - mProgressHeaderFooterStrokeWidth;
        // Draw the progress
        mProgressPaint.setStrokeWidth(mProgressStrokeWidth);
        canvas.drawLine(progressCenterX, progressTop, progressCenterX, progressBottom, mProgressPaint);

        final float headerFooterStart = progressCenterX - mProgressHeaderFooterLength / 2f;
        final float headerFooterEnd = headerFooterStart + mProgressHeaderFooterLength;
        final float halfOfProgressHeaderFooterStrokeWidth = mProgressHeaderFooterStrokeWidth / 2f;
        //noinspection SuspiciousNameCombination
        final float headerCenterY = halfOfProgressHeaderFooterStrokeWidth;
        final float footerCenterY = height - halfOfProgressHeaderFooterStrokeWidth;
        // Draw header & footer of the progress
        mProgressPaint.setStrokeWidth(mProgressHeaderFooterStrokeWidth);
        canvas.drawLine(headerFooterStart, headerCenterY, headerFooterEnd, headerCenterY, mProgressPaint);
        canvas.drawLine(headerFooterStart, footerCenterY, headerFooterEnd, footerCenterY, mProgressPaint);
    }

    private float progressCenterXToProgressPercent(float progressCenterX) {
        final float range = mThumbsGallery.getWidth();
        final float hopsw = mProgressStrokeWidth / 2f;
        final float min = mDrawableWidth + mLeftDrawableOffset * range + hopsw;
        final float max = mDrawableWidth + (1.0f - mRightDrawableOffset) * range - hopsw;
        if (Utils.isLayoutRtl(this)) {
            if (progressCenterX < min) {
                // Calculates the percentage to the left side of the progress cursor located
                // next to the left drawable in right-to-left layout direction.
                return (range - (min - hopsw - mDrawableWidth)) / range;
            } else {
                progressCenterX = Util.constrainValue(progressCenterX, min, max);
                return (range - (progressCenterX + hopsw - mDrawableWidth)) / range;
            }
        } else {
            if (progressCenterX > max) {
                // Calculates the percentage to the right side of the progress cursor located
                // next to the right drawable in left-to-right layout direction.
                return (max + hopsw - mDrawableWidth) / range;
            } else {
                progressCenterX = Util.constrainValue(progressCenterX, min, max);
                return (progressCenterX - hopsw - mDrawableWidth) / range;
            }
        }
    }

    private float progressPercentToProgressCenterX(float progressPercent) {
        final float range = mThumbsGallery.getWidth();
        final float hopsw = mProgressStrokeWidth / 2f;
        if (Utils.isLayoutRtl(this)) {
            return Math.max(mDrawableWidth + (1.0f - progressPercent) * range - hopsw,
                    mDrawableWidth + mLeftDrawableOffset * range + hopsw);
        } else {
            return Math.min(mDrawableWidth + progressPercent * range + hopsw,
                    mDrawableWidth + (1.0f - mRightDrawableOffset) * range - hopsw);
        }
    }

    private void resetProgressPercent(boolean fromUser) {
        if (Utils.isLayoutRtl(this)) {
            setProgressPercent(1.0f - mRightDrawableOffset, fromUser);
        } else {
            setProgressPercent(mLeftDrawableOffset, fromUser);
        }
    }

    private void setProgressPercent(float percent, boolean fromUser) {
        if (!mDrawableOffsetsResolved) {
            resolveDrawableOffsets();
        }
        final boolean rtl = Utils.isLayoutRtl(this);
        final float min = rtl ? mRightDrawableOffset : mLeftDrawableOffset;
        final float max = 1.0f - (rtl ? mLeftDrawableOffset : mRightDrawableOffset);
        percent = Util.constrainValue(percent, min, max);
        if (mProgressPercent != percent) {
            mProgressPercent = percent;
            notifyListenersOfSelectionChange(fromUser);
        }
    }

    /**
     * Sets the selection for the video clip.
     *
     * @param selection millisecond position within the selectable time interval
     */
    public void setSelection(int selection) {
        final float old = mProgressPercent;
        setSelectionInternal(selection);
        if (mProgressPercent != old) {
            invalidate(); // Redraw progress cursor
        }
    }

    private void setSelectionInternal(int selection) {
        final float percent = (float) selection / (mMaximumClipDuration + mMinimumUnselectedClipDuration);
        setProgressPercent(percent, false);
    }

    /**
     * @return position of the selected millisecond within the selectable time interval
     */
    public int getSelection() {
        return (int) ((mMaximumClipDuration + mMinimumUnselectedClipDuration) * mProgressPercent + 0.5f);
    }

    /**
     * Gets the time interval in millisecond of the selected video clip by providing an array
     * of two integers that will hold the start and end value in that order.
     */
    public void getSelectionInterval(int[] outInterval) {
        if (outInterval == null || outInterval.length < 2) {
            throw new IllegalArgumentException("outInterval must be an array of two integers");
        }
        if (mDrawableOffsetsResolved) {
            final boolean rtl = Utils.isLayoutRtl(this);
            final float duration = mMaximumClipDuration + mMinimumUnselectedClipDuration;
            outInterval[0] = (int) (0.5f + duration *
                    (rtl ? mRightDrawableOffset : mLeftDrawableOffset));
            outInterval[1] = (int) (0.5f + duration *
                    (1.0f - (rtl ? mLeftDrawableOffset : mRightDrawableOffset)));
            // The drawable offset properties may have not been determined yet
        } else {
            outInterval[0] = 0;
            outInterval[1] = Math.max((int) (mMaximumClipDuration / 2f + 0.5f), mMinimumClipDuration);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(ev)) {
                    break;
                }

                if (tryHandleTouchEvent()) {
                    notifyListenersWhenSelectionDragStarts();
                    return true;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if ((mTouchFlags & TOUCH_MASK) != 0) {
                    notifyListenersWhenSelectionDragStops();
                }
                resetTouch();
                break;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(event)) {
                    return false;
                }

                if ((mTouchFlags & TOUCH_MASK) == 0) {
                    if (tryHandleTouchEvent()) {
                        notifyListenersWhenSelectionDragStarts();
                    }
                } else {
                    final float x = mTouchX[mTouchX.length - 1];
                    final float lastX = mTouchX[mTouchX.length - 2];

                    boolean invalidateNeeded = false;
                    if ((mTouchFlags & TFLAG_PROGRESS_BEING_DRAGGED) != 0) {
                        final float old = mProgressPercent;
                        final float percent = progressCenterXToProgressPercent(x + mProgressMoveOffset);
                        setProgressPercent(percent, true);
                        invalidateNeeded = mProgressPercent != old;
                    } else {
                        final int[] startInterval = mSelectionInterval.clone();
                        getSelectionInterval(startInterval);

                        final float originalRange = mThumbsGallery.getWidth();
                        if ((mTouchFlags & TFLAG_LEFT_DRAWABLE_BEING_DRAGGED) != 0) {
                            final float range = originalRange * (1.0f - mRightDrawableOffset);
                            mLeftDrawableOffset = Util.constrainValue(
                                    mLeftDrawableOffset * originalRange + x - lastX,
                                    Math.max(0, range - mMaximumClipBackwardsForwardGap),
                                    range - mMinimumClipBackwardsForwardGap) / originalRange;
                        } else /*if ((mTouchFlags & TFLAG_RIGHT_DRAWABLE_BEING_DRAGGED) != 0)*/ {
                            final float range = originalRange * (1.0f - mLeftDrawableOffset);
                            mRightDrawableOffset = Util.constrainValue(
                                    mRightDrawableOffset * originalRange + lastX - x,
                                    Math.max(0, range - mMaximumClipBackwardsForwardGap),
                                    range - mMinimumClipBackwardsForwardGap) / originalRange;
                        }

                        final int[] endInterval = mSelectionInterval;
                        getSelectionInterval(endInterval);
                        if (!Arrays.equals(endInterval, startInterval)) {
                            invalidateNeeded = true;
                            notifyListenersOfSelectionIntervalChange();
                            // Resets the position of the progress cursor, in case it is out of our range.
                            resetProgressPercent(true);
                        }
                    }
                    if (invalidateNeeded) {
                        invalidate();
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if ((mTouchFlags & TOUCH_MASK) != 0) {
                    notifyListenersWhenSelectionDragStops();
                }
                resetTouch();
                break;
        }
        return true;
    }

    private boolean tryHandleTouchEvent() {
        if (mLeftDrawable.getBounds().contains((int) mDownX, (int) mDownY)) {
            if (checkTouchSlop()) {
                mTouchFlags |= TFLAG_LEFT_DRAWABLE_BEING_DRAGGED;
                requestParentDisallowInterceptTouchEvent();
                return true;
            }
        } else if (mRightDrawable.getBounds().contains((int) mDownX, (int) mDownY)) {
            if (checkTouchSlop()) {
                mTouchFlags |= TFLAG_RIGHT_DRAWABLE_BEING_DRAGGED;
                requestParentDisallowInterceptTouchEvent();
                return true;
            }
        } else {
            mProgressMoveOffset = progressPercentToProgressCenterX(mProgressPercent) - mDownX;
            if (!Float.isNaN(mProgressMoveOffset)) {
                final float absPMO = Math.abs(mProgressMoveOffset);
                if (absPMO >= 0 && absPMO <= 25f * mDip && checkTouchSlop()) {
                    mTouchFlags |= TFLAG_PROGRESS_BEING_DRAGGED;
                    requestParentDisallowInterceptTouchEvent();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkTouchSlop() {
        final float absDx = Math.abs(mTouchX[mTouchX.length - 1] - mDownX);
        if (absDx > mTouchSlop) {
            final float absDy = Math.abs(mTouchY[mTouchY.length - 1] - mDownY);
            return absDx > absDy;
        }
        return false;
    }

    private void requestParentDisallowInterceptTouchEvent() {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void onPointerDown(MotionEvent e) {
        final int actionIndex = e.getActionIndex();
        mActivePointerId = e.getPointerId(actionIndex);
        mDownX = e.getX(actionIndex);
        mDownY = e.getY(actionIndex);
        markCurrTouchPoint(mDownX, mDownY);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean onPointerMove(MotionEvent e) {
        final int pointerIndex = e.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) {
            Log.e(TAG, "Error processing scroll; pointer index for id "
                    + mActivePointerId + " not found. Did any MotionEvents get skipped?");
            return false;
        }
        markCurrTouchPoint(e.getX(pointerIndex), e.getY(pointerIndex));
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent e) {
        final int pointerIndex = e.getActionIndex();
        final int pointerId = e.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up.
            // Choose a new active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = e.getPointerId(newPointerIndex);
            mDownX = e.getX(newPointerIndex);
            mDownY = e.getY(newPointerIndex);
            markCurrTouchPoint(mDownX, mDownY);
        }
    }

    private void markCurrTouchPoint(float x, float y) {
        System.arraycopy(mTouchX, 1, mTouchX, 0, mTouchX.length - 1);
        mTouchX[mTouchX.length - 1] = x;
        System.arraycopy(mTouchY, 1, mTouchY, 0, mTouchY.length - 1);
        mTouchY[mTouchY.length - 1] = y;
    }

    private void resetTouch() {
        mTouchFlags &= ~TOUCH_MASK;
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        mProgressMoveOffset = Float.NaN;
    }

    public void addThumbnail(@Nullable Bitmap thumb) {
        addThumbnail(mThumbsAdapter.mThumbnails.size(), thumb);
    }

    public void addThumbnail(int index, @Nullable Bitmap thumb) {
        mThumbsAdapter.mThumbnails.add(index, thumb);
        mThumbsAdapter.notifyItemInserted(index);
        mThumbsAdapter.notifyItemRangeChanged(0, mThumbsAdapter.getItemCount());
    }

    public void setThumbnail(int index, @Nullable Bitmap thumb) {
        mThumbsAdapter.mThumbnails.set(index, thumb);
        mThumbsAdapter.notifyItemChanged(index);
    }

    public void removeThumbnail(@Nullable Bitmap thumb) {
        final int index = mThumbsAdapter.mThumbnails.indexOf(thumb);
        if (index != -1) {
            removeThumbnail(index);
        }
    }

    public void removeThumbnail(int index) {
        mThumbsAdapter.mThumbnails.remove(index);
        mThumbsAdapter.notifyItemRemoved(index);
        mThumbsAdapter.notifyItemRangeChanged(0, mThumbsAdapter.getItemCount());
    }

    public void clearThumbnails() {
        final int itemCount = mThumbsAdapter.getItemCount();
        mThumbsAdapter.mThumbnails.clear();
        mThumbsAdapter.notifyItemRangeRemoved(0, itemCount);
    }

    private static class ThumbsAdapter extends RecyclerView.Adapter<ThumbsAdapter.ViewHolder> {

        final List<Bitmap> mThumbnails = new ArrayList<>();

        RecyclerView mHost;

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            mHost = recyclerView;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_clip_thumbs_gallery, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Bitmap thumb = mThumbnails.get(position);

            ViewGroup.LayoutParams lp = holder.thumbImage.getLayoutParams();
            lp.width = (int) ((float) mHost.getWidth() / mThumbnails.size() + 0.5f);
            lp.height = mHost.getHeight();

            holder.thumbImage.setImageBitmap(thumb);
        }

        @Override
        public int getItemCount() {
            return mThumbnails.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumbImage;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                thumbImage = itemView.findViewById(R.id.image_thumb);
            }
        }
    }
}
