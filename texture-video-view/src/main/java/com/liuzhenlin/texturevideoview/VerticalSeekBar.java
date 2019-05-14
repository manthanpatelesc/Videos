/*
 * Created on 2017/10/01.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.graphics.drawable.DrawableCompat;

import com.liuzhenlin.texturevideoview.utils.Utils;

/**
 * @author 刘振林
 */
public class VerticalSeekBar extends AppCompatSeekBar {

    public void setOnVerticalSeekBarChangeListener(OnVerticalSeekBarChangeListener l) {
        mListener = l;
    }

    public interface OnVerticalSeekBarChangeListener {
        void onVerticalProgressChanged(VerticalSeekBar verticalSeekBar, int progress, boolean fromUser);

        void onStartVerticalTrackingTouch(VerticalSeekBar verticalSeekBar);

        void onStopVerticalTrackingTouch(VerticalSeekBar verticalSeekBar);
    }

    private OnVerticalSeekBarChangeListener mListener;

    private boolean mIsDragging;
    private float mTouchDownY;
    private int mScaledTouchSlop;

    private Drawable mThumb;

    public VerticalSeekBar(Context context) {
        super(context);
        init(context);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //noinspection SuspiciousNameCombination
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(h, w, oldw, oldh);
    }

    @Override
    protected synchronized void onDraw(Canvas c) {
        if (Utils.isLayoutRtl(this)) {
            c.rotate(90);
            c.translate(0, -getWidth());
        } else {
            c.rotate(-90);
            c.translate(-getHeight(), 0);
        }
        super.onDraw(c);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (Utils.isInScrollingContainer(this)) {
                    mTouchDownY = event.getY();
                } else {
                    startDrag(event);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mIsDragging) {
                    trackTouchEvent(event);
                } else {
                    final float y = event.getY();
                    if (Math.abs(y - mTouchDownY) > mScaledTouchSlop) {
                        startDrag(event);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mIsDragging) {
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                    setPressed(false);
                } else {
                    // Touch up when we never crossed the touch slop threshold should
                    // be interpreted as a tap-seek to that location.
                    onStartTrackingTouch();
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                }
                // ProgressBar doesn't know to repaint the thumb drawable in its inactive state
                // when the touch stops (because the value has not apparently changed)
                invalidate();
                break;

            case MotionEvent.ACTION_CANCEL:
                if (mIsDragging) {
                    onStopTrackingTouch();
                    setPressed(false);
                }
                invalidate(); // see above explanation
                break;
        }
        return true;
    }

    @Override
    public void setThumb(Drawable thumb) {
        super.setThumb(thumb);
        mThumb = thumb;
    }

    private void startDrag(MotionEvent event) {
        setPressed(true);

        if (mThumb != null) {
            // This may be within the padding region.
            invalidate(mThumb.getBounds());
        }

        onStartTrackingTouch();
        trackTouchEvent(event);
        attemptClaimDrag();
    }

    private void setHotspot(float x, float y) {
        final Drawable bg = getBackground();
        if (bg != null) {
            DrawableCompat.setHotspot(bg, x, y);
        }
    }

    private void trackTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();

        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();
        final int height = getHeight();
        final int availableHeight = height - paddingTop - paddingBottom;

        final float scale;
        if (y > height - paddingBottom) {
            scale = 0.0f;
        } else if (y < paddingTop) {
            scale = 1.0f;
        } else {
            scale = ((float) height - (float) paddingBottom - y) / (float) availableHeight;
        }
        final int progress;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final int min = getMin();
            final int range = getMax() - min;
            progress = Math.round(scale * (float) range + (float) min);
        } else {
            progress = (int) (scale * (float) getMax() + 0.5f);
        }

        setHotspot(x, y);
        setProgress(progress);
    }

    /**
     * Tries to claim the user's drag motion, and requests disallowing any ancestors from stealing
     * events in the drag.
     */
    private void attemptClaimDrag() {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    /**
     * This is called when the user has started touching this widget.
     */
    void onStartTrackingTouch() {
        mIsDragging = true;
        if (mListener != null) {
            mListener.onStartVerticalTrackingTouch(this);
        }
    }

    /**
     * This is called when the user either releases his touch or the touch is canceled.
     */
    void onStopTrackingTouch() {
        mIsDragging = false;
        if (mListener != null) {
            mListener.onStopVerticalTrackingTouch(this);
        }
    }

    @Override
    public synchronized void setProgress(int progress) {
        final int current = getProgress();
        if (current != progress) {
            super.setProgress(progress);
            if (getProgress() != current) {
                onSizeChanged(getWidth(), getHeight(), 0, 0);
                if (mListener != null) {
                    mListener.onVerticalProgressChanged(this, progress, isPressed());
                }
            }
        }
    }
}