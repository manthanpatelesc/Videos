/*
 * Created on 3/18/19 8:45 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */
@file:JvmName("Consts")

package com.liuzhenlin.videos

import androidx.core.content.ContextCompat

/**
 * @author 刘振林
 */

const val NO_ID = -1L

internal const val KEY_DIRECTORY_PATH = "path_directory"
internal const val KEY_VIDEODIR = "videodir"
internal const val KEY_VIDEO = "video"
internal const val KEY_VIDEOS = "videos"
internal const val KEY_SELECTION = "index"

internal const val REQUEST_CODE_PLAY_VIDEO = 3
internal const val RESULT_CODE_PLAY_VIDEO = 3

internal const val REQUEST_CODE_PLAY_VIDEOS = 4
internal const val RESULT_CODE_PLAY_VIDEOS = 4

internal const val REQUEST_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT = 5
internal const val RESULT_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT = 5

internal const val REQUEST_CODE_LOCAL_FOLDED_VIDEOS_FRAGMENT = 6
internal const val RESULT_CODE_LOCAL_FOLDED_VIDEOS_FRAGMENT = 6

internal const val REQUEST_CODE_ADD_PICTURE = 7
internal const val RESULT_CODE_ADD_PICTURE = 7

const val EMPTY_STRING = ""
@JvmField
val EMPTY_STRING_ARRAY = arrayOf<String>()

private val _COLOR_SELECTOR by lazy(LazyThreadSafetyMode.NONE) { ContextCompat.getColor(App.getInstanceUnsafe()!!, R.color.selectorColor) }
@JvmField
internal val COLOR_SELECTOR = _COLOR_SELECTOR

private val _COLOR_ACCENT by lazy(LazyThreadSafetyMode.NONE) { ContextCompat.getColor(App.getInstanceUnsafe()!!, R.color.colorAccent) }
@JvmField
internal val COLOR_ACCENT = _COLOR_ACCENT
