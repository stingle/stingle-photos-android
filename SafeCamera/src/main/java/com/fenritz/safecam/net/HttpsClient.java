package com.fenritz.safecam.net;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.Helpers;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class HttpsClient {

	public static void post(String urlStr, HashMap<String, String> params, OnNetworkFinish onFinish) {
		new NetworkRequest(urlStr, params, onFinish).execute();
	}

	public static JSONObject postFunc(String urlStr, HashMap<String, String> params) {
		JSONObject json = null;
		try {
			URL url = new URL(urlStr);
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

			// Create the SSL connection
			SSLContext sc;
			sc = SSLContext.getInstance("TLS");
			sc.init(null, null, new java.security.SecureRandom());
			conn.setSSLSocketFactory(sc.getSocketFactory());

			// Use this if you need SSL authentication
			//String userpass = user + ":" + password;
			//String basicAuth = "Basic " + Base64.encodeToString(userpass.getBytes(), Base64.DEFAULT);
			//conn.setRequestProperty("Authorization", basicAuth);

			// set Timeout and method
			conn.setReadTimeout(7000);
			conn.setConnectTimeout(7000);
			conn.setRequestMethod("POST");
			conn.setDoInput(true);

			// Add any data you wish to post here
			String data = "";
			for (String key : params.keySet()) {
				data += URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(params.get(key), "UTF-8") + "&";
			}

			if (data.length() > 0) {
				data = data.substring(0, data.length() - 1);

				OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());

				wr.write(data);
				wr.flush();
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line = null;

			// Read Server Response
			while ((line = reader.readLine()) != null) {
				// Append server response in string
				sb.append(line + "\n");
			}

			json = new JSONObject(sb.toString());

			reader.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}

		//conn.connect();
		return json;
	}

	public static class NetworkRequest extends AsyncTask<Void, Void, JSONObject> {

		protected String url;
		protected HashMap<String, String> params;
		protected OnNetworkFinish onFinish;

		public NetworkRequest(String url, HashMap<String, String> params, OnNetworkFinish onFinish){
			this.url = url;
			this.params = params;
			this.onFinish = onFinish;
		}

		@Override
		protected JSONObject doInBackground(Void... params) {
			return postFunc(this.url, this.params);
		}

		@Override
		protected void onPostExecute(JSONObject result) {
			super.onPostExecute(result);
			onFinish.onFinish(result);
		}
	}

	abstract public static class OnNetworkFinish {
		abstract public void onFinish(JSONObject result);
	}

}
