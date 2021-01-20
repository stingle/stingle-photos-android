package org.stingle.photos.AsyncTasks.Gallery;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

public class DownloadAsyncTask extends AsyncTask<Void, Void, Void> {

	private WeakReference<Context> context;
	protected ArrayList<StingleDbFile> files;
	protected SyncManager.OnFinish onFinish;
	private int set = -1;
	private String albumId = null;
	public static NotificationManager mNotifyManager;
	public static Notification.Builder notificationBuilder;
	public static boolean isNotificationActive = false;

	public DownloadAsyncTask(Context context, ArrayList<StingleDbFile> files, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.files = files;
		this.onFinish = onFinish;
		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public DownloadAsyncTask setSet(int set){
		this.set = set;
		return this;
	}

	public DownloadAsyncTask setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}

	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}
		if(set == -1){
			return null;
		}
		showNotification();

		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);

		String homeDir = FileManager.getHomeDir(myContext);

		int count = 1;
		for(StingleDbFile file : files) {
			if(file.isLocal){
				continue;
			}
			HashMap<String, String> postParams = new HashMap<>();

			postParams.put("token", KeyManagement.getApiToken(myContext));
			postParams.put("file", file.filename);
			postParams.put("thumb", "0");
			postParams.put("set", String.valueOf(set));


			final int itemNumber = count;
			try {
				String path = homeDir + "/" + file.filename;
				HttpsClient.downloadFile(StinglePhotosApplication.getApiUrl() + myContext.getString(R.string.download_file_path), postParams, path, new HttpsClient.OnUpdateProgress() {
					@Override
					public void onUpdate(int progress) {
						if(progress % 5 == 0) {
							updateNotification(files.size(), itemNumber, progress);
						}
					}
				});
				file.isLocal = true;
				if(set == SyncManager.GALLERY) {
					galleryDb.updateFile(file);
				}
				else if(set == SyncManager.ALBUM){
					albumFilesDb.updateFile(file);
				}
				else if(set == SyncManager.TRASH){
					trashDb.updateFile(file);
				}
			}
			catch (NoSuchAlgorithmException | KeyManagementException | IOException e) {
				e.printStackTrace();
			}
			count++;
		}

		removeNotification();

		galleryDb.close();
		albumFilesDb.close();
		trashDb.close();
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);

		if(onFinish != null){
			onFinish.onFinish(true);
		}
	}

	private void showNotification() {
		if(isNotificationActive){
			return;
		}
		Context myContext = context.get();
		if(myContext == null){
			return;
		}

		isNotificationActive = true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "org.stingle.photos.download";
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, myContext.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW);
			chan.setLightColor(myContext.getColor(R.color.primaryLightColor));
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) myContext.getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			notificationBuilder = new Notification.Builder(myContext, NOTIFICATION_CHANNEL_ID);
		}
		else{
			notificationBuilder = new Notification.Builder(myContext);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(myContext, 0,
				new Intent(myContext, GalleryActivity.class), 0);

		Notification notification = notificationBuilder
				.setSmallIcon(R.drawable.ic_cloud_download)  // the status icon
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.build();

		mNotifyManager.notify(R.string.download_service_started, notification);
	}

	private void updateNotification(int totalItemsNumber, int downloadedFilesCount, int progess){
		Context myContext = context.get();
		if(myContext == null){
			return;
		}
		//showNotification();
		notificationBuilder.setProgress(100, progess, false);
		notificationBuilder.setContentTitle(myContext.getString(R.string.downloading_file, String.valueOf(downloadedFilesCount), String.valueOf(totalItemsNumber)));
		mNotifyManager.notify(R.string.download_service_started, notificationBuilder.build());
	}

	private void removeNotification(){
		mNotifyManager.cancel(R.string.download_service_started);
	}

}
