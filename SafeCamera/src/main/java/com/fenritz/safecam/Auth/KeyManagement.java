package com.fenritz.safecam.Auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Net.HttpsClient;

import java.io.IOException;
import java.util.HashMap;

public class KeyManagement {

	public static String getApiToken(Context context){
		SharedPreferences preferences = context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, context.MODE_PRIVATE);
		return preferences.getString(SafeCameraApplication.API_TOKEN, null);
	}

	public static void setApiToken(Context context, String token){
		SharedPreferences preferences = context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS,context. MODE_PRIVATE);
		preferences.edit().putString(SafeCameraApplication.API_TOKEN, token).commit();
	}

	public static boolean uploadKeyBundle(Context context, String password, HttpsClient.OnNetworkFinish onFinish){
		HashMap<String, String> postParams = new HashMap<String, String>();

		String keyBundle;
		String publicKey;
		try {
			byte[] keyBundleBytes = SafeCameraApplication.getCrypto().exportKeyBundle(password);
			keyBundle = Base64.encodeToString(keyBundleBytes, Base64.NO_WRAP);

			byte[] publicKeyBytes = SafeCameraApplication.getCrypto().exportPublicKey();
			publicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP);
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return false;
		}

		postParams.put("token", getApiToken(context));
		postParams.put("keyBundle", keyBundle);
		postParams.put("publicKey", publicKey);

		HttpsClient.post(context, context.getString(R.string.api_server_url) + context.getString(R.string.upload_key_bundle_path), postParams, onFinish);

		return true;
	}
}
