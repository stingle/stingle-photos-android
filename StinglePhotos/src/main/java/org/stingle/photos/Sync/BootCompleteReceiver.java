package org.stingle.photos.Sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.stingle.photos.Sync.JobScheduler.ImportJobSchedulerService;

public class BootCompleteReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("stingleStarted", "started");
		if (SyncManager.isImportEnabled(context)) {
			ImportJobSchedulerService.scheduleJob(context);
		}
		SyncManager.startPeriodicWork(context);
		SyncManager.startSync(context);
	}
}