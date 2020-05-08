package org.stingle.photos.Sync;


import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {


	public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@NonNull
	@Override
	public Result doWork() {
		SyncManager.syncCloudToLocalDb(getApplicationContext(), null, AsyncTask.THREAD_POOL_EXECUTOR);
		return Result.success();
	}
}
