package org.stingle.photos.Sync;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.Db.Query.ImportedIdsDb;
import org.stingle.photos.Files.ImportFile;
import org.stingle.photos.Util.Helpers;

import java.io.File;

public class ImportMedia {


	public static boolean importMedia(Context context){
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

		String selection =
				"("
						+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
						+ MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
						+ " OR "
						+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
						+ MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")"
						+ " AND " + MediaStore.MediaColumns.DATE_ADDED + " > ?";

		String[] selectionArgs = new String[] { String.valueOf(lastImportedFileDate) };

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


			ImportedIdsDb db = new ImportedIdsDb(context);
			Log.e("cursorCount", "" + cursor.getCount());
			while (cursor.moveToNext()) {
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

				String parentName;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
					parentName = cursor.getString(parentColumn);
				}
				else{
					parentName = (new File(cursor.getString(parentColumn))).getParentFile().getName();
				}
				Log.e("parentName", parentName);
				boolean isParentFolderIsOk = true;
				if(SyncManager.getImportFrom(context).equals("camera_folder") && !parentName.equalsIgnoreCase("Camera")){
					isParentFolderIsOk = false;
				}

				if (contentUri != null && isParentFolderIsOk && !db.isIdExists(id)) {
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
						LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("MEDIA_ENC_FINISH"));
						if(SyncManager.isImportDeleteEnabled(context)){
							context.getApplicationContext().getContentResolver().delete(contentUri, null, null);
						}
					}
				}

				if(dateAdded > lastImportedFileDate){
					lastImportedFileDate = dateAdded;
				}
			}

			Helpers.storePreference(context, SyncManager.LAST_IMPORTED_FILE_DATE, lastImportedFileDate);

			db.close();
		}

		if(isSomethingImported){
			SyncManager.startSync(context);
		}

		return isSomethingImported;
	}
}
