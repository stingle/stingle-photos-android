package org.stingle.photos.Sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import org.stingle.photos.AsyncTasks.Sync.DownloadThumbsAsyncTask;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;

/**
 * Runs the background thumbnail download inside a foreground service so the work isn't
 * frozen/killed when the app goes to the background. On modern Android (12+) a cached
 * (backgrounded) process is frozen, which made the old plain-AsyncTask download stall;
 * a foreground service with the {@code dataSync} type keeps the process running until
 * the download finishes. The progress notification posted by
 * {@link org.stingle.photos.AsyncTasks.MultithreadDownloaderAsyncTask} reuses this same
 * notification id, so it updates this service's foreground notification.
 */
public class ThumbsDownloadService extends Service {

	public static final int NOTIFICATION_ID = R.string.download_thumb_service_started;
	private static final String NOTIFICATION_CHANNEL_ID = "org.stingle.photos.download";

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startForegroundCompat();

		StinglePhotosApplication app = (StinglePhotosApplication) getApplicationContext();
		if (app.downloadThumbsAsyncTask == null) {
			app.downloadThumbsAsyncTask = new DownloadThumbsAsyncTask(getApplicationContext(), new SyncManager.OnFinish() {
				@Override
				public void onFinish(Boolean needToUpdateUI) {
					app.downloadThumbsAsyncTask = null;
					stopSelf();
				}
			});
			app.downloadThumbsAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

		return START_NOT_STICKY;
	}

	private void startForegroundCompat() {
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder builder;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
					getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			if (manager != null) {
				manager.createNotificationChannel(chan);
			}
			builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
		} else {
			builder = new Notification.Builder(this);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, GalleryActivity.class), PendingIntent.FLAG_IMMUTABLE);

		Notification notification = builder
				.setSmallIcon(R.drawable.ic_cloud_download)
				.setContentTitle(getString(R.string.download_channel_name))
				.setContentIntent(contentIntent)
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.build();

		ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
						? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);
	}

	@Override
	public void onDestroy() {
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
		super.onDestroy();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
