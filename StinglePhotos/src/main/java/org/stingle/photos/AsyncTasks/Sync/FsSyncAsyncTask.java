package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Query.FolderFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class FsSyncAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	protected SyncManager.OnFinish onFinish;

	public FsSyncAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}

		Log.d("fsSync", "START");

		boolean needToUpdateUI = false;

		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);
		FolderFilesDb folderFilesDb = new FolderFilesDb(myContext);


		File dir = new File(FileManager.getHomeDir(myContext));

		File[] currentFolderFiles = dir.listFiles();

		if(currentFolderFiles != null) {
			for (File file : currentFolderFiles) {
				boolean isOurFile = file.isFile() && file.getName().endsWith(StinglePhotosApplication.FILE_EXTENSION);
				boolean existsInGallery = galleryDb.getFileIfExists(file.getName()) != null;
				boolean existsInTrash = trashDb.getFileIfExists(file.getName()) != null;
				boolean existsInFolders = folderFilesDb.getFileIfExists(file.getName()) != null;

				if (isOurFile && !existsInGallery && !existsInTrash && !existsInFolders) {
					try {
						String headers = Crypto.getFileHeadersFromFile(file.getAbsolutePath(), FileManager.getThumbsDir(myContext) + "/" + file.getName());
						galleryDb.insertFile(file.getName(), true, false, GalleryTrashDb.INITIAL_VERSION, file.lastModified(), System.currentTimeMillis(), headers);
						Log.d("fsSync-add", file.getName());
						needToUpdateUI = true;
					} catch (IOException | CryptoException e) {
						e.printStackTrace();
					}

				}
			}
		}
		galleryDb.close();
		trashDb.close();
		folderFilesDb.close();

		return needToUpdateUI;
	}


	@Override
	protected void onPostExecute(Boolean needToUpdateUI) {
		super.onPostExecute(needToUpdateUI);

		if(onFinish != null){
			onFinish.onFinish(needToUpdateUI);
		}
	}
}
