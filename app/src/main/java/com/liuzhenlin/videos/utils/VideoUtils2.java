/*
 * Created on 2017/09/30.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.liuzhenlin.texturevideoview.utils.BitmapUtils;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.model.Video;

/**
 * @author 刘振林
 */
public class VideoUtils2 {
    private VideoUtils2() {
    }

    public static void loadVideoThumbnail(@NonNull ImageView image, @NonNull Video video) {
        Context context = image.getContext();
        Resources res = context.getResources();

        final float aspectRatio = (float) video.getWidth() / (float) video.getHeight();
        final int thumbWidth = App.getInstance().getVideoThumbWidth();
        final int height = (int) ((float) thumbWidth / aspectRatio + 0.5f);
        final int maxHeight = (int) (thumbWidth * 9f / 16f + 0.5f);
        final int thumbHeight = height > maxHeight ? maxHeight : height;

        Bitmap bitmap = BitmapUtils.createScaledBitmap(
                BitmapFactory.decodeResource(res, R.drawable.ic_default_image),
                thumbWidth, thumbHeight, true);
        Glide.with(context)
                .load(video.getPath())
                .override(thumbWidth, thumbHeight)
                .centerCrop()
                .placeholder(new BitmapDrawable(res, bitmap))
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
    public static String formatVideoResolution(int width, int height) {
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
    public static String concatVideoProgressAndDuration(int progress, int duration) {
        final StringBuilder result = new StringBuilder();

        final boolean chinese = "zh".equals(
                App.getInstance().getResources().getConfiguration().locale.getLanguage());

        final String haveFinishedWatching = chinese ? "已看完" : "Have finished watching";
        final String separator = " | ";
        final String wordSeparator = chinese ? "" : " ";
        final String watchedTo = chinese ? "观看至" : "Watched to";
        final String hoursPlural = chinese ? "小时" : "hours";
        final String minutesPlural = chinese ? "分钟" : "minutes";
        final String slong = chinese ? "时长" : "long";
        final String lessThanAMinute = chinese ? "小于1分钟" : "Less than a minute";

        if (progress >= duration) {
            result.append(haveFinishedWatching).append(separator);
        } else {
            final int totalSeconds = progress / 1000;
            final int minutes = (totalSeconds / 60) % 60;
            final int hours = totalSeconds / 3600;

            final String shours = chinese ? "小时" : hours > 1 ? "hours" : "hour";
            final String sminutes = chinese ? "分钟" : minutes > 1 ? "minutes" : "minute";

            if (hours > 0) {
                result.append(watchedTo)
                        .append(wordSeparator).append(hours).append(wordSeparator).append(shours);
                if (minutes > 0) {
                    result.append(wordSeparator).append(minutes).append(wordSeparator).append(sminutes);
                }
                result.append(separator);

            } else if (minutes > 0) {
                result.append(watchedTo)
                        .append(wordSeparator).append(minutes).append(wordSeparator).append(sminutes)
                        .append(separator);
            }
        }

        final int totalSeconds = duration / 1000;
        final int minutes = (totalSeconds / 60) % 60;
        final int hours = totalSeconds / 3600;

        final String shours = chinese ? "小时" : hours > 1 ? "hours" : "hour";
        final String sminutes = chinese ? "分钟" : minutes > 1 ? "minutes" : "minute";

        if (hours > 0) {
            if (chinese) {
                result.append(slong)
                        .append(wordSeparator).append(hours).append(wordSeparator).append(shours);
            } else {
                result.append(hours).append(wordSeparator).append(minutes > 0 ? shours : hoursPlural);
            }
            if (minutes > 0) {
                result.append(wordSeparator).append(minutes).append(wordSeparator).append(minutesPlural);
            }
            if (!chinese) {
                result.append(wordSeparator).append(slong);
            }
        } else if (minutes > 0) {
            if (chinese) {
                result.append(slong)
                        .append(wordSeparator).append(minutes).append(wordSeparator).append(sminutes);
            } else {
                result.append(minutes).append(wordSeparator).append(minutesPlural).append(wordSeparator)
                        .append(slong);
            }
        } else {
            result.append(lessThanAMinute);
        }

        return result.toString();
    }
}
