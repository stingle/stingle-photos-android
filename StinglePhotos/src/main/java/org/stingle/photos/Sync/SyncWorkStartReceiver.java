package org.stingle.photos.Sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SyncWorkStartReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SyncManager.startPeriodicWork(context);
	}
}