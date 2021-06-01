package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public class GetBillingInfoAsyncTask extends AsyncTask<Void, Void, GetBillingInfoAsyncTask.BillingInfo> {

	private WeakReference<Activity> activity;
	private OnAsyncTaskFinish onFinish;
	private ProgressDialog progressDialog;

	public GetBillingInfoAsyncTask(Activity activity, OnAsyncTaskFinish onFinish) {
		this.activity = new WeakReference<>(activity);;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		Activity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		progressDialog = new ProgressDialog(myActivity);
		progressDialog.setMessage(myActivity.getString(R.string.loading));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected GetBillingInfoAsyncTask.BillingInfo doInBackground(Void... vd) {
		Context myContext = activity.get();
		if(myContext == null){
			return null;
		}

		HashMap<String, String> params = new HashMap<>();

		params.put("rand", StinglePhotosApplication.getCrypto().getRandomString(12));

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(myContext));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + myContext.getString(R.string.get_billing_info), postParams);
			StingleResponse response = new StingleResponse(myContext, json, true);

			if (response.isStatusOk()) {
				if(!response.has("plan") || !response.has("expiration") || !response.has("paymentGw") || !response.has("spaceUsed") || !response.has("spaceQuota") || !response.has("isManual")){
					return null;
				}

				GetBillingInfoAsyncTask.BillingInfo info = new BillingInfo();
				info.plan = response.get("plan");
				info.expiration = response.get("expiration");
				info.paymentGw = response.get("paymentGw");
				info.spaceUsed = response.get("spaceUsed");
				info.spaceQuota = response.get("spaceQuota");
				info.isManual = response.get("isManual").equals("1");
				return info;
			}
		} catch (CryptoException ignored) {}
		return null;
	}


	@Override
	protected void onPostExecute(GetBillingInfoAsyncTask.BillingInfo result) {
		super.onPostExecute(result);

		if(result == null){
			Activity myActivity = activity.get();
			if(myActivity == null){
				return;
			}
			myActivity.finish();
			Toast.makeText(myActivity, myActivity.getString(R.string.error_loading_storage), Toast.LENGTH_LONG).show();
		}

		if (onFinish != null) {
			onFinish.onFinish(result);
		}
		progressDialog.dismiss();
	}

	public static class BillingInfo{
		public String plan;
		public String expiration;
		public String paymentGw;
		public String spaceUsed;
		public String spaceQuota;
		public boolean isManual;
	}
}
