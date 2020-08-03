package org.stingle.photos.AsyncTasks.Sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.FSSync;
import org.stingle.photos.Sync.ImportMedia;
import org.stingle.photos.Sync.SyncCloudToLocalDb;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.UploadToCloud;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

public class SyncAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;

	private ExecutorService cachedThreadPool;
	private NotificationManager mNotifyManager;
	private Notification.Builder notificationBuilder;
	private boolean isNotificationActive = false;

	protected UploadToCloud.UploadProgress progress;

	public SyncAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.onFinishListener = onFinishListener;

		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		progress = new UploadToCloud.UploadProgress() {
			@Override
			public void currentFile(String filename, String headers, int set, String albumId) {
				super.currentFile(filename, headers, set, albumId);
				Bundle b = new Bundle();
				b.putString("currentFile", filename);
				b.putString("headers", headers);
				b.putInt("set", set);
				b.putString("albumId", albumId);
				//sendBundleToUi(MSG_SYNC_CURRENT_FILE, b);
			}

			@Override
			public void fileUploadFinished(String filename, int set, String albumId) {
				FilesDb db;
				int sort = StingleDb.SORT_DESC;
				if(set == SyncManager.GALLERY || set == SyncManager.TRASH) {
					db = new GalleryTrashDb(context, set);
				}
				else if(set == SyncManager.ALBUM){
					db = new AlbumFilesDb(context);
					sort = StingleDb.SORT_ASC;
				}
				else{
					return;
				}

				Integer filePos = db.getFilePositionByFilename(filename, albumId, sort);
				db.close();

				Log.d("updateItem number", String.valueOf(filePos));
				Bundle values = new Bundle();
				values.putInt("position", filePos);
				values.putInt("set", set);
				values.putString("albumId", albumId);
				//sendBundleToUi(MSG_REFRESH_GALLERY_ITEM, values);
			}

			@Override
			public void uploadProgress(int uploadedFilesCount) {
				super.uploadProgress(uploadedFilesCount);
				showNotification(context);
				HashMap<String, Integer> values = new HashMap<String, Integer>();
				values.put("uploadedFilesCount", uploadedFilesCount);
				values.put("totalItemsNumber", progress.totalItemsNumber);
				//sendIntToUi(MSG_SYNC_UPLOAD_PROGRESS, values);
				notificationBuilder.setProgress(progress.totalItemsNumber, uploadedFilesCount, false);
				notificationBuilder.setContentTitle(context.getString(R.string.uploading_file, String.valueOf(uploadedFilesCount), String.valueOf(progress.totalItemsNumber)));
				mNotifyManager.notify(R.string.sync_service_started, notificationBuilder.build());
			}
		};
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

	public void startSync(Context context){
		if(StinglePhotosApplication.syncStatus != SyncManager.STATUS_IDLE){
			StinglePhotosApplication.syncRestartAfterFinish = true;
			return;
		}
		SyncManager.setSyncStatus(context, SyncManager.STATUS_REFRESHING);

		syncCloudToLocalDb(context);
		FSSync(context);
		autoImport(context);
		upload(context);

		if(StinglePhotosApplication.syncRestartAfterFinish){
			StinglePhotosApplication.syncRestartAfterFinish = false;
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
			//sendMessageToUI(MSG_REFRESH_GALLERY);
		}
	}

	public void upload(Context context){
		//sendIntToUi(MSG_SYNC_STATUS_CHANGE, "newStatus", currentStatus);
		(new UploadToCloud(context, progress, this)).upload();

	}

	private void showNotification(Context context) {
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

	}


}
