package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import org.json.JSONObject;

import java.util.HashMap;

public class LoginAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected Activity context;
	protected String email;
	protected String password;
	protected ProgressDialog progressDialog;
	protected StingleResponse response;


	public LoginAsyncTask(Activity context, String email, String password){
		this.context = context;
		this.email = email;
		this.password = password;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(context);
		progressDialog.setMessage(context.getString(R.string.signing_in));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {

		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("email", email);

		JSONObject resultJson = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.pre_login_path), postParams);
		response = new StingleResponse(this.context, resultJson, false);


		if (response.isStatusOk()) {
			String salt = response.get("salt");
			if (salt != null) {

				final String loginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(password, salt);
				Log.d("loginhash", loginHash);
				HashMap<String, String> postParams2 = new HashMap<String, String>();

				postParams2.put("email", email);
				postParams2.put("password", loginHash);

				JSONObject resultJson2 = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.login_path), postParams2);
				response = new StingleResponse(this.context, resultJson2, false);


				if (response.isStatusOk()) {
					String token = response.get("token");
					String keyBundle = response.get("keyBundle");
					String userId = response.get("userId");
					String homeFolder = response.get("homeFolder");
					if (token != null && keyBundle != null && homeFolder != null && userId != null && token.length() > 0 && keyBundle.length() > 0 && homeFolder.length() > 0 && userId.length() > 0) {
						try {
							boolean importResult = KeyManagement.importKeyBundle(context, keyBundle, password);

							if (!importResult) {
								return false;
							}

							KeyManagement.setApiToken(context, token);
							Helpers.storePreference(context, StinglePhotosApplication.USER_ID, userId);
							Helpers.storePreference(context, StinglePhotosApplication.USER_EMAIL, email);
							Helpers.storePreference(context, StinglePhotosApplication.USER_HOME_FOLDER, homeFolder);

							StinglePhotosApplication.setKey(StinglePhotosApplication.getCrypto().getPrivateKey(password));

							return true;
						} catch (CryptoException e) {
							e.printStackTrace();
							return false;
						}
					}
				}
			}
		}


		return false;
	}



	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result) {
			SyncManager.syncFSToDB(context, new SyncManager.OnFinish() {
				@Override
				public void onFinish(Boolean needToUpdateUI) {
					progressDialog.dismiss();
					Intent intent = new Intent();
					intent.setClass(context, GalleryActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					context.startActivity(intent);
					context.finish();
				}
			}, AsyncTask.SERIAL_EXECUTOR);
		}
		else{
			if(response.areThereErrorInfos()) {
				response.showErrorsInfos();
			}
			else {
				Helpers.showAlertDialog(context, context.getString(R.string.fail_login));
			}
		}

	}
}
