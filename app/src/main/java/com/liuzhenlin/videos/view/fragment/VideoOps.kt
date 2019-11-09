/*
 * Created on 2018/09/07.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.liuzhenlin.texturevideoview.utils.FileUtils
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.dao.VideoDaoHelper
import com.liuzhenlin.videos.model.Video
import com.liuzhenlin.videos.utils.UiUtils
import com.liuzhenlin.videos.view.activity.VideoActivity
import java.io.File
import java.util.*

/**
 * @author 刘振林
 */
private val sDeleteVideoTasks = ArrayDeque<AsyncTask<Unit, Unit, Unit>>()

private fun deleteVideosInternal(vararg videos: Video) {
    if (videos.isEmpty()) return

    sDeleteVideoTasks.offer(object : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg units: Unit) {
            val helper = VideoDaoHelper.getInstance(App.getInstanceUnsafe()!!)

            for ((id) in videos) {
                // videos[i]的路径可能已在主线程中被修改（重命名视频）
                val video = helper.queryVideoById(id)
                if (video != null) {
                    File(video.path).delete()
                }

                helper.deleteVideo(id)
            }
        }

        override fun onPostExecute(unit: Unit) {
            sDeleteVideoTasks.poll()
            sDeleteVideoTasks.peek()?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    })
    if (sDeleteVideoTasks.size == 1) {
        sDeleteVideoTasks.peek()!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }
}

interface VideoOpCallback {
    val isAsyncDeletingVideos get() = sDeleteVideoTasks.size > 0

    fun showDeleteVideoDialog(video: Video, onDeleteAction: () -> Unit)
    fun showDeleteVideosPopupWindow(vararg videos: Video, onDeleteAction: () -> Unit)
    fun showRenameVideoDialog(video: Video, onRenameAction: () -> Unit)
    fun showVideoDetailsDialog(video: Video)

    fun deleteVideos(vararg videos: Video) = deleteVideosInternal(*videos)

    fun renameVideo(video: Video, newName: String, view: View? = null): Boolean {
        // 如果名称没有变化
        if (newName == video.name) return false

        val context: Context = view?.context ?: App.getInstanceUnsafe()!!

        // 如果不存在该视频文件
        val file = File(video.path)
        if (!file.exists()) {
            if (view == null) {
                Toast.makeText(context, R.string.renameFailedForThisVideoDoesNotExist,
                        Toast.LENGTH_SHORT).show()
            } else {
                UiUtils.showUserCancelableSnackbar(view,
                        R.string.renameFailedForThisVideoDoesNotExist, Snackbar.LENGTH_SHORT)
            }
            return false
        }

        val newFile = File(file.parent, newName)
        // 该路径下存在相同名称的视频文件
        if (!newName.equals(video.name, ignoreCase = true) && newFile.exists()) {
            if (view == null) {
                Toast.makeText(context, R.string.renameFailedForThatDirectoryHasSomeFileWithTheSameName,
                        Toast.LENGTH_SHORT).show()
            } else {
                UiUtils.showUserCancelableSnackbar(view,
                        R.string.renameFailedForThatDirectoryHasSomeFileWithTheSameName,
                        Snackbar.LENGTH_SHORT)
            }
            return false
        }

        // 如果重命名失败
        if (!file.renameTo(newFile)) {
            if (view == null) {
                Toast.makeText(context, R.string.renameFailed,
                        Toast.LENGTH_SHORT).show()
            } else {
                UiUtils.showUserCancelableSnackbar(view,
                        R.string.renameFailed, Snackbar.LENGTH_SHORT)
            }
            return false
        }

        video.name = newName
        video.path = newFile.absolutePath
        return if (VideoDaoHelper.getInstance(context).updateVideo(video)) {
            if (view == null) {
                Toast.makeText(context, R.string.renameSuccessful,
                        Toast.LENGTH_SHORT).show()
            } else {
                UiUtils.showUserCancelableSnackbar(view,
                        R.string.renameSuccessful, Snackbar.LENGTH_SHORT)
            }
            true
        } else {
            if (view == null) {
                Toast.makeText(context, R.string.renameFailed,
                        Toast.LENGTH_SHORT).show()
            } else {
                UiUtils.showUserCancelableSnackbar(view,
                        R.string.renameFailed, Snackbar.LENGTH_SHORT)
            }
            false
        }
    }
}

fun Fragment.shareVideo(video: Video) {
    (activity ?: context).shareVideo(video)
}

fun Context?.shareVideo(video: Video) {
    val app = App.getInstanceUnsafe()!!
    FileUtils.shareFile(this ?: app, app.authority, File(video.path), "video/*")
}

fun Fragment.playVideo(video: Video) {
    startActivityForResult(
            Intent(activity ?: context, VideoActivity::class.java)
                    .putExtra(KEY_VIDEO, video),
            REQUEST_CODE_PLAY_VIDEO)
}

fun Activity.playVideo(video: Video) {
    startActivityForResult(
            Intent(this, VideoActivity::class.java)
                    .putExtra(KEY_VIDEO, video),
            REQUEST_CODE_PLAY_VIDEO)
}

fun Fragment.playVideos(vararg videos: Video, selection: Int) {
    if (videos.isEmpty()) return

    startActivityForResult(
            Intent(activity ?: context, VideoActivity::class.java)
                    .putExtra(KEY_VIDEOS, videos).putExtra(KEY_SELECTION, selection),
            REQUEST_CODE_PLAY_VIDEOS)
}

fun Activity.playVideos(vararg videos: Video, selection: Int) {
    if (videos.isEmpty()) return

    startActivityForResult(
            Intent(this, VideoActivity::class.java)
                    .putExtra(KEY_VIDEOS, videos).putExtra(KEY_SELECTION, selection),
            REQUEST_CODE_PLAY_VIDEOS)
}