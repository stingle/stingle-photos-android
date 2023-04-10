package org.stingle.photos.Sync.SyncSteps;

import static android.content.Context.BATTERY_SERVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AutoCloseableCursor;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class UploadToCloud {

	private Context context;
	private File dir;
	private File thumbDir;
	private AsyncTask<?,?,?> task;
	private int uploadedFilesCount = 0;
	private int totalFilesCount = 0;

	public static NotificationManager mNotifyManager;
	public static Notification.Builder notificationBuilder;
	public static boolean isNotificationActive = false;

	public UploadToCloud(Context context, AsyncTask<?,?,?> task){
		this.context = context;
		this.task = task;
		dir = new File(FileManager.getHomeDir(context));
		thumbDir = new File(FileManager.getThumbsDir(context));
		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public void upload(){
		if(!isUploadAllowed()){
			return;
		}
		showNotification();
		totalFilesCount = getFilesCountToUpload(SyncManager.GALLERY) + getFilesCountToUpload(SyncManager.TRASH) + getFilesCountToUpload(SyncManager.ALBUM);

		SyncManager.setSyncStatus(context, SyncManager.STATUS_UPLOADING);

		uploadSet(SyncManager.GALLERY);
		uploadSet(SyncManager.TRASH);
		uploadSet(SyncManager.ALBUM);
		SyncManager.setSyncStatus(context, SyncManager.STATUS_IDLE);
		isNotificationActive = false;
		removeNotification();
	}

	private boolean isUploadAllowed(){
		if(!LoginManager.isLoggedIn(context)) {
			return false;
		}
		boolean isUploadSuspended = Helpers.getPreference(context, SyncManager.PREF_SUSPEND_UPLOAD, false);
		if(isUploadSuspended){
			int lastAvailableSpace = Helpers.getPreference(context, SyncManager.PREF_LAST_AVAILABLE_SPACE, 0);
			int availableSpace = Helpers.getAvailableUploadSpace(context);
			if(availableSpace > lastAvailableSpace || availableSpace > 0){
				Helpers.storePreference(context, SyncManager.PREF_SUSPEND_UPLOAD, false);
				Helpers.deletePreference(context, SyncManager.PREF_LAST_AVAILABLE_SPACE);
				isUploadSuspended = false;
				Log.d("upload", "resuming upload");
			}
		}

		int allowedStatus = isUploadAllowedInternal(context);
		if(isUploadSuspended) {
			Log.d("upload", "upload is disabled, no space");
			SyncManager.setSyncStatus(context, SyncManager.STATUS_NO_SPACE_LEFT);
			return false;
		}
		else if(allowedStatus != 0) {
			Log.d("upload", "upload is disabled, not allowed");
			SyncManager.setSyncStatus(context, allowedStatus);
			return false;
		}

		return true;
	}

	private int isUploadAllowedInternal(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		if(!SyncManager.isBackupEnabled(context)){
			return SyncManager.STATUS_DISABLED;
		}

		boolean isOnlyOnWifi = prefs.getBoolean(SyncManager.PREF_BACKUP_ONLY_WIFI, false);
		int uploadBatteryLevel = prefs.getInt(SyncManager.PREF_BACKUP_BATTERY_LEVEL, 0);

		if(isOnlyOnWifi) {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if(cm != null) {
				NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
				if (activeNetwork != null) {
					// connected to the internet
					if (activeNetwork.getType() != ConnectivityManager.TYPE_WIFI) {
						return SyncManager.STATUS_NOT_WIFI;
					}
				} else {
					return SyncManager.STATUS_NOT_WIFI;
				}
			}
		}

		if(uploadBatteryLevel > 0){
			IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = context.registerReceiver(null, iFilter);
			if(batteryStatus != null) {
				int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
				boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
						status == BatteryManager.BATTERY_STATUS_FULL;

				BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
				if(bm != null) {
					int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

					Log.d("battery level", String.valueOf(batLevel));

					if (!isCharging && batLevel < uploadBatteryLevel) {
						return SyncManager.STATUS_BATTERY_LOW;
					}
				}
			}
		}

		return 0;
	}

	protected int getFilesCountToUpload(int set){
		FilesDb db;
		if(set == SyncManager.GALLERY || set == SyncManager.TRASH){
			db = new GalleryTrashDb(context, set);
		}
		else if (set == SyncManager.ALBUM){
			db = new AlbumFilesDb(context);
		}
		else{
			return 0;
		}

		try(
				AutoCloseableCursor result = db.getFilesList(GalleryTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_ASC, null, null);
				AutoCloseableCursor reuploadResult = db.getReuploadFilesList();
		) {
			int uploadCount = result.getCursor().getCount();
			result.close();



			int reuploadCount = reuploadResult.getCursor().getCount();
			reuploadResult.close();

			db.close();

			return uploadCount + reuploadCount;
		}
	}

	protected void uploadSet(int set){
		FilesDb db;
		if(set == SyncManager.GALLERY || set == SyncManager.TRASH){
			db = new GalleryTrashDb(context, set);
		}
		else if (set == SyncManager.ALBUM){
			db = new AlbumFilesDb(context);
		}
		else{
			return;
		}

		try(
			AutoCloseableCursor resultAutoCloseableCursor = db.getFilesList(GalleryTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_DESC, null, null);
			AutoCloseableCursor reuploadResultAutoCloseableCursor = db.getReuploadFilesList()
		) {
			Cursor result = resultAutoCloseableCursor.getCursor();
			Cursor reuploadResult = reuploadResultAutoCloseableCursor.getCursor();
			while (result.moveToNext()) {
				if (task != null && task.isCancelled()) {
					break;
				}
				if (!isUploadAllowed()) {
					break;
				}
				uploadedFilesCount++;
				uploadFile(set, db, result, false);
			}
			result.close();


			while (reuploadResult.moveToNext()) {
				if (task != null && task.isCancelled()) {
					break;
				}
				if (!isUploadAllowed()) {
					break;
				}
				uploadedFilesCount++;
				uploadFile(set, db, reuploadResult, true);
			}
			reuploadResult.close();
		}
		finally {
			db.close();
		}
	}

	protected void uploadFile(int set, FilesDb db, Cursor result, boolean isReupload){
		String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FILENAME));
		String version = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_VERSION));
		String dateCreated = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED));
		String dateModified = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));
		String headers = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_HEADERS));
		String albumId = "";
		try {
			albumId = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID));
		}
		catch (IllegalArgumentException ignored) {}

		notifyGalleryAboutProgress(filename, headers, set, albumId);

		Log.d("uploadingFile", filename);
		File file = new File(dir.getPath() + "/" + filename);
		File thumb = new File(thumbDir.getPath() + "/" + filename);

		int overallSize = Helpers.bytesToMb(file.length() + thumb.length());
		if(!Helpers.isUploadSpaceAvailable(context, overallSize)){
			Helpers.storePreference(context, SyncManager.PREF_LAST_AVAILABLE_SPACE, Helpers.getAvailableUploadSpace(context));
			Helpers.storePreference(context, SyncManager.PREF_SUSPEND_UPLOAD, true);
			Log.d("not_uploading", "space is over, not uploading file " + file.getName());
			return;
		}

		HttpsClient.FileToUpload fileToUpload = new HttpsClient.FileToUpload("file", file.getPath(), SyncManager.SP_FILE_MIME_TYPE);
		HttpsClient.FileToUpload thumbToUpload = new HttpsClient.FileToUpload("thumb", thumb.getPath(), SyncManager.SP_FILE_MIME_TYPE);

		ArrayList<HttpsClient.FileToUpload> filesToUpload = new ArrayList<>();
		filesToUpload.add(fileToUpload);
		filesToUpload.add(thumbToUpload);

		HashMap<String, String> postParams = new HashMap<>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("set", String.valueOf(set));
		postParams.put("albumId", albumId);
		postParams.put("version", version);
		postParams.put("dateCreated", dateCreated);
		postParams.put("dateModified", dateModified);
		postParams.put("headers", headers);

		JSONObject resp = HttpsClient.multipartUpload(
				StinglePhotosApplication.getApiUrl() + context.getString(R.string.upload_file_path),
				postParams,
				filesToUpload
		);
		StingleResponse response = new StingleResponse(this.context, resp, false);
		if(response.isStatusOk()){
			db.markFileAsRemote(filename);

			String spaceUsedStr = response.get("spaceUsed");
			String spaceQuotaStr = response.get("spaceQuota");

			if(spaceUsedStr != null && spaceUsedStr.length() > 0){
				int spaceUsed = Integer.parseInt(spaceUsedStr);
				if(spaceUsed >= 0){
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_USED, spaceUsed);
				}
			}

			if(spaceQuotaStr != null && spaceQuotaStr.length() > 0){
				int spaceQuota = Integer.parseInt(spaceQuotaStr);
				if(spaceQuota >= 0){
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, spaceQuota);
				}
			}

			GalleryActions.refreshGalleryItem(context, filename, set, albumId);
		}

		if(isReupload){
			db.markFileAsReuploaded(filename);
		}
	}

	private void notifyGalleryAboutProgress(String filename, String headers, int set, String albumId){
		Bundle params = new Bundle();
		params.putInt("totalFilesCount", totalFilesCount);
		params.putInt("uploadedFilesCount", uploadedFilesCount);
		params.putString("filename", filename);
		params.putString("headers", headers);
		params.putInt("set", set);
		params.putString("albumId", albumId);

		SyncManager.setSyncStatus(context, SyncManager.STATUS_UPLOADING, params);
		updateNotification(totalFilesCount, uploadedFilesCount);
	}

	private void showNotification() {
		if(isNotificationActive){
			return;
		}

		isNotificationActive = true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "org.stingle.photos.sync";
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.sync_channel_name), NotificationManager.IMPORTANCE_LOW);
			chan.setLightColor(context.getColor(R.color.primaryLightColor));
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			notificationBuilder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
		} else {
			notificationBuilder = new Notification.Builder(context);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				new Intent(context, GalleryActivity.class), PendingIntent.FLAG_IMMUTABLE);

		Notification notification = notificationBuilder
				.setSmallIcon(R.drawable.ic_sp)  // the status icon
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.build();

		mNotifyManager.notify(R.string.sync_service_started, notification);
	}

	private void updateNotification(int totalItemsNumber, int uploadedFilesCount){
		//showNotification();
		notificationBuilder.setProgress(totalItemsNumber, uploadedFilesCount, false);
		notificationBuilder.setContentTitle(context.getString(R.string.uploading_file, String.valueOf(uploadedFilesCount), String.valueOf(totalItemsNumber)));
		mNotifyManager.notify(R.string.sync_service_started, notificationBuilder.build());
	}

	private void removeNotification(){
		mNotifyManager.cancel(R.string.sync_service_started);
	}

}
