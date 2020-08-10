package org.stingle.photos.Sync;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {

	Context context;
	public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		this.context = context;
	}

	@NonNull
	@Override
	public Result doWork() {
		SyncManager.startSync(context, SyncAsyncTask.MODE_CLOUD_TO_LOCAL_AND_UPLOAD);
		return Result.success();
	}
}
