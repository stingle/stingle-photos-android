package org.stingle.photos.AsyncTasks;

import android.os.AsyncTask;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;

import java.util.HashMap;

public class SetSKHashAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private AppCompatActivity activity;
	private OnAsyncTaskFinish onFinish;


	public SetSKHashAsyncTask(AppCompatActivity activity, OnAsyncTaskFinish onFinish){
		this.activity = activity;
		this.onFinish = onFinish;
	}


	@Override
	protected Boolean doInBackground(Void... params) {

		String skHash = Crypto.getKeyHash(StinglePhotosApplication.getKey());

		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(activity));
		postParams.put("skHash", skHash);

		JSONObject json = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.set_sk_hash), postParams);
		StingleResponse response = new StingleResponse(activity, json, false);

		if(response.isStatusOk()) {
			return true;
		}
		return false;

	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		onFinish.onFinish(result);
	}
}
