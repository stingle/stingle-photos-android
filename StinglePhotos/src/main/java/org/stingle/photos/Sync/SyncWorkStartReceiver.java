package org.stingle.photos.Sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SyncWorkStartReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.e("stingleStarted", "started");
		SyncManager.startPeriodicWork(context);
	}
}