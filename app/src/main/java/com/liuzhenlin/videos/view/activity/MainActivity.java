/*
 * Created on 2017/09/26.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.liuzhenlin.floatingmenu.DensityUtils;
import com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout;
import com.liuzhenlin.texturevideoview.utils.BitmapUtils;
import com.liuzhenlin.texturevideoview.utils.FileUtils;
import com.liuzhenlin.texturevideoview.utils.SystemBarUtils;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.AppSharedPreferences;
import com.liuzhenlin.videos.utils.AppUpdateChecker;
import com.liuzhenlin.videos.utils.ColorUtils;
import com.liuzhenlin.videos.utils.FloatingWindowPermissionUtils;
import com.liuzhenlin.videos.utils.OSHelper;
import com.liuzhenlin.videos.utils.TextViewUtils;
import com.liuzhenlin.videos.utils.UiUtils;
import com.liuzhenlin.videos.view.ScrollDisableListView;
import com.liuzhenlin.videos.view.ScrollDisableViewPager;
import com.liuzhenlin.videos.view.adapter.BaseAdapter2;
import com.liuzhenlin.videos.view.fragment.LocalVideosFragment;
import com.liuzhenlin.videos.view.fragment.OnlineVideoFragment;
import com.liuzhenlin.videos.view.swiperefresh.SwipeRefreshLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author 刘振林
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        AdapterView.OnItemClickListener, SlidingDrawerLayout.OnDrawerScrollListener,
        LocalVideosFragment.InteractionCallback {

    private LocalVideosFragment mLocalVideosFragment;
    private OnlineVideoFragment mOnlineVideoFragment;

    private SlidingDrawerLayout mSlidingDrawerLayout;

    @SuppressWarnings("FieldCanBeLocal")
    private ScrollDisableViewPager mFragmentViewPager;
    private TabLayout mFragmentTabLayout;

    private ScrollDisableListView mDrawerList;
    private DrawerListAdapter mDrawerListAdapter;
    private ImageView mDrawerImage;
    private boolean mIsDrawerStatusLight = true;
    private boolean mIsDrawerListForegroundLight = false;
    private float mOldDrawerScrollPercent;

    private static final int REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE = 7;
    private static final int REQUEST_CODE_APPLY_FOR_FLOATING_WINDOW_PERMISSION = 8;

    private String mCheckUpdateResultText;
    private String mIsTheLatestVersion;
    private String mFindNewVersion;
    private AppUpdateChecker.OnResultListener mOnCheckUpdateResultListener;

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

        if (savedInstanceState == null) {
            mLocalVideosFragment = new LocalVideosFragment();
            mOnlineVideoFragment = new OnlineVideoFragment();
        } else {
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof LocalVideosFragment) {
                    mLocalVideosFragment = (LocalVideosFragment) fragment;
                } else if (fragment instanceof OnlineVideoFragment) {
                    mOnlineVideoFragment = (OnlineVideoFragment) fragment;
                }
            }
        }

        initViews();
    }

    private void initViews() {
        final App app = App.getInstance(this);
        final int screenWidth = app.getScreenWidthIgnoreOrientation();

        mSlidingDrawerLayout = findViewById(R.id.slidingDrawerLayout);
        mSlidingDrawerLayout.setStartDrawerWidthPercent(
                1f - (app.getVideoThumbWidth() + DensityUtils.dp2px(app, 20f)) / (float) screenWidth);
        mSlidingDrawerLayout.setContentSensitiveEdgeSize(screenWidth);
        mSlidingDrawerLayout.addOnDrawerScrollListener(new SlidingDrawerLayout.SimpleOnDrawerScrollListener() {
            @Override
            public void onScrollStateChange(@NonNull SlidingDrawerLayout parent,
                                            @NonNull View drawer, int state) {
                parent.removeOnDrawerScrollListener(this);

                mDrawerList = drawer.findViewById(R.id.list_drawer);
                mDrawerList.setDivider(null);
//                View divider = new ViewStub(app);
//                mDrawerList.addHeaderView(divider);
//                mDrawerList.addFooterView(divider);
                mDrawerList.setAdapter(mDrawerListAdapter = new DrawerListAdapter());
                mDrawerList.setOnItemClickListener(MainActivity.this);
                mDrawerList.setScrollEnabled(false);

                mDrawerImage = findViewById(R.id.image_drawer);
                AppSharedPreferences asp = AppSharedPreferences.getInstance(app);
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
        mSlidingDrawerLayout.addOnDrawerScrollListener(mLocalVideosFragment);
        mSlidingDrawerLayout.addOnDrawerScrollListener(mOnlineVideoFragment);

        mFragmentViewPager = findViewById(R.id.viewpager_fragments);
        mFragmentViewPager.setScrollEnabled(false);
        mFragmentViewPager.setAdapter(
                new FragmentPagerAdapter(getSupportFragmentManager(),
                        FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
                    final Fragment[] fragments = {
                            mLocalVideosFragment, mOnlineVideoFragment
                    };
                    final String[] fragmentTittles = {
                            getString(R.string.localVideos), getString(R.string.onlineVideo)
                    };

                    @NonNull
                    @Override
                    public Fragment getItem(int position) {
                        return fragments[position];
                    }

                    @Nullable
                    @Override
                    public CharSequence getPageTitle(int position) {
                        return fragmentTittles[position];
                    }

                    @Override
                    public int getCount() {
                        return fragments.length;
                    }
                });

        mFragmentTabLayout = findViewById(R.id.tablayout_fragments);
        mFragmentTabLayout.setupWithViewPager(mFragmentViewPager);
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
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // 打开应用时自动检测更新（有悬浮窗权限时才去检查，不然弹不出更新提示对话框）
        checkUpdateIfPermissionGranted(false);
    }

    @Override
    public void onBackPressed() {
        //noinspection StatementWithEmptyBody
        if (mLocalVideosFragment.onBackPressed()) {

        } else if (mSlidingDrawerLayout.hasOpenedDrawer()) {
            mSlidingDrawerLayout.closeDrawer(true);

        } else if (!mIsBackPressed) {
            mIsBackPressed = true;
            mSlidingDrawerLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mIsBackPressed = false;
                }
            }, 1500);
            UiUtils.showUserCancelableSnackbar(mSlidingDrawerLayout,
                    R.string.pressAgainToExitApp, Snackbar.LENGTH_SHORT);

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
        if (mOnCheckUpdateResultListener != null) {
            AppUpdateChecker.getInstance(this).removeOnResultListener(mOnCheckUpdateResultListener);
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
            case REQUEST_CODE_APPLY_FOR_FLOATING_WINDOW_PERMISSION:
                checkUpdateIfPermissionGranted(true);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_ok_aboutAppDialog:
            case R.id.btn_ok_updateLogsDialog:
                ((Dialog) v.getTag()).cancel();
                break;
        }
    }

    private static final int[] sDrawerListItemIDs = {
            R.string.checkForUpdates,
            R.string.aboutApp,
            R.string.updateLogs,
            R.string.userFeedback,
            R.string.drawerSettings,
    };

    private final class DrawerListAdapter extends BaseAdapter2 {

        final String[] mDrawerListItems;

        @ColorInt
        int mTextColor = Color.BLACK;
        @ColorInt
        int mSubTextColor = Color.GRAY;
        @ColorInt
        static final int SUBTEXT_HIGHLIGHT_COLOR = Color.RED;

        final Drawable mForwardDrawable;
//        final Drawable mLightDrawerListDivider = ContextCompat.getDrawable(
//                MainActivity.this, R.drawable.divider_light_drawer_list);
//        final Drawable mDarkDrawerListDivider = ContextCompat.getDrawable(
//                MainActivity.this, R.drawable.divider_dark_drawer_list);

        DrawerListAdapter() {
            mDrawerListItems = new String[sDrawerListItemIDs.length];
            for (int i = 0; i < mDrawerListItems.length; i++) {
                mDrawerListItems[i] = getString(sDrawerListItemIDs[i]);
            }

            Drawable temp = ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_forward);
            assert temp != null;
            mForwardDrawable = DrawableCompat.wrap(temp);
            DrawableCompat.setTintList(mForwardDrawable, null);

//            mDrawerList.setDivider(mDarkDrawerListDivider);
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

//                if (light) {
//                    mDrawerList.setDivider(mLightDrawerListDivider);
//                } else {
//                    mDrawerList.setDivider(mDarkDrawerListDivider);
//                }
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
            ViewHolder vh;
            if (convertView == null) {
                vh = new ViewHolder(listview.getContext());
                convertView = vh.convertView;
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            vh.text.setText(mDrawerListItems[position]);
            vh.text.setTextColor(mTextColor);
            if (position == 0 && !TextUtils.isEmpty(mCheckUpdateResultText)) {
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

        final class ViewHolder {
            final View convertView;
            final TextView text;
            final TextView subText;

            ViewHolder(Context context) {
                convertView = View.inflate(context, R.layout.item_drawer_list, null);
                text = convertView.findViewById(R.id.text_list);
                subText = convertView.findViewById(R.id.subtext_list);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> listview, View view, int position, long id) {
        switch ((int) id) {
            case R.string.checkForUpdates:
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
            case R.string.drawerSettings:
                showDrawerSettingsMenu(view);
                break;
        }
    }

    private void checkUpdate() {
        if (!FloatingWindowPermissionUtils.hasPermission(this)) {
            FloatingWindowPermissionUtils.applyForPermission(
                    this, REQUEST_CODE_APPLY_FOR_FLOATING_WINDOW_PERMISSION);
            return;
        }

        baseCheckUpdate(true);
    }

    private void checkUpdateIfPermissionGranted(boolean toastResult) {
        if (FloatingWindowPermissionUtils.hasPermission(this)) {
            baseCheckUpdate(toastResult);
        }
    }

    private void baseCheckUpdate(boolean toastResult) {
        AppUpdateChecker auc = AppUpdateChecker.getInstance(this);
        if (mOnCheckUpdateResultListener == null) {
            mOnCheckUpdateResultListener = new AppUpdateChecker.OnResultListener() {
                @Override
                public void onResult(boolean findNewVersion) {
                    mCheckUpdateResultText = findNewVersion ? mFindNewVersion : mIsTheLatestVersion;
                    if (mDrawerListAdapter != null) {
                        mDrawerListAdapter.notifyItemChanged(0);
                    }
                }
            };
            auc.addOnResultListener(mOnCheckUpdateResultListener);
        }
        auc.checkUpdate(toastResult);
    }

    private void showAboutAppDialog() {
        View view = View.inflate(this, R.layout.dialog_about_app, null);
        TextView button = view.findViewById(R.id.btn_ok_aboutAppDialog);

        Dialog dialog = new AppCompatDialog(this, R.style.DialogStyle_NoTitle);
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

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    getAssets().open("updateLogs.txt"), "utf-8"));
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
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //
                }
            }
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

        View button = view.findViewById(R.id.btn_ok_updateLogsDialog);
        button.setOnClickListener(this);
        button.setTag(dialog);
    }

    private void showDrawerSettingsMenu(View anchor) {
        PopupMenu ppm = new PopupMenu(this, anchor);

        Menu menu = ppm.getMenu();
        menu.add(Menu.NONE, R.id.setBackground, Menu.NONE, R.string.setBackground);
        SubMenu subMenu = menu.addSubMenu(Menu.NONE, R.id.setForeground, Menu.NONE, R.string.setForeground);

        subMenu.add(R.id.setForeground, R.id.changeTextColor, Menu.NONE,
                mIsDrawerListForegroundLight ? R.string.setDarkTexts : R.string.setLightTexts);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            subMenu.add(R.id.setForeground, R.id.changeStatusTextColor, Menu.NONE,
                    mIsDrawerStatusLight ? R.string.setLightStatus : R.string.setDarkStatus);
        }

        ppm.setGravity(Gravity.END);
        ppm.show();
        ppm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.setBackground:
                        Intent it = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
                        startActivityForResult(it, REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE);
                        return true;
                    case R.id.changeTextColor:
                        mDrawerListAdapter.setLightDrawerListForeground(!mIsDrawerListForegroundLight);
                        return true;
                    case R.id.changeStatusTextColor:
                        setLightDrawerStatus(!mIsDrawerStatusLight);
                        return true;
                    default:
                        return false;
                }
            }
        });
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
        switch (state) {
            case SlidingDrawerLayout.SCROLL_STATE_TOUCH_SCROLL:
            case SlidingDrawerLayout.SCROLL_STATE_AUTO_SCROLL:
                mFragmentTabLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                break;
            case SlidingDrawerLayout.SCROLL_STATE_IDLE:
                mFragmentTabLayout.setLayerType(View.LAYER_TYPE_NONE, null);
                break;
        }
    }

    @Override
    public boolean isLayoutUnderStatusBar() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    @Override
    public void onClickHomeAsUpIndicator() {
        if (mSlidingDrawerLayout.hasOpenedDrawer()) {
            mSlidingDrawerLayout.closeDrawer(true);
        } else {
            mSlidingDrawerLayout.openDrawer(Gravity.START, true);
        }
    }

    @Override
    public void showTabItems(boolean show) {
        mFragmentTabLayout.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setTabItemsEnabled(boolean enabled) {
        UiUtils.setTabItemsEnabled(mFragmentTabLayout, enabled);
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

    @Override
    public void goToLocalFoldedVideosFragment(@NonNull Bundle args) {
        mLocalVideosFragment.goToLocalFoldedVideosFragment(args);
    }

    @Override
    public boolean isRefreshLayoutEnabled() {
        return mLocalVideosFragment.isRefreshLayoutEnabled();
    }

    @Override
    public void setRefreshLayoutEnabled(boolean enabled) {
        mLocalVideosFragment.setRefreshLayoutEnabled(enabled);
    }

    @Override
    public boolean isRefreshLayoutRefreshing() {
        return mLocalVideosFragment.isRefreshLayoutRefreshing();
    }

    @Override
    public void setRefreshLayoutRefreshing(boolean refreshing) {
        mLocalVideosFragment.setRefreshLayoutRefreshing(refreshing);
    }

    @Override
    public void setOnRefreshLayoutChildScrollUpCallback(@Nullable SwipeRefreshLayout.OnChildScrollUpCallback callback) {
        mLocalVideosFragment.setOnRefreshLayoutChildScrollUpCallback(callback);
    }
}
