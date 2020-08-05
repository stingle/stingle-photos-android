package org.stingle.photos.AsyncTasks.Sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.FSSync;
import org.stingle.photos.Sync.ImportMedia;
import org.stingle.photos.Sync.SyncCloudToLocalDb;
import org.stingle.photos.Sync.UploadToCloud;

import java.lang.ref.WeakReference;

public class SyncAsyncTask extends AsyncTask<Void, Void, Boolean> {

	public static SyncAsyncTask instance;
	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;
	private int mode;

	public static NotificationManager mNotifyManager;
	public static Notification.Builder notificationBuilder;
	public static boolean isNotificationActive = false;

	static final public int MODE_FULL = 0;
	static final public int MODE_IMPORT_AND_UPLOAD = 1;
	static final public int MODE_CLOUD_TO_LOCAL = 2;

	public SyncAsyncTask(Context context) {
		this(context, MODE_FULL, null);
	}

	public SyncAsyncTask(Context context, int mode) {
		this(context, mode, null);
	}

	public SyncAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this(context, MODE_FULL, onFinishListener);
	}

	public SyncAsyncTask(Context context, int mode, OnAsyncTaskFinish onFinishListener) {
		instance = this;
		this.context = new WeakReference<>(context);;
		this.mode = mode;
		this.onFinishListener = onFinishListener;

		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		if(!LoginManager.isLoggedIn(myContext)){
			return false;
		}

		startSync(myContext);


		return true;
	}

	private void startSync(Context context){
		switch (mode){
			case MODE_FULL:
				syncCloudToLocalDb(context);
				FSSync(context);
				autoImport(context);
				upload(context);
				break;
			case MODE_IMPORT_AND_UPLOAD:
				autoImport(context);
				upload(context);
				break;
			case MODE_CLOUD_TO_LOCAL:
				syncCloudToLocalDb(context);
				break;
		}


		if(StinglePhotosApplication.syncRestartAfterFinish){
			StinglePhotosApplication.syncRestartAfterFinish = false;
			mode = StinglePhotosApplication.syncRestartAfterFinishMode;
			StinglePhotosApplication.syncRestartAfterFinishMode = MODE_FULL;
			startSync(context);
		}
		isNotificationActive = false;
	}

	public void FSSync(Context context){
		boolean needToUpdateUI = FSSync.sync(context);
		if(needToUpdateUI){
			//sendMessageToUI(MSG_REFRESH_GALLERY);
		}
	}

	public void autoImport(Context context){
		ImportMedia.importMedia(context);
	}

	public void syncCloudToLocalDb(Context context){
		boolean needToUpdateUI = (new SyncCloudToLocalDb(context)).sync();
		if (needToUpdateUI){
			LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("REFRESH_GALLERY"));
		}
	}

	public void upload(Context context){
		(new UploadToCloud(context,  this)).upload();

	}

	public static void showNotification(Context context) {
		if(isNotificationActive){
			return;
		}

		isNotificationActive = true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "org.stingle.photos.sync";
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.sync_channel_name), NotificationManager.IMPORTANCE_NONE);
			chan.setLightColor(context.getColor(R.color.primaryLightColor));
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			notificationBuilder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
		}
		else{
			notificationBuilder = new Notification.Builder(context);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				new Intent(context, GalleryActivity.class), 0);

		Notification notification = notificationBuilder
				.setSmallIcon(R.drawable.ic_sp)  // the status icon
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
				.build();

		mNotifyManager.notify(R.string.sync_service_started, notification);
	}

	public static void updateNotification(Context context, int totalItemsNumber, int uploadedFilesCount){
		showNotification(context);
		notificationBuilder.setProgress(totalItemsNumber, uploadedFilesCount, false);
		notificationBuilder.setContentTitle(context.getString(R.string.uploading_file, String.valueOf(uploadedFilesCount), String.valueOf(totalItemsNumber)));
		mNotifyManager.notify(R.string.sync_service_started, notificationBuilder.build());
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(onFinishListener != null) {
			if (result) {
				onFinishListener.onFinish();
			} else {
				onFinishListener.onFail();
			}
		}
		instance = null;

	}


}
