/*
 * Created on 10/19/19 9:37 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * @author 刘振林
 */
public class ScrollDisableViewPager extends ViewPager {
    private boolean mScrollEnabled = true;

    public ScrollDisableViewPager(@NonNull Context context) {
        super(context);
    }

    public ScrollDisableViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean isScrollEnabled() {
        return mScrollEnabled;
    }

    public void setScrollEnabled(boolean enabled) {
        mScrollEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mScrollEnabled) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mScrollEnabled) {
            return false;
        }
        return super.onTouchEvent(ev);
    }
}
