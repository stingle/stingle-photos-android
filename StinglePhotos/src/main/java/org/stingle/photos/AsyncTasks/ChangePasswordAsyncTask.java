package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;

import org.stingle.photos.Auth.FingerprintManagerWrapper;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import org.json.JSONObject;

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
	protected Boolean doInBackground(Void... params) {
		byte[] currentEncPrivKey = StinglePhotosApplication.getCrypto().getEncryptedPrivateKey();
		try {
			HashMap<String, String> newLoginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(newPassword);

			StinglePhotosApplication.getCrypto().reencryptPrivateKey(oldPassword, newPassword);

			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(activity));
			postParams.put("newPassword", newLoginHash.get("hash"));
			postParams.put("newSalt", newLoginHash.get("salt"));

			JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.change_pass_path), postParams);
			response = new StingleResponse(this.activity, resultJson, false);


			if (response.isStatusOk()) {
				String token = response.get("token");
				if (token != null) {

					KeyManagement.setApiToken(activity, token);

					boolean uploadResult = KeyManagement.uploadKeyBundle(activity, newPassword);
					if (uploadResult) {
						((StinglePhotosApplication) activity.getApplication()).setKey(StinglePhotosApplication.getCrypto().getPrivateKey(newPassword));
						FingerprintManagerWrapper.turnOffFingerprint(activity);
						return true;
					}

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
			if (response.areThereErrorInfos()) {
				response.showErrorsInfos();
			} else {
				Helpers.showAlertDialog(activity, activity.getString(R.string.fail_reg));
			}
		}

	}
}
