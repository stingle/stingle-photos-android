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
import org.stingle.photos.Sync.TransferProgressTracker;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UploadToCloud {

	// Number of files uploaded concurrently. Uploads are network-bound, so a small
	// fixed pool keeps the pipe full between files without hammering the server or
	// burning memory (each in-flight upload buffers ~1 MB).
	private static final int UPLOAD_THREAD_COUNT = 3;

	// Guards the per-file space gate + suspend-pref writes and the success-path DB/pref
	// writes. Held only around those short critical sections, never across the network call.
	private static final Object DB_LOCK = new Object();
	// Notification.Builder is not thread-safe; serialize all mutations + notify() calls.
	private static final Object NOTIF_LOCK = new Object();

	private Context context;
	private File dir;
	private File thumbDir;
	private AsyncTask<?,?,?> task;
	private int totalFilesCount = 0;

	private final Map<Integer, FilesDb> dbForSet = new HashMap<>();

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

		TransferProgressTracker.getInstance().resetUploads(totalFilesCount);
		SyncManager.setSyncStatus(context, SyncManager.STATUS_UPLOADING);

		dbForSet.put(SyncManager.GALLERY, new GalleryTrashDb(context, SyncManager.GALLERY));
		dbForSet.put(SyncManager.TRASH, new GalleryTrashDb(context, SyncManager.TRASH));
		dbForSet.put(SyncManager.ALBUM, new AlbumFilesDb(context));

		// Collect all work off the cursors before any threading (Cursor is not thread-safe).
		List<UploadItem> items = new ArrayList<>();
		items.addAll(collectItems(SyncManager.GALLERY));
		items.addAll(collectItems(SyncManager.TRASH));
		items.addAll(collectItems(SyncManager.ALBUM));

		if(!items.isEmpty()) {
			ExecutorService executor = Executors.newFixedThreadPool(UPLOAD_THREAD_COUNT);
			for (UploadItem item : items) {
				if (task != null && task.isCancelled()) {
					break;
				}
				executor.execute(() -> uploadItem(item));
			}
			executor.shutdown();
			try {
				executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				executor.shutdownNow();
			}
		}

		for (FilesDb db : dbForSet.values()) {
			db.close();
		}
		dbForSet.clear();

		TransferProgressTracker.getInstance().clearUploads();
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

	private List<UploadItem> collectItems(int set){
		List<UploadItem> items = new ArrayList<>();
		FilesDb db = dbForSet.get(set);
		if(db == null){
			return items;
		}

		try(
			AutoCloseableCursor resultAutoCloseableCursor = db.getFilesList(GalleryTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_DESC, null, null);
			AutoCloseableCursor reuploadResultAutoCloseableCursor = db.getReuploadFilesList()
		) {
			Cursor result = resultAutoCloseableCursor.getCursor();
			while (result.moveToNext()) {
				items.add(itemFromCursor(result, set, false));
			}

			Cursor reuploadResult = reuploadResultAutoCloseableCursor.getCursor();
			while (reuploadResult.moveToNext()) {
				items.add(itemFromCursor(reuploadResult, set, true));
			}
		}

		return items;
	}

	private UploadItem itemFromCursor(Cursor result, int set, boolean isReupload){
		UploadItem item = new UploadItem();
		item.set = set;
		item.isReupload = isReupload;
		item.filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FILENAME));
		item.version = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_VERSION));
		item.dateCreated = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED));
		item.dateModified = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));
		item.headers = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_HEADERS));
		item.albumId = "";
		try {
			item.albumId = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID));
		}
		catch (IllegalArgumentException ignored) {}
		return item;
	}

	private void uploadItem(UploadItem item){
		if((task != null && task.isCancelled()) || !isUploadAllowed()){
			return;
		}

		FilesDb db = dbForSet.get(item.set);
		if(db == null){
			return;
		}

		File file = new File(dir.getPath() + "/" + item.filename);
		File thumb = new File(thumbDir.getPath() + "/" + item.filename);

		synchronized (DB_LOCK) {
			if(Helpers.getPreference(context, SyncManager.PREF_SUSPEND_UPLOAD, false)){
				return;
			}
			int overallSize = Helpers.bytesToMb(file.length() + thumb.length());
			if(!Helpers.isUploadSpaceAvailable(context, overallSize)){
				Helpers.storePreference(context, SyncManager.PREF_LAST_AVAILABLE_SPACE, Helpers.getAvailableUploadSpace(context));
				Helpers.storePreference(context, SyncManager.PREF_SUSPEND_UPLOAD, true);
				Log.d("not_uploading", "space is over, not uploading file " + file.getName());
				return;
			}
		}

		TransferProgressTracker.getInstance().startUpload(item.filename, item.headers, item.set, item.albumId);
		notifyProgress();

		Log.d("uploadingFile", item.filename);

		HttpsClient.FileToUpload fileToUpload = new HttpsClient.FileToUpload("file", file.getPath(), SyncManager.SP_FILE_MIME_TYPE);
		HttpsClient.FileToUpload thumbToUpload = new HttpsClient.FileToUpload("thumb", thumb.getPath(), SyncManager.SP_FILE_MIME_TYPE);

		ArrayList<HttpsClient.FileToUpload> filesToUpload = new ArrayList<>();
		filesToUpload.add(fileToUpload);
		filesToUpload.add(thumbToUpload);

		HashMap<String, String> postParams = new HashMap<>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("set", String.valueOf(item.set));
		postParams.put("albumId", item.albumId);
		postParams.put("version", item.version);
		postParams.put("dateCreated", item.dateCreated);
		postParams.put("dateModified", item.dateModified);
		postParams.put("headers", item.headers);

		JSONObject resp = HttpsClient.multipartUpload(
				StinglePhotosApplication.getApiUrl() + context.getString(R.string.upload_file_path),
				postParams,
				filesToUpload,
				new HttpsClient.OnUpdateProgress() {
					@Override
					public void onUpdate(int progress) {
						TransferProgressTracker.getInstance().updateUploadPercent(item.filename, progress);
						SyncManager.setSyncStatus(context, SyncManager.STATUS_UPLOADING);
					}
				}
		);
		StingleResponse response = new StingleResponse(this.context, resp, false);
		if(response.isStatusOk()){
			synchronized (DB_LOCK) {
				db.markFileAsRemote(item.filename);

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
			}

			GalleryActions.refreshGalleryItem(context, item.filename, item.set, item.albumId);
		}

		if(item.isReupload){
			synchronized (DB_LOCK) {
				db.markFileAsReuploaded(item.filename);
			}
		}

		TransferProgressTracker.getInstance().finishUpload(item.filename);
		notifyProgress();
	}

	private void notifyProgress(){
		TransferProgressTracker.Snapshot snap = TransferProgressTracker.getInstance().snapshot();
		SyncManager.setSyncStatus(context, SyncManager.STATUS_UPLOADING);
		synchronized (NOTIF_LOCK) {
			updateNotification(snap.uploadTotal, snap.uploadCompleted);
		}
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

	private static class UploadItem {
		String filename;
		String version;
		String dateCreated;
		String dateModified;
		String headers;
		String albumId;
		int set;
		boolean isReupload;
	}

}
