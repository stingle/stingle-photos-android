package org.stingle.photos.Billing;

import android.app.Activity;

import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.R;
import org.stingle.photos.StorageActivity;

public class PlayBillingProxy {
	public static boolean isPlayBillingAvailable(Activity activity){
		final Boolean[] isPlayBillingAvailable = {true};
		PlayBilling billing = new PlayBilling(activity, new BillingEventsListener() {
			@Override
			public void playBillingNotAvailable() {
				isPlayBillingAvailable[0] = false;
			}
		});
		return isPlayBillingAvailable[0];
		//return true;
	}

	public static void initiatePayment(StorageActivity activity, String plan){
		PlayBilling billing = new PlayBilling(activity, new BillingEventsListener() {
			@Override
			public void playBillingNotAvailable() {
				makeSnackBar(activity.getString(R.string.play_billing_unavailable));
			}
			@Override
			public void refresh() {
				activity.syncAndUpdateQuota();
			}

			@Override
			public void makeSnackBar(String text) {
				Snackbar.make(activity.findViewById(R.id.drawer_layout), text, Snackbar.LENGTH_LONG).show();
			}
		});
		billing.pay(plan);
	}
}
