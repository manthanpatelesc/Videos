/*
 * Created on 3/11/19 7:49 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videoplayer.view.fragment;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * @author 刘振林
 */
public interface FragmentPartLifecycleCallback {
    void onFragmentAttached(@NonNull Fragment fragment);

    void onFragmentViewCreated(@NonNull Fragment fragment);

    void onFragmentViewDestroyed(@NonNull Fragment fragment);

    void onFragmentDetached(@NonNull Fragment fragment);
}
