/*
 * Created on 10/19/19 7:13 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.content.Intent
import android.net.Uri
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
import com.liuzhenlin.videos.R
import com.liuzhenlin.videos.utils.UiUtils
import com.liuzhenlin.videos.view.activity.VideoActivity

/**
 * @author 刘振林
 */
class OnlineVideoFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_online_video, container, false)
        val til: TextInputLayout = view.findViewById(R.id.textinput_videolink)
        view.findViewById<Button>(R.id.btn_ok).setOnClickListener {
            val link = til.editText!!.text.trim().toString()
            if (TextUtils.isEmpty(link)) {
                UiUtils.showUserCancelableSnackbar(view,
                        R.string.pleaseInputVideoLinkFirst, Snackbar.LENGTH_SHORT)
                return@setOnClickListener
            }
            if (link.matches(Patterns.WEB_URL.toRegex())) {
                startActivity(
                        Intent(activity ?: context, VideoActivity::class.java)
                                .setData(Uri.parse(link)))
            } else {
                UiUtils.showUserCancelableSnackbar(view,
                        R.string.illegalInputLink, Snackbar.LENGTH_SHORT)
            }
        }
        return view
    }
}
