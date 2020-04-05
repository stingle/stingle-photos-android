package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.os.AsyncTask;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.util.HashMap;

public class SignUpAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected AppCompatActivity activity;
	protected String email;
	protected String password;
	protected Boolean isBackup;
	protected ProgressDialog progressDialog;
	protected StingleResponse response;


	public SignUpAsyncTask(AppCompatActivity activity, String email, String password, Boolean isBackup){
		this.activity = activity;
		this.email = email;
		this.password = password;
		this.isBackup = isBackup;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(activity);
		progressDialog.setMessage(activity.getString(R.string.creating_account));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {

		HashMap<String, String> loginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(password);

		HashMap<String, String> postParams = new HashMap<String, String>();
		try {
			StinglePhotosApplication.getCrypto().generateMainKeypair(password);

			postParams.put("email", email);
			postParams.put("password", loginHash.get("hash"));
			postParams.put("salt", loginHash.get("salt"));
			postParams.put("isBackup", (isBackup ? "1" : "0"));
			postParams.putAll(KeyManagement.getUploadKeyBundlePostParams(password, isBackup));

			JSONObject resultJson = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + activity.getString(R.string.registration_path), postParams);
			response = new StingleResponse(this.activity, resultJson, false);

			if (response.isStatusOk()) {
				return true;
			}
		}
		catch (CryptoException e) {
			e.printStackTrace();
		}

		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		progressDialog.dismiss();
		if(result) {
			LoginAsyncTask loginAsyncTask = new LoginAsyncTask(activity, email, password);
			loginAsyncTask.setPrivateKeyIsAlreadySaved(true);
			loginAsyncTask.execute();
		}
		else{
			if(response.areThereErrorInfos()) {
				response.showErrorsInfos();
			}
			else {
				Helpers.showAlertDialog(activity, activity.getString(R.string.fail_reg));
			}
		}

	}
}
