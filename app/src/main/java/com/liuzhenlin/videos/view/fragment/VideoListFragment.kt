/*
 * Created on 2018/08/15.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.liuzhenlin.circularcheckbox.CircularCheckBox
import com.liuzhenlin.floatingmenu.DensityUtils
import com.liuzhenlin.simrv.SlidingItemMenuRecyclerView
import com.liuzhenlin.swipeback.SwipeBackFragment
import com.liuzhenlin.texturevideoview.utils.FileUtils
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.dao.IVideoDao
import com.liuzhenlin.videos.dao.VideoDaoHelper
import com.liuzhenlin.videos.model.Video
import com.liuzhenlin.videos.model.VideoDirectory
import com.liuzhenlin.videos.model.VideoListItem
import com.liuzhenlin.videos.utils.FileUtils2
import com.liuzhenlin.videos.utils.UiUtils
import com.liuzhenlin.videos.utils.VideoUtils2
import com.liuzhenlin.videos.view.fragment.PackageConsts.*
import com.liuzhenlin.videos.view.swiperefresh.SwipeRefreshLayout
import java.util.*

/**
 * @author 刘振林
 */
class VideoListFragment : SwipeBackFragment(), VideoOpCallback, SwipeRefreshLayout.OnRefreshListener,
        View.OnClickListener, View.OnLongClickListener {
    private lateinit var mActivity: Activity
    private lateinit var mContext: Context
    private lateinit var mInteractionCallback: InteractionCallback

    private lateinit var mRecyclerView: SlidingItemMenuRecyclerView
    private val mAdapter = VideoListAdapter()
    private val PAYLOAD_REFRESH_VIDEODIR_THUMB = PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION shl 1
    private val PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT = PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION shl 2

    private var mItemOptionsWindow: PopupWindow? = null
    private var mDeleteItemsWindow: PopupWindow? = null
    private var mDeleteItemDialog: Dialog? = null
    private var mRenameItemDialog: Dialog? = null
    private var mItemDetailsDialog: Dialog? = null

    private var mTitleWindowFrame: FrameLayout? = null
    private var mSelectAllButton: TextView? = null

    private var mTitleText_IOW: TextView? = null
    private var mDeleteButton_IOW: TextView? = null
    private var mRenameButton_IOW: TextView? = null
    private var mShareButton_IOW: TextView? = null
    private var mDetailsButton_IOW: TextView? = null

    private var mNeedReloadVideos = false
    private val mVideoListItems = mutableListOf<VideoListItem>()
    private var mVideoObserver: VideoObserver? = null
    private var mOnReloadVideosListeners: MutableList<OnReloadVideosListener>? = null
    private var mLoadVideosTask: LoadVideosTask? = null

    /* internal inline */ val allVideos: ArrayList<Video>?
        get() {
            var videos: ArrayList<Video>? = null
            for (item in mVideoListItems) {
                if (videos == null) videos = ArrayList()
                when (item) {
                    is Video -> videos.add(item)
                    is VideoDirectory -> videos.addAll(item.videos)
                }
            }
            return videos?.apply {
                deepCopy(videos)
                sortByElementName()
            }
        }

    private inline val checkedItems: List<VideoListItem>?
        get() {
            var checkedItems: MutableList<VideoListItem>? = null
            for (item in mVideoListItems) {
                if (item.isChecked) {
                    if (checkedItems == null) {
                        checkedItems = mutableListOf()
                    }
                    checkedItems.add(item)
                }
            }
            return checkedItems
        }

    private var _TOP: String? = null
    private inline val TOP: String
        get() {
            if (_TOP == null) {
                _TOP = getString(R.string.top)
            }
            return _TOP!!
        }
    private var _CANCEL_TOP: String? = null
    private inline val CANCEL_TOP: String
        get() {
            if (_CANCEL_TOP == null) {
                _CANCEL_TOP = getString(R.string.cancelTop)
            }
            return _CANCEL_TOP!!
        }
    private var _SELECT_ALL: String? = null
    private inline val SELECT_ALL: String
        get() {
            if (_SELECT_ALL == null) {
                _SELECT_ALL = getString(R.string.selectAll)
            }
            return _SELECT_ALL!!
        }
    private var _SELECT_NONE: String? = null
    private inline val SELECT_NONE: String
        get() {
            if (_SELECT_NONE == null) {
                _SELECT_NONE = getString(R.string.selectNone)
            }
            return _SELECT_NONE!!
        }

    fun addOnReloadVideosListener(listener: OnReloadVideosListener) {
        if (mOnReloadVideosListeners == null)
            mOnReloadVideosListeners = LinkedList()
        if (!mOnReloadVideosListeners!!.contains(listener))
            mOnReloadVideosListeners!!.add(listener)
    }

    fun removeOnReloadVideosListener(listener: OnReloadVideosListener?) {
        mOnReloadVideosListeners?.remove(listener ?: return)
    }

    private fun notifyListenersOnReloadVideos() = mOnReloadVideosListeners?.let {
        if (it.isEmpty()) return@let

        val videos = allVideos
        for (listener in it.toTypedArray()) {
            @Suppress("UNCHECKED_CAST")
            val copy = videos?.clone() as? MutableList<Video>
            copy?.deepCopy(videos)
            listener.onReloadVideos(copy)
        }
    }

    override fun showDeleteVideoDialog(video: Video, onDeleteAction: () -> Unit) =
            showDeleteItemDialog(video, onDeleteAction)

    override fun showDeleteVideosPopupWindow(vararg videos: Video, onDeleteAction: () -> Unit) =
            showDeleteItemWindow(videos, onDeleteAction)

    override fun showRenameVideoDialog(video: Video, onRenameAction: () -> Unit) =
            showRenameItemDialog(video, onRenameAction)

    override fun showVideoDetailsDialog(video: Video) = showItemDetailsDialog(video)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = context as Activity
        mContext = context.applicationContext

        val parent = parentFragment
        mInteractionCallback = when {
            parent is InteractionCallback -> parent
            context is InteractionCallback -> context
            parent != null -> throw RuntimeException("Neither $context nor $parent " +
                    "has implemented VideoListFragment.InteractionCallback")
            else -> throw RuntimeException(
                    "$context must implement VideoListFragment.InteractionCallback")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contentView = inflater.inflate(R.layout.fragment_video_list, container, false)
        mRecyclerView = contentView.findViewById(R.id.simrv_videoList)
        mRecyclerView.layoutManager = LinearLayoutManager(mActivity)
        mRecyclerView.adapter = mAdapter
        mRecyclerView.addItemDecoration(DividerItemDecoration(mActivity, DividerItemDecoration.VERTICAL))
        mRecyclerView.setHasFixedSize(true)

        isSwipeBackEnabled = false
        return attachViewToSwipeBackLayout(contentView)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        autoLoadVideos()
    }

    override fun onStart() {
        super.onStart()
        mVideoObserver?.stopWatching()
        if (mNeedReloadVideos) {
            mNeedReloadVideos = false
            autoLoadVideos()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!mActivity.isFinishing && !isRemoving && !isDetached) {
            (mVideoObserver ?: VideoObserver(mActivity.window.decorView.handler)).startWatching()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mItemOptionsWindow?.dismiss()
        mDeleteItemsWindow?.dismiss()
        mDeleteItemDialog?.dismiss()
        mRenameItemDialog?.dismiss()
        mItemDetailsDialog?.dismiss()

        mVideoObserver?.stopWatching()
        mNeedReloadVideos = false

        val task = mLoadVideosTask
        if (task != null) {
            mLoadVideosTask = null
            task.cancel(false)
        }
//        mVideoListItems.clear()
//        notifyListenersOnReloadVideos()
    }

    override fun onDestroy() {
        super.onDestroy()
        App.getInstance(mContext).refWatcher.watch(this)
    }

    /**
     * @return 是否消费点按返回键事件
     */
    fun onBackPressed(): Boolean {
        mItemOptionsWindow?.dismiss() ?: return false
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_PLAY_VIDEO -> if (resultCode == RESULT_CODE_PLAY_VIDEO) {
                val video = data?.getParcelableExtra<Video>(KEY_VIDEO) ?: return
                if (video.id == NO_ID) return

                for (i in mVideoListItems.indices) {
                    val item = mVideoListItems[i] as? Video ?: continue
                    if (item != video) continue

                    if (item.progress != video.progress) {
                        item.progress = video.progress
                        mAdapter.notifyItemChanged(i, PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION)
                    }
                    break
                }
            }
            REQUEST_CODE_SEARCHED_VIDEOS_FRAGMENT ->
                if (resultCode == RESULT_CODE_SEARCHED_VIDEOS_FRAGMENT) {
                    val videos = data?.getParcelableArrayListExtra<Video>(KEY_VIDEOS) ?: return
                    if (!videos.allEquals(allVideos)) { //XXX: 更加高效的局部刷新
                        onReloadVideoListItems(videos.classifyByDirectories())
                    }
                }
            REQUEST_CODE_FOLDED_VIDEOS_FRAGMENT ->
                if (resultCode == RESULT_CODE_FOLDED_VIDEOS_FRAGMENT) {
                    val dirPath = data?.getStringExtra(KEY_DIRECTORY_PATH) ?: return
                    val videos = data.getParcelableArrayListExtra<Video>(KEY_VIDEOS)
                    loop@ for ((i, item) in mVideoListItems.withIndex()) {
                        if (item.path != dirPath) continue@loop

                        when (videos.size) {
                            0 -> {
                                mVideoListItems.removeAt(i)
                                mAdapter.notifyItemRemoved(i)
                                mAdapter.notifyItemRangeChanged(i, mAdapter.itemCount - i)
                            }
                            1 -> {
                                val video = videos[0]
                                video.isTopped = false
                                mVideoListItems[i] = video
                                mVideoListItems.sortByElementName()
                                val newIndex = mVideoListItems.indexOf(video)
                                if (newIndex == i) {
                                    mAdapter.notifyItemChanged(i) // without payload
                                } else {
                                    mAdapter.notifyItemRemoved(i)
                                    mAdapter.notifyItemInserted(newIndex)
                                    mAdapter.notifyItemRangeChanged(Math.min(i, newIndex),
                                            Math.abs(newIndex - i) + 1)
                                }
                            }
                            else -> {
                                if (item is Video) {
                                    val vd = VideoDaoHelper.getInstance(mContext).queryVideoDirByPathOrInsert(dirPath)
                                    vd.videos = videos
                                    vd.size = videos.countAllVideoSize()

                                    mVideoListItems[i] = vd
                                    mVideoListItems.sortByElementName()
                                    val newIndex = mVideoListItems.indexOf(vd)
                                    if (newIndex == i) {
                                        mAdapter.notifyItemChanged(i)
                                    } else {
                                        mAdapter.notifyItemRemoved(i)
                                        mAdapter.notifyItemInserted(newIndex)
                                        mAdapter.notifyItemRangeChanged(Math.min(i, newIndex),
                                                Math.abs(newIndex - i) + 1)
                                    }
                                } else if (item is VideoDirectory) {
                                    val oldVideos = item.videos
                                    val oldSize = item.size
                                    val oldVideoCount = oldVideos.size
                                    if (!oldVideos.allEquals(videos)) {
                                        item.videos = videos
                                        item.size = videos.countAllVideoSize()

                                        var payloads = 0
                                        if (!oldVideos[0].allEquals(videos[0])) {
                                            payloads = payloads or PAYLOAD_REFRESH_VIDEODIR_THUMB
                                        }
                                        if (oldSize != item.size || oldVideoCount != videos.size) {
                                            payloads = payloads or PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT
                                        }
                                        if (payloads != 0) {
                                            mAdapter.notifyItemChanged(i, payloads)
                                        }
                                    }
                                }
                            }
                        }
                        break@loop
                    }
                }
        }
    }

    private fun onReloadVideoListItems(items: List<VideoListItem>?, notifyListeners: Boolean = false) {
        if (items == null || items.isEmpty()) {
            if (mVideoListItems.isNotEmpty()) {
                mVideoListItems.clear()
                mAdapter.notifyDataSetChanged()
                if (notifyListeners) notifyListenersOnReloadVideos()
            }
        } else
            if (items.size == mVideoListItems.size) {
                var changedIndices: MutableList<Int>? = null
                for (i in items.indices) {
                    if (!items[i].allEquals(mVideoListItems[i])) {
                        if (changedIndices == null) changedIndices = LinkedList()
                        changedIndices.add(i)
                    }
                }
                if (changedIndices != null)
                    for (index in changedIndices) {
                        mVideoListItems[index] = items[index]
                        mAdapter.notifyItemChanged(index) // without payload
                    }
                if (notifyListeners) notifyListenersOnReloadVideos()
            } else {
                mVideoListItems.set(items)
                mAdapter.notifyDataSetChanged()
                if (notifyListeners) notifyListenersOnReloadVideos()
            }
    }

    private fun autoLoadVideos() {
        mInteractionCallback.isRefreshLayoutRefreshing = true
        queryAllVideos()
    }

    override fun onRefresh() = queryAllVideos()

    private fun queryAllVideos() {
        if (isAsyncDeletingVideos) { // 页面自动刷新或用户手动刷新时，还有视频在被异步删除...
            mInteractionCallback.isRefreshLayoutRefreshing = false
            return
        }

        /*
         * 1）自动刷新时隐藏弹出的多选窗口
         * 2）用户长按列表时可能又在下拉刷新，多选窗口会被弹出，需要隐藏
         */
        mItemOptionsWindow?.dismiss()

        // 不在加载视频时才加载
        if (mLoadVideosTask == null) {
            mLoadVideosTask = LoadVideosTask()
            mLoadVideosTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class LoadVideosTask : AsyncTask<Void, Void, List<VideoListItem>?>() {

        override fun onPreExecute() {
            mRecyclerView.releaseItemView(false)
            mRecyclerView.isItemScrollingEnabled = false
        }

        override fun doInBackground(vararg voids: Void): List<VideoListItem>? {
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

            return videos.classifyByDirectories()
        }

        override fun onPostExecute(items: List<VideoListItem>?) {
            onReloadVideoListItems(items, true)
            mRecyclerView.isItemScrollingEnabled = true
            mInteractionCallback.isRefreshLayoutRefreshing = false
            mLoadVideosTask = null
        }

        override fun onCancelled(result: List<VideoListItem>?) {
            if (mLoadVideosTask == null) {
                mRecyclerView.isItemScrollingEnabled = true
                mInteractionCallback.isRefreshLayoutRefreshing = false
            }
        }
    }

    private inner class VideoObserver(handler: Handler) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            // 此页面放入后台且数据有变化，标记为需要刷新数据
            // 以在此页面重新显示在前台时刷新列表
            mNeedReloadVideos = true
        }

        fun startWatching() {
            mVideoObserver = this
            mContext.contentResolver.registerContentObserver(
                    IVideoDao.VIDEO_URI, false, this)
        }

        fun stopWatching() {
            mVideoObserver = null
            mContext.contentResolver.unregisterContentObserver(this)
        }
    }

    private inner class VideoListAdapter : RecyclerView.Adapter<VideoListAdapter.VideoListViewHolder>() {
        val VIEW_TYPE_VIDEODIR = 1
        val VIEW_TYPE_VIDEO = 2

        override fun getItemCount() = mVideoListItems.size

        override fun getItemViewType(position: Int) =
                if (mVideoListItems[position] is Video)
                    VIEW_TYPE_VIDEO
                else /* if (item is VideoDirectory) */
                    VIEW_TYPE_VIDEODIR

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoListViewHolder =
                when (viewType) {
                    VIEW_TYPE_VIDEO -> VideoViewHolder(LayoutInflater.from(mActivity)
                            .inflate(R.layout.video_list_item_video, parent, false))
                    VIEW_TYPE_VIDEODIR -> VideoDirViewHolder(LayoutInflater.from(mActivity)
                            .inflate(R.layout.video_list_item_videodir, parent, false))
                    else -> throw IllegalArgumentException("Unknown itemView type")
                }

        override fun onBindViewHolder(holder: VideoListViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position)
            } else {
                val item = mVideoListItems[position]

                val payload = payloads[0] as Int
                if (payload and PAYLOAD_CHANGE_ITEM_LPS_AND_BG != 0) {
                    separateToppedItemsFromUntoppedOnes(holder, position)
                }
                if (payload and PAYLOAD_CHANGE_CHECKBOX_VISIBILITY != 0) {
                    if (mItemOptionsWindow == null) {
                        holder.checkBox.visibility = View.GONE
                    } else {
                        holder.checkBox.visibility = View.VISIBLE
                    }
                }
                if (payload and PAYLOAD_REFRESH_CHECKBOX != 0) {
                    holder.checkBox.isChecked = item.isChecked
                } else if (payload and PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR != 0) {
                    holder.checkBox.setChecked(item.isChecked, true)
                }
                if (payload and PAYLOAD_REFRESH_ITEM_NAME != 0) {
                    when (holder) {
                        is VideoViewHolder -> holder.videoNameText.text = item.name
                        is VideoDirViewHolder -> holder.videodirNameText.text = item.name
                    }
                }
                if (payload and PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION != 0) {
                    val (_, _, _, _, _, progress, duration) = item as Video
                    (holder as VideoViewHolder).videoProgressAndDurationText.text =
                            VideoUtils2.concatVideoProgressAndDuration(progress, duration)
                }
                if (payload and PAYLOAD_REFRESH_VIDEODIR_THUMB != 0) {
                    val vh = holder as VideoDirViewHolder
                    val videos = (item as VideoDirectory).videos
                    VideoUtils2.loadVideoThumbnail(vh.videodirImage, videos[0])
                }
                if (payload and PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT != 0) {
                    val vh = holder as VideoDirViewHolder
                    val videos = (item as VideoDirectory).videos
                    vh.videodirSizeText.text = FileUtils2.formatFileSize(item.size.toDouble())
                    vh.videoCountText.text = getString(R.string.aTotalOfSeveralVideos, videos.size)
                }
            }
        }

        override fun onBindViewHolder(holder: VideoListViewHolder, position: Int) {
            holder.itemVisibleFrame.tag = position
            holder.checkBox.tag = position
            holder.topButton.tag = position
            holder.deleteButton.tag = position

            separateToppedItemsFromUntoppedOnes(holder, position)

            val item = mVideoListItems[position]
            if (mItemOptionsWindow == null) {
                holder.checkBox.visibility = View.GONE
            } else {
                holder.checkBox.visibility = View.VISIBLE
                holder.checkBox.isChecked = item.isChecked
            }
            when (getItemViewType(position)) {
                VIEW_TYPE_VIDEO -> {
                    val vh = holder as VideoViewHolder
                    val video = item as Video

                    VideoUtils2.loadVideoThumbnail(vh.videoImage, video)
                    vh.videoNameText.text = item.name
                    vh.videoSizeText.text = FileUtils2.formatFileSize(item.size.toDouble())
                    vh.videoProgressAndDurationText.text =
                            VideoUtils2.concatVideoProgressAndDuration(video.progress, video.duration)
                }
                VIEW_TYPE_VIDEODIR -> {
                    val vh = holder as VideoDirViewHolder
                    val videos = (item as VideoDirectory).videos

                    VideoUtils2.loadVideoThumbnail(vh.videodirImage, videos[0])
                    vh.videodirNameText.text = item.name
                    vh.videodirSizeText.text = FileUtils2.formatFileSize(item.size.toDouble())
                    vh.videoCountText.text = getString(R.string.aTotalOfSeveralVideos, videos.size)
                }
            }
        }

        private fun separateToppedItemsFromUntoppedOnes(holder: VideoListViewHolder, position: Int) {
            val lp = holder.topButton.layoutParams

            if (mVideoListItems[position].isTopped) {
                ViewCompat.setBackground(holder.itemVisibleFrame,
                        ContextCompat.getDrawable(mActivity, R.drawable.selector_topped_recycler_item))

                lp.width = DensityUtils.dp2px(mContext, 120f)
                holder.topButton.layoutParams = lp
                holder.topButton.text = CANCEL_TOP
            } else {
                ViewCompat.setBackground(holder.itemVisibleFrame,
                        ContextCompat.getDrawable(mActivity, R.drawable.default_selector_recycler_item))

                lp.width = DensityUtils.dp2px(mContext, 90f)
                holder.topButton.layoutParams = lp
                holder.topButton.text = TOP
            }
        }

        open inner class VideoListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val itemVisibleFrame: ViewGroup = itemView.findViewById(R.id.itemVisibleFrame)
            val checkBox: CircularCheckBox = itemView.findViewById(R.id.checkbox)
            val topButton: TextView = itemView.findViewById(R.id.bt_top)
            val deleteButton: TextView = itemView.findViewById(R.id.bt_delete)

            init {
                itemVisibleFrame.setOnClickListener(this@VideoListFragment)
                checkBox.setOnClickListener(this@VideoListFragment)
                topButton.setOnClickListener(this@VideoListFragment)
                deleteButton.setOnClickListener(this@VideoListFragment)

                itemVisibleFrame.setOnLongClickListener(this@VideoListFragment)
            }
        }

        inner class VideoViewHolder(itemView: View) : VideoListViewHolder(itemView) {
            val videoImage: ImageView = itemView.findViewById(R.id.image_video)
            val videoNameText: TextView = itemView.findViewById(R.id.text_videoName)
            val videoSizeText: TextView = itemView.findViewById(R.id.text_videoSize)
            val videoProgressAndDurationText: TextView = itemView.findViewById(R.id.text_videoProgressAndDuration)
        }

        inner class VideoDirViewHolder(itemView: View) : VideoListViewHolder(itemView) {
            val videodirImage: ImageView = itemView.findViewById(R.id.image_videodir)
            val videodirNameText: TextView = itemView.findViewById(R.id.text_videodirName)
            val videodirSizeText: TextView = itemView.findViewById(R.id.text_videodirSize)
            val videoCountText: TextView = itemView.findViewById(R.id.text_videoCount)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.itemVisibleFrame -> {
                val position = v.tag as Int
                val item = mVideoListItems[position]

                if (mItemOptionsWindow == null) {
                    when (item) {
                        is Video -> playVideo(item) // 播放视频
                        is VideoDirectory -> { // 显示指定目录的视频
                            val arguments = Bundle()
                            arguments.putParcelable(KEY_VIDEODIR, item.deepCopy())
                            mInteractionCallback.goToFoldedVideosFragment(arguments)
                                    .setTargetFragment(this, REQUEST_CODE_FOLDED_VIDEOS_FRAGMENT)
                        }
                    }
                } else {
                    item.isChecked = !item.isChecked
                    mAdapter.notifyItemChanged(position, PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)
                    onItemCheckedChange()
                }
            }

            R.id.checkbox -> {
                val item = mVideoListItems[v.tag as Int]
                item.isChecked = !item.isChecked
                onItemCheckedChange()
            }

            // 置顶或取消置顶视频（目录）
            R.id.bt_top -> {
                val position = v.tag as Int
                val item = mVideoListItems[position]

                val topped = !item.isTopped
                item.isTopped = topped
                VideoDaoHelper.getInstance(mContext).setVideoListItemTopped(item, topped)

                val newPosition = mVideoListItems.reorder().indexOf(item)
                if (newPosition == position) {
                    mAdapter.notifyItemChanged(position, PAYLOAD_CHANGE_ITEM_LPS_AND_BG)
                } else {
                    mVideoListItems.add(newPosition, mVideoListItems.removeAt(position))
                    mAdapter.notifyItemRemoved(position)
                    mAdapter.notifyItemInserted(newPosition)
                    mAdapter.notifyItemRangeChanged(Math.min(position, newPosition),
                            Math.abs(newPosition - position) + 1)
                }
            }

            // 删除视频
            R.id.bt_delete -> showDeleteItemDialog(mVideoListItems[v.tag as Int])
            R.id.bt_confirm_deleteVideoListItemDialog -> {
                val window = mDeleteItemDialog!!.window!!
                val decorView = window.decorView as ViewGroup
                val item = decorView.tag as VideoListItem
                @Suppress("UNCHECKED_CAST")
                val onDeleteAction = decorView[0].tag as (() -> Unit)?

                mDeleteItemDialog!!.cancel()

                when (item) {
                    is Video -> deleteVideos(item)
                    is VideoDirectory -> deleteVideos(*item.videos.toTypedArray())
                }

                if (onDeleteAction != null) {
                    onDeleteAction()
                } else {
                    val index = mVideoListItems.indexOf(item)
                    if (index != -1) {
                        mVideoListItems.removeAt(index)
                        mAdapter.notifyItemRemoved(index)
                        mAdapter.notifyItemRangeChanged(index, mAdapter.itemCount - index)
                    }
                }
            }
            R.id.bt_cancel_deleteVideoListItemDialog -> mDeleteItemDialog!!.cancel()

            // 删除（多个）视频
            R.id.bt_delete_vlow -> {
                val items = checkedItems ?: return
                showDeleteItemWindow(items.toTypedArray())
            }
            R.id.bt_confirm_deleteVideosWindow -> {
                val view = mDeleteItemsWindow!!.contentView as ViewGroup
                @Suppress("UNCHECKED_CAST")
                val items = view.tag as Array<VideoListItem>
                @Suppress("UNCHECKED_CAST")
                val onDeleteAction = view[0].tag as (() -> Unit)?

                mDeleteItemsWindow!!.dismiss()
                mItemOptionsWindow?.dismiss()

                if (items.size == 1) {
                    val item = items[0]
                    when (item) {
                        is Video -> deleteVideos(item)
                        is VideoDirectory -> deleteVideos(*item.videos.toTypedArray())
                    }

                    if (onDeleteAction != null) {
                        onDeleteAction()
                    } else {
                        val index = mVideoListItems.indexOf(item)
                        if (index != -1) {
                            mVideoListItems.removeAt(index)
                            mAdapter.notifyItemRemoved(index)
                            mAdapter.notifyItemRangeChanged(index, mAdapter.itemCount - index)
                        }
                    }
                } else {
                    val videos = LinkedList<Video>()
                    for (item in items) {
                        when (item) {
                            is Video -> videos.add(item)
                            is VideoDirectory -> videos.addAll(item.videos)
                        }
                    }
                    deleteVideos(*videos.toTypedArray())

                    if (onDeleteAction != null) {
                        onDeleteAction()
                    } else {
                        var start = -1
                        var index = 0
                        val it = mVideoListItems.iterator()
                        while (it.hasNext()) {
                            if (items.contains(it.next())) {
                                if (start == -1) {
                                    start = index
                                }
                                it.remove()
                                mAdapter.notifyItemRemoved(index)
                                index--
                            }
                            index++
                        }
                        mAdapter.notifyItemRangeChanged(start, mAdapter.itemCount - start)
                    }
                }
            }
            R.id.bt_cancel_deleteVideosWindow -> mDeleteItemsWindow!!.dismiss()

            // 重命名视频或给视频目录取别名
            R.id.bt_rename -> {
                showRenameItemDialog(checkedItems?.get(0) ?: return)
                mItemOptionsWindow!!.dismiss()
            }
            R.id.bt_complete_renameVideoListItemDialog -> {
                val editText = v.tag as EditText
                val text = editText.text.toString().trim()
                val newName =
                        if (text.isEmpty()) editText.hint as String
                        else text + editText.tag as String

                val window = mRenameItemDialog!!.window!!
                val decorView = window.decorView as ViewGroup
                val item = decorView.tag as VideoListItem
                @Suppress("UNCHECKED_CAST")
                val onRenameAction = decorView[0].tag as (() -> Unit)?

                mRenameItemDialog!!.cancel()

                if (renameVideoListItem(item, newName)) {
                    if (onRenameAction != null) {
                        onRenameAction()
                    } else {
                        val position = mVideoListItems.indexOf(item)
                        if (position != -1) {
                            val newPosition = mVideoListItems.reorder().indexOf(item)
                            if (newPosition == position) {
                                mAdapter.notifyItemChanged(position, PAYLOAD_REFRESH_ITEM_NAME)
                            } else {
                                mVideoListItems.add(newPosition, mVideoListItems.removeAt(position))
                                mAdapter.notifyItemRemoved(position)
                                mAdapter.notifyItemInserted(newPosition)
                                mAdapter.notifyItemRangeChanged(Math.min(position, newPosition),
                                        Math.abs(newPosition - position) + 1)
                            }
                        }
                    }
                }
            }
            R.id.bt_cancel_renameVideoListItemDialog -> mRenameItemDialog!!.cancel()

            // 分享
            R.id.bt_share -> {
                shareVideo(checkedItems?.get(0) as? Video ?: return)
                mItemOptionsWindow!!.dismiss()
            }

            // 显示视频（目录）详情
            R.id.bt_details -> {
                showItemDetailsDialog(checkedItems?.get(0) ?: return)
                mItemOptionsWindow!!.dismiss()
            }
            R.id.bt_confirm_videoListItemDetailsDialog -> mItemDetailsDialog!!.cancel()

            R.id.button_cancel_vow -> mItemOptionsWindow!!.dismiss()
            // 全（不）选
            R.id.bt_selectAll -> {
                // 全选
                if (SELECT_ALL == mSelectAllButton!!.text.toString()) {
                    for (i in mVideoListItems.indices) {
                        val item = mVideoListItems[i]
                        if (!item.isChecked) {
                            item.isChecked = true
                            mAdapter.notifyItemChanged(i, PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)
                        }
                    }
                    // 全不选
                } else {
                    for (item in mVideoListItems) {
                        item.isChecked = false
                    }
                    mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount,
                            PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)
                }
                onItemCheckedChange()
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.itemVisibleFrame -> {
                if (mItemOptionsWindow != null || mInteractionCallback.isRefreshLayoutRefreshing) {
                    return false
                }

                mTitleWindowFrame = View.inflate(mActivity,
                        R.layout.popup_window_main_title, null) as FrameLayout
                mTitleWindowFrame!!.post(object : Runnable {
                    init {
                        mTitleWindowFrame!!.findViewById<View>(R.id.button_cancel_vow)
                                .setOnClickListener(this@VideoListFragment)

                        mSelectAllButton = mTitleWindowFrame!!.findViewById(R.id.bt_selectAll)
                        mSelectAllButton!!.setOnClickListener(this@VideoListFragment)
                    }

                    override fun run() = mTitleWindowFrame?.run {
                        val statusHeight = App.getInstance(mContext).statusHeightInPortrait
                        layoutParams.height = height + statusHeight
                        setPadding(paddingLeft, paddingTop + statusHeight, paddingRight, paddingBottom)
                    } ?: Unit
                })
                val pw = PopupWindow(mTitleWindowFrame,
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                pw.isClippingEnabled = false
                pw.animationStyle = R.style.WindowAnimations_TopPopupWindow
                pw.showAtLocation(v, Gravity.TOP, 0, 0)

                val contentView = View.inflate(mActivity,
                        R.layout.popup_window_videolist_options, null)
                mTitleText_IOW = contentView.findViewById(R.id.text_title)
                mDeleteButton_IOW = contentView.findViewById(R.id.bt_delete_vlow)
                mDeleteButton_IOW!!.setOnClickListener(this)
                mRenameButton_IOW = contentView.findViewById(R.id.bt_rename)
                mRenameButton_IOW!!.setOnClickListener(this)
                mShareButton_IOW = contentView.findViewById(R.id.bt_share)
                mShareButton_IOW!!.setOnClickListener(this)
                mDetailsButton_IOW = contentView.findViewById(R.id.bt_details)
                mDetailsButton_IOW!!.setOnClickListener(this)

                mItemOptionsWindow = PopupWindow(contentView,
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                mItemOptionsWindow!!.animationStyle = R.style.WindowAnimations_BottomPopupWindow
                mItemOptionsWindow!!.showAtLocation(v, Gravity.BOTTOM, 0, 0)

                contentView.post(object : Runnable {
                    val selection = v.tag as Int

                    init {
                        mInteractionCallback.setLightStatus(true)
                        mInteractionCallback.setSideDrawerEnabled(false)
                        mInteractionCallback.isRefreshLayoutEnabled = false
                        mRecyclerView.isItemScrollingEnabled = false
                    }

                    override fun run() {
                        val parent = mRecyclerView.parent as View
                        val lp = parent.layoutParams
                        lp.height = parent.height - contentView.height
                        parent.layoutParams = lp

                        val itemBottom = (v.parent as View).bottom
                        if (itemBottom <= lp.height) {
                            notifyItemsToShowCheckBoxes()
                        } else {
                            mRecyclerView.post {
                                // 使长按的itemView在RecyclerView高度改变后可见
                                mRecyclerView.scrollBy(0, itemBottom - lp.height)

                                notifyItemsToShowCheckBoxes()
                            }
                        }
                    }

                    fun notifyItemsToShowCheckBoxes() = mAdapter.run {
                        notifyItemRangeChanged(0, selection,
                                PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)
                        notifyItemRangeChanged(selection + 1, itemCount - selection - 1,
                                PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)
                        // 勾选当前长按的itemView
                        mVideoListItems[selection].isChecked = true
                        notifyItemChanged(selection,
                                PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)
                        onItemCheckedChange()
                    }
                })
                mItemOptionsWindow!!.setOnDismissListener {
                    mTitleWindowFrame = null
                    mSelectAllButton = null
                    pw.dismiss()

                    mItemOptionsWindow = null
                    mTitleText_IOW = null
                    mDeleteButton_IOW = null
                    mRenameButton_IOW = null
                    mShareButton_IOW = null
                    mDetailsButton_IOW = null

                    val parent = mRecyclerView.parent as View
                    val lp = parent.layoutParams
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                    parent.layoutParams = lp

                    for (item in mVideoListItems) {
                        item.isChecked = false
                    }
                    mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount,
                            PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)

                    mInteractionCallback.setLightStatus(false)
                    mInteractionCallback.setSideDrawerEnabled(true)
                    mInteractionCallback.isRefreshLayoutEnabled = true
                    mRecyclerView.isItemScrollingEnabled = true
                }
                return true
            }
        }
        return false
    }

    private fun onItemCheckedChange() {
        mItemOptionsWindow ?: return

        var firstCheckedItem: VideoListItem? = null
        var checkedItemCount = 0
        for (item in mVideoListItems) {
            if (item.isChecked) {
                checkedItemCount++
                if (checkedItemCount == 1) {
                    firstCheckedItem = item
                }
            }
        }

        if (checkedItemCount == mVideoListItems.size) {
            mSelectAllButton!!.text = SELECT_NONE
        } else {
            mSelectAllButton!!.text = SELECT_ALL
        }

        var aVideoCheckedOnly = false
        mTitleText_IOW!!.text = when (checkedItemCount) {
            0 -> EMPTY_STRING
            1 -> {
                val item = firstCheckedItem!!
                aVideoCheckedOnly = item is Video

                if (aVideoCheckedOnly) item.name else getString(R.string.someDirectory, item.name)
            }
            else -> getString(R.string.severalItemsHaveBeenSelected, checkedItemCount)
        }

        mDeleteButton_IOW!!.isEnabled = checkedItemCount >= 1
        val enabled = checkedItemCount == 1
        mRenameButton_IOW!!.isEnabled = enabled
        mShareButton_IOW!!.isEnabled = aVideoCheckedOnly
        mDetailsButton_IOW!!.isEnabled = enabled
    }

    private fun showDeleteItemDialog(item: VideoListItem, onDeleteAction: (() -> Unit)? = null) {
        val view = View.inflate(mActivity, R.layout.dialog_message, null)

        view.findViewById<TextView>(R.id.text_message).text = when (item) {
            is Video -> getString(R.string.areYouSureToDeleteSth, item.name)
            else /* is VideoDirectory */ -> getString(R.string.areYouSureToDeleteSomeVideoDir, item.name)
        }

        val cancel = view.findViewById<TextView>(R.id.bt_cancel)
        cancel.id = R.id.bt_cancel_deleteVideoListItemDialog
        cancel.setOnClickListener(this)
        val lp = cancel.layoutParams as RelativeLayout.LayoutParams
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            lp.addRule(RelativeLayout.START_OF, R.id.bt_confirm_deleteVideoListItemDialog)
        } else {
            lp.addRule(RelativeLayout.LEFT_OF, R.id.bt_confirm_deleteVideoListItemDialog)
        }

        val confirm = view.findViewById<TextView>(R.id.bt_confirm)
        confirm.id = R.id.bt_confirm_deleteVideoListItemDialog
        confirm.setOnClickListener(this)

        mDeleteItemDialog = AppCompatDialog(mActivity, R.style.DialogStyle_MinWidth_NoTitle)
        mDeleteItemDialog!!.setContentView(view)
        mDeleteItemDialog!!.setOnDismissListener { mDeleteItemDialog = null }
        mDeleteItemDialog!!.show()

        val window = mDeleteItemDialog!!.window!!
        val decorView = window.decorView as ViewGroup
        decorView.tag = item
        decorView[0].tag = onDeleteAction
    }

    private fun showDeleteItemWindow(items: Array<out VideoListItem>, onDeleteAction: (() -> Unit)? = null) {
        if (items.isEmpty()) return

        val view = View.inflate(mActivity, R.layout.popup_window_delete_videos, null) as ViewGroup
        view.findViewById<TextView>(R.id.text_message).text = if (items.size == 1) {
            when (items[0]) {
                is Video -> getString(R.string.areYouSureToDeleteSth, items[0].name)
                else /* is VideoDirectory */ -> getString(R.string.areYouSureToDeleteSomeVideoDir, items[0].name)
            }
        } else {
            getString(R.string.areYouSureToDelete)
        }
        view.findViewById<View>(R.id.bt_confirm_deleteVideosWindow).setOnClickListener(this)
        view.findViewById<View>(R.id.bt_cancel_deleteVideosWindow).setOnClickListener(this)
        view.tag = items
        view[0].tag = onDeleteAction

        val fadedContentView = mActivity.window.decorView as FrameLayout

        mDeleteItemsWindow = PopupWindow(view,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mDeleteItemsWindow!!.isTouchable = true
        mDeleteItemsWindow!!.isFocusable = true
        // 这是必须的，否则'setFocusable'将无法在Android 6.0以下运行
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mDeleteItemsWindow!!.setBackgroundDrawable(BitmapDrawable(resources, null as Bitmap?))
        }
        mDeleteItemsWindow!!.showAtLocation(fadedContentView, Gravity.BOTTOM, 0, 0)
        mDeleteItemsWindow!!.setOnDismissListener {
            mDeleteItemsWindow = null

            mTitleWindowFrame?.foreground = null
            fadedContentView.foreground = null
        }

        mTitleWindowFrame?.foreground = ColorDrawable(0x7F000000)
        fadedContentView.foreground = ColorDrawable(0x7F000000)
    }

    private fun showRenameItemDialog(item: VideoListItem, onRenameAction: (() -> Unit)? = null) {
        val name = item.name
        val postfix: String = when (item) {
            is Video -> {
                val index = name.lastIndexOf(".")
                if (index == -1) EMPTY_STRING else name.substring(index)
            }
            else /* is VideoDirectory */ -> EMPTY_STRING
        }

        val view = View.inflate(mActivity, R.layout.dialog_rename_video_list_item, null)

        val titleText = view.findViewById<TextView>(R.id.text_title)
        titleText.setText(if (item is Video) R.string.renameVideo else R.string.renameDirectory)

        val thumb = VideoUtils2.generateMiniThumbnail(mContext,
                (item as? Video)?.path ?: (item as VideoDirectory).videos[0].path)
        view.findViewById<ImageView>(R.id.image_videoListItem).setImageBitmap(thumb)

        val editText = view.findViewById<EditText>(R.id.editor_rename)
        editText.hint = name
        editText.setText(name.replace(postfix, EMPTY_STRING))
        editText.setSelection(editText.text.length)
        editText.post { UiUtils.showSoftInput(editText) }
        editText.tag = postfix

        val cancelButton = view.findViewById<TextView>(R.id.bt_cancel_renameVideoListItemDialog)
        cancelButton.setOnClickListener(this)

        val completeButton = view.findViewById<TextView>(R.id.bt_complete_renameVideoListItemDialog)
        completeButton.setOnClickListener(this)
        completeButton.tag = editText

        mRenameItemDialog = AppCompatDialog(mActivity, R.style.DialogStyle_MinWidth_NoTitle)
        mRenameItemDialog!!.setContentView(view)
        mRenameItemDialog!!.show()
        mRenameItemDialog!!.setCancelable(true)
        mRenameItemDialog!!.setCanceledOnTouchOutside(false)
        mRenameItemDialog!!.setOnDismissListener {
            mRenameItemDialog = null
            thumb?.recycle()
        }

        val window = mRenameItemDialog!!.window!!
        val decorView = window.decorView as ViewGroup
        decorView.tag = item
        decorView[0].tag = onRenameAction
    }

    private fun renameVideoListItem(item: VideoListItem, newName: String) = when (item) {
        is Video -> renameVideo(item, newName)
        is VideoDirectory -> {
            // 如果名称没有变化
            if (newName == item.name) {
                false
            } else {
                item.name = newName
                if (VideoDaoHelper.getInstance(mContext).updateVideoDir(item)) {
                    Toast.makeText(mActivity, R.string.renameSuccessful, Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Toast.makeText(mActivity, R.string.renameFailed, Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
        else -> false
    }

    private fun showItemDetailsDialog(item: VideoListItem) {
        val view: View
        val thumb: Bitmap?
        val colon = getString(R.string.colon)
        var ss: SpannableString
        if (item is Video) {
            view = View.inflate(mActivity, R.layout.dialog_video_details, null)

            thumb = VideoUtils2.generateMiniThumbnail(mContext, item.path)

            val videoNameText = view.findViewById<TextView>(R.id.text_videoName)
            videoNameText.setCompoundDrawablesWithIntrinsicBounds(
                    null, BitmapDrawable(resources, thumb), null, null)
            ss = SpannableString(getString(R.string.name, item.name))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoNameText.text = ss

            val videoSizeText = view.findViewById<TextView>(R.id.text_videoSize)
            ss = SpannableString(getString(
                    R.string.size, FileUtils2.formatFileSize(item.size.toDouble())))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoSizeText.text = ss

            val videoResolutionText = view.findViewById<TextView>(R.id.text_videoResolution)
            ss = SpannableString(getString(
                    R.string.resolution, VideoUtils2.formatVideoResolution(item.width, item.height)))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoResolutionText.text = ss

            val videoPathText = view.findViewById<TextView>(R.id.text_videoPath)
            ss = SpannableString(getString(R.string.path, item.path))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoPathText.text = ss
        } else /* if (item is VideoDirectory) */ {
            view = View.inflate(mActivity, R.layout.dialog_videodir_details, null)

            val videos = (item as VideoDirectory).videos

            thumb = VideoUtils2.generateMiniThumbnail(mContext, videos[0].path)

            val path = item.path
            val dirname = FileUtils.getFileNameFromFilePath(path)

            val videodirNameText = view.findViewById<TextView>(R.id.text_videodirName)
            videodirNameText.setCompoundDrawablesWithIntrinsicBounds(null,
                    BitmapDrawable(resources, thumb), null, null)
            ss = SpannableString(getString(
                    if (item.name.equals(dirname, ignoreCase = true)) R.string.name
                    else R.string.alias
                    , item.name))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videodirNameText.text = ss

            val videodirSizeText = view.findViewById<TextView>(R.id.text_videodirSize)
            ss = SpannableString(getString(
                    R.string.size, FileUtils2.formatFileSize(item.size.toDouble())))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videodirSizeText.text = ss

            val videoCountText = view.findViewById<TextView>(R.id.text_videoCount)
            ss = SpannableString(getString(R.string.videoCount, videos.size))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoCountText.text = ss

            val videodirPathText = view.findViewById<TextView>(R.id.text_videodirPath)
            ss = SpannableString(getString(R.string.path, path))
            ss.setSpan(ForegroundColorSpan(Color.BLACK),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videodirPathText.text = ss
        }
        val confirmButton = view.findViewById<TextView>(R.id.bt_confirm_videoListItemDetailsDialog)
        confirmButton.setOnClickListener(this)

        mItemDetailsDialog = AppCompatDialog(mActivity, R.style.DialogStyle_MinWidth_NoTitle)
        mItemDetailsDialog!!.setContentView(view)
        mItemDetailsDialog!!.show()
        mItemDetailsDialog!!.setOnDismissListener {
            mItemDetailsDialog = null
            thumb?.recycle()
        }
    }

    interface InteractionCallback : RefreshLayoutCallback {
        fun setLightStatus(light: Boolean)

        fun setSideDrawerEnabled(enabled: Boolean)

        fun goToFoldedVideosFragment(args: Bundle): Fragment
    }
}