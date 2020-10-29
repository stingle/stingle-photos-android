package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;

public class SetAlbumCoverAsyncTask extends AsyncTask<Void, Void, Boolean> {

	public static final int ALBUM_COVER_DEFAULT = 0;
	public static final int ALBUM_COVER_BLANK = 1;
	public static final int ALBUM_COVER_FILE = 2;
	public static final String ALBUM_COVER_BLANK_TEXT = "__b__";


	private WeakReference<Context> context;
	private String albumId;
	private int mode = ALBUM_COVER_DEFAULT;
	private String filename;
	private final OnAsyncTaskFinish onFinishListener;

	public SetAlbumCoverAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;

		this.onFinishListener = onFinishListener;
	}

	public SetAlbumCoverAsyncTask setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}
	public SetAlbumCoverAsyncTask setMode(int mode){
		this.mode = mode;
		return this;
	}
	public SetAlbumCoverAsyncTask setFilename(String filename){
		this.filename = filename;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null || albumId == null){
			return false;
		}

		AlbumsDb db = new AlbumsDb(myContext);

		StingleDbAlbum album = db.getAlbumById(albumId);

		if(album == null || !album.isOwner){
			return false;
		}

		switch (mode){
			case ALBUM_COVER_DEFAULT:
				album.cover = null;
				break;
			case ALBUM_COVER_BLANK:
				album.cover = ALBUM_COVER_BLANK_TEXT;
				break;
			case ALBUM_COVER_FILE:
				if(filename == null || filename.length() == 0){
					return false;
				}
				album.cover = filename;
				break;
		}

		boolean notifyResult = SyncManager.notifyCloudAboutCoverChange(myContext, album);
		if(notifyResult){
			db.updateAlbum(album);
			db.close();
			return true;
		}

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
