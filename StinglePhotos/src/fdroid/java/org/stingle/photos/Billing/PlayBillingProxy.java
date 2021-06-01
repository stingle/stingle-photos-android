package org.stingle.photos.Billing;

import android.app.Activity;

public class PlayBillingProxy {
	public static void playBillingListener(Activity activity, BillingEventsListener listener){
		listener.playBillingNotAvailable();
	}

	public static void initiatePayment(Activity activity, String plan){
		return;
	}
}
