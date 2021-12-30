package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.util.HashMap;

public class ChangeEmailAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected Activity activity;
	protected String newEmail;
	protected ProgressDialog progressDialog;
	protected StingleResponse response;
	private final OnAsyncTaskFinish onFinishListener;


	public ChangeEmailAsyncTask(Activity context, String newEmail, OnAsyncTaskFinish onFinishListener) {
		this.activity = context;
		this.newEmail = newEmail;
		this.onFinishListener = onFinishListener;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(activity);
		progressDialog.setMessage(activity.getString(R.string.changing_email));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... p) {
		if(newEmail == null || newEmail.length() == 0){
			return false;
		}
		try {
			HashMap<String, String> params = new HashMap<>();
			params.put("newEmail", newEmail);

			HashMap<String, String> postParams = new HashMap<>();

			postParams.put("token", KeyManagement.getApiToken(activity));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject resultJson = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + activity.getString(R.string.change_email_path), postParams);
			response = new StingleResponse(this.activity, resultJson, false);

			if (response.isStatusOk()) {
				String recvEmail = response.get("email");
				if (recvEmail != null) {
					Helpers.storePreference(activity, StinglePhotosApplication.USER_EMAIL, recvEmail);
					return true;
				}
			}
		} catch (CryptoException e) {
			e.printStackTrace();
		}

		return false;

	}


	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		progressDialog.dismiss();
		if (result) {
			Helpers.showInfoDialog(activity, activity.getString(R.string.success), activity.getString(R.string.success_change_email), null, null);
			onFinishListener.onFinish();
		} else {
			if (response!= null && response.areThereErrorInfos()) {
				response.showErrorsInfos();
			} else {
				Helpers.showAlertDialog(activity, activity.getString(R.string.error), activity.getString(R.string.fail_reg));
			}
			onFinishListener.onFail();
		}

	}
}
