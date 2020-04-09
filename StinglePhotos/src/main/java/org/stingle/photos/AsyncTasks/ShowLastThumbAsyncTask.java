package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.ImageView;

import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Sync.SyncManager;

public class ShowLastThumbAsyncTask extends AsyncTask<Void, Void, StingleDbFile> {

	private Context context;
	private ImageView imageView;
	private int set = SyncManager.GALLERY;
	private Integer thumbSize = null;

	public ShowLastThumbAsyncTask(Context context, ImageView imageView) {
		this.context = context;
		this.imageView = imageView;
	}

	public ShowLastThumbAsyncTask setSet(int set){
		this.set = set;
		return this;
	}

	public ShowLastThumbAsyncTask setThumbSize(int size){
		thumbSize = size;
		return this;
	}

	@Override
	protected StingleDbFile doInBackground(Void... params) {
		GalleryTrashDb db = new GalleryTrashDb(context, SyncManager.GALLERY);
		return db.getFileAtPosition(0, null, StingleDb.SORT_DESC);
	}

	@Override
	protected void onPostExecute(StingleDbFile dbFile) {
		super.onPostExecute(dbFile);

		if(dbFile != null) {
			boolean isRemote = false;
			if (!dbFile.isLocal && dbFile.isRemote) {
				isRemote = true;
			}

			(new ShowEncThumbInImageView(context, dbFile.filename, imageView))
					.setIsRemote(isRemote)
					.setThumbSize(thumbSize)
					.setSet(set)
					.setHeaders(dbFile.headers)
					.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}
}
