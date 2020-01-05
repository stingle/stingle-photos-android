package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Auth.BiometricsManagerWrapper;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.util.HashMap;

public class ChangePasswordAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected Activity activity;
	protected String oldPassword;
	protected String newPassword;
	protected ProgressDialog progressDialog;
	protected StingleResponse response;


	public ChangePasswordAsyncTask(Activity context, String oldPassword, String newPassword) {
		this.activity = context;
		this.oldPassword = oldPassword;
		this.newPassword = newPassword;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(activity);
		progressDialog.setMessage(activity.getString(R.string.changing_password));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... p) {
		byte[] currentEncPrivKey = StinglePhotosApplication.getCrypto().getEncryptedPrivateKey();
		try {
			HashMap<String, String> newLoginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(newPassword);

			StinglePhotosApplication.getCrypto().reencryptPrivateKey(oldPassword, newPassword);

			HashMap<String, String> params = new HashMap<>();
			params.put("newPassword", newLoginHash.get("hash"));
			params.put("newSalt", newLoginHash.get("salt"));
			params.putAll(KeyManagement.getUploadKeyBundlePostParams(newPassword, true));

			HashMap<String, String> postParams = new HashMap<>();

			postParams.put("token", KeyManagement.getApiToken(activity));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.change_pass_path), postParams);
			response = new StingleResponse(this.activity, resultJson, false);

			if (response.isStatusOk()) {
				String token = response.get("token");
				if (token != null) {
					KeyManagement.setApiToken(activity, token);

					StinglePhotosApplication.setKey(StinglePhotosApplication.getCrypto().getPrivateKey(newPassword));
					BiometricsManagerWrapper.turnOffBiometrics(activity);
					return true;
				}
			}
		} catch (CryptoException e) {
			e.printStackTrace();
		}

		StinglePhotosApplication.getCrypto().saveEncryptedPrivateKey(currentEncPrivKey);

		return false;

	}


	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		progressDialog.dismiss();
		if (result) {
			Helpers.showInfoDialog(activity, activity.getString(R.string.success_change_pass), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					activity.finish();
				}
			});
		} else {
			if (response!= null && response.areThereErrorInfos()) {
				response.showErrorsInfos();
			} else {
				Helpers.showAlertDialog(activity, activity.getString(R.string.fail_reg));
			}
		}

	}
}
