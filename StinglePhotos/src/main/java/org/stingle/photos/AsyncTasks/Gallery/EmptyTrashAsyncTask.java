package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.FolderFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.lang.ref.WeakReference;

public class EmptyTrashAsyncTask extends AsyncTask<Void, Void, Void> {

	private WeakReference<Context> context;
	protected SyncManager.OnFinish onFinish;

	public EmptyTrashAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
	}

	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		if(!SyncManager.notifyCloudAboutEmptyTrash(myContext)){
			return null;
		}

		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		FolderFilesDb folderFilesDb = new FolderFilesDb(myContext);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);
		String homeDir = FileManager.getHomeDir(myContext);
		String thumbDir = FileManager.getThumbsDir(myContext);

		Cursor result = trashDb.getFilesList(GalleryTrashDb.GET_MODE_ALL, StingleDb.SORT_ASC, null, null);

		while(result.moveToNext()) {
			StingleDbFile dbFile = new StingleDbFile(result);

			boolean existsInGallery = galleryDb.getFileIfExists(dbFile.filename) != null;
			boolean existsInFolders = folderFilesDb.getFileIfExists(dbFile.filename) != null;

			if(!existsInGallery && !existsInFolders) {
				if (dbFile.isLocal) {
					File mainFile = new File(homeDir + "/" + dbFile.filename);
					if (mainFile.exists()) {
						mainFile.delete();
					}
				}
				File thumbFile = new File(thumbDir + "/" + dbFile.filename);
				if (thumbFile.exists()) {
					thumbFile.delete();
				}
			}

			trashDb.deleteFile(dbFile.filename);
		}

		result.close();
		galleryDb.close();
		folderFilesDb.close();
		trashDb.close();
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);

		if(onFinish != null){
			onFinish.onFinish(true);
		}
	}
}
