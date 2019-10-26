/*
 * Created on 10/19/19 7:08 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import com.liuzhenlin.swipeback.SwipeBackLayout
import com.liuzhenlin.videos.KEY_VIDEOS
import com.liuzhenlin.videos.R
import com.liuzhenlin.videos.REQUEST_CODE_LOCAL_FOLDED_VIDEOS_FRAGMENT
import com.liuzhenlin.videos.REQUEST_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT
import com.liuzhenlin.videos.view.swiperefresh.SwipeRefreshLayout

/**
 * @author 刘振林
 */
class LocalVideosFragment : Fragment(), FragmentPartLifecycleCallback, RefreshLayoutCallback {

    private lateinit var mInteractionCallback: InteractionCallback

    private lateinit var mLocalVideoListFragment: LocalVideoListFragment
    private var mLocalFoldedVideosFragment: LocalFoldedVideosFragment? = null
    private var mLocalSearchedVideosFragment: LocalSearchedVideosFragment? = null

    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout

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
        mSwipeRefreshLayout = inflater.inflate(R.layout.fragment_local_videos, container, false) as SwipeRefreshLayout
        mSwipeRefreshLayout.setColorSchemeResources(R.color.pink, R.color.lightBlue, R.color.purple)
        mSwipeRefreshLayout.setOnRequestDisallowInterceptTouchEventCallback { true }
        return mSwipeRefreshLayout
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
        when {
            // this::mLocalVideoListFragment.isInitialized: 在Activity被系统销毁后自动创建时，
            // mLocalVideoListFragment还未在此类中被初始化，但子Fragment已经attach到此Fragment了
            this::mLocalVideoListFragment.isInitialized
                    && childFragment === mLocalVideoListFragment -> {
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)
            }
            childFragment === mLocalFoldedVideosFragment -> {
                childFragment.setVideoOpCallback(mLocalVideoListFragment)
                mLocalVideoListFragment.addOnReloadVideosListener(childFragment)
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)

                mInteractionCallback.onLocalFoldedVideosFragmentAttached()
            }
            childFragment === mLocalSearchedVideosFragment -> {
                childFragment.setVideoOpCallback(mLocalVideoListFragment)
                mLocalVideoListFragment.addOnReloadVideosListener(childFragment)
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)

                mInteractionCallback.onLocalSearchedVideosFragmentAttached()
            }
        }
    }

    override fun onFragmentViewCreated(childFragment: Fragment) {
        if (childFragment === mLocalFoldedVideosFragment) {
            childFragment.swipeBackLayout.addSwipeListener(mInteractionCallback)
        }
    }

    override fun onFragmentViewDestroyed(childFragment: Fragment) {}

    override fun onFragmentDetached(childFragment: Fragment) {
        when {
            childFragment === mLocalFoldedVideosFragment -> {
                mLocalVideoListFragment.removeOnReloadVideosListener(mLocalFoldedVideosFragment)
                mLocalFoldedVideosFragment = null
                mSwipeRefreshLayout.setOnRefreshListener(mLocalVideoListFragment)

                mInteractionCallback.onLocalFoldedVideosFragmentDetached()
            }
            childFragment === mLocalSearchedVideosFragment -> {
                mLocalVideoListFragment.removeOnReloadVideosListener(mLocalSearchedVideosFragment)
                mLocalSearchedVideosFragment = null
                mSwipeRefreshLayout.setOnRefreshListener(mLocalVideoListFragment)

                mInteractionCallback.onLocalSearchedVideosFragmentDetached()
            }
        }
    }

    fun goToLocalFoldedVideosFragment(args: Bundle) {
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

    fun goToLocalSearchedVideosFragment() {
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

    fun onBackPressed(): Boolean {
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

    companion object {
        private const val TAG_LOCAL_VIDEO_LIST_FRAGMENT = "LocalVideoListFragment"
        private const val TAG_LOCAL_FOLDED_VIDEOS_FRAGMENT = "LocalFoldedVideosFragment"
        private const val TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT = "LocalSearchedVideosFragment"
    }

    interface InteractionCallback : SwipeBackLayout.SwipeListener,
            LocalVideoListFragment.InteractionCallback,
            LocalFoldedVideosFragment.InteractionCallback,
            LocalSearchedVideosFragment.InteractionCallback {
        fun onLocalFoldedVideosFragmentAttached()
        fun onLocalFoldedVideosFragmentDetached()

        fun onLocalSearchedVideosFragmentAttached()
        fun onLocalSearchedVideosFragmentDetached()
    }
}
