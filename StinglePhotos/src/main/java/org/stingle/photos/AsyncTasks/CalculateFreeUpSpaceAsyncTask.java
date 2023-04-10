package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AutoCloseableCursor;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.lang.ref.WeakReference;

public class CalculateFreeUpSpaceAsyncTask extends AsyncTask<Void, Void, Long> {

	private final File dir;
	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;

	public CalculateFreeUpSpaceAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.onFinishListener = onFinishListener;

		dir = new File(FileManager.getHomeDir(context));
	}


	@Override
	protected Long doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return 0L;
		}

		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);

		long totalSize = 0;

		totalSize += getTotalFilesSize(galleryDb);
		totalSize += getTotalFilesSize(trashDb);
		totalSize += getTotalFilesSize(albumFilesDb);

		galleryDb.close();
		trashDb.close();
		albumFilesDb.close();

		return totalSize;
	}

	private long getTotalFilesSize(FilesDb db){
		long totalSize = 0;

		try(AutoCloseableCursor autoCloseableCursor = db.getFilesList(FilesDb.GET_MODE_LOCAL_AND_REMOTE, StingleDb.SORT_ASC, null, null)){
			Cursor result = autoCloseableCursor.getCursor();
			while (result.moveToNext()) {
				String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FILENAME));
				File file = new File(dir.getPath() + "/" + filename);
				totalSize += file.length();
			}
			result.close();
		}
		catch (Exception e){
			e.printStackTrace();
		}

		return totalSize;
	}

	@Override
	protected void onPostExecute(Long result) {
		super.onPostExecute(result);

		onFinishListener.onFinish(result);
	}
}
