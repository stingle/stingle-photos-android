package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.stingle.photos.Auth.BiometricsManagerWrapper;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.GalleryActivity;
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
	protected ProgressDialog progressDialog;
	protected StingleResponse response;


	public SignUpAsyncTask(AppCompatActivity activity, String email, String password){
		this.activity = activity;
		this.email = email;
		this.password = password;
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

		postParams.put("email", email);
		postParams.put("password", loginHash.get("hash"));
		postParams.put("salt", loginHash.get("salt"));

		JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.registration_path), postParams);
		response = new StingleResponse(this.activity, resultJson, false);



		if(response.isStatusOk()) {
			String token = response.get("token");
			String homeFolder = response.get("homeFolder");
			String userId = response.get("userId");
			if(token != null && homeFolder != null && userId != null && token.length() > 0 && homeFolder.length() > 0 && userId.length() > 0) {

				KeyManagement.setApiToken(activity, token);

				try {
					StinglePhotosApplication.getCrypto().generateMainKeypair(password);

					boolean uploadResult = KeyManagement.uploadKeyBundle(activity, password);
					if(uploadResult) {
						StinglePhotosApplication.setKey(StinglePhotosApplication.getCrypto().getPrivateKey(password));

						Helpers.storePreference(activity, StinglePhotosApplication.USER_ID, userId);
						Helpers.storePreference(activity, StinglePhotosApplication.USER_EMAIL, email);
						Helpers.storePreference(activity, StinglePhotosApplication.USER_HOME_FOLDER, homeFolder);

						return true;
					}
				}
				catch (CryptoException e) {
					e.printStackTrace();
				}
			}
		}

		return false;

	}

	private void gotoGallery(){
		Intent intent = new Intent();
		intent.setClass(activity, GalleryActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		activity.startActivity(intent);
		activity.finish();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		progressDialog.dismiss();
		if(result) {
			BiometricsManagerWrapper biometricsManagerWrapper = new BiometricsManagerWrapper(activity);
			if(biometricsManagerWrapper.isBiometricsAvailable()) {
				Helpers.showConfirmDialog(activity, activity.getString(R.string.enable_biometrics),
						(dialog, which) -> {
							biometricsManagerWrapper.setupBiometrics(null, password, new BiometricsManagerWrapper.BiometricsSetupCallback() {
								@Override
								public void onSuccess() {
									gotoGallery();
								}

								@Override
								public void onFailed() {
									gotoGallery();
								}
							});

						},
						(dialog, which) -> {
							gotoGallery();
						});
			}
			else{
				gotoGallery();
			}
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
