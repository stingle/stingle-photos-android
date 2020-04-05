package org.stingle.photos.Billing;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.util.HashMap;

public class StingleBilling {

	public static class NotifyServerAboutPurchase extends AsyncTask<Void, Void, Boolean> {

		private Context context;
		private String productId;
		private String token;
		private OnFinish onFinish = null;

		public static String PREF_BILLING_PLAN = "billing_plan";
		public static String PREF_BILLING_PURCHASE_TOKEN = "billing_purchase_token";
		public static String BILLING_PLAN_FREE = "plan_free";
		public static String BILLING_PLAN_PAID = "plan_paid";

		public NotifyServerAboutPurchase(Context context, String productId, String token, OnFinish onFinish){
			this.context = context;
			this.productId = productId;
			this.token = token;
			this.onFinish = onFinish;
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("productId", productId);
			postParams.put("purchaseToken", token);


			JSONObject resp = HttpsClient.postFunc(
					StinglePhotosApplication.getApiUrl() + context.getString(R.string.notify_purchase),
					postParams
			);
			StingleResponse response = new StingleResponse(this.context, resp, false);
			if(response.isStatusOk()) {
				Helpers.storePreference(context, PREF_BILLING_PLAN, productId);
				Helpers.storePreference(context, PREF_BILLING_PURCHASE_TOKEN, token);

				String spaceUsedStr = response.get("spaceUsed");
				String spaceQuotaStr = response.get("spaceQuota");


				if(spaceUsedStr != null && spaceUsedStr.length() > 0){
					int spaceUsed = Integer.parseInt(spaceUsedStr);
					if(spaceUsed >= 0){
						Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_USED, spaceUsed);
					}
				}

				if(spaceQuotaStr != null && spaceQuotaStr.length() > 0){
					int spaceQuota = Integer.parseInt(spaceQuotaStr);
					if(spaceQuota >= 0){
						Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, spaceQuota);
					}
				}
				return true;
			}

			return false;
		}



		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish(result);
			}
		}
	}

	public static abstract class OnFinish{
		public abstract void onFinish(boolean isSuccess);
	}
}
