package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public class DeleteAccountAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private final WeakReference<Activity> activity;
	private final String password;
	private ProgressDialog progressDialog;


	public DeleteAccountAsyncTask(Activity activity, String password){
		this.activity = new WeakReference<>(activity);
		this.password = password;
	}


	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		Activity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		progressDialog = new ProgressDialog(myActivity);
		progressDialog.setMessage(myActivity.getString(R.string.deleting_account));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Activity myActivity = activity.get();
		if(myActivity == null){
			return false;
		}

		String email = Helpers.getPreference(myActivity, StinglePhotosApplication.USER_EMAIL, "");
		HashMap<String, String> postParams = new HashMap<>();

		postParams.put("email", email);

		JSONObject resultJson = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + myActivity.getString(R.string.pre_login_path), postParams);
		StingleResponse response = new StingleResponse(myActivity, resultJson, false);


		if (response.isStatusOk()) {
			String salt = response.get("salt");
			if (salt != null) {

				final String loginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(password, salt);

				HashMap<String, String> postParams2 = new HashMap<>();
				postParams2.put("password", loginHash);

				try {
					HashMap<String, String> postParamsEnc2 = new HashMap<>();
					postParamsEnc2.put("token", KeyManagement.getApiToken(myActivity));
					postParamsEnc2.put("params", CryptoHelpers.encryptParamsForServer(postParams2));

					JSONObject resultJson2 = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + myActivity.getString(R.string.delete_account_url), postParamsEnc2);
					StingleResponse response2 = new StingleResponse(myActivity, resultJson2, true);

					if (response2.isStatusOk()) {
						FileManager.deleteRecursive(new File(FileManager.getHomeDir(myActivity)));
						return true;
					}
				} catch (CryptoException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}


	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		Activity myActivity = activity.get();
		if(myActivity == null){
			return;
		}

		if(result) {
			LoginManager.logoutLocally(myActivity);
		}
		progressDialog.dismiss();
	}
}
