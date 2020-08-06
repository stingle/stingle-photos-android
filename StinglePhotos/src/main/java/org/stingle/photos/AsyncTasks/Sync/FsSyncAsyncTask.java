package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.Sync.SyncSteps.FSSync;
import org.stingle.photos.Sync.SyncManager;

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

		return FSSync.sync(myContext);
	}


	@Override
	protected void onPostExecute(Boolean needToUpdateUI) {
		super.onPostExecute(needToUpdateUI);

		if(onFinish != null){
			onFinish.onFinish(needToUpdateUI);
		}
	}
}
