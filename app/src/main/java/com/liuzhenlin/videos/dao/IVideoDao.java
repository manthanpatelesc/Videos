/*
 * Created on 2017/12/07.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.dao;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.videos.model.Video;
import com.liuzhenlin.videos.model.VideoDirectory;

/**
 * @author 刘振林
 */
public interface IVideoDao {
    Uri VIDEO_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    String VIDEO_ID = MediaStore.Video.Media._ID;
    String VIDEO_NAME = MediaStore.Video.Media.DISPLAY_NAME;
    String VIDEO_PATH = MediaStore.Video.Media.DATA;
    String VIDEO_SIZE = MediaStore.Video.Media.SIZE;
    String VIDEO_DURATION = MediaStore.Video.Media.DURATION;
    String VIDEO_RESOLUTION = MediaStore.Video.Media.RESOLUTION;

    boolean insertVideo(@NonNull Video video);
    boolean deleteVideo(long id);
    boolean updateVideo(@NonNull Video video);
    @Nullable
    Video queryVideoByPath(@Nullable String path);
    @Nullable
    Video queryVideoById(long id);
    Cursor queryAllVideos();
    @Nullable
    Cursor queryAllVideosInDirectory(@Nullable String directory);

    boolean insertVideoDir(@NonNull VideoDirectory videodir);
    boolean deleteVideoDir(@Nullable String path);
    boolean updateVideoDir(@NonNull VideoDirectory videodir);
    @Nullable
    VideoDirectory queryVideoDirByPath(@Nullable String path);
}
