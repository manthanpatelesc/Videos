/*
 * Created on 11/3/19 2:23 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.R;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppUpdateChecker {

    public interface OnResultListener {
        void onResult(boolean findNewVersion);
    }

    private static final int TIMEOUT_CONNECTION = 10 * 1000; // ms
    private static final int TIMEOUT_READ = 60 * 1000; // ms

    private static final String LINK_APP_INFOS =
            "https://raw.githubusercontent.com/freeze-frames/Videos/release/app.json";

    private String mAppName;
    private String mVersionName;
    private String mAppLink;
    private StringBuilder mUpdateLog;

    private Dialog mUpdateDialog;

    private final Context mContext;
    private Handler mHandler;
    private boolean mToastResult;
    private boolean mCheckInProgress;

    private List<OnResultListener> mListeners;

    private static final String EXTRA_MESSENGER = "extra_messenger";
    private static final String EXTRA_APP_NAME = "extra_appName";
    private static final String EXTRA_VERSION_NAME = "extra_versionName";
    private static final String EXTRA_APP_LINK = "extra_appLink";
    private Intent mServiceIntent;

    private static volatile AppUpdateChecker sInstance;

    private AppUpdateChecker(Context context) {
        mContext = context.getApplicationContext();
    }

    @NonNull
    public static AppUpdateChecker getInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (AppUpdateChecker.class) {
                if (sInstance == null) {
                    sInstance = new AppUpdateChecker(context);
                }
            }
        }
        return sInstance;
    }

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler();
        }
        return mHandler;
    }

    private boolean hasOnResultListener() {
        return mListeners != null && !mListeners.isEmpty();
    }

    // 注：添加后请记得移除，以免引起内存泄漏
    public void addOnResultListener(@Nullable OnResultListener listener) {
        if (listener != null) {
            if (mListeners == null) {
                mListeners = new ArrayList<>(1);
            }
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void removeOnResultListener(@Nullable OnResultListener listener) {
        if (listener != null && hasOnResultListener()) {
            mListeners.remove(listener);
        }
    }

    public void clearOnResultListeners() {
        if (hasOnResultListener()) {
            mListeners.clear();
        }
    }

    public void checkUpdate() {
        checkUpdate(true);
    }

    @SuppressLint("StaticFieldLeak")
    public void checkUpdate(boolean toastResult) {
        mToastResult = toastResult;

        if (mCheckInProgress) return;
        mCheckInProgress = true;
        new AsyncTask<Void, Void, Integer>() {
            static final int RESULT_FIND_NEW_VERSION = 1;
            static final int RESULT_NO_NEW_VERSION = 2;
            static final int RESULT_CONNECTION_TIMEOUT = 3;
            static final int RESULT_READ_TIMEOUT = 4;

            @Override
            protected Integer doInBackground(Void... voids) {
                StringBuilder json = null;

                HttpURLConnection conn = null;
                BufferedReader reader = null;
                try {
                    URL url = new URL(LINK_APP_INFOS);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_CONNECTION);
                    conn.setReadTimeout(TIMEOUT_READ);

                    reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"));
                    final char[] buffer = new char[1024];
                    int len;
                    while ((len = reader.read(buffer)) != -1) {
                        if (json == null) {
                            json = new StringBuilder(len);
                        }
                        json.append(buffer, 0, len);
                    }
                    Objects.requireNonNull(json);

                    // 连接服务器超时
                } catch (ConnectTimeoutException e) {
                    return RESULT_CONNECTION_TIMEOUT;

                    // 读取数据超时
                } catch (SocketTimeoutException e) {
                    return RESULT_READ_TIMEOUT;

                } catch (IOException e) {
                    e.printStackTrace();
                    return 0;

                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            //
                        }
                    }
                    if (conn != null) {
                        conn.disconnect();
                    }
                }

                Gson gson = new Gson();
                Map<String, Object> infos = gson.fromJson(json.toString(),  //@formatter:off
                            new TypeToken<Map<String, Object>>() {}.getType()); //@formatter:on
                //noinspection unchecked
                Map<String, Object> appInfos = (Map<String, Object>) infos.get("appInfos");

                //noinspection ConstantConditions
                final boolean findNewVersion =
                        (Double) appInfos.get("versionCode") > BuildConfig.VERSION_CODE;
                // 检测到版本更新
                if (findNewVersion) {
                    mAppName = (String) appInfos.get("appName");
                    mVersionName = (String) appInfos.get("versionName");
                    mAppLink = (String) appInfos.get("appLink");
                    mUpdateLog = new StringBuilder();
                    //noinspection ConstantConditions
                    for (Object log : (List) appInfos.get("updateLogs")) {
                        mUpdateLog.append(log).append("\n");
                    }
                    mUpdateLog.deleteCharAt(mUpdateLog.length() - 1);
                }
                return findNewVersion ? RESULT_FIND_NEW_VERSION : RESULT_NO_NEW_VERSION;
            }

            @Override
            protected void onPostExecute(Integer result) {
                switch (result) {
                    case RESULT_FIND_NEW_VERSION:
                        getHandler().sendEmptyMessage(Handler.MSG_FIND_NEW_VERSION);
                        showUpdateDialog();
                        break;
                    case RESULT_NO_NEW_VERSION:
                        getHandler().sendEmptyMessage(Handler.MSG_NO_NEW_VERSION);
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.isTheLatestVersion, Toast.LENGTH_SHORT).show();
                        }
                        reset();
                        break;
                    case RESULT_CONNECTION_TIMEOUT:
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.connectionTimeout, Toast.LENGTH_SHORT).show();
                        }
                        reset();
                        break;
                    case RESULT_READ_TIMEOUT:
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.readTimeout, Toast.LENGTH_SHORT).show();
                        }
                    default:
                        reset();
                        break;
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * 弹出对话框，提醒用户更新
     */
    private void showUpdateDialog() {
        View view = View.inflate(mContext, R.layout.dialog_update_app, null);
        view.<TextView>findViewById(R.id.text_updateLogTitle)
                .setText(mContext.getString(R.string.updateLog, mAppName, mVersionName));
        final TextView tv = view.findViewById(R.id.text_updateLog);
        tv.setText(mUpdateLog);
        tv.post(new Runnable() {
            @Override
            public void run() {
                TextViewUtils.setHangingIndents(tv, 4);
            }
        });
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    // 当点确定按钮时从服务器上下载新的apk，然后安装
                    case R.id.btn_confirm:
                        mUpdateDialog.cancel();
                        mUpdateDialog = null;
                        if (FileUtils2.isExternalStorageMounted()) {
                            mServiceIntent = new Intent(mContext, UpdateAppService.class)
                                    .putExtra(EXTRA_MESSENGER, new Messenger(getHandler()))
                                    .putExtra(EXTRA_APP_NAME, mAppName)
                                    .putExtra(EXTRA_VERSION_NAME, mVersionName)
                                    .putExtra(EXTRA_APP_LINK, mAppLink);
                            mContext.startService(mServiceIntent);
                        } else {
                            reset();
                            Toast.makeText(mContext, R.string.pleaseInsertSdCardOnYourDeviceFirst,
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                    // 当点取消按钮时不做任何举动
                    case R.id.btn_cancel:
                        mUpdateDialog.cancel();
                        reset();
                        break;
                }
            }
        };
        view.findViewById(R.id.btn_cancel).setOnClickListener(listener);
        view.findViewById(R.id.btn_confirm).setOnClickListener(listener);

        mUpdateDialog = new AppCompatDialog(mContext, R.style.DialogStyle_MinWidth_NoTitle);
        Objects.requireNonNull(mUpdateDialog.getWindow()).setType(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mUpdateDialog.setContentView(view);
        mUpdateDialog.setCancelable(false);
        mUpdateDialog.setCanceledOnTouchOutside(false);
        mUpdateDialog.show();
    }

    private void reset() {
        mAppName = null;
        mVersionName = null;
        mAppLink = null;
        mUpdateLog = null;
        mUpdateDialog = null;
        mCheckInProgress = false;
        mServiceIntent = null;
    }

    @SuppressLint("HandlerLeak")
    private final class Handler extends android.os.Handler {
        static final int MSG_STOP_UPDATE_APP_SERVICE = -1;
        static final int MSG_NO_NEW_VERSION = 0;
        static final int MSG_FIND_NEW_VERSION = 1;

        @Override
        public void handleMessage(@NonNull Message msg) {
            final int what = msg.what;
            switch (what) {
                case MSG_STOP_UPDATE_APP_SERVICE:
                    if (mServiceIntent != null) {
                        mContext.stopService(mServiceIntent);
                        reset();
                    }
                    break;
                case MSG_NO_NEW_VERSION:
                case MSG_FIND_NEW_VERSION:
                    if (hasOnResultListener()) {
                        for (int i = mListeners.size() - 1; i >= 0; i--) {
                            mListeners.get(i).onResult(what != MSG_NO_NEW_VERSION);
                        }
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    }

    @VisibleForTesting
    public static final class UpdateAppService extends Service {
        private UpdateAppTask mTask;

        private static final int INDEX_APP_NAME = 0;
        private static final int INDEX_VERSION_NAME = 1;
        private static final int INDEX_APP_LINK = 2;

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            mTask = new UpdateAppTask(this,
                    intent.getParcelableExtra(AppUpdateChecker.EXTRA_MESSENGER));
            mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    intent.getStringExtra(AppUpdateChecker.EXTRA_APP_NAME),
                    intent.getStringExtra(AppUpdateChecker.EXTRA_VERSION_NAME),
                    intent.getStringExtra(AppUpdateChecker.EXTRA_APP_LINK));
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mTask != null) {
                mTask.cancel();
            }
        }

        @VisibleForTesting
        public static final class CancelAppUpdateReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Messenger messenger = intent.getParcelableExtra(AppUpdateChecker.EXTRA_MESSENGER);
                Message msg = Message.obtain();
                msg.what = Handler.MSG_STOP_UPDATE_APP_SERVICE;
                try {
                    Objects.requireNonNull(messenger).send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        private static final class UpdateAppTask extends AsyncTask<String, Void, Void> {
            @SuppressLint("StaticFieldLeak")
            final UpdateAppService mService;
            final Messenger mMessenger;

            @SuppressLint("StaticFieldLeak")
            final Context mContext;
            final String mPkgName;
            final CharSequence mAppLabel;

            static NotificationManager sNotificationManager;
            NotificationCompat.Builder mNotificationBuilder;
            PendingIntent mCancelPendingIntent;

            static android.os.Handler sHandler;
            static final Runnable sCancelNotificationRunnable = new Runnable() {
                @Override
                public void run() {
                    sNotificationManager.cancel(ID_NOTIFICATION);
                }
            };

            static final String TAG_NOTIFICATION =
                    "notification_AppUpdateChecker$UpdateAppService$UpdateAppTask";
            static final int ID_NOTIFICATION;

            static {
                int count = 0;
                for (int i = TAG_NOTIFICATION.length() - 1; i >= 0; i--) {
                    count += TAG_NOTIFICATION.charAt(i);
                }
                ID_NOTIFICATION = count;
            }

            static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
            // We want at least 2 threads and at most 4 threads to download the new apk,
            // preferring to have 1 less than the CPU count to avoid saturating the CPU
            // with background work
            static final int COUNT_DOWNLOAD_APP_TASK = Math.max(2, Math.min(CPU_COUNT - 1, 4));

            List<DownloadAppTask> mDownloadAppTasks;
            String mAppLink;
            File mApk;
            int mApkLength = -1;
            final AtomicInteger mProgress = new AtomicInteger();

            UpdateAppTask(UpdateAppService service, Messenger messenger) {
                mService = service;
                mMessenger = messenger;
                mContext = service.getApplicationContext();
                mPkgName = service.getPackageName();
                mAppLabel = service.getApplicationInfo().loadLabel(service.getPackageManager());
            }

            @Override
            protected void onPreExecute() {
                if (sNotificationManager == null) {
                    sNotificationManager = (NotificationManager)
                            mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel channel = new NotificationChannel(
                                mPkgName, mAppLabel, NotificationManager.IMPORTANCE_DEFAULT);
                        channel.enableLights(true);
                        channel.enableVibration(false);
                        sNotificationManager.createNotificationChannel(channel);
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // 应用名可能会改变...
                        sNotificationManager.getNotificationChannel(mPkgName).setName(mAppLabel);
                    }

                    if (sHandler != null) {
                        sHandler.removeCallbacks(sCancelNotificationRunnable);
                    }
                }
                RemoteViews nv = createNotificationView();
                mNotificationBuilder = new NotificationCompat.Builder(mContext, mPkgName)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setCustomContentView(nv)
                        .setCustomBigContentView(nv)
                        .setOnlyAlertOnce(true)
                        .setDefaults(Notification.DEFAULT_LIGHTS)
                        .setTicker(mContext.getString(R.string.downloadingUpdates));
                mService.startForeground(ID_NOTIFICATION, mNotificationBuilder.build());
            }

            private RemoteViews createNotificationView() {
                if (mCancelPendingIntent == null) {
                    mCancelPendingIntent = PendingIntent.getBroadcast(mContext,
                            0,
                            new Intent(mContext, CancelAppUpdateReceiver.class)
                                    .putExtra(EXTRA_MESSENGER, mMessenger),
                            PendingIntent.FLAG_ONE_SHOT);
                }
                RemoteViews nv = new RemoteViews(mPkgName, R.layout.notification_view_download_app);
                nv.setOnClickPendingIntent(R.id.btn_cancel_danv, mCancelPendingIntent);
                return nv;
            }

            @Override
            protected Void doInBackground(String... strings) {
                HttpURLConnection conn = null;
                try {
                    mAppLink = strings[INDEX_APP_LINK];
                    URL url = new URL(mAppLink);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_CONNECTION);
                    conn.setReadTimeout(TIMEOUT_READ);

                    final int length = conn.getContentLength();
                    //noinspection StatementWithEmptyBody
                    if (isCancelled()) {

                    } else if (length <= 0) {
                        onDownloadError();

                    } else if (FileUtils2.hasEnoughStorageOnDisk(length)) {
                        final String dirPath = App.getAppDirectory();
                        File dir = new File(dirPath);
                        if (!dir.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            dir.mkdirs();
                        }
                        mApk = new File(dir,
                                strings[INDEX_APP_NAME] + " "
                                        + strings[INDEX_VERSION_NAME].replace(".", "_")
                                        + ".apk");
                        mApkLength = length;
                    } else {
                        getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mContext,
                                        R.string.notHaveEnoughStorage, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (ConnectTimeoutException e) {
                    onConnectionTimeout();
                } catch (SocketTimeoutException e) {
                    onReadTimeout();
                } catch (IOException e) {
                    e.printStackTrace();
                    onDownloadError();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (mApkLength <= 0) {
                    cancel();
                } else if (isCancelled()) {
                    deleteApk();
                } else {
                    final int blockSize = mApkLength / COUNT_DOWNLOAD_APP_TASK;
                    mDownloadAppTasks = new ArrayList<>(COUNT_DOWNLOAD_APP_TASK);
                    for (int i = 0; i < COUNT_DOWNLOAD_APP_TASK; i++) {
                        final int start = i * blockSize;
                        final int end = i == COUNT_DOWNLOAD_APP_TASK - 1 ?
                                mApkLength : (i + 1) * blockSize - 1;
                        mDownloadAppTasks.add(new DownloadAppTask());
                        mDownloadAppTasks.get(i)
                                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, start, end);
                    }
                }
            }

            void cancel() {
                cancel(false);
                if (mDownloadAppTasks != null) {
                    for (DownloadAppTask task : mDownloadAppTasks) {
                        task.cancel(false);
                    }
                }

                deleteApk();
                release();

                // 确保不再有任何通知被弹出...
                android.os.Handler handler = getHandler();
                handler.postDelayed(sCancelNotificationRunnable, 100);
            }

            private void deleteApk() {
                if (mApk != null) {
                    //noinspection ResultOfMethodCallIgnored
                    mApk.delete();
                }
            }

            private void release() {
                mService.mTask = null;

                Message msg = Message.obtain();
                msg.what = Handler.MSG_STOP_UPDATE_APP_SERVICE;
                try {
                    mMessenger.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            android.os.Handler getHandler() {
                if (sHandler == null) {
                    sHandler = sInstance.mHandler;
                }
                return sHandler;
            }

            private void onConnectionTimeout() {
                if (!isCancelled()) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            cancel();
                            Toast.makeText(mContext,
                                    R.string.connectionTimeout, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            private void onReadTimeout() {
                if (!isCancelled()) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            cancel();
                            Toast.makeText(mContext,
                                    R.string.readTimeout, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            private void onDownloadError() {
                if (!isCancelled()) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            cancel();
                            Toast.makeText(mContext,
                                    R.string.downloadError, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @SuppressLint("StaticFieldLeak")
            final class DownloadAppTask extends AsyncTask<Integer, Integer, Void> {
                final UpdateAppTask mHost = UpdateAppTask.this;

                @Override
                protected Void doInBackground(Integer... indices) {
                    final int startIndex = indices[0];
                    final int endIndex = indices[1];

                    HttpURLConnection conn = null;
                    InputStream in = null;
                    RandomAccessFile out = null;
                    try {
                        URL url = new URL(mAppLink);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(TIMEOUT_CONNECTION);
                        conn.setReadTimeout(TIMEOUT_READ);
                        // 重要：请求服务器下载部分文件 指定文件的位置
                        conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                        // 从服务器请求全部资源返回 200 ok；从服务器请求部分资源返回 206 ok
                        // final int responseCode = conn.getResponseCode();

                        in = new BufferedInputStream(conn.getInputStream());

                        out = new RandomAccessFile(mApk, "rwd");
                        out.seek(startIndex);

                        int len;
                        final byte[] buffer = new byte[8 * 1024];
                        while (!mHost.isCancelled() && (len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);

                            if (!mHost.isCancelled()) {
//                                publishProgress(len);
                                notifyProgressUpdated(mProgress.addAndGet(len));
                            }
                        }
                    } catch (ConnectTimeoutException e) {
                        onConnectionTimeout();
                    } catch (SocketTimeoutException e) {
                        onReadTimeout();
                    } catch (IOException e) {
                        e.printStackTrace();
                        onDownloadError();
                    } finally {
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException e) {
                                //
                            }
                        }
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                //
                            }
                        }
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }

                    return null;
                }

                @Override
                protected void onProgressUpdate(Integer... values) {
//                    notifyProgressUpdated(mProgress += values[0]);
                }

                private void notifyProgressUpdated(int progress) {
                    RemoteViews nv = createNotificationView();
                    nv.setProgressBar(R.id.progress, mApkLength, progress, false);
                    nv.setTextViewText(R.id.text_progressPercent,
                            mContext.getString(R.string.progress,
                                    (float) progress / (float) mApkLength * 100f));
                    nv.setTextViewText(R.id.text_progressNumber,
                            mContext.getString(R.string.progressNumberOfUpdatingApp,
                                    FileUtils2.formatFileSize(progress),
                                    FileUtils2.formatFileSize(mApkLength)));

                    synchronized (mHost) {
                        mNotificationBuilder.setCustomContentView(nv);
                        mNotificationBuilder.setCustomBigContentView(nv);

                        Notification n = mNotificationBuilder.build();

                        // 确保下载被取消后不再有任何通知被弹出...
                        if (!mHost.isCancelled()) {
                            sNotificationManager.notify(ID_NOTIFICATION, n);
                        }
                    }
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    mDownloadAppTasks.remove(this);
                    if (mDownloadAppTasks.isEmpty()) {
                        release();
                        installApk(mApk);
                    }
                }
            }

            void installApk(File apk) {
                if (apk == null || !apk.exists() || apk.length() != mApkLength) {
                    Toast.makeText(mContext, R.string.theInstallationPackageHasBeenDestroyed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent it = new Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Android 7.0 共享文件需要通过 FileProvider 添加临时权限，否则系统会抛出 FileUriExposedException.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Uri contentUri = FileProvider.getUriForFile(
                            mContext, App.getInstance(mContext).getAuthority(), apk);
                    it.setDataAndType(contentUri, "application/vnd.android.package-archive");
                } else {
                    it.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
                }
                mContext.startActivity(it);
            }
        }
    }
}