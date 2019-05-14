package com.liuzhenlin.videos.view.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import com.liuzhenlin.galleryviewer.GalleryViewPager;

import java.util.List;

/**
 * @author 刘振林
 */
public class GalleryPagerAdapter<V extends View> extends PagerAdapter
        implements GalleryViewPager.ItemCallback {
    @Nullable
    private final List<V> mViews;

    @Nullable
    public List<V> getViews() {
        return mViews;
    }

    public GalleryPagerAdapter(@Nullable List<V> views) {
        mViews = views;
    }

    @Override
    public int getCount() {
        return mViews == null ? 0 : mViews.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        assert mViews != null;
        View view = mViews.get(position);
        if (view.getParent() == null) {
            container.addView(view);
        }
        return view;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return POSITION_NONE;
    }

    @Override
    public Object getItemAt(int position) {
        if (mViews != null && position >= 0 && position < mViews.size()) {
            return mViews.get(position);
        }
        return null;
    }
}