/*
 * Created on 2018/08/15.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.liuzhenlin.floatingmenu.FloatingMenu
import com.liuzhenlin.simrv.BuildConfig
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.dao.VideoDaoHelper
import com.liuzhenlin.videos.model.Video
import com.liuzhenlin.videos.utils.AlgorithmUtils
import com.liuzhenlin.videos.utils.UiUtils
import com.liuzhenlin.videos.utils.VideoUtils2
import com.liuzhenlin.videos.view.adapter.HeaderAndFooterWrapper
import com.liuzhenlin.videos.view.fragment.PackageConsts.PAYLOAD_REFRESH_ITEM_NAME
import com.liuzhenlin.videos.view.fragment.PackageConsts.PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION
import com.liuzhenlin.videos.view.swiperefresh.SwipeRefreshLayout
import java.util.*

/**
 * @author 刘振林
 */
class SearchedVideosFragment : Fragment(), View.OnClickListener, View.OnLongClickListener,
        View.OnTouchListener, OnReloadVideosListener, SwipeRefreshLayout.OnRefreshListener {

    private lateinit var mActivity: Activity
    private lateinit var mContext: Context
    private lateinit var mInteractionCallback: InteractionCallback
    private var mLifecycleCallback: FragmentPartLifecycleCallback? = null

    private var mVideoOpCallback: VideoOpCallback? = null

    private var mSearchText = EMPTY_STRING
    private lateinit var mSearchResultText: TextView
    private lateinit var mRecyclerView: RecyclerView
    private val mAdapterWrapper = HeaderAndFooterWrapper(SearchedVideoListAdapter())
    private val mSearchedVideos = mutableListOf<Video>()
    private var mSelectedItemIndex = NO_POSITION
    private val PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS = PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION shl 1

    private var mLoadVideosTask: LoadVideosTask? = null
    private var _mVideos: ArrayList<Video>? = null
    private inline val mVideos: ArrayList<Video>
        get() {
            if (_mVideos == null) {
                _mVideos = arguments?.getParcelableArrayList(KEY_VIDEOS) ?: arrayListOf()
            }
            return _mVideos!!
        }

    private var mVideoOptionsMenu: FloatingMenu? = null
    private var mDownX = 0
    private var mDownY = 0

    fun setVideoOpCallback(callback: VideoOpCallback) {
        mVideoOpCallback = callback
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = context as Activity
        mContext = context.applicationContext
        if (context is InteractionCallback) {
            mInteractionCallback = context
        } else {
            throw RuntimeException("$context must implement SearchedVideosFragment.InteractionCallback")
        }
        if (context is FragmentPartLifecycleCallback) {
            mLifecycleCallback = context
            context.onFragmentAttached(this)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_searched_videos, container, false)
        initViews(view)
        return view
    }

    private fun initViews(contentView: View) {
        contentView.setOnTouchListener(this)

        val actionbar = mInteractionCallback.getActionBar(this)
        actionbar.findViewById<SearchView>(R.id.search_view).run {
            UiUtils.setViewMargins(findViewById(R.id.search_edit_frame), 0, 0, 0, 0)

            findViewById<View>(R.id.search_plate).setBackgroundResource(R.drawable.bg_search_view_plate)

            val searchSrcText = findViewById<SearchView.SearchAutoComplete>(R.id.search_src_text)
            searchSrcText.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.subtitle_text_size))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                searchSrcText.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }

            onActionViewExpanded()
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                override fun onQueryTextSubmit(query: String) = true

                override fun onQueryTextChange(newText: String): Boolean {
                    mSearchText = newText.trim()
                    refreshList(true)
                    return true
                }
            })
        }
        actionbar.findViewById<View>(R.id.button_cancel_search).setOnClickListener {
            UiUtils.hideSoftInput(mActivity.window)
            requireFragmentManager().popBackStackImmediate()
        }

        mRecyclerView = contentView.findViewById(R.id.recycler_searchedVideoList)
        mRecyclerView.layoutManager = LinearLayoutManager(mActivity)
        mRecyclerView.adapter = mAdapterWrapper.also {
            mSearchResultText = LayoutInflater.from(mActivity)
                    .inflate(R.layout.text_search_result, mRecyclerView, false) as TextView
            it.addHeaderView(mSearchResultText)
        }
        mRecyclerView.addItemDecoration(DividerItemDecoration(mActivity))
        mRecyclerView.setHasFixedSize(true)
