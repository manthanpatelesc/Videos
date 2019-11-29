/*
 * Created on 10/19/19 7:08 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.view.MarginLayoutParamsCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import com.liuzhenlin.floatingmenu.DensityUtils
import com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout
import com.liuzhenlin.swipeback.SwipeBackLayout
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.utils.UiUtils
import com.liuzhenlin.videos.view.swiperefresh.SwipeRefreshLayout

/**
 * @author 刘振林
 */
class LocalVideosFragment : Fragment(), ILocalVideosFragment, FragmentPartLifecycleCallback,
        LocalFoldedVideosFragment.InteractionCallback, LocalSearchedVideosFragment.InteractionCallback,
        View.OnClickListener, SwipeBackLayout.SwipeListener, SlidingDrawerLayout.OnDrawerScrollListener {

    private lateinit var mInteractionCallback: InteractionCallback

    private lateinit var mLocalVideoListFragment: LocalVideoListFragment
    private var mLocalFoldedVideosFragment: LocalFoldedVideosFragment? = null
    private var mLocalSearchedVideosFragment: LocalSearchedVideosFragment? = null

    private lateinit var mActionBarContainer: ViewGroup

    // LocalVideoListFragment的ActionBar
    private lateinit var mActionBar: ViewGroup
    private lateinit var mHomeAsUpIndicator: ImageButton
    private lateinit var mTitleText: TextView
    private lateinit var mSearchButton: ImageButton
    private lateinit var mDrawerArrowDrawable: DrawerArrowDrawable

    // 临时缓存LocalSearchedVideosFragment或LocalFoldedVideosFragment的ActionBar
    private var mTmpActionBar: ViewGroup? = null

    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout

    private var mSwipeBackScrollPercent = 0.0f

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val parent = parentFragment
        mInteractionCallback = when {
            parent is InteractionCallback -> parent
            context is InteractionCallback -> context
            parent != null -> throw RuntimeException("Neither $context nor $parent " +
                    "has implemented LocalVideosFragment.InteractionCallback")
            else -> throw RuntimeException(
                    "$context must implement LocalVideosFragment.InteractionCallback")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contentView = inflater.inflate(R.layout.fragment_local_videos, container, false)
        initViews(contentView)
        return contentView
    }

    private fun initViews(contentView: View) {
        mActionBarContainer = contentView.findViewById(R.id.container_actionbar)
        mActionBar = contentView.findViewById(R.id.actionbar)
        insertTopPaddingToActionBarIfNeeded(mActionBar)

        mDrawerArrowDrawable = DrawerArrowDrawable(contentView.context)
        mDrawerArrowDrawable.gapSize = 12.5f
        mDrawerArrowDrawable.color = Color.WHITE

        mHomeAsUpIndicator = mActionBar.findViewById(R.id.btn_homeAsUpIndicator)
        mHomeAsUpIndicator.setImageDrawable(mDrawerArrowDrawable)
        mHomeAsUpIndicator.setOnClickListener(this)

        mTitleText = mActionBar.findViewById(R.id.text_title)
        mTitleText.post {
            val app = App.getInstance(contentView.context)
            val hauilp = mHomeAsUpIndicator.layoutParams as ViewGroup.MarginLayoutParams
            val ttlp = mTitleText.layoutParams as ViewGroup.MarginLayoutParams
            MarginLayoutParamsCompat.setMarginStart(ttlp,
                    ((DensityUtils.dp2px(app, 10f) /* margin */ + app.videoThumbWidth
                            - hauilp.leftMargin - hauilp.rightMargin
                            - mHomeAsUpIndicator.width - mTitleText.width) + 0.5f).toInt())
            mTitleText.layoutParams = ttlp
        }

        mSearchButton = mActionBar.findViewById(R.id.btn_search)
        mSearchButton.setOnClickListener(this)

        mSwipeRefreshLayout = contentView.findViewById(R.id.swipeRefreshLayout)
        mSwipeRefreshLayout.setColorSchemeResources(R.color.pink, R.color.lightBlue, R.color.purple)
        mSwipeRefreshLayout.setOnRequestDisallowInterceptTouchEventCallback { true }
    }

    private fun insertTopPaddingToActionBarIfNeeded(actionbar: View) {
        if (mInteractionCallback.isLayoutUnderStatusBar()) {
            val statusHeight = App.getInstance(actionbar.context).statusHeightInPortrait
            when (actionbar.layoutParams.height) {
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT -> {
                }
                else -> actionbar.layoutParams.height += statusHeight
            }
            actionbar.setPadding(
                    actionbar.paddingLeft,
                    actionbar.paddingTop + statusHeight,
                    actionbar.paddingRight,
                    actionbar.paddingBottom)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            mLocalVideoListFragment = LocalVideoListFragment()
            childFragmentManager.beginTransaction()
                    .add(R.id.container_child_fragments, mLocalVideoListFragment, TAG_LOCAL_VIDEO_LIST_FRAGMENT)
                    .commit()
        } else {
            val fm = childFragmentManager

            mLocalVideoListFragment = fm
                    .findFragmentByTag(TAG_LOCAL_VIDEO_LIST_FRAGMENT) as LocalVideoListFragment
            onFragmentAttached(mLocalVideoListFragment)

            mLocalFoldedVideosFragment = fm
                    .findFragmentByTag(TAG_LOCAL_FOLDED_VIDEOS_FRAGMENT) as LocalFoldedVideosFragment?
            if (mLocalFoldedVideosFragment != null) {
                onFragmentAttached(mLocalFoldedVideosFragment!!)
            }

            mLocalSearchedVideosFragment = fm
                    .findFragmentByTag(TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT) as LocalSearchedVideosFragment?
            if (mLocalSearchedVideosFragment != null) {
                onFragmentAttached(mLocalSearchedVideosFragment!!)
            }
        }
    }

    override fun onFragmentAttached(childFragment: Fragment) {
        // this::mLocalVideoListFragment.isInitialized用于判断我们是否已经初始化了子Fragment属性
        // 在Activity被系统销毁后自动创建时，子Fragment属性还未在此类的onViewCreated()方法中被初始化，
        // 但子Fragments已经attach到此Fragment了
        if (!this::mLocalVideoListFragment.isInitialized) return

        when {
            childFragment === mLocalVideoListFragment -> {
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)
            }
            childFragment === mLocalFoldedVideosFragment -> {
                childFragment.setVideoOpCallback(mLocalVideoListFragment)
                mLocalVideoListFragment.addOnReloadVideosListener(childFragment)
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)

                mInteractionCallback.setSideDrawerEnabled(false)

                mActionBar.visibility = View.GONE
                mTmpActionBar = LayoutInflater.from(mActionBar.context).inflate(
                        R.layout.actionbar_local_folded_videos_fragment, mActionBarContainer, false) as ViewGroup
                mActionBarContainer.addView(mTmpActionBar, 1)
                insertTopPaddingToActionBarIfNeeded(mTmpActionBar!!)

                mInteractionCallback.showTabItems(false)
                mInteractionCallback.setTabItemsClickable(false)
            }
            childFragment === mLocalSearchedVideosFragment -> {
                childFragment.setVideoOpCallback(mLocalVideoListFragment)
                mLocalVideoListFragment.addOnReloadVideosListener(childFragment)
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)

                mInteractionCallback.setSideDrawerEnabled(false)

                mTmpActionBar = LayoutInflater.from(mActionBar.context).inflate(
                        R.layout.actionbar_local_searched_videos_fragment, mActionBarContainer, false) as ViewGroup
                if (mInteractionCallback.isLayoutUnderStatusBar()) {
                    UiUtils.setViewMargins(mTmpActionBar!!,
                            0, App.getInstance(mActionBar.context).statusHeightInPortrait, 0, 0)
                }
                mActionBarContainer.addView(mTmpActionBar, 1)

                mInteractionCallback.showTabItems(false)
            }
        }
    }

    override fun onFragmentViewCreated(childFragment: Fragment) {
        if (childFragment === mLocalFoldedVideosFragment) {
            childFragment.swipeBackLayout.addSwipeListener(this)
        }
    }

    override fun onFragmentViewDestroyed(childFragment: Fragment) {}

    override fun onFragmentDetached(childFragment: Fragment) {
        when {
            childFragment === mLocalFoldedVideosFragment -> {
                mLocalVideoListFragment.removeOnReloadVideosListener(mLocalFoldedVideosFragment)
                mLocalFoldedVideosFragment = null
                mSwipeRefreshLayout.setOnRefreshListener(mLocalVideoListFragment)

                mInteractionCallback.setSideDrawerEnabled(true)

//                mActionBar.setVisibility(View.VISIBLE)
                mActionBarContainer.removeView(mTmpActionBar)
                mTmpActionBar = null

                mInteractionCallback.setTabItemsClickable(true)
//                mInteractionCallback.showTabItems(true)
            }
            childFragment === mLocalSearchedVideosFragment -> {
                mLocalVideoListFragment.removeOnReloadVideosListener(mLocalSearchedVideosFragment)
                mLocalSearchedVideosFragment = null
                mSwipeRefreshLayout.setOnRefreshListener(mLocalVideoListFragment)

                mInteractionCallback.setSideDrawerEnabled(true)

                mActionBarContainer.removeView(mTmpActionBar)
                mTmpActionBar = null

                mInteractionCallback.showTabItems(true)
            }
        }
    }

    override fun goToLocalFoldedVideosFragment(args: Bundle) {
        mLocalFoldedVideosFragment = LocalFoldedVideosFragment()
        mLocalFoldedVideosFragment!!.arguments = args
        mLocalFoldedVideosFragment!!.setTargetFragment(mLocalVideoListFragment,
                REQUEST_CODE_LOCAL_FOLDED_VIDEOS_FRAGMENT)

        childFragmentManager.beginTransaction()
                .setCustomAnimations(
                        R.anim.anim_open_enter, R.anim.anim_open_exit,
                        R.anim.anim_close_enter, R.anim.anim_close_exit)
                .hide(mLocalVideoListFragment)
                .add(R.id.container_child_fragments, mLocalFoldedVideosFragment!!, TAG_LOCAL_FOLDED_VIDEOS_FRAGMENT)
                .addToBackStack(TAG_LOCAL_FOLDED_VIDEOS_FRAGMENT)
                .commit()
    }

    private fun goToLocalSearchedVideosFragment() {
        val args = Bundle()
        args.putParcelableArrayList(KEY_VIDEOS, mLocalVideoListFragment.allVideos)

        mLocalSearchedVideosFragment = LocalSearchedVideosFragment()
        mLocalSearchedVideosFragment!!.arguments = args
        mLocalSearchedVideosFragment!!.setTargetFragment(mLocalVideoListFragment,
                REQUEST_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT)

        childFragmentManager.beginTransaction()
                .add(R.id.container_child_fragments, mLocalSearchedVideosFragment!!, TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT)
                .addToBackStack(TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT)
                .commit()
    }

    override fun onBackPressed(): Boolean {
        if (mLocalFoldedVideosFragment != null) {
            if (!mLocalFoldedVideosFragment!!.onBackPressed()) {
                mLocalFoldedVideosFragment!!.swipeBackLayout.scrollToFinishActivityOrPopUpFragment()
            }
            return true
        }
        return if (mLocalSearchedVideosFragment != null) {
            childFragmentManager.popBackStackImmediate()
        } else {
            mLocalVideoListFragment.onBackPressed()
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_homeAsUpIndicator -> mInteractionCallback.onClickHomeAsUpIndicator()
            R.id.btn_search -> goToLocalSearchedVideosFragment()
        }
    }

    override fun getActionBar(fragment: Fragment): ViewGroup =
            if (fragment === mLocalVideoListFragment) mActionBar else mTmpActionBar!!

    override fun isRefreshLayoutEnabled() = mSwipeRefreshLayout.isEnabled

    override fun setRefreshLayoutEnabled(enabled: Boolean) {
        mSwipeRefreshLayout.isEnabled = enabled
    }

    override fun isRefreshLayoutRefreshing() = mSwipeRefreshLayout.isRefreshing

    override fun setRefreshLayoutRefreshing(refreshing: Boolean) {
        mSwipeRefreshLayout.isRefreshing = refreshing
    }

    override fun setOnRefreshLayoutChildScrollUpCallback(callback: SwipeRefreshLayout.OnChildScrollUpCallback?) {
        mSwipeRefreshLayout.setOnChildScrollUpCallback(callback)
    }

    override fun onScrollStateChange(edge: Int, state: Int) {
        when (state) {
            SwipeBackLayout.STATE_DRAGGING,
            SwipeBackLayout.STATE_SETTLING -> {
                mActionBar.visibility = View.VISIBLE
                mInteractionCallback.showTabItems(true)
            }
            SwipeBackLayout.STATE_IDLE ->
                if (mSwipeBackScrollPercent == 0.0f) {
                    mActionBar.visibility = View.GONE
                    mInteractionCallback.showTabItems(false)
                }
        }
    }

    override fun onScrollPercentChange(edge: Int, percent: Float) {
        mSwipeBackScrollPercent = percent
        mActionBar.alpha = percent
        mTmpActionBar!!.alpha = 1 - percent
    }

    override fun onDrawerOpened(parent: SlidingDrawerLayout, drawer: View) {
    }

    override fun onDrawerClosed(parent: SlidingDrawerLayout, drawer: View) {
    }

    override fun onScrollPercentChange(parent: SlidingDrawerLayout, drawer: View, percent: Float) {
        if (view == null) return

        mDrawerArrowDrawable.progress = percent
    }

    override fun onScrollStateChange(parent: SlidingDrawerLayout, drawer: View, state: Int) {
        if (view == null) return

        when (state) {
            SlidingDrawerLayout.SCROLL_STATE_TOUCH_SCROLL,
            SlidingDrawerLayout.SCROLL_STATE_AUTO_SCROLL -> {
                mTitleText.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                mSearchButton.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                mSwipeRefreshLayout[0] /* fragment container */
                        .setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            SlidingDrawerLayout.SCROLL_STATE_IDLE -> {
                mTitleText.setLayerType(View.LAYER_TYPE_NONE, null)
                mSearchButton.setLayerType(View.LAYER_TYPE_NONE, null)
                mSwipeRefreshLayout[0] /* fragment container */
                        .setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }

    companion object {
        private const val TAG_LOCAL_VIDEO_LIST_FRAGMENT = "LocalVideoListFragment"
        private const val TAG_LOCAL_FOLDED_VIDEOS_FRAGMENT = "LocalFoldedVideosFragment"
        private const val TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT = "LocalSearchedVideosFragment"
    }

    interface InteractionCallback : LocalVideoListFragment.InteractionCallback {
        fun isLayoutUnderStatusBar(): Boolean

        fun onClickHomeAsUpIndicator()

        fun showTabItems(show: Boolean)
        fun setTabItemsClickable(clickable: Boolean)
    }
}
