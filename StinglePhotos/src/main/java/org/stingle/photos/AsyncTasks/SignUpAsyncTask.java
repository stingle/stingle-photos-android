package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import org.json.JSONObject;

import java.util.HashMap;

public class SignUpAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected Activity context;
	protected String email;
	protected String password;
	protected ProgressDialog progressDialog;
	protected StingleResponse response;


	public SignUpAsyncTask(Activity context, String email, String password){
		this.context = context;
		this.email = email;
		this.password = password;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(context);
		progressDialog.setMessage(context.getString(R.string.creating_account));
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

		JSONObject resultJson = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.registration_path), postParams);
		response = new StingleResponse(this.context, resultJson, false);



		if(response.isStatusOk()) {
			String token = response.get("token");
			String homeFolder = response.get("homeFolder");
			if(token != null && homeFolder != null && token.length() > 0 && homeFolder.length() > 0) {

				KeyManagement.setApiToken(context, token);

				try {
					StinglePhotosApplication.getCrypto().generateMainKeypair(password);

					boolean uploadResult = KeyManagement.uploadKeyBundle(context, password);
					if(uploadResult) {
						((StinglePhotosApplication) context.getApplication()).setKey(StinglePhotosApplication.getCrypto().getPrivateKey(password));
						Helpers.storePreference(context, StinglePhotosApplication.USER_EMAIL, email);

						Helpers.storePreference(context, StinglePhotosApplication.USER_HOME_FOLDER, homeFolder);

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



	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		progressDialog.dismiss();
		if(result) {
			Intent intent = new Intent();
			intent.setClass(context, GalleryActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(intent);
			context.finish();
		}
		else{
			if(response.areThereErrorInfos()) {
				response.showErrorsInfos();
			}
			else {
				Helpers.showAlertDialog(context, context.getString(R.string.fail_reg));
			}
		}

	}
}
