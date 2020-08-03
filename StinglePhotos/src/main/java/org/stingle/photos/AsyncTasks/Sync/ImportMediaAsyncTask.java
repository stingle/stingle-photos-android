package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.OpenableColumns;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.ImportMedia;

import java.io.File;
import java.lang.ref.WeakReference;

public class ImportMediaAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected WeakReference<Context> context;
	protected File dir;
	protected File thumbDir;
	protected OnAsyncTaskFinish onFinish = null;

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

		return ImportMedia.importMedia(myContext);
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
