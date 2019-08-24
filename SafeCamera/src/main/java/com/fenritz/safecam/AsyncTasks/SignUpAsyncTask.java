package com.fenritz.safecam.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.GalleryActivity;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;

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

		HashMap<String, String> loginHash = SafeCameraApplication.getCrypto().getPasswordHashForStorage(password);

		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("email", email);
		postParams.put("password", loginHash.get("hash"));
		postParams.put("salt", loginHash.get("salt"));

		JSONObject resultJson = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.registration_path), postParams);
		response = new StingleResponse(this.context, resultJson, false);



		if(response.isStatusOk()) {
			String token = response.get("token");
			if(token != null) {

				KeyManagement.setApiToken(context, token);

				try {
					SafeCameraApplication.getCrypto().generateMainKeypair(password);

					boolean uploadResult = KeyManagement.uploadKeyBundle(context, password);
					if(uploadResult) {
						((SafeCameraApplication) context.getApplication()).setKey(SafeCameraApplication.getCrypto().getPrivateKey(password));
						Helpers.storePreference(context, SafeCameraApplication.USER_EMAIL, email);

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