//        mRecyclerView.setOnTouchListener(this) // not work
        mInteractionCallback.setOnRefreshLayoutChildScrollUpCallback { _, _ ->
            if (mSearchText == EMPTY_STRING) {
                true
            } else {
                mRecyclerView.canScrollVertically(-1)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent) = when {
        v === view -> {
            if (event.action == MotionEvent.ACTION_UP) {
                UiUtils.hideSoftInput(mActivity.window)
                requireFragmentManager().popBackStackImmediate()
            }
            false
        }
        v.parent === mRecyclerView -> {
            if (event.action == MotionEvent.ACTION_DOWN) {
                mDownX = event.rawX.toInt()
                mDownY = event.rawY.toInt()

                UiUtils.hideSoftInput(mActivity.window)
                mRecyclerView.requestFocus()
            }
            false
        }
        else -> false
    }

    override fun onClick(v: View) =
            if (v.parent === mRecyclerView)
                playVideo(mSearchedVideos[v.tag as Int])
            else Unit

    override fun onLongClick(v: View) = if (v.parent === mRecyclerView) {
        val index = v.tag as Int
        val video = mSearchedVideos[index]

        val headersCount = mAdapterWrapper.headersCount
        val position = headersCount + index
        val itemCount = mAdapterWrapper.itemCount - position

        mVideoOptionsMenu = FloatingMenu(mRecyclerView)
        mVideoOptionsMenu!!.inflate(R.menu.floatingmenu_video_ops)
        mVideoOptionsMenu!!.setOnItemClickListener { menuItem, _ ->
            when (menuItem.iconResId) {
                R.drawable.ic_delete_24dp_menu -> mVideoOpCallback?.showDeleteVideoDialog(video) {
                    mVideos.remove(video)

                    mSearchedVideos.removeAt(index)
                    mAdapterWrapper.notifyItemRemoved(position)
                    mAdapterWrapper.notifyItemRangeChanged(position, itemCount - 1)
                    updateSearchResult()
                }
                R.drawable.ic_edit_24dp_menu -> mVideoOpCallback?.showRenameVideoDialog(video) {
                    mVideos.sortByElementName()

                    if (mSearchText.length == AlgorithmUtils.LCS(
                                    video.name, mSearchText, false).length) {
                        mSearchedVideos.sortByElementName()
                        val newIndex = mSearchedVideos.indexOf(video)
                        if (newIndex == index) {
                            mAdapterWrapper.notifyItemChanged(position, PAYLOAD_REFRESH_ITEM_NAME)
                        } else {
                            val newPosition = headersCount + newIndex
                            mAdapterWrapper.notifyItemRemoved(position)
                            mAdapterWrapper.notifyItemInserted(newPosition)
                            mAdapterWrapper.notifyItemRangeChanged(Math.min(position, newPosition),
                                    Math.abs(newPosition - position) + 1)
                        }
                    } else {
                        mSearchedVideos.removeAt(index)
                        mAdapterWrapper.notifyItemRemoved(position)
                        mAdapterWrapper.notifyItemRangeChanged(position, itemCount - 1)
                        updateSearchResult()
                    }
                }
                R.drawable.ic_share_24dp_menu -> shareVideo(video)
                R.drawable.ic_info_24dp_menu -> mVideoOpCallback?.showVideoDetailsDialog(video)
            }
        }
        mVideoOptionsMenu!!.setOnDismissListener {
            mSelectedItemIndex = NO_POSITION
            mAdapterWrapper.notifyItemChanged(position, PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS)
            mVideoOptionsMenu = null
        }
        mVideoOptionsMenu!!.show(mDownX, mDownY)

        // 高亮选中的itemView
        mSelectedItemIndex = index
        mAdapterWrapper.notifyItemChanged(position, PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS)

        true
    } else false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mLifecycleCallback?.onFragmentViewCreated(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mLifecycleCallback?.onFragmentViewDestroyed(this)

        mSelectedItemIndex = NO_POSITION
        mVideoOptionsMenu?.dismiss()

        mSearchText = EMPTY_STRING
        mSearchedVideos.clear()
        val task = mLoadVideosTask
        if (task != null) {
            mLoadVideosTask = null
            task.cancel(false)
        }

        mInteractionCallback.setOnRefreshLayoutChildScrollUpCallback(null)
    }

    override fun onDetach() {
        super.onDetach()
        mLifecycleCallback?.onFragmentDetached(this)

        targetFragment?.onActivityResult(targetRequestCode,
                RESULT_CODE_SEARCHED_VIDEOS_FRAGMENT,
                Intent().putParcelableArrayListExtra(KEY_VIDEOS, mVideos))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_PLAY_VIDEO -> if (resultCode == RESULT_CODE_PLAY_VIDEO) {
                val video = data?.getParcelableExtra<Video>(KEY_VIDEO) ?: return
                if (video.id == NO_ID) return

                val headersCount = mAdapterWrapper.headersCount
                for ((i, v) in mSearchedVideos.withIndex()) {
                    if (v != video) continue
                    if (v.progress != video.progress) {
                        v.progress = video.progress
                        mAdapterWrapper.notifyItemChanged(
                                headersCount + i, PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION)
                    }
                    break
                }
            }
        }
    }

    override fun onReloadVideos(videos: List<Video>?) =
            if (!mVideos.allEquals(videos)) {
                mVideos.set(videos)
                refreshList(false)
            } else Unit

    private fun refreshList(searchTextChanged: Boolean) {
        var searchedVideos: MutableList<Video>? = null
        if (mSearchText.isNotEmpty()) {
            for (video in mVideos) {
                if (mSearchText.length == AlgorithmUtils.LCS(
                                video.name, mSearchText, false).length) {
                    if (searchedVideos == null) searchedVideos = mutableListOf()
                    searchedVideos.add(video)
                }
            }
        }
        if (searchedVideos == null || searchedVideos.isEmpty()) {
            if (mSearchedVideos.isNotEmpty()) {
                mSearchedVideos.clear()
                mAdapterWrapper.notifyDataSetChanged()
                updateSearchResult()
            }
        } else if (searchedVideos.size == mSearchedVideos.size) {
            var changedIndices: MutableList<Int>? = null
            for (i in searchedVideos.indices) {
                if (!searchedVideos[i].allEquals(mSearchedVideos[i])) {
                    if (changedIndices == null) changedIndices = LinkedList()
                    changedIndices.add(i)
                }
            }
            val headersCount = mAdapterWrapper.headersCount
            if (searchTextChanged) {
                for (index in searchedVideos.indices) {
                    if (changedIndices?.contains(index) == true) {
                        mSearchedVideos[index] = searchedVideos[index]
                        mAdapterWrapper.notifyItemChanged(headersCount + index)
                    } else {
                        mAdapterWrapper.notifyItemChanged(headersCount + index, PAYLOAD_REFRESH_ITEM_NAME)
                    }
                }
            } else if (changedIndices != null) {
                for (index in changedIndices) {
                    mSearchedVideos[index] = searchedVideos[index]
                    mAdapterWrapper.notifyItemChanged(headersCount + index)
                }
            }
        } else {
            mSearchedVideos.set(searchedVideos)
            mAdapterWrapper.notifyDataSetChanged()
            updateSearchResult()
        }
    }

    private fun updateSearchResult() {
        val size = mSearchedVideos.size
        if (size == 0) {
            mRecyclerView.visibility = View.GONE
        } else {
            mRecyclerView.visibility = View.VISIBLE
            mSearchResultText.text = getString(R.string.findSomeVideos, size)
        }
    }

    override fun onRefresh() {
        if (mVideoOpCallback?.isAsyncDeletingVideos == true) {
            mInteractionCallback.isRefreshLayoutRefreshing = false
            return
        }
        if (mLoadVideosTask == null) {
            mLoadVideosTask = LoadVideosTask()
            mLoadVideosTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class LoadVideosTask : AsyncTask<Void, Void, List<Video>?>() {
        override fun doInBackground(vararg voids: Void): List<Video>? {
            val helper = VideoDaoHelper.getInstance(mContext)

            val videoCursor = helper.queryAllVideos() ?: return null

            var videos: MutableList<Video>? = null
            while (!isCancelled && videoCursor.moveToNext()) {
                val video = helper.buildVideo(videoCursor)
                if (video != null) {
                    if (videos == null) videos = LinkedList()
                    videos.add(video)
                }
            }
            videoCursor.close()

            return videos
        }

        override fun onPostExecute(videos: List<Video>?) {
            onReloadVideos(videos)
            mInteractionCallback.isRefreshLayoutRefreshing = false
            mLoadVideosTask = null
        }

        override fun onCancelled(result: List<Video>?) {
            if (mLoadVideosTask == null) mInteractionCallback.isRefreshLayoutRefreshing = false
        }
    }

    private inner class SearchedVideoListAdapter : RecyclerView.Adapter<SearchedVideoListAdapter.ViewHolder>() {
        override fun getItemCount() = mSearchedVideos.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ViewHolder(LayoutInflater.from(mActivity)
                        .inflate(R.layout.item_searched_video_list, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position)
            } else {
                val payload = payloads[0] as Int
                if (payload and PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS != 0) {
                    highlightSelectedItemIfExists(holder, position)
                }
                if (payload and PAYLOAD_REFRESH_ITEM_NAME != 0) {
                    updateItemName(holder, mSearchedVideos[position].name)
                }
                if (payload and PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION != 0) {
                    val (_, _, _, _, _, progress, duration) = mSearchedVideos[position]
                    holder.videoProgressAndDurationText.text =
                            VideoUtils2.concatVideoProgressAndDuration(progress, duration)
                }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemView.tag = position

            highlightSelectedItemIfExists(holder, position)

            val video = mSearchedVideos[position]
            VideoUtils2.loadVideoThumbnail(holder.videoImage, video)
            updateItemName(holder, video.name)
            holder.videoProgressAndDurationText.text =
                    VideoUtils2.concatVideoProgressAndDuration(video.progress, video.duration)
        }

        // 高亮搜索关键字
        fun updateItemName(holder: ViewHolder, name: String) {
            val text = SpannableString(name)
            var fromIndex = 0
            for (char in mSearchText.toCharArray()) {
                val start = name.toLowerCase().indexOf(char.toLowerCase(), fromIndex)
                text.setSpan(ForegroundColorSpan(COLOR_ACCENT),
                        start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                fromIndex = start + 1
            }
            holder.videoNameText.text = text
        }

        fun highlightSelectedItemIfExists(holder: ViewHolder, position: Int) {
            if (position == mSelectedItemIndex) {
                if (holder.selectorView.tag == null) {
                    holder.selectorView.tag = holder.selectorView.background
                }
                holder.selectorView.setBackgroundColor(COLOR_SELECTOR)
            } else {
                ViewCompat.setBackground(holder.selectorView,
                        holder.selectorView.tag as Drawable? ?: return)
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val selectorView: View = (itemView as ViewGroup)[0]
            val videoImage: ImageView = itemView.findViewById(R.id.image_video)
            val videoNameText: TextView = itemView.findViewById(R.id.text_videoName)
            val videoProgressAndDurationText: TextView = itemView.findViewById(R.id.text_videoProgressAndDuration)

            init {
                itemView.setOnTouchListener(this@SearchedVideosFragment)
                itemView.setOnClickListener(this@SearchedVideosFragment)
                itemView.setOnLongClickListener(this@SearchedVideosFragment)
            }
        }
    }

    private class DividerItemDecoration
    @SuppressLint("LongLogTag") constructor(context: Context) : RecyclerView.ItemDecoration() {

        var mDivider: Drawable? = null

        val mBounds = Rect()

        init {
            val a = context.obtainStyledAttributes(ATTRS)
            mDivider = a.getDrawable(0)
            if (mDivider == null && BuildConfig.DEBUG) {
                Log.w(TAG, "@android:attr/listDivider was not set in the theme used for this "
                        + "DividerItemDecoration. Please set that attribute all call setDivider()")
            }
            a.recycle()
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val divider = mDivider ?: return

            canvas.save()

            val left: Int
            val right: Int
            if (parent.clipToPadding) {
                left = parent.paddingLeft
                right = parent.width - parent.paddingRight
                canvas.clipRect(left, parent.paddingTop, right,
                        parent.height - parent.paddingBottom)
            } else {
                left = 0
                right = parent.width
            }

            for (i in 0 until parent.childCount) {
                val child = parent[i]
                if (parent.getChildAdapterPosition(child) >= HEADER_COUNT) {
                    parent.getDecoratedBoundsWithMargins(child, mBounds)
                    val bottom = mBounds.bottom + Math.round(child.translationY)
                    val top = bottom - divider.intrinsicHeight
                    divider.setBounds(left, top, right, bottom)
                    divider.draw(canvas)
                }
            }

            canvas.restore()
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                                    state: RecyclerView.State) {
            val divider = mDivider
            if (divider == null) {
                outRect.set(0, 0, 0, 0)
                return
            }

            val dividerHeight = divider.intrinsicHeight
            outRect.set(0, 0, 0,
                    if (parent.getChildAdapterPosition(view) >= HEADER_COUNT) dividerHeight else 0)
        }

        companion object {
            private const val HEADER_COUNT = 1
            private const val TAG = "SearchedVideosFragment.DividerItemDecoration"
            private val ATTRS = intArrayOf(android.R.attr.listDivider)
        }
    }

    interface InteractionCallback : ActionBarCallback, RefreshLayoutCallback
}
