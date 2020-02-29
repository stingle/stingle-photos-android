package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;

import java.util.HashMap;

public class GetServerPKAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private Context activity;


	public GetServerPKAsyncTask(Context context){
		this.activity = context;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(activity));

		JSONObject resultJson = HttpsClient.postFunc(activity.getString(R.string.api_server_url) + activity.getString(R.string.get_server_pk), postParams);
		StingleResponse response = new StingleResponse(this.activity, resultJson, false);

		if (response.isStatusOk()) {

			String serverPK = response.get("serverPK");
			if(serverPK != null){
				KeyManagement.importServerPublicKey(serverPK);
				return true;
			}
		}

		return false;
	}
}
