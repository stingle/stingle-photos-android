package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DeleteFilesAsyncTask extends AsyncTask<Void, Void, Void> {

	private WeakReference<Context> context;
	protected ArrayList<StingleDbFile> files;
	protected SyncManager.OnFinish onFinish;

	public DeleteFilesAsyncTask(Context context, ArrayList<StingleDbFile> files, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.files = files;
		this.onFinish = onFinish;
	}

	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);

		String homeDir = FileManager.getHomeDir(myContext);
		String thumbDir = FileManager.getThumbsDir(myContext);

		ArrayList<String> filenamesToNotify = new ArrayList<String>();

		for(StingleDbFile file : files) {
			if (file.isRemote) {
				filenamesToNotify.add(file.filename);
			}
		}

		if (filenamesToNotify.size() == 0 || (filenamesToNotify.size() > 0 && SyncManager.notifyCloudAboutDelete(myContext, filenamesToNotify))) {

			for (StingleDbFile file : files) {
				boolean existsInGallery = galleryDb.getFileIfExists(file.filename) != null;
				boolean existsInAlbums = albumFilesDb.getFileIfExists(file.filename) != null;

				if(!existsInGallery && !existsInAlbums) {
					File mainFile = new File(homeDir + "/" + file.filename);
					File thumbFile = new File(thumbDir + "/" + file.filename);

					if (mainFile.exists()) {
						mainFile.delete();
					}
					if (thumbFile.exists()) {
						thumbFile.delete();
					}
					Log.d("deleteFiles", "deleted - " + file.filename);
				}
				else{
					Log.d("deleteFiles", "NOT deleted - " + file.filename);
				}

				trashDb.deleteFile(file.filename);
			}
		}

		galleryDb.close();
		albumFilesDb.close();
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
