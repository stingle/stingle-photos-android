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
import org.stingle.photos.Util.Helpers;

import java.util.HashMap;

public class ReuploadKeysAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private Activity activity;
	private String password;
	private boolean isDeleteing;
	private ProgressDialog progressDialog;
	private OnAsyncTaskFinish onFinish;
	private StingleResponse response;


	public ReuploadKeysAsyncTask(Activity activity, String password, boolean isDeleteing, OnAsyncTaskFinish onFinish) {
		this.activity = activity;
		this.password = password;
		this.isDeleteing = isDeleteing;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(activity);
		if(isDeleteing) {
			progressDialog.setMessage(activity.getString(R.string.deleting_keys));
		}
		else{
			progressDialog.setMessage(activity.getString(R.string.uploading_keys));
		}
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... p) {
		try {
			HashMap<String, String> params = new HashMap<>();
			params.putAll(KeyManagement.getUploadKeyBundlePostParams(password, !isDeleteing));

			HashMap<String, String> postParams = new HashMap<>();

			postParams.put("token", KeyManagement.getApiToken(activity));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.reupload_keys), postParams);
			response = new StingleResponse(this.activity, resultJson, false);

			if (response.isStatusOk()) {
					return true;
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
			if(isDeleteing) {
				Helpers.showInfoDialog(activity, activity.getString(R.string.success_delete_keys));
			}
			else{
				Helpers.showInfoDialog(activity, activity.getString(R.string.success_backup_keys));
			}
			onFinish.onFinish();
		} else {
			onFinish.onFail();
			if (response!= null && response.areThereErrorInfos()) {
				response.showErrorsInfos();
			} else {
				Helpers.showAlertDialog(activity, activity.getString(R.string.fail_reg));
			}
		}

	}
}
