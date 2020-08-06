package org.stingle.photos.Sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.stingle.photos.Sync.JobScheduler.ImportJobSchedulerService;

public class BootCompleteReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.e("stingleStarted", "started");
		if (SyncManager.isImportEnabled(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			ImportJobSchedulerService.scheduleJob(context);
			SyncManager.startPeriodicWork(context);
		}
	}
}