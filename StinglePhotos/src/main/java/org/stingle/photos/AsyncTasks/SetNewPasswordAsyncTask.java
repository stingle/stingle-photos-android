package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Base64;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.MnemonicUtils;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.util.HashMap;

public class SetNewPasswordAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private AppCompatActivity activity;
	private String email;
	private String phrase;
	private String password;
	private ProgressDialog progressDialog;
	private OnAsyncTaskFinish onFinish;


	public SetNewPasswordAsyncTask(AppCompatActivity context, String email, String phrase, String password, OnAsyncTaskFinish onFinish){
		this.activity = context;
		this.email = email;
		this.phrase = phrase;
		this.password = password;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(activity);
		progressDialog.setMessage(activity.getString(R.string.setting_new_password));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		try {
			byte[] privateKey = MnemonicUtils.generateKey(activity, phrase);
			if(!MnemonicUtils.validateMnemonic(activity, phrase)){
				return false;
			}

			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("email", email);
			postParams.put("skHash", Crypto.getKeyHash(privateKey));
			postParams.put("justCheck", "no");

			JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.check_key), postParams);
			StingleResponse response = new StingleResponse(this.activity, resultJson, true);

			if (response.isStatusOk() && response.get("result").equals("OK")) {
				if(response.get("token") == null ||	response.get("publicKey") == null){
					return false;
				}

				String token = response.get("token");
				KeyManagement.setApiToken(activity, token);
				byte[] publicKeyExport = Base64.decode(response.get("publicKey"), Base64.NO_WRAP);
				byte[] publicKey = Crypto.getPublicKeyFromExport(publicKeyExport);

				try {
					StinglePhotosApplication.getCrypto().generateMainKeypair(password, privateKey, publicKey);

					boolean uploadResult = KeyManagement.uploadKeyBundle(activity, password);
					if(uploadResult) {
						if(changePasswordHashesOnServer(token)) {
							return true;
						}
					}
				}
				catch (CryptoException e) {
					e.printStackTrace();
				}

				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	private boolean changePasswordHashesOnServer(String token){
		HashMap<String, String> newLoginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(password);

		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", token);
		postParams.put("newPassword", newLoginHash.get("hash"));
		postParams.put("newSalt", newLoginHash.get("salt"));
		postParams.put("dontIssueToken", "1");

		JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.change_pass_path), postParams);
		StingleResponse response = new StingleResponse(activity, resultJson, true);

		if (response.isStatusOk()) {
			return true;
		}
		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		progressDialog.dismiss();

		if(result) {
			onFinish.onFinish();
			(new LoginAsyncTask(activity, email, password)).execute();
		}
		else{
			onFinish.onFail();
			Helpers.showAlertDialog(activity, activity.getString(R.string.something_went_wrong));
		}
	}
}
