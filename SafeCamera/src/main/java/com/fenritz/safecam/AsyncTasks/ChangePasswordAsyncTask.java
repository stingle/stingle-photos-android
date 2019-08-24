package com.fenritz.safecam.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;

import com.fenritz.safecam.Auth.FingerprintManagerWrapper;
import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.GalleryActivity;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;

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
		byte[] currentEncPrivKey = SafeCameraApplication.getCrypto().getEncryptedPrivateKey();
		try {
			HashMap<String, String> newLoginHash = SafeCameraApplication.getCrypto().getPasswordHashForStorage(newPassword);

			SafeCameraApplication.getCrypto().reencryptPrivateKey(oldPassword, newPassword);

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
						((SafeCameraApplication) activity.getApplication()).setKey(SafeCameraApplication.getCrypto().getPrivateKey(newPassword));
						FingerprintManagerWrapper fingerprintManager = new FingerprintManagerWrapper(activity);

						if(!fingerprintManager.isFingerprintAvailable()){
							fingerprintManager.turnOffFingerprint();
						}
						return true;
					}

				}
			}
		} catch (CryptoException e) {
			e.printStackTrace();
		}

		SafeCameraApplication.getCrypto().saveEncryptedPrivateKey(currentEncPrivKey);

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
