package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;

public class LeaveAlbumAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private String albumId;
	private final OnAsyncTaskFinish onFinishListener;

	public LeaveAlbumAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;

		this.onFinishListener = onFinishListener;
	}

	public LeaveAlbumAsyncTask setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}

		AlbumsDb db = new AlbumsDb(myContext);
		AlbumFilesDb filesDb = new AlbumFilesDb(myContext);

		StingleDbAlbum album = db.getAlbumById(albumId);

		if(album == null || album.isOwner || !album.isShared){
			return false;
		}

		boolean notifyResult = SyncManager.notifyCloudAboutAlbumLeave(myContext, album);
		if(notifyResult){
			filesDb.deleteAlbumFilesIfNotNeeded(myContext, albumId);
			filesDb.deleteAllFilesInAlbum(albumId);
			db.deleteAlbum(albumId);
			filesDb.close();
			db.close();
			return true;
		}

		filesDb.close();
		db.close();
		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result){
			onFinishListener.onFinish();
		}
		else{
			onFinishListener.onFail();
		}

	}
}
