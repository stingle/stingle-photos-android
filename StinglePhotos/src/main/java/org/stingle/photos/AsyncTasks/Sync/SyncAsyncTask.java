package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncSteps.FSSync;
import org.stingle.photos.Sync.SyncSteps.ImportMedia;
import org.stingle.photos.Sync.SyncSteps.SyncCloudToLocalDb;
import org.stingle.photos.Sync.SyncSteps.UploadToCloud;

import java.lang.ref.WeakReference;

public class SyncAsyncTask extends AsyncTask<Void, Void, Boolean> {

	public static SyncAsyncTask instance;
	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;
	private int mode;

	static final public int MODE_FULL = 0;
	static final public int MODE_IMPORT_AND_UPLOAD = 1;
	static final public int MODE_CLOUD_TO_LOCAL = 2;

	public SyncAsyncTask(Context context) {
		this(context, MODE_FULL, null);
	}

	public SyncAsyncTask(Context context, int mode) {
		this(context, mode, null);
	}

	public SyncAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this(context, MODE_FULL, onFinishListener);
	}

	public SyncAsyncTask(Context context, int mode, OnAsyncTaskFinish onFinishListener) {
		instance = this;
		this.context = new WeakReference<>(context);;
		this.mode = mode;
		this.onFinishListener = onFinishListener;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}

		if(!LoginManager.isLoggedIn(myContext)){
			return false;
		}

		startSync(myContext);

		return true;
	}

	private void startSync(Context context){
		switch (mode){
			case MODE_FULL:
				syncCloudToLocalDb(context);
				FSSync(context);
				autoImport(context);
				upload(context);
				break;
			case MODE_IMPORT_AND_UPLOAD:
				autoImport(context);
				upload(context);
				break;
			case MODE_CLOUD_TO_LOCAL:
				syncCloudToLocalDb(context);
				break;
		}


		if(!isCancelled() && StinglePhotosApplication.syncRestartAfterFinish){
			StinglePhotosApplication.syncRestartAfterFinish = false;
			mode = StinglePhotosApplication.syncRestartAfterFinishMode;
			StinglePhotosApplication.syncRestartAfterFinishMode = MODE_FULL;
			startSync(context);
		}
	}

	public void FSSync(Context context){
		boolean needToUpdateUI = FSSync.sync(context);
		if(needToUpdateUI){
			LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("REFRESH_GALLERY"));
		}
	}

	public void autoImport(Context context){
		ImportMedia.importMedia(context);
	}

	public void syncCloudToLocalDb(Context context){
		boolean needToUpdateUI = (new SyncCloudToLocalDb(context)).sync();
		if (needToUpdateUI){
			LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("REFRESH_GALLERY"));
		}
	}

	public void upload(Context context){
		(new UploadToCloud(context,  this)).upload();

	}



	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(onFinishListener != null) {
			if (result) {
				onFinishListener.onFinish();
			} else {
				onFinishListener.onFail();
			}
		}
		instance = null;

	}


}
