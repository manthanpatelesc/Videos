/*
 * Created on 2017/09/30.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videoplayer.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.liuzhenlin.videoplayer.App;
import com.liuzhenlin.videoplayer.R;
import com.liuzhenlin.videoplayer.model.Video;

/**
 * @author 刘振林
 */
public class VideoUtils {
    private VideoUtils() {
    }

    public static void loadVideoThumbnail(@NonNull ImageView image, @NonNull Video video) {
        Context context = image.getContext();

        final float scale = (float) video.getWidth() / (float) video.getHeight();
        final int thumbWidth = App.getInstance().getVideoThumbWidth();
        final int height = (int) ((float) thumbWidth / scale + 0.5f);
        final int maxHeight = (int) (thumbWidth * 9f / 16f + 0.5f);
        final int thumbHeight = height > maxHeight ? maxHeight : height;

        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_default_image)
                .override(thumbWidth, thumbHeight)
                .centerCrop();
        Glide.with(context)
                .load(video.getPath())
                .apply(options)
                .into(image);
    }

    @Nullable
    public static Bitmap generateMiniThumbnail(@NonNull Context context, @Nullable String path) {
        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
        if (thumb != null) {
            final float ratio = context.getResources().getDisplayMetrics().widthPixels / 1080f;
            if (ratio != 1) {
                thumb = ThumbnailUtils.extractThumbnail(thumb,
                        (int) (thumb.getWidth() * ratio + 0.5f),
                        (int) (thumb.getHeight() * ratio + 0.5f),
                        ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            }
        }
        return thumb;
    }

    @NonNull
    public static String formatVideoScale(int width, int height) {
        if (width == 250 && height == 320 || height == 250 && width == 320) {
            return "250P";
        }
        if (width == 360 && height == 480 || height == 360 && width == 480) {
            return "360P";
        }
        if (width == 540 && height == 960 || height == 540 && width == 960) {
            return "540P";
        }
        if (width == 720 && height == 1280 || height == 720 && width == 1280) {
            return "720P";
        }
        if (width == 1080 && height == 1920 || height == 1080 && width == 1920) {
            return "1080P";
        }
        if (width == 1440 && height == 2560 || height == 1440 && width == 2560) {
            return "2K";
        }
        if (width == 2160 && height == 3840 || height == 2160 && width == 3840) {
            return "4K";
        }

        return width + " × " + height;
    }

    @NonNull
    public static String jointVideoProgressAndDuration(int progress, int duration) {
        String result;

        if (progress >= duration) {
            result = "已看完 | ";
        } else {
            final int totalSeconds = progress / 1000;
            final int minutes = (totalSeconds / 60) % 60;
            final int hours = totalSeconds / 3600;

            if (hours > 0) {
                result = "观看至" + hours + "小时";
                if (minutes > 0) {
                    result += minutes + "分钟";
                }
                result += " | ";

            } else if (minutes > 0) {
                result = "观看至" + minutes + "分钟 | ";
            } else {
                result = "";
            }
        }

        final int totalSeconds = duration / 1000;
        final int minutes = (totalSeconds / 60) % 60;
        final int hours = totalSeconds / 3600;
        if (hours > 0) {
            result += "时长" + hours + "小时";
            if (minutes > 0) {
                result += minutes + "分钟";
            }
        } else if (minutes > 0) {
            result += "时长" + minutes + "分钟";
        } else {
            result = "小于1分钟";
        }

        return result;
    }
}
