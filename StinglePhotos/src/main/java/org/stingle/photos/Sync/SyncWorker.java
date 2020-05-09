package org.stingle.photos.Sync;


import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.stingle.photos.Auth.LoginManager;

public class SyncWorker extends Worker {


	public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@NonNull
	@Override
	public Result doWork() {
		if(LoginManager.isLoggedIn(getApplicationContext())) {
			SyncManager.syncCloudToLocalDb(getApplicationContext(), null, AsyncTask.THREAD_POOL_EXECUTOR);
		}
		return Result.success();
	}
}
