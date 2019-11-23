/*
 * Created on 10/19/19 7:13 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout
import com.liuzhenlin.videos.App
import com.liuzhenlin.videos.R
import com.liuzhenlin.videos.utils.UiUtils
import com.liuzhenlin.videos.view.activity.VideoActivity

/**
 * @author 刘振林
 */
class OnlineVideoFragment : Fragment(), SlidingDrawerLayout.OnDrawerScrollListener {

    private lateinit var mActivity: Activity
    private lateinit var mContext: Context

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = context as Activity
        mContext = context.applicationContext
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_online_video, container, false)
        initViews(view)
        return view
    }

    private fun initViews(contentView: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val statusHeight = App.getInstance(mContext).statusHeightInPortrait
            val statusbarView = contentView.findViewById<View>(R.id.view_statusBar)
            if (statusbarView.layoutParams.height != statusHeight) {
                statusbarView.layoutParams.height = statusHeight
            }
        }

        val til: TextInputLayout = contentView.findViewById(R.id.textinput_videolink)
        contentView.findViewById<Button>(R.id.btn_ok).setOnClickListener {
            val link = til.editText!!.text.trim().toString()
            if (TextUtils.isEmpty(link)) {
                UiUtils.showUserCancelableSnackbar(contentView,
                        R.string.pleaseInputVideoLinkFirst, Snackbar.LENGTH_SHORT)
                return@setOnClickListener
            }
            if (link.matches(Patterns.WEB_URL.toRegex())) {
                startActivity(
                        Intent(activity ?: context, VideoActivity::class.java)
                                .setData(Uri.parse(link)))
            } else {
                UiUtils.showUserCancelableSnackbar(contentView,
                        R.string.illegalInputLink, Snackbar.LENGTH_SHORT)
            }
        }
    }

    override fun onDrawerOpened(parent: SlidingDrawerLayout, drawer: View) {
    }

    override fun onDrawerClosed(parent: SlidingDrawerLayout, drawer: View) {
    }

    override fun onScrollPercentChange(parent: SlidingDrawerLayout, drawer: View, percent: Float) {
    }

    override fun onScrollStateChange(parent: SlidingDrawerLayout, drawer: View, state: Int) {
        when (state) {
            SlidingDrawerLayout.SCROLL_STATE_TOUCH_SCROLL,
            SlidingDrawerLayout.SCROLL_STATE_AUTO_SCROLL -> {
                val window = mActivity.window
                if (UiUtils.isSoftInputShown(window)) {
                    UiUtils.hideSoftInput(window)
                }

                view!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            SlidingDrawerLayout.SCROLL_STATE_IDLE ->
                view!!.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
}
