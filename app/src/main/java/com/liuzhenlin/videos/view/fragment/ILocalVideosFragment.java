/*
 * Created on 11/6/19 12:13 AM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;

public interface ILocalVideosFragment extends OnBackPressedListener {
    void goToLocalFoldedVideosFragment(@NonNull Bundle args);
}
