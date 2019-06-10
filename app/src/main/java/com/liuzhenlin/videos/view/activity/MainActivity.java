/*
 * Created on 2017/09/26.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.liuzhenlin.floatingmenu.DensityUtils;
import com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout;
import com.liuzhenlin.swipeback.SwipeBackLayout;
import com.liuzhenlin.texturevideoview.utils.BitmapUtils;
import com.liuzhenlin.texturevideoview.utils.FileUtils;
import com.liuzhenlin.texturevideoview.utils.SystemBarUtils;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.AppSharedPreferences;
import com.liuzhenlin.videos.utils.ColorUtils;
import com.liuzhenlin.videos.utils.FileUtils2;
import com.liuzhenlin.videos.utils.NetworkUtil;
import com.liuzhenlin.videos.utils.OSHelper;
import com.liuzhenlin.videos.utils.TextViewUtils;
import com.liuzhenlin.videos.utils.UiUtils;
import com.liuzhenlin.videos.view.ScrollDisableListView;
import com.liuzhenlin.videos.view.adapter.BaseAdapter2;
import com.liuzhenlin.videos.view.fragment.FoldedVideosFragment;
import com.liuzhenlin.videos.view.fragment.FragmentPartLifecycleCallback;
import com.liuzhenlin.videos.view.fragment.SearchedVideosFragment;
import com.liuzhenlin.videos.view.fragment.VideoListFragment;
import com.liuzhenlin.videos.view.swiperefresh.SwipeRefreshLayout;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author 刘振林
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
        SlidingDrawerLayout.OnDrawerScrollListener, SwipeBackLayout.SwipeListener,
        FragmentPartLifecycleCallback, VideoListFragment.InteractionCallback,
        SearchedVideosFragment.InteractionCallback, FoldedVideosFragment.InteractionCallback {

    private static final String TAG_VIDEO_LIST_FRAGMENT = "VideoListFragment";
    private static final String TAG_SEARCHED_VIDEOS_FRAGMENT = "SearchedVideosFragment";
    private static final String TAG_FOLDED_VIDEOS_FRAGMENT = "FoldedVideosFragment";
    private VideoListFragment mVideoListFragment;
    private SearchedVideosFragment mSearchedVideosFragment;
    private FoldedVideosFragment mFoldedVideosFragment;

    private SlidingDrawerLayout mSlidingDrawerLayout;
    private ScrollDisableListView mDrawerList;
    private DrawerListAdapter mDrawerListAdapter;
    private ImageView mDrawerImage;
    private boolean mIsDrawerStatusLight = true;
    private boolean mIsDrawerListForegroundLight = false;
    private float mOldDrawerScrollPercent;

    private static final int REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE = 2;

    private ViewGroup mActionBarContainer;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    // VideoListFragment的ActionBar
    private ViewGroup mActionBar;
    private ImageButton mHomeAsUpIndicator;
    private DrawerArrowDrawable mDrawerArrowDrawable;
    private TextView mTitleText;
    private ImageButton mSearchButton;

    // 临时缓存SearchedVideosFragment或FoldedVideosFragment的ActionBar
    private ViewGroup mTmpActionBar;

    private float mSwipeBackScrollPercent;

    private UpdateChecker mUpdateChecker;
    private String mCheckUpdateResultText;
    private String mIsTheLatestVersion;
    private String mFindNewVersion;
    private boolean mIsAutoCheckUpdate = true;

    private boolean mIsBackPressed;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mIsTheLatestVersion = getString(R.string.isTheLatestVersion);
        mFindNewVersion = getString(R.string.findNewVersion);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SystemBarUtils.setTransparentStatus(window);
            } else {
                SystemBarUtils.setTranslucentStatus(window, true);
            }
        }

        initViews(this);

        if (savedInstanceState == null) {
            mVideoListFragment = new VideoListFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container_fragment, mVideoListFragment, TAG_VIDEO_LIST_FRAGMENT)
                    .commit();
        } else {
            FragmentManager fm = getSupportFragmentManager();

            mVideoListFragment = (VideoListFragment) fm
                    .findFragmentByTag(TAG_VIDEO_LIST_FRAGMENT);
            assert mVideoListFragment != null;
            onAttachFragment(mVideoListFragment);

            mSearchedVideosFragment = (SearchedVideosFragment) fm
                    .findFragmentByTag(TAG_SEARCHED_VIDEOS_FRAGMENT);
            if (mSearchedVideosFragment != null) {
                onAttachFragment(mSearchedVideosFragment);
            }

            mFoldedVideosFragment = (FoldedVideosFragment) fm
                    .findFragmentByTag(TAG_FOLDED_VIDEOS_FRAGMENT);
            if (mFoldedVideosFragment != null) {
                onAttachFragment(mFoldedVideosFragment);
            }
        }
    }

    private void initViews(final Context context) {
        final int screenWidth = App.getInstance().getScreenWidthIgnoreOrientation();

        mSlidingDrawerLayout = findViewById(R.id.slidingDrawerLayout);
        mSlidingDrawerLayout.setStartDrawerWidthPercent(
                1f - (App.getInstance().getVideoThumbWidth() +
                        DensityUtils.dp2px(context, 20f)) / (float) screenWidth);
        mSlidingDrawerLayout.setContentSensitiveEdgeSize(screenWidth);
        mSlidingDrawerLayout.addOnDrawerScrollListener(new SlidingDrawerLayout.SimpleOnDrawerScrollListener() {
            @Override
            public void onScrollStateChange(@NonNull SlidingDrawerLayout parent,
                                            @NonNull View drawer, int state) {
                parent.removeOnDrawerScrollListener(this);

                mDrawerList = drawer.findViewById(R.id.list_drawer);
                mDrawerListAdapter = new DrawerListAdapter();
                View divider = new ViewStub(context);
                mDrawerList.addHeaderView(divider);
                mDrawerList.addFooterView(divider);
                mDrawerList.setAdapter(mDrawerListAdapter);
                mDrawerList.setOnItemClickListener(MainActivity.this);
                mDrawerList.setOnItemLongClickListener(MainActivity.this);
                mDrawerList.setScrollEnabled(false);

                mDrawerImage = findViewById(R.id.image_drawer);
                AppSharedPreferences asp = AppSharedPreferences.getInstance(context);
                final String path = asp.getDrawerBackgroundPath();
                // 未设置背景图片
                if (path == null) {
                    setLightDrawerStatus(asp.isLightDrawerStatus());
                    mDrawerListAdapter.setLightDrawerListForeground(asp.isLightDrawerListForeground());

                } else if (new File(path).exists()) {
                    setDrawerBackground(path);
                    // 用户从存储卡中删除了该路径下的图片或其路径已改变
                } else {
                    asp.setDrawerBackgroundPath(null);
                    asp.setLightDrawerStatus(true);
                    asp.setLightDrawerListForeground(false);
                }
            }
        });
        mSlidingDrawerLayout.addOnDrawerScrollListener(this);

        mSwipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.pink, R.color.lightBlue, R.color.purple);
        mSwipeRefreshLayout.setOnRequestDisallowInterceptTouchEventCallback(
                new SwipeRefreshLayout.OnRequestDisallowInterceptTouchEventCallback() {
                    @Override
                    public boolean shouldPassUpToRequestDisallowInterceptTouchEvent() {
                        return true;
                    }
                });

        mActionBarContainer = findViewById(R.id.container_actionbar);
        mActionBar = findViewById(R.id.actionbar);
        insertTopPaddingToActionBarIfNeeded(mActionBar);

        mDrawerArrowDrawable = new DrawerArrowDrawable(this);
        mDrawerArrowDrawable.setGapSize(12.5f);
        mDrawerArrowDrawable.setColor(Color.WHITE);

        mHomeAsUpIndicator = mActionBar.findViewById(R.id.bt_homeAsUpIndicator);
        mHomeAsUpIndicator.setImageDrawable(mDrawerArrowDrawable);
        mHomeAsUpIndicator.setOnClickListener(this);

        mTitleText = mActionBar.findViewById(R.id.text_title);
        mTitleText.post(new Runnable() {
            @Override
            public void run() {
                ViewGroup.MarginLayoutParams hauilp = (ViewGroup.MarginLayoutParams) mHomeAsUpIndicator.getLayoutParams();
                ViewGroup.MarginLayoutParams ttlp = (ViewGroup.MarginLayoutParams) mTitleText.getLayoutParams();
                MarginLayoutParamsCompat.setMarginStart(ttlp, (int) (
                        DensityUtils.dp2px(MainActivity.this, 10) // margin
                                + App.getInstance().getVideoThumbWidth()
                                - hauilp.leftMargin - hauilp.rightMargin
                                - mHomeAsUpIndicator.getWidth() - mTitleText.getWidth() + 0.5f));
                mTitleText.setLayoutParams(ttlp);
            }
        });

        mSearchButton = mActionBar.findViewById(R.id.bt_search);
        mSearchButton.setOnClickListener(this);
    }

    private void insertTopPaddingToActionBarIfNeeded(View actionbar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final int statusHeight = App.getInstance().getStatusHeight();
            actionbar.getLayoutParams().height += statusHeight;
            actionbar.setPadding(
                    actionbar.getPaddingLeft(),
                    actionbar.getPaddingTop() + statusHeight,
                    actionbar.getPaddingRight(),
                    actionbar.getPaddingBottom());
        }
    }

    private void setLightDrawerStatus(boolean light) {
        if (mIsDrawerStatusLight != light) {
            mIsDrawerStatusLight = light;
            AppSharedPreferences.getInstance(this).setLightDrawerStatus(light);
            if (mSlidingDrawerLayout.hasOpenedDrawer()) {
                setLightStatus(light);
            }
        }
    }

    private void setDrawerBackground(String path) {
        if (path == null || path.equals(mDrawerImage.getTag())) {
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(path, null);
        if (bitmap != null) {
            recycleDrawerImage();
            mDrawerImage.setImageBitmap(bitmap);
            mDrawerImage.setTag(path);

            AppSharedPreferences asp = AppSharedPreferences.getInstance(this);
            final String savedPath = asp.getDrawerBackgroundPath();
            if (path.equals(savedPath)) {
                setLightDrawerStatus(asp.isLightDrawerStatus());
                mDrawerListAdapter.setLightDrawerListForeground(asp.isLightDrawerListForeground());
            } else {
                asp.setDrawerBackgroundPath(path);

                final boolean lightBackground = ColorUtils.isLightColor(
                        BitmapUtils.getDominantColor(bitmap, Color.WHITE));
                setLightDrawerStatus(lightBackground);
                mDrawerListAdapter.setLightDrawerListForeground(!lightBackground);
            }
        }
    }

    private void recycleDrawerImage() {
        if (mDrawerImage.getBackground() instanceof BitmapDrawable) {
            ((BitmapDrawable) mDrawerImage.getBackground()).getBitmap().recycle();
        }
    }

    @Override
    public void onDrawerOpened(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {
    }

    @Override
    public void onDrawerClosed(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {
    }

    @Override
    public void onScrollPercentChange(@NonNull SlidingDrawerLayout parent,
                                      @NonNull View drawer, float percent) {
        mDrawerArrowDrawable.setProgress(percent);

        final boolean light = percent >= 0.5f;
        if ((light && mOldDrawerScrollPercent < 0.5f || !light && mOldDrawerScrollPercent >= 0.5f)
                && AppSharedPreferences.getInstance(this).isLightDrawerStatus()) {
            setLightStatus(light);
        }
        mOldDrawerScrollPercent = percent;
    }

    @Override
    public void onScrollStateChange(@NonNull SlidingDrawerLayout parent,
                                    @NonNull View drawer, int state) {
        View view = mVideoListFragment.getView();
        switch (state) {
            case SlidingDrawerLayout.SCROLL_STATE_TOUCH_SCROLL:
            case SlidingDrawerLayout.SCROLL_STATE_AUTO_SCROLL:
                mTitleText.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                mSearchButton.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                if (view != null) {
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }
                break;
            case SlidingDrawerLayout.SCROLL_STATE_IDLE:
                mTitleText.setLayerType(View.LAYER_TYPE_NONE, null);
                mSearchButton.setLayerType(View.LAYER_TYPE_NONE, null);
                if (view != null) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                break;
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        checkUpdate(); // 打开应用时检测更新
    }

    @Override
    public void onBackPressed() {
        if (mFoldedVideosFragment != null) {
            if (!mFoldedVideosFragment.onBackPressed()) {
//                super.onBackPressed();
                mFoldedVideosFragment.getSwipeBackLayout().scrollToFinishActivityOrPopUpFragment();
            }
        } else if (mSearchedVideosFragment != null) {
            super.onBackPressed();

        } else //noinspection StatementWithEmptyBody
            if (mVideoListFragment.onBackPressed()) {

            } else if (mSlidingDrawerLayout.hasOpenedDrawer()) {
                mSlidingDrawerLayout.closeDrawer(true);

            } else if (!mIsBackPressed) {
                mIsBackPressed = true;
                mSlidingDrawerLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mIsBackPressed = false;
                    }
                }, 3000);
                Toast.makeText(this, R.string.pressAgainToExitApp, Toast.LENGTH_SHORT).show();

            } else {
                super.onBackPressed();
            }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDrawerList != null) {
            recycleDrawerImage();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE:
                if (data != null && data.getData() != null) {
                    setDrawerBackground(FileUtils.UriResolver.getPath(this, data.getData()));
                }
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_homeAsUpIndicator:
                if (mSlidingDrawerLayout.hasOpenedDrawer()) {
                    mSlidingDrawerLayout.closeDrawer(true);
                } else {
                    mSlidingDrawerLayout.openDrawer(Gravity.START, true);
                }
                break;
            case R.id.bt_search:
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList(Consts.KEY_VIDEOS, mVideoListFragment.getAllVideos());

                mSearchedVideosFragment = new SearchedVideosFragment();
                mSearchedVideosFragment.setArguments(bundle);
                mSearchedVideosFragment.setTargetFragment(mVideoListFragment,
                        Consts.REQUEST_CODE_SEARCHED_VIDEOS_FRAGMENT);

                getSupportFragmentManager().beginTransaction()
                        .add(R.id.container_fragment, mSearchedVideosFragment, TAG_SEARCHED_VIDEOS_FRAGMENT)
                        .addToBackStack(TAG_SEARCHED_VIDEOS_FRAGMENT)
                        .commit();
                break;

            case R.id.bt_confirm_aboutAppDialog:
            case R.id.bt_confirm_updateLogsDialog:
                ((Dialog) v.getTag()).cancel();
                break;
        }
    }

    private static final int[] sDrawerListItemIDs = {
            R.string.checkUpdate,
            R.string.aboutApp,
            R.string.updateLogs,
            R.string.userFeedback,
            R.string.setBackground
    };

    private class DrawerListAdapter extends BaseAdapter2 {

        final String[] mDrawerListItems;

        @ColorInt
        int mTextColor = Color.BLACK;
        @ColorInt
        int mSubTextColor = Color.GRAY;
        @ColorInt
        static final int SUBTEXT_HIGHLIGHT_COLOR = Color.RED;

        final Drawable mForwardDrawable;
        final Drawable mLightDrawerListDivider = ContextCompat.getDrawable(
                MainActivity.this, R.drawable.divider_light_drawer_list);
        final Drawable mDarkDrawerListDivider = ContextCompat.getDrawable(
                MainActivity.this, R.drawable.divider_dark_drawer_list);

        DrawerListAdapter() {
            mDrawerListItems = new String[sDrawerListItemIDs.length];
            for (int i = 0; i < mDrawerListItems.length; i++) {
                mDrawerListItems[i] = getString(sDrawerListItemIDs[i]);
            }

            Drawable temp = ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_forward);
            assert temp != null;
            mForwardDrawable = DrawableCompat.wrap(temp);
            DrawableCompat.setTintList(mForwardDrawable, null);
        }

        void setLightDrawerListForeground(boolean light) {
            if (mIsDrawerListForegroundLight != light) {
                mIsDrawerListForegroundLight = light;
                AppSharedPreferences.getInstance(MainActivity.this).setLightDrawerListForeground(light);

                if (light) {
                    mTextColor = Color.WHITE;
                    mSubTextColor = 0xFFE0E0E0;
                    DrawableCompat.setTint(mForwardDrawable, Color.LTGRAY);
                } else {
                    mTextColor = Color.BLACK;
                    mSubTextColor = Color.GRAY;
                    // 清除tint
                    DrawableCompat.setTintList(mForwardDrawable, null);
                }
                notifyDataSetChanged();

                if (light) {
                    mDrawerList.setDivider(mLightDrawerListDivider);
                } else {
                    mDrawerList.setDivider(mDarkDrawerListDivider);
                }
            }
        }

        @Override
        public int getCount() {
            return mDrawerListItems.length;
        }

        @Override
        public String getItem(int position) {
            return mDrawerListItems[position];
        }

        @Override
        public long getItemId(int position) {
            return sDrawerListItemIDs[position];
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup listview) {
            DrawerListViewHolder vh;
            if (convertView == null) {
                vh = new DrawerListViewHolder(listview.getContext());
                convertView = vh.convertView;
                convertView.setTag(vh);
            } else {
                vh = (DrawerListViewHolder) convertView.getTag();
            }
            vh.text.setText(mDrawerListItems[position]);
            vh.text.setTextColor(mTextColor);
            if (position == 0) {
                vh.subText.setText(mCheckUpdateResultText);
                vh.subText.setTextColor(mFindNewVersion.equals(mCheckUpdateResultText) ?
                        SUBTEXT_HIGHLIGHT_COLOR : mSubTextColor);
                vh.subText.setCompoundDrawables(null, null, null, null);
            } else {
                vh.subText.setText(Consts.EMPTY_STRING);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    vh.subText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            null, null, mForwardDrawable, null);
                } else {
                    vh.subText.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, mForwardDrawable, null);
                }
            }
            return super.getView(position, convertView, mDrawerList);
        }

        class DrawerListViewHolder {
            final View convertView;
            final TextView text;
            final TextView subText;

            DrawerListViewHolder(Context context) {
                convertView = View.inflate(context, R.layout.item_drawer_list, null);
                text = convertView.findViewById(R.id.text_list);
                subText = convertView.findViewById(R.id.subtext_list);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> listview, View view, int position, long id) {
        switch ((int) id) {
            case R.string.checkUpdate:
                checkUpdate();
                break;
            case R.string.aboutApp:
                showAboutAppDialog();
                break;
            case R.string.updateLogs:
                showUpdateLogsDialog();
                break;
            case R.string.userFeedback:
                startActivity(new Intent(this, FeedbackActivity.class));
                break;
            case R.string.setBackground:
                Intent it = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
                startActivityForResult(it, REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE);
                break;
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, final View view, int position, long id) {
        switch ((int) id) {
            case R.string.setBackground:
                PopupMenu ppm = new PopupMenu(this, view);
                Menu menu = ppm.getMenu();
                menu.add(Menu.NONE, R.id.changeTextColor, Menu.NONE,
                        mIsDrawerListForegroundLight ? R.string.setDarkTexts : R.string.setLightTexts);
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    menu.add(Menu.NONE, R.id.changeStatusTextColor, Menu.NONE,
                            mIsDrawerStatusLight ? R.string.setLightStatus : R.string.setDarkStatus);
                }
                ppm.setGravity(Gravity.END);
                ppm.show();
                ppm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.changeTextColor:
                                mDrawerListAdapter.setLightDrawerListForeground(
                                        !mIsDrawerListForegroundLight);
                                return true;
                            case R.id.changeStatusTextColor:
                                setLightDrawerStatus(!mIsDrawerStatusLight);
                                return true;
                        }
                        return false;
                    }
                });
                return true;
        }
        return false;
    }

    private void showAboutAppDialog() {
        View view = View.inflate(this, R.layout.dialog_about_app, null);
        TextView button = view.findViewById(R.id.bt_confirm_aboutAppDialog);

        Dialog dialog = new AppCompatDialog(this);
        dialog.setContentView(view);
        dialog.show();

        button.setOnClickListener(this);
        button.setTag(dialog);
    }

    private void showUpdateLogsDialog() {
        View view = View.inflate(this, R.layout.dialog_update_logs, null);
        final ScrollView scrollView = view.findViewById(R.id.scrollview);
        final TextView tv = view.findViewById(R.id.text_updateLogs);

        StringBuilder text = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("updateLogs.txt"), "utf-8"))) {
            final char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                if (text == null) {
                    text = new StringBuilder(len);
                }
                text.append(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (text == null) return;
        tv.setText(text);

        tv.post(new Runnable() {
            @Override
            public void run() {
                TextViewUtils.setHangingIndents(tv, 4);

                final String newText = tv.getText().toString();
                final SpannableString ss = new SpannableString(newText);

                final String start = getString(R.string.appName_chinese) + "v";
                final String end = getString(R.string.updateAppendedColon);
                for (int i = 0, count = BuildConfig.VERSION_CODE - 1, fromIndex = 0; i < count; i++) {
                    final int startIndex = newText.indexOf(start, fromIndex);
                    final int endIndex = newText.indexOf(end, startIndex) + end.length();
                    ss.setSpan(new TextAppearanceSpan(MainActivity.this,
                                    R.style.TextAppearance_UpdateLogTitle),
                            startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    fromIndex = endIndex;
                }

                tv.setText(ss);

                tv.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.smoothScrollTo(0, tv.getHeight() - scrollView.getHeight());
                    }
                });
            }
        });

        Dialog dialog = new AppCompatDialog(this, R.style.DialogStyle_MinWidth_NoTitle);
        dialog.setContentView(view);
        dialog.show();

        TextView button = view.findViewById(R.id.bt_confirm_updateLogsDialog);
        button.setOnClickListener(this);
        button.setTag(dialog);
    }

    private void checkUpdate() {
        if (mUpdateChecker == null) {
            if (NetworkUtil.isNetworkConnected(this)) {
                mUpdateChecker = new UpdateChecker();
                mUpdateChecker.checkUpdate();

            } else if (!mIsAutoCheckUpdate) {
                Toast.makeText(this, R.string.noNetworkConnection, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class UpdateChecker implements View.OnClickListener {

        static final int TIMEOUT_CONNECTION = 10 * 1000; // ms
        static final int TIMEOUT_READ = 30 * 1000; // ms

        static final String LINK_APP_INFOS =
                "https://raw.githubusercontent.com/freeze-frames/Videos/release/app.json";

        String mAppName;
        String mVersionName;
        String mAppLink;
        StringBuilder mUpdateLog;

        Dialog mUpdateDialog;

        UpdateAppTask mUpdateAppTask;

        @SuppressLint("StaticFieldLeak")
        public void checkUpdate() {
            new AsyncTask<Void, Void, Integer>() {
                static final int RESULT_FIND_NEW_VERSION = 1;
                static final int RESULT_NO_NEW_VERSION = 2;
                static final int RESULT_CONNECTION_TIMEOUT = 3;
                static final int RESULT_READ_TIMEOUT = 4;

                @Override
                protected Integer doInBackground(Void... voids) {
                    final StringBuilder json;

                    HttpURLConnection conn = null;
                    BufferedReader reader = null;
                    try {
                        URL url = new URL(LINK_APP_INFOS);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(TIMEOUT_CONNECTION);
                        conn.setReadTimeout(TIMEOUT_READ);

                        json = new StringBuilder();
                        reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "utf-8"));
                        final char[] buffer = new char[1024];
                        int len;
                        while ((len = reader.read(buffer)) != -1) {
                            json.append(buffer, 0, len);
                        }

                        // 连接服务器超时
                    } catch (ConnectTimeoutException e) {
                        return RESULT_CONNECTION_TIMEOUT;

                        // 读取数据超时
                    } catch (SocketTimeoutException e) {
                        return RESULT_READ_TIMEOUT;

                    } catch (IOException e) {
                        e.printStackTrace();
                        return 0;

                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                //
                            }
                        }
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }

                    Gson gson = new Gson();
                    Map<String, Object> infos = gson.fromJson(json.toString(),  //@formatter:off
                            new TypeToken<Map<String, Object>>() {}.getType()); //@formatter:on
                    // noinspection unchecked
                    Map<String, Object> appInfos = (Map<String, Object>) infos.get("appInfos");

                    //noinspection ConstantConditions
                    final boolean findNewVersion =
                            (Double) appInfos.get("versionCode") > BuildConfig.VERSION_CODE;
                    // 检测到版本更新
                    if (findNewVersion) {
                        mAppName = /*(String) appInfos.get("appName")*/ getString(R.string.appName);
                        mVersionName = (String) appInfos.get("versionName");
                        mAppLink = (String) appInfos.get("appLink");
                        mUpdateLog = new StringBuilder();
                        //noinspection ConstantConditions
                        for (Object log : (List) appInfos.get("updateLogs")) {
                            mUpdateLog.append(log).append("\n");
                        }
                        mUpdateLog.deleteCharAt(mUpdateLog.length() - 1);
                    }
                    return findNewVersion ? RESULT_FIND_NEW_VERSION : RESULT_NO_NEW_VERSION;
                }

                @Override
                protected void onPostExecute(Integer result) {
                    switch (result) {
                        case RESULT_FIND_NEW_VERSION:
                            mCheckUpdateResultText = mFindNewVersion;
                            if (mDrawerList != null) {
                                mDrawerListAdapter.notifyItemChanged(0);
                            }
                            showUpdateDialog(MainActivity.this);
                            break;
                        case RESULT_NO_NEW_VERSION:
                            mCheckUpdateResultText = mIsTheLatestVersion;
                            if (mDrawerList != null) {
                                mDrawerListAdapter.notifyItemChanged(0);
                            }
                            if (!mIsAutoCheckUpdate) {
                                Toast.makeText(MainActivity.this, mCheckUpdateResultText,
                                        Toast.LENGTH_SHORT).show();
                            }
                            reset();
                            break;
                        case RESULT_CONNECTION_TIMEOUT:
                            if (!mIsAutoCheckUpdate) {
                                Toast.makeText(MainActivity.this, R.string.connectionTimeout,
                                        Toast.LENGTH_SHORT).show();
                            }
                            reset();
                            break;
                        case RESULT_READ_TIMEOUT:
                            if (!mIsAutoCheckUpdate) {
                                Toast.makeText(MainActivity.this, R.string.readTimeout,
                                        Toast.LENGTH_SHORT).show();
                            }
                        default:
                            reset();
                            break;
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        /**
         * 弹出对话框，提醒用户更新
         */
        private void showUpdateDialog(Context context) {
            View view = View.inflate(context, R.layout.dialog_update_app, null);
            view.<TextView>findViewById(R.id.text_updateLogTitle)
                    .setText(context.getString(R.string.updateLog, mAppName, mVersionName));
            final TextView tv = view.findViewById(R.id.text_updateLog);
            tv.setText(mUpdateLog);
            tv.post(new Runnable() {
                @Override
                public void run() {
                    TextViewUtils.setHangingIndents(tv, 4);
                }
            });
            view.findViewById(R.id.bt_cancel).setOnClickListener(this);
            view.findViewById(R.id.bt_confirm).setOnClickListener(this);

            mUpdateDialog = new AppCompatDialog(
                    context, R.style.DialogStyle_MinWidth_NoTitle);
            mUpdateDialog.setContentView(view);
            mUpdateDialog.setCancelable(false);
            mUpdateDialog.setCanceledOnTouchOutside(false);
            mUpdateDialog.show();
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                // 当点确定按钮时从服务器上下载新的apk，然后安装
                case R.id.bt_confirm:
                    mUpdateDialog.cancel();
                    mUpdateDialog = null;
                    if (FileUtils2.isExternalStorageMounted()) {
                        mUpdateAppTask = new UpdateAppTask();
                        mUpdateAppTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } else {
                        reset();
                        Toast.makeText(MainActivity.this,
                                R.string.pleaseInsertSdCardOnYourDeviceFirst,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                // 当点取消按钮时不做任何举动
                case R.id.bt_cancel:
                    mUpdateDialog.cancel();
                    reset();
                    break;

                case R.id.bt_cancel_uapd:
                    mUpdateAppTask.cancel();
                    break;
            }
        }

        private void reset() {
            mUpdateChecker = null;
            mIsAutoCheckUpdate = false;
        }

        @SuppressLint("StaticFieldLeak")
        class UpdateAppTask extends AsyncTask<Void, Void, Void> {
            Dialog mDialog;
            ProgressBar mProgressBar;
            TextView mPercentText;
            TextView mProgressNumberText;

            List<DownloadAppTask> mDownloadAppTasks;
            File mApk;
            int mApkLength = -1;

            final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
            // We want at least 2 threads and at most 4 threads to download the new apk,
            // preferring to have 1 less than the CPU count to avoid saturating the CPU
            // with background work
            final int COUNT_DOWNLOAD_APP_TASK = Math.max(2, Math.min(CPU_COUNT - 1, 4));

            @Override
            protected void onPreExecute() {
                View view = View.inflate(MainActivity.this, R.layout.progress_dialog_update_app, null);
                view.<TextView>findViewById(R.id.text_title).setText(R.string.downloadingUpdates);
                view.findViewById(R.id.bt_cancel_uapd).setOnClickListener(UpdateChecker.this);
                mProgressBar = view.findViewById(R.id.progress);
                mPercentText = view.findViewById(R.id.text_progressPercent);
                mProgressNumberText = view.findViewById(R.id.text_progressNumber);

                mDialog = new AppCompatDialog(
                        MainActivity.this, R.style.DialogStyle_MinWidth_NoTitle);
                mDialog.setContentView(view);
                mDialog.setCancelable(false);
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.show();
            }

            @Override
            protected final Void doInBackground(Void... voids) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(mAppLink);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_CONNECTION);
                    conn.setReadTimeout(TIMEOUT_READ);

                    final int length = conn.getContentLength();
                    if (FileUtils2.hasEnoughStorageOnDisk(length)) {
                        mApkLength = length;
                        // 设置最大进度
                        mProgressBar.setMax(mApkLength);
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                cancel();
                                Toast.makeText(MainActivity.this,
                                        R.string.notHaveEnoughStorage, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (ConnectTimeoutException e) {
                    onConnectionTimeout();
                } catch (SocketTimeoutException e) {
                    onReadTimeout();
                } catch (IOException e) {
                    e.printStackTrace();
                    onDownloadError();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (mApkLength != -1) {
                    final String dirPath = App.getAppDirectory();
                    File dir = new File(dirPath);
                    if (!dir.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();
                    }
                    mApk = new File(dir, mAppName + " "
                            + mVersionName.replace(".", "_") + ".apk");

                    final int blockSize = mApkLength / COUNT_DOWNLOAD_APP_TASK;
                    mDownloadAppTasks = new ArrayList<>(COUNT_DOWNLOAD_APP_TASK);
                    for (int i = 0; i < COUNT_DOWNLOAD_APP_TASK; i++) {
                        final int start = i * blockSize;
                        final int end = i == COUNT_DOWNLOAD_APP_TASK - 1 ?
                                mApkLength : (i + 1) * blockSize - 1;
                        mDownloadAppTasks.add(new DownloadAppTask());
                        mDownloadAppTasks.get(i).executeOnExecutor(
                                AsyncTask.THREAD_POOL_EXECUTOR, start, end);
                    }
                } else {
                    mDialog.cancel();
                    reset();
                }
            }

            void cancel() {
                cancel(false);
                if (mDownloadAppTasks != null) {
                    for (DownloadAppTask task : mDownloadAppTasks) {
                        task.cancel(false);
                    }
                }
                if (mApk != null) {
                    //noinspection ResultOfMethodCallIgnored
                    mApk.delete();
                }
                mDialog.cancel();
                reset();
            }

            private void onConnectionTimeout() {
                if (!isCancelled()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cancel();
                            Toast.makeText(MainActivity.this,
                                    R.string.connectionTimeout, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            private void onReadTimeout() {
                if (!isCancelled()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cancel();
                            Toast.makeText(MainActivity.this,
                                    R.string.readTimeout, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            private void onDownloadError() {
                if (!isCancelled()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cancel();
                            Toast.makeText(MainActivity.this,
                                    R.string.downloadError, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            class DownloadAppTask extends AsyncTask<Integer, Integer, Void> {
                @Override
                protected Void doInBackground(Integer... indices) {
                    final int startIndex = indices[0];
                    final int endIndex = indices[1];

                    HttpURLConnection conn = null;
                    InputStream in = null;
                    RandomAccessFile out = null;
                    try {
                        URL url = new URL(mAppLink);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(TIMEOUT_CONNECTION);
                        conn.setReadTimeout(TIMEOUT_READ);
                        // 重要：请求服务器下载部分文件 指定文件的位置
                        conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                        // 从服务器请求全部资源返回 200 ok；从服务器请求部分资源返回 206 ok
                        // final int responseCode = conn.getResponseCode();

                        in = new BufferedInputStream(conn.getInputStream());

                        out = new RandomAccessFile(mApk, "rwd");
                        out.seek(startIndex);

                        int len;
                        final byte[] buffer = new byte[8 * 1024];
                        while (!isCancelled() && (len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                            publishProgress(len);
                        }
                    } catch (ConnectTimeoutException e) {
                        onConnectionTimeout();
                    } catch (SocketTimeoutException e) {
                        onReadTimeout();
                    } catch (IOException e) {
                        e.printStackTrace();
                        onDownloadError();
                    } finally {
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException e) {
                                //
                            }
                        }
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                //
                            }
                        }
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }

                    return null;
                }

                @Override
                protected void onProgressUpdate(Integer... values) {
                    final int progress = mProgressBar.getProgress() + values[0];
                    mProgressBar.setProgress(progress);
                    mPercentText.setText(getString(R.string.progress,
                            (float) progress / (float) mApkLength * 100f));
                    mProgressNumberText.setText(
                            getString(R.string.progressNumberOfUpdatingApp,
                                    FileUtils2.formatFileSize(progress),
                                    FileUtils2.formatFileSize(mApkLength)));
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    mDownloadAppTasks.remove(this);
                    if (mDownloadAppTasks.isEmpty()) {
                        mDialog.dismiss();
                        reset();
                        installApk(mDialog.getContext(), mApk);
                    }
                }
            }

            void installApk(Context context, File apk) {
                if (apk == null || !apk.exists() || apk.length() != mApkLength) {
                    Toast.makeText(context, R.string.theInstallationPackageHasBeenDestroyed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent it = new Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Android 7.0 共享文件需要通过 FileProvider 添加临时权限，否则系统会抛出 FileUriExposedException.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Uri contentUri = FileProvider.getUriForFile(
                            context, App.getInstance().getAuthority(), apk);
                    it.setDataAndType(contentUri, "application/vnd.android.package-archive");
                } else {
                    it.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
                }
                startActivity(it);
            }
        }
    }

    @NonNull
    @Override
    public ViewGroup getActionBar(@NonNull Fragment fragment) {
        return fragment == mVideoListFragment ? mActionBar : mTmpActionBar;
    }

    @Override
    public boolean isRefreshLayoutEnabled() {
        return mSwipeRefreshLayout.isEnabled();
    }

    @Override
    public void setRefreshLayoutEnabled(boolean enabled) {
        mSwipeRefreshLayout.setEnabled(enabled);
    }

    @Override
    public boolean isRefreshLayoutRefreshing() {
        return mSwipeRefreshLayout.isRefreshing();
    }

    @Override
    public void setRefreshLayoutRefreshing(boolean refreshing) {
        mSwipeRefreshLayout.setRefreshing(refreshing);
    }

    @Override
    public void setOnRefreshLayoutChildScrollUpCallback(@Nullable SwipeRefreshLayout.OnChildScrollUpCallback callback) {
        mSwipeRefreshLayout.setOnChildScrollUpCallback(callback);
    }

    @Override
    public void setLightStatus(boolean light) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SystemBarUtils.setLightStatus(window, light);
            // MIUI6...
        } else if (OSHelper.getMiuiVersion() >= 6) {
            SystemBarUtils.setLightStatusForMIUI(window, light);
            // FlyMe4...
        } else if (OSHelper.isFlyme4OrLater()) {
            SystemBarUtils.setLightStatusForFlyme(window, light);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SystemBarUtils.setTranslucentStatus(window, light);
        }
    }

    @Override
    public void setSideDrawerEnabled(boolean enabled) {
        mSlidingDrawerLayout.setDrawerEnabledInTouch(Gravity.START, enabled);
    }

    @NonNull
    @Override
    public Fragment goToFoldedVideosFragment(@NonNull Bundle args) {
        mFoldedVideosFragment = new FoldedVideosFragment();
        mFoldedVideosFragment.setArguments(args);

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.anim_open_enter, R.anim.anim_open_exit,
                        R.anim.anim_close_enter, R.anim.anim_close_exit)
                .hide(mVideoListFragment)
                .add(R.id.container_fragment, mFoldedVideosFragment, TAG_FOLDED_VIDEOS_FRAGMENT)
                .addToBackStack(TAG_FOLDED_VIDEOS_FRAGMENT)
                .commit();

        return mFoldedVideosFragment;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment == mVideoListFragment) {
            mSwipeRefreshLayout.setOnRefreshListener(mVideoListFragment);

        } else if (fragment == mSearchedVideosFragment) {
            mSearchedVideosFragment.setVideoOpCallback(mVideoListFragment);
            mVideoListFragment.addOnReloadVideosListener(mSearchedVideosFragment);
            mSwipeRefreshLayout.setOnRefreshListener(mSearchedVideosFragment);

            mSlidingDrawerLayout.setDrawerEnabledInTouch(Gravity.START, false);

            mTmpActionBar = (ViewGroup) LayoutInflater.from(this).inflate(
                    R.layout.actionbar_searched_videos_fragment, mActionBarContainer, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                UiUtils.setViewMargins(mTmpActionBar,
                        0, App.getInstance().getStatusHeight(), 0, 0);
            }
            mActionBarContainer.addView(mTmpActionBar, 1);
        } else if (fragment == mFoldedVideosFragment) {
            mFoldedVideosFragment.setVideoOpCallback(mVideoListFragment);
            mVideoListFragment.addOnReloadVideosListener(mFoldedVideosFragment);
            mSwipeRefreshLayout.setOnRefreshListener(mFoldedVideosFragment);

            mSlidingDrawerLayout.setDrawerEnabledInTouch(Gravity.START, false);

            mActionBar.setVisibility(View.INVISIBLE);
            mTmpActionBar = (ViewGroup) LayoutInflater.from(this).inflate(
                    R.layout.actionbar_folded_videos_fragment, mActionBarContainer, false);
            mActionBarContainer.addView(mTmpActionBar, 1);
            insertTopPaddingToActionBarIfNeeded(mTmpActionBar);
        }
    }

    @Override
    public void onFragmentAttached(@NonNull Fragment fragment) {
    }

    @Override
    public void onFragmentViewCreated(@NonNull Fragment fragment) {
        if (fragment == mFoldedVideosFragment) {
            mFoldedVideosFragment.getSwipeBackLayout().addSwipeListener(this);
        }
    }

    @Override
    public void onFragmentViewDestroyed(@NonNull Fragment fragment) {
    }

    @Override
    public void onFragmentDetached(@NonNull Fragment fragment) {
        if (fragment == mSearchedVideosFragment) {
            mVideoListFragment.removeOnReloadVideosListener(mSearchedVideosFragment);
            mSearchedVideosFragment = null;
            mSwipeRefreshLayout.setOnRefreshListener(mVideoListFragment);

            mSlidingDrawerLayout.setDrawerEnabledInTouch(Gravity.START, true);

            mActionBarContainer.removeView(mTmpActionBar);
            mTmpActionBar = null;

        } else if (fragment == mFoldedVideosFragment) {
            mVideoListFragment.removeOnReloadVideosListener(mFoldedVideosFragment);
            mFoldedVideosFragment = null;
            mSwipeRefreshLayout.setOnRefreshListener(mVideoListFragment);

            mSlidingDrawerLayout.setDrawerEnabledInTouch(Gravity.START, true);

            mActionBarContainer.removeView(mTmpActionBar);
            mTmpActionBar = null;
        }
    }

    @Override
    public void onScrollStateChange(int edge, int state) {
        switch (state) {
            case SwipeBackLayout.STATE_DRAGGING:
            case SwipeBackLayout.STATE_SETTLING:
                mActionBar.setVisibility(View.VISIBLE);
                break;
            case SwipeBackLayout.STATE_IDLE:
                if (mSwipeBackScrollPercent == 0) {
                    mActionBar.setVisibility(View.INVISIBLE);
                }
                break;
        }
    }

    @Override
    public void onScrollPercentChange(int edge, float percent) {
        mSwipeBackScrollPercent = percent;
        mActionBar.setAlpha(percent);
        mTmpActionBar.setAlpha(1 - percent);
    }
}
