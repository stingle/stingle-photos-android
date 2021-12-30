package org.stingle.photos.CameraX.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.video.VideoRecordEvent;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.GetThumbFromPlainFile;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.ShowLastThumbAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.CameraX.MediaEncryptService;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.lang.ref.WeakReference;

public class MediaSaveHelper implements LifecycleObserver {

    private static final String TAG = "Camera-MediaSaveHelper";

    private final WeakReference<AppCompatActivity> activityRef;

    private WeakReference<ImageView> imageViewRef;
    private final int thumbSize;
    private boolean isVideoRecording;
    private LocalBroadcastManager lbm;

    private final BroadcastReceiver onEncFinish = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showLastThumb();
        }
    };

    public MediaSaveHelper(AppCompatActivity activity) {
        activityRef = new WeakReference<>(activity);
        thumbSize = (int) Math.round(Helpers.getScreenWidthByColumns(activity) / 1.4);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private void onCreate() {
        lbm = LocalBroadcastManager.getInstance(activityRef.get());
        lbm.registerReceiver(onEncFinish, new IntentFilter("MEDIA_ENC_FINISH"));
        sendCameraStatusBroadcast(true, false);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private void onPause() {
        sendCameraStatusBroadcast(false, isVideoRecording);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private void onDestroy() {
        lbm.unregisterReceiver(onEncFinish);
    }

    public void setVideoRecording(boolean videoRecording) {
        isVideoRecording = videoRecording;
    }

    public void setImageViewRef(ImageView imageView) {
        this.imageViewRef = new WeakReference<>(imageView);
    }

    public void saveVideo(VideoRecordEvent event) {
        File file = new File(((VideoRecordEvent.Finalize) event).getOutputResults().getOutputUri().getPath());
        sendNewMediaBroadcast(file);
        if (!LoginManager.isKeyInMemory()) {
            getThumbIntoMemory(file, true);
        }
        showLastThumb();
        Log.d(TAG, ((VideoRecordEvent.Finalize) event).getOutputResults().getOutputUri().getPath());
    }

    public void showLastThumb() {
        (new ShowLastThumbAsyncTask(activityRef.get(), imageViewRef.get()))
                .setThumbSize(thumbSize)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void getThumbIntoMemory(File file, boolean isVideo) {
        if (LoginManager.isLoggedIn(activityRef.get())) {
            return;
        }
        (new GetThumbFromPlainFile(activityRef.get(), file).setThumbSize(thumbSize)
                .setIsVideo(isVideo).setOnFinish(new OnAsyncTaskFinish() {
                    @Override
                    public void onFinish(Object object) {
                        super.onFinish(object);
                        if (object != null) {
                            imageViewRef.get().setImageBitmap((Bitmap) object);
                        }
                    }
                })).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void sendNewMediaBroadcast(File file) {
        Log.d(TAG, file.getPath());
        startMediaEncryptionService();
        Intent broadcastIntent = new Intent("NEW_MEDIA");
        broadcastIntent.putExtra("file", file.getAbsolutePath());
        lbm.sendBroadcast(broadcastIntent);
    }

    public void sendCameraStatusBroadcast(boolean isRunning, boolean wasRecoringVideo) {
        if (isRunning) {
            startMediaEncryptionService();
        }
        Intent broadcastIntent = new Intent("CAMERA_STATUS");
        broadcastIntent.putExtra("isRunning", isRunning);
        broadcastIntent.putExtra("wasRecoringVideo", wasRecoringVideo);
        lbm.sendBroadcast(broadcastIntent);
    }

    private void startMediaEncryptionService() {
        if (activityRef == null) {
            return;
        }
        activityRef.get().startService(new Intent(activityRef.get(), MediaEncryptService.class));
    }
}
