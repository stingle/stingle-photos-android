package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.UploadToCloud;

import java.lang.ref.WeakReference;

public class UploadToCloudAsyncTask extends AsyncTask<Void, Void, Void> {

	private WeakReference<Context> context;
	private UploadToCloud.UploadProgress progress;
	private SyncManager.OnFinish onFinish;

	public UploadToCloudAsyncTask(Context context, UploadToCloud.UploadProgress progress, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.progress = progress;
		this.onFinish = onFinish;
	}

	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}

		(new UploadToCloud(myContext, progress, this)).upload();
		return null;
	}


	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);

		if(onFinish != null){
			onFinish.onFinish(false);
		}
	}
}
