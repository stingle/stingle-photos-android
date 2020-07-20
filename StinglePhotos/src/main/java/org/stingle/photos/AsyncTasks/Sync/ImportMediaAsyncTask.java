package org.stingle.photos.AsyncTasks.Sync;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
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

	public ImportMediaAsyncTask(Context context, OnAsyncTaskFinish onFinish){
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

		boolean isSomethingImported = false;

		int lastImportedFileDate = Helpers.getPreference(myContext, LAST_IMPORTED_FILE_DATE, 0);

		String[] projection = new String[] {
				MediaStore.Video.Media._ID,
				MediaStore.Video.Media.DISPLAY_NAME,
				MediaStore.Files.FileColumns.MEDIA_TYPE,
				MediaStore.Video.Media.DATE_ADDED
		};
		//String selection = MediaStore.Video.Media.DATE_ADDED + " >= ?";
		//String[] selectionArgs = new String[] { String.valueOf(TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES))};
		String selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
				+ MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
				+ " OR "
				+ MediaStore.Files.FileColumns.MEDIA_TYPE + "="
				+ MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")"
				+ " AND " + MediaStore.Video.Media.DATE_ADDED + " > ?";
		String[] selectionArgs = new String[] { String.valueOf(lastImportedFileDate) };

		String sortOrder = MediaStore.Video.Media.DATE_ADDED + " ASC";

		try (Cursor cursor = myContext.getApplicationContext().getContentResolver().query(
				MediaStore.Files.getContentUri("external"),
				projection,
				selection,
				selectionArgs,
				sortOrder
		)) {
			// Cache column indices.
			int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
			int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
			int typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE);
			int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);

			while (cursor.moveToNext()) {
				long id = cursor.getLong(idColumn);
				String name = cursor.getString(nameColumn);
				int type = cursor.getInt(typeColumn);
				int dateAdded = cursor.getInt(dateAddedColumn);

				Uri contentUri = null;
				if(type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
					contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
				}
				else if(type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
					contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
				}

				if(contentUri != null) {
					Log.e("uri", name + " - " + dateAdded + " - " + type + " - " + contentUri.toString());
					if(ImportFile.importFile(myContext, contentUri, SyncManager.GALLERY, null, this)){
						isSomethingImported = true;
					}
				}

				if(dateAdded > lastImportedFileDate){
					lastImportedFileDate = dateAdded;
				}
			}

			Helpers.storePreference(myContext, LAST_IMPORTED_FILE_DATE, lastImportedFileDate);
		}

		return isSomethingImported;
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
	}

}
