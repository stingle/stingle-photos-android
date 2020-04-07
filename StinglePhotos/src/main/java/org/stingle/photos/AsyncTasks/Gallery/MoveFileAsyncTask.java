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

	private int fromFolder = -1;
	private int toFolder = -1;
	private String fromFolderId = null;
	private String toFolderId = null;
	private boolean isMoving = false;

	public MoveFileAsyncTask(Context context, ArrayList<StingleDbFile> files, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);
		this.files = files;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	public MoveFileAsyncTask setFromFolder(int folder){
		this.fromFolder = folder;
		return this;
	}
	public MoveFileAsyncTask setToFolder(int folder){
		this.toFolder = folder;
		return this;
	}
	public MoveFileAsyncTask setToFolderId(String folderId){
		this.toFolderId = folderId;
		return this;
	}
	public MoveFileAsyncTask setFromFolderId(String folderId){
		this.fromFolderId = folderId;
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
		if(fromFolder == -1 || toFolder == -1){
			return false;
		}
		if(files == null){
			return false;
		}
		if(files.size() == 0){
			return true;
		}

		return SyncManager.moveFiles(myContext, files, fromFolder, toFolder, fromFolderId, toFolderId, isMoving);
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
