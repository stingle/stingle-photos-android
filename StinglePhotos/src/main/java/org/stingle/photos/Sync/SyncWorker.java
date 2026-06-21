package org.stingle.photos.Sync;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {

	Context context;
	public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
		this.context = context;
	}

	@NonNull
	@Override
	public Result doWork() {
		// Run the sync synchronously and BLOCK until it finishes. SyncManager.startSync
		// kicks off an AsyncTask and returns immediately; if doWork() returned right away
		// (as it used to), WorkManager would consider the work done and stop keeping the
		// process alive, so the detached sync would get frozen/killed in the background.
		// Blocking here keeps the process alive (WorkManager holds a wakelock) for the
		// duration of the sync. MODE_CLOUD_TO_LOCAL_AND_UPLOAD also auto-imports new
		// device media, so this periodic run is the catch-up that imports photos/videos
		// even if the MediaStore content-trigger job was missed (battery optimization,
		// app-standby buckets, the process having been killed, etc.).
		final CountDownLatch latch = new CountDownLatch(1);

		SyncManager.startSync(context, SyncAsyncTask.MODE_CLOUD_TO_LOCAL_AND_UPLOAD, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				latch.countDown();
			}

			@Override
			public void onFail() {
				latch.countDown();
			}
		});

		try {
			// Cap the wait below WorkManager's ~10 minute execution limit.
			if (!latch.await(9, TimeUnit.MINUTES)) {
				return Result.retry();
			}
		} catch (InterruptedException e) {
			return Result.retry();
		}

		return Result.success();
	}
}
