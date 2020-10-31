package org.stingle.photos.Sync.SyncSteps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Query.ImportedIdsDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ImportFile;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;

public class ImportMedia {

	public static final int REFRESH_GALLERY_AFTER_N_IMPORT = 10;

	private Context context;
	private AsyncTask<?,?,?> task;
	private int totalFilesCount = 0;
	private int importedFilesCount = 0;

	public static NotificationManager mNotifyManager;
	public static Notification.Builder notificationBuilder;
	public static boolean isNotificationActive = false;

	public ImportMedia(Context context, AsyncTask<?,?,?> task){
		this.context = context;
		this.task = task;
		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public boolean importMedia(){
		if(!LoginManager.isLoggedIn(context)) {
			return false;
		}
		if(!SyncManager.isImportEnabled(context)){
			return false;
		}
		Log.e("EnteredImportMedia", "1");
		boolean isSomethingImported = false;

		long lastImportedFileDate = Helpers.getPreference(context, SyncManager.LAST_IMPORTED_FILE_DATE, 0L);

		String[] projection;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
			projection = new String[] {
					MediaStore.MediaColumns._ID,
					MediaStore.MediaColumns.DISPLAY_NAME,
					MediaStore.Files.FileColumns.MEDIA_TYPE,
					MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
					MediaStore.MediaColumns.DATE_TAKEN,
					MediaStore.MediaColumns.DATE_ADDED
			};
		}
		else{
			projection = new String[] {
					MediaStore.MediaColumns._ID,
					MediaStore.MediaColumns.DISPLAY_NAME,
					MediaStore.Files.FileColumns.MEDIA_TYPE,
					MediaStore.MediaColumns.DATA,
					MediaStore.MediaColumns.DATE_ADDED
			};
		}
		String selection;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && SyncManager.getImportFrom(context).equals("camera_folder")) {
			selection =
					"("
							+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
							+ MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
							+ " OR "
							+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
							+ MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")"
							+ " AND " + MediaStore.MediaColumns.DATE_ADDED + " > ?"
							+ " AND " + MediaStore.MediaColumns.BUCKET_DISPLAY_NAME + " = ?";
		}
		else{
			selection =
					"("
							+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
							+ MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
							+ " OR "
							+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
							+ MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")"
							+ " AND " + MediaStore.MediaColumns.DATE_ADDED + " > ?";
		}

		String[] selectionArgs;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && SyncManager.getImportFrom(context).equals("camera_folder")) {
			selectionArgs = new String[]{String.valueOf(lastImportedFileDate), "Camera"};
		}
		else{
			selectionArgs = new String[]{String.valueOf(lastImportedFileDate)};
		}

		String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " ASC";

		try (Cursor cursor = context.getApplicationContext().getContentResolver().query(
				MediaStore.Files.getContentUri("external"),
				projection,
				selection,
				selectionArgs,
				sortOrder
		)) {
			// Cache column indices.
			int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
			int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
			int typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE);


			int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);;
			int dateTakenColumn = -1;
			int parentColumn;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
				dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
				parentColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
			}
			else{
				parentColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
			}

			int importCountForRefresh = 0;
			ImportedIdsDb db = new ImportedIdsDb(context);
			Log.e("cursorCount", "" + cursor.getCount());
			totalFilesCount = cursor.getCount();
			if(totalFilesCount > 0){
				SyncManager.setSyncStatus(context, SyncManager.STATUS_IMPORTING);
				showNotification();
			}

			while (cursor.moveToNext()) {
				if(task != null && task.isCancelled()){
					break;
				}
				Log.e("EnteredImportMediaFor", "1");
				long id = cursor.getLong(idColumn);
				String name = cursor.getString(nameColumn);
				int type = cursor.getInt(typeColumn);
				long dateAdded = cursor.getLong(dateAddedColumn);

				Uri contentUri = null;
				if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
					contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
				} else if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
					contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
				}

				importedFilesCount++;


				String parentName;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
					parentName = cursor.getString(parentColumn);
				}
				else{
					parentName = (new File(cursor.getString(parentColumn))).getParentFile().getName();
				}

				boolean isParentFolderIsOk = true;
				if(SyncManager.getImportFrom(context).equals("camera_folder") && parentName != null && !parentName.equalsIgnoreCase("Camera")){
					isParentFolderIsOk = false;
				}

				if(parentName != null && parentName.equalsIgnoreCase(FileManager.DECRYPT_DIR)){
					isParentFolderIsOk = false;
				}

				if (contentUri != null && isParentFolderIsOk && !db.isIdExists(id)) {
					notifyGalleryAboutProgress();
					db.insertImportedId(id);

					long dateAddedMillis;
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && dateTakenColumn >= 0) {
						dateAddedMillis = cursor.getLong(dateTakenColumn);
						if(dateAddedMillis == 0){
							dateAddedMillis = dateAdded * 1000;
						}
					} else {
						dateAddedMillis = dateAdded * 1000;
					}
					Log.e("uri", name + " - " + dateAddedMillis + " - " + type + " - " + contentUri.toString());

					if (ImportFile.importFile(context, contentUri, SyncManager.GALLERY, null, dateAddedMillis, null)) {
						isSomethingImported = true;
						if(SyncManager.isImportDeleteEnabled(context)){
							context.getApplicationContext().getContentResolver().delete(contentUri, null, null);
						}
						importCountForRefresh++;
						if(importCountForRefresh > REFRESH_GALLERY_AFTER_N_IMPORT) {
							LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("MEDIA_ENC_FINISH"));
							importCountForRefresh = 0;
						}
					}
				}

				if(dateAdded > lastImportedFileDate){
					lastImportedFileDate = dateAdded;
					Helpers.storePreference(context, SyncManager.LAST_IMPORTED_FILE_DATE, lastImportedFileDate);
				}
			}

			if(isSomethingImported) {
				LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("MEDIA_ENC_FINISH"));
			}

			db.close();
		}

		SyncManager.setSyncStatus(context, SyncManager.STATUS_IDLE);
		isNotificationActive = false;
		removeNotification();

		return isSomethingImported;
	}

	private void notifyGalleryAboutProgress(){
		Bundle params = new Bundle();
		params.putInt("totalFilesCount", totalFilesCount);
		params.putInt("importedFilesCount", importedFilesCount);

		SyncManager.setSyncStatus(context, SyncManager.STATUS_IMPORTING, params);
		updateNotification(totalFilesCount, importedFilesCount);
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
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.build();

		mNotifyManager.notify(R.string.sync_service_started, notification);
	}

	private void updateNotification(int totalItemsNumber, int importedFilesCount){
		//showNotification();
		notificationBuilder.setProgress(totalItemsNumber, importedFilesCount, false);
		notificationBuilder.setContentTitle(context.getString(R.string.importing_file, String.valueOf(importedFilesCount), String.valueOf(totalItemsNumber)));
		mNotifyManager.notify(R.string.sync_service_started, notificationBuilder.build());
	}

	private void removeNotification(){
		mNotifyManager.cancel(R.string.sync_service_started);
	}
}
