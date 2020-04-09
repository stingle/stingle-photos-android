package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MoveFileAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private ArrayList<StingleDbFile> files = null;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	private int fromSet = -1;
	private int toSet = -1;
	private String fromAlbumId = null;
	private String toAlbumId = null;
	private boolean isMoving = false;

	public MoveFileAsyncTask(Context context, ArrayList<StingleDbFile> files, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);
		this.files = files;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	public MoveFileAsyncTask setFromSet(int set){
		this.fromSet = set;
		return this;
	}
	public MoveFileAsyncTask setToSet(int set){
		this.toSet = set;
		return this;
	}
	public MoveFileAsyncTask setToAlbumId(String albumId){
		this.toAlbumId = albumId;
		return this;
	}
	public MoveFileAsyncTask setFromAlbumId(String albumId){
		this.fromAlbumId = albumId;
		return this;
	}
	public MoveFileAsyncTask setIsMoving(boolean isMoving){
		this.isMoving = isMoving;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}
		if(fromSet == -1 || toSet == -1){
			return false;
		}
		if(files == null){
			return false;
		}
		if(files.size() == 0){
			return true;
		}

		return SyncManager.moveFiles(myContext, files, fromSet, toSet, fromAlbumId, toAlbumId, isMoving);
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
