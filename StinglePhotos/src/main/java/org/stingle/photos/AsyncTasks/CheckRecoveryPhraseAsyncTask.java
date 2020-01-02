package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.os.AsyncTask;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.MnemonicUtils;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.util.HashMap;

public class CheckRecoveryPhraseAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private AppCompatActivity activity;
	private String email;
	private String phrase;
	private ProgressDialog progressDialog;
	private StingleResponse response;
	private OnAsyncTaskFinish onFinish;


	public CheckRecoveryPhraseAsyncTask(AppCompatActivity context, String email, String phrase, OnAsyncTaskFinish onFinish){
		this.activity = context;
		this.email = email;
		this.phrase = phrase;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(activity);
		progressDialog.setMessage(activity.getString(R.string.checking_phrase));
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
			postParams.put("justCheck", "yes");

			JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.check_key), postParams);
			response = new StingleResponse(this.activity, resultJson, false);

			if (response.isStatusOk() && response.get("result").equals("OK")) {
				return true;

			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		progressDialog.dismiss();

		if(result) {
			onFinish.onFinish();
		}
		else{
			onFinish.onFail();
			if(response.areThereErrorInfos()) {
				response.showErrorsInfos();
			}
			else {
				Helpers.showAlertDialog(activity, activity.getString(R.string.fail_phrase_check));
			}
		}
	}
}
