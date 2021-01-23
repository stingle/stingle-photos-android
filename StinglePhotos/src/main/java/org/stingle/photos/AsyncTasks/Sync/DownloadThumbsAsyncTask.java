package org.stingle.photos.AsyncTasks.Sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class DownloadThumbsAsyncTask extends AsyncTask<Void, Void, Void> {

	private WeakReference<Context> context;
	protected SyncManager.OnFinish onFinish;
	public static NotificationManager mNotifyManager;
	public static Notification.Builder notificationBuilder;
	public static boolean isNotificationActive = false;
	private File thumbDir;
	private File thumbCacheDir;
	private int count = 1;
	private int gallerySize;

	public DownloadThumbsAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		thumbDir = new File(FileManager.getThumbsDir(context));
		thumbCacheDir = new File(context.getCacheDir().getPath() + "/" + FileManager.THUMB_CACHE_DIR);
	}


	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);


		Cursor result = galleryDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
		Cursor resultAlbums = albumFilesDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
		Cursor resultTrash = trashDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);

		gallerySize = result.getCount() + resultAlbums.getCount() + resultTrash.getCount();

		while(result.moveToNext()) {
			StingleDbFile dbFile = new StingleDbFile(result);
			downloadThumb(myContext, dbFile, SyncManager.GALLERY);
		}
		result.close();

		while(resultAlbums.moveToNext()) {
			StingleDbFile dbFile = new StingleDbFile(resultAlbums);
			downloadThumb(myContext, dbFile, SyncManager.ALBUM);
		}
		resultAlbums.close();

		while(resultTrash.moveToNext()) {
			StingleDbFile dbFile = new StingleDbFile(resultTrash);
			downloadThumb(myContext, dbFile, SyncManager.TRASH);
		}
		resultTrash.close();

		removeNotification();

		galleryDb.close();
		albumFilesDb.close();
		trashDb.close();
		return null;
	}

	private void downloadThumb(Context context, StingleDbFile dbFile, int set){
		File thumb = new File(thumbDir + "/" + dbFile.filename);
		File thumbCache = new File(thumbCacheDir + "/" + dbFile.filename);
		if(!thumb.exists() && !thumbCache.exists()) {
			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("file", dbFile.filename);
			postParams.put("thumb", "1");
			postParams.put("set", String.valueOf(set));
			byte[] encFile;

			try {
				encFile = HttpsClient.getFileAsByteArray(StinglePhotosApplication.getApiUrl() + context.getString(R.string.download_file_path), postParams);


				if (encFile == null || encFile.length == 0) {
					return;
				}

				byte[] fileBeginning = Arrays.copyOfRange(encFile, 0, Crypto.FILE_BEGGINIG_LEN);
				if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
					return;
				}

				if (!thumbCacheDir.exists()) {
					thumbCacheDir.mkdirs();
				}

				File cachedFile = new File(thumbCacheDir.getPath() + "/" + dbFile.filename);
				FileOutputStream out = new FileOutputStream(cachedFile);
				out.write(encFile);
				out.close();

				GalleryActions.refreshGalleryItem(context, dbFile.filename, set, dbFile.albumId);

				updateNotification(gallerySize, count);
			} catch (NoSuchAlgorithmException | KeyManagementException | IOException e) {
				e.printStackTrace();
			}
		}
		count++;
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

		mNotifyManager.notify(R.string.download_thumb_service_started, notification);
	}

	private void updateNotification(int totalItemsNumber, int downloadedFilesCount){
		Context myContext = context.get();
		if(myContext == null){
			return;
		}
		showNotification();
		notificationBuilder.setProgress(totalItemsNumber, downloadedFilesCount, false);
		notificationBuilder.setContentTitle(myContext.getString(R.string.downloading_thumb, String.valueOf(downloadedFilesCount), String.valueOf(totalItemsNumber)));
		mNotifyManager.notify(R.string.download_thumb_service_started, notificationBuilder.build());
	}

	private void removeNotification(){
		mNotifyManager.cancel(R.string.download_thumb_service_started);
	}

}
