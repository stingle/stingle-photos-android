package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DeleteAlbumAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	private String albumId = null;


	public DeleteAlbumAsyncTask(Context context, String albumId, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.albumId = albumId;;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(albumId == null){
			return false;
		}

		AlbumsDb albumsDb = new AlbumsDb(myContext);

		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
		ArrayList<StingleFile> files = new ArrayList<>();

		Cursor result = albumFilesDb.getAlbumFilesList(albumId, StingleDb.SORT_ASC, "");
		while(result.moveToNext()) {
			files.add(new StingleDbFile(result));
		}
		albumFilesDb.close();

		FileMoveAsyncTask moveTask = new FileMoveAsyncTask(myContext, files, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();

				albumsDb.deleteAlbum(albumId);
				albumsDb.close();
				onFinishListener.onFinish();
			}

			@Override
			public void onFail() {
				super.onFail();
				albumsDb.close();
				onFinishListener.onFail();
			}
		});
		moveTask.setFromFolder(SyncManager.FOLDER_ALBUM);
		moveTask.setFromFolderId(albumId);
		moveTask.setToFolder(SyncManager.FOLDER_TRASH);
		moveTask.setIsMoving(true);
		moveTask.execute();

		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);


	}
}
