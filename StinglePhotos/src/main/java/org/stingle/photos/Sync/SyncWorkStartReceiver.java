package org.stingle.photos.Sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class SyncWorkStartReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.e("stingleStarted", "started");
		SyncManager.startPeriodicWork(context);
		if (SyncManager.isImportEnabled(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			JobSchedulerService.scheduleJob(context);
		}
	}
}