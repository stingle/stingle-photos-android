package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.os.AsyncTask;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
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
	private Boolean localOnly;
	private OnAsyncTaskFinish onFinish;

	private ProgressDialog progressDialog;
	private boolean showProgress = true;
	private boolean uploadPrivateKey = true;

	public SetNewPasswordAsyncTask(AppCompatActivity context, String email, String phrase, String password, OnAsyncTaskFinish onFinish){
		this(context, email, phrase, password, false, onFinish);
	}

	public SetNewPasswordAsyncTask(AppCompatActivity context, String email, String phrase, String password, Boolean localOnly, OnAsyncTaskFinish onFinish){
		this.activity = context;
		this.email = email;
		this.phrase = phrase;
		this.password = password;
		this.localOnly = localOnly;
		this.onFinish = onFinish;
	}

	public SetNewPasswordAsyncTask setShowProgress(boolean showProgress){
		this.showProgress = showProgress;
		return this;
	}

	public SetNewPasswordAsyncTask setUploadPrivateKey(boolean uploadPrivateKey){
		this.uploadPrivateKey = uploadPrivateKey;
		return this;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		if(showProgress) {
			progressDialog = new ProgressDialog(activity);
			progressDialog.setMessage(activity.getString(R.string.setting_new_password));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.setCancelable(false);
			progressDialog.show();
		}
	}

	@Override
	protected Boolean doInBackground(Void... p) {
		try {
			byte[] privateKey = MnemonicUtils.generateKey(activity, phrase);
			if(!MnemonicUtils.validateMnemonic(activity, phrase)){
				return false;
			}

			byte[] publicKey = StinglePhotosApplication.getCrypto().getPublicKeyFromPrivateKey(privateKey);

			String serverPKB64 = StinglePhotosApplication.getTempStore("serverPK");
			if(serverPKB64 == null){
				return false;
			}
			byte[] serverPK = Crypto.base64ToByteArray(serverPKB64);

			StinglePhotosApplication.getCrypto().generateMainKeypair(password, privateKey, publicKey);

			if(localOnly) {
				StinglePhotosApplication.setKey(privateKey);
				return true;
			}
			else{
				HashMap<String, String> loginHash = StinglePhotosApplication.getCrypto().getPasswordHashForStorage(password);

				HashMap<String, String> params = new HashMap<>();

				params.put("newPassword", loginHash.get("hash"));
				params.put("newSalt", loginHash.get("salt"));
				params.putAll(KeyManagement.getUploadKeyBundlePostParams(password, uploadPrivateKey));

				HashMap<String, String> postParams = new HashMap<>();
				postParams.put("email", email);
				postParams.put("params", CryptoHelpers.encryptParamsForServer(params, serverPK, privateKey));

				JSONObject resultJson = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + activity.getString(R.string.recover_account), postParams);
				StingleResponse response = new StingleResponse(this.activity, resultJson, true);

				if (response.isStatusOk()) {
					String result = response.get("result");
					if (result != null && result.equals("OK")) {
						return true;
					}
				}
			}
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(showProgress) {
			progressDialog.dismiss();
		}

		if(result) {
			onFinish.onFinish();
			if(!localOnly) {
				LoginAsyncTask loginAsyncTask = new LoginAsyncTask(activity, email, password);
				loginAsyncTask.setPrivateKeyIsAlreadySaved(true);
				loginAsyncTask.execute();
			}
		}
		else{
			StinglePhotosApplication.getCrypto().deleteKeys();
			onFinish.onFail();
			Helpers.showAlertDialog(activity, activity.getString(R.string.error), activity.getString(R.string.something_went_wrong));
		}
	}
}
