package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DeleteUrisAsyncTask extends AsyncTask<Void, Integer, Void> {

	private WeakReference<AppCompatActivity> activity;
	private ArrayList<Uri> uris;
	private FileManager.OnFinish onFinish;
	private ProgressDialog progress;
	private boolean deleteFailed = false;


	public DeleteUrisAsyncTask(AppCompatActivity activity, ArrayList<Uri> uris, FileManager.OnFinish onFinish){
		this.activity = new WeakReference<>(activity);
		this.uris = uris;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		progress = Helpers.showProgressDialogWithBar(myActivity, myActivity.getString(R.string.deleting_files), null, uris.size(), new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialogInterface) {
				Helpers.releaseWakeLock(myActivity);
				DeleteUrisAsyncTask.this.cancel(false);
				onFinish.onFinish();
			}
		});
		Helpers.acquireWakeLock(myActivity);
	}

	@Override
	protected Void doInBackground(Void... params) {
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return null;
		}
		int index = 0;

		for (Uri uri : uris) {
			Uri contentUri = getContentUri(myActivity, uri);
			if(contentUri != null) {
				try {
					myActivity.getApplicationContext().getContentResolver().delete(contentUri, null, null);
				}
				catch (Exception e){
					deleteFailed = true;
				}
			}
			else{
				deleteFailed = true;
			}


			publishProgress(index+1);
			index++;
		}

		return null;
	}

	public static Uri getContentUri(final Context context, final Uri uri) {
		// DocumentProvider
		if (DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Uri.fromFile(new File(Environment.getExternalStorageDirectory() + "/" + split[1]));
				}
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return Uri.fromFile(new File(getDataColumn(context, contentUri, null, null)));
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];
				final Long id = Long.parseLong(split[1]);

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
				} else if ("video".equals(type)) {
					contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
				} else if ("audio".equals(type)) {
					contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
				}

				return contentUri;
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {
			return uri;
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri;
		}

		return null;
	}

	public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};
		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}


	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
		progress.setProgress(values[0]);

	}

	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);

		progress.dismiss();

		if(onFinish != null){
			onFinish.onFinish();
		}
		AppCompatActivity myActivity = activity.get();
		if(myActivity != null){
			Helpers.releaseWakeLock(myActivity);
		}

		if(deleteFailed){
			Helpers.showAlertDialog(
					myActivity,
					myActivity.getString(R.string.delete_failed),
					myActivity.getString(R.string.delete_failed_desc)
			);
		}
	}
}
