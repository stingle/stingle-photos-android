package org.stingle.photos.AsyncTasks.Sync;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Query.ImportedIdsDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ImportFile;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.lang.ref.WeakReference;

public class ImportMediaAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected WeakReference<Context> context;
	protected File dir;
	protected File thumbDir;
	protected OnAsyncTaskFinish onFinish = null;

	public static final String LAST_IMPORTED_FILE_DATE = "last_imported_date";

	private static ImportMediaAsyncTask instance;

	public static ImportMediaAsyncTask getInstance(){
		return instance;
	}

	public ImportMediaAsyncTask(Context context, OnAsyncTaskFinish onFinish){
		instance = this;
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
		dir = new File(FileManager.getHomeDir(context));
		thumbDir = new File(FileManager.getThumbsDir(context));
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}
		Log.e("EnteredImportMedia", "1");
		boolean isSomethingImported = false;

		long lastImportedFileDate = Helpers.getPreference(myContext, LAST_IMPORTED_FILE_DATE, (long)0);

		String[] projection = new String[0];
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
			projection = new String[] {
					MediaStore.MediaColumns._ID,
					MediaStore.MediaColumns.DISPLAY_NAME,
					MediaStore.Files.FileColumns.MEDIA_TYPE,
					MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
					MediaStore.MediaColumns.DATE_TAKEN
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
		//String selection = MediaStore.Video.Media.DATE_ADDED + " >= ?";
		//String[] selectionArgs = new String[] { String.valueOf(TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES))};
		String selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
				+ MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
				+ " OR "
				+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
				+ MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
			selection += " AND " + MediaStore.MediaColumns.DATE_TAKEN + " > ?";
		}
		else {
			selection += " AND " + MediaStore.MediaColumns.DATE_ADDED + " >= ?";
		}

		String[] selectionArgs = new String[] { String.valueOf(lastImportedFileDate) };

		String sortOrder;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
			sortOrder = MediaStore.MediaColumns.DATE_TAKEN + " ASC";
		}
		else{
			sortOrder = MediaStore.MediaColumns.DATE_ADDED + " ASC";
		}

		try (Cursor cursor = myContext.getApplicationContext().getContentResolver().query(
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


			int dateAddedColumn;
			int parentColumn;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
				dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
				parentColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
			}
			else{
				dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
				parentColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
			}


			ImportedIdsDb db = new ImportedIdsDb(myContext);
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
				if(SyncManager.getImportFrom(myContext).equals("camera_folder") && !parentName.equalsIgnoreCase("Camera")){
					isParentFolderIsOk = false;
				}

				if (contentUri != null && isParentFolderIsOk && !db.isIdExists(id)) {
					db.insertImportedId(id);
					Log.e("uri", name + " - " + dateAdded + " - " + type + " - " + contentUri.toString());

					long dateAddedMillis;
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
						dateAddedMillis = dateAdded;
					} else {
						dateAddedMillis = dateAdded * 1000;
					}

					if (ImportFile.importFile(myContext, contentUri, SyncManager.GALLERY, null, dateAddedMillis, this)) {
						isSomethingImported = true;
						LocalBroadcastManager.getInstance(myContext).sendBroadcast(new Intent("MEDIA_ENC_FINISH"));
						if(SyncManager.isImportDeleteEnabled(myContext)){
							myContext.getApplicationContext().getContentResolver().delete(contentUri, null, null);
						}
					}
				}

				if(dateAdded > lastImportedFileDate){
					lastImportedFileDate = dateAdded;
				}
			}

			Helpers.storePreference(myContext, LAST_IMPORTED_FILE_DATE, lastImportedFileDate);

			db.close();
		}

		if(isSomethingImported){
			SyncManager.startSync(myContext);
		}

		return isSomethingImported;
	}


	private String getFilename(Context context, Uri uri){
		Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);

		int displayNameIndex = returnCursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
		return returnCursor.getString(displayNameIndex);
	}

	/*private void listBuckets(Context context){
		String[] PROJECTION_BUCKET = {
				MediaStore.Files.FileColumns.PARENT,
				MediaStore.Images.ImageColumns.DATA};

		String selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
				+ MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
				+ " OR "
				+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
				+ MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";

		Cursor cur = context.getContentResolver().query(
				MediaStore.Files.getContentUri("external"), PROJECTION_BUCKET, selection, null, null);

		if (cur.moveToFirst()) {
			String data;
			int dataColumn = cur.getColumnIndex(MediaStore.Images.Media.DATA);

			do {
				data = cur.getString(dataColumn);
				File file = new File(data);
				String parent = cur.getString(cur.getColumnIndex(MediaStore.Files.FileColumns.PARENT));

				Log.e("image", data + " - " + parent + " - " + file.getParentFile().getName());
			} while (cur.moveToNext());
		}
	}*/

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(onFinish != null){
			onFinish.onFinish(result);
		}

		instance = null;
	}

}
