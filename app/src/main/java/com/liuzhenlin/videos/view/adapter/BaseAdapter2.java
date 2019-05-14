/*
 * Created on 2018/09/05.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import androidx.annotation.CallSuper;

/**
 * @author 刘振林
 */
public abstract class BaseAdapter2 extends BaseAdapter {
    private AdapterView mAdapterView;

    @CallSuper
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        mAdapterView = (AdapterView) parent;
        return convertView;
    }

    public void notifyItemChanged(int position) {
        if (mAdapterView == null) return;

        // 第一个可见item的位置
        final int firstVisiblePosition = mAdapterView.getFirstVisiblePosition();
        // 最后一个可见item的位置
        final int lastVisiblePosition = mAdapterView.getLastVisiblePosition();

        // 在看得见范围内才更新，不可见的滑动后自动会调用getView方法更新
        if (position >= firstVisiblePosition && position <= lastVisiblePosition) {
            int itemIndex = position - firstVisiblePosition;
            if (mAdapterView instanceof ListView) {
                ListView listView = (ListView) mAdapterView;
                itemIndex += listView.getHeaderViewsCount();
            }
            // 获取指定位置的view对象
            View itemView = mAdapterView.getChildAt(itemIndex);
            getView(position, itemView, mAdapterView);
        }
    }
}
