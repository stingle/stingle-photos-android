package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

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

public class DowngradeToFreeAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Activity> activity;
	private OnAsyncTaskFinish onFinish;
	private ProgressDialog progressDialog;

	public DowngradeToFreeAsyncTask(Activity activity, OnAsyncTaskFinish onFinish) {
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
	protected Boolean doInBackground(Void... vd) {
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

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + myContext.getString(R.string.get_billing_downgrade), postParams);
			StingleResponse response = new StingleResponse(myContext, json, true);

			if (response.isStatusOk()) {
				return true;
			}
		} catch (CryptoException ignored) {}
		return null;
	}


	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if (onFinish != null) {
			onFinish.onFinish(result);
		}
		progressDialog.dismiss();
	}
}
