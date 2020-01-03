package org.stingle.photos.Auth;

import android.content.Context;
import android.content.SharedPreferences;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.StinglePhotosApplication;

import java.io.IOException;
import java.util.HashMap;

public class KeyManagement {

	public static String getApiToken(Context context){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, context.MODE_PRIVATE);
		return preferences.getString(StinglePhotosApplication.API_TOKEN, null);
	}

	public static void setApiToken(Context context, String token){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS,context. MODE_PRIVATE);
		preferences.edit().putString(StinglePhotosApplication.API_TOKEN, token).apply();
	}

	public static void removeApiToken(Context context){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS,context. MODE_PRIVATE);
		preferences.edit().remove(StinglePhotosApplication.API_TOKEN).apply();
	}

	public static HashMap<String, String> getUploadKeyBundlePostParams(String password, boolean includePrivateKey){
		HashMap<String, String> postParams = new HashMap<String, String>();

		try {
			byte[] keyBundleBytes;
			if(includePrivateKey) {
				keyBundleBytes = StinglePhotosApplication.getCrypto().exportKeyBundle(password);
			}
			else{
				keyBundleBytes = StinglePhotosApplication.getCrypto().exportPublicKey();
			}
			String keyBundle = Crypto.byteArrayToBase64Default(keyBundleBytes);
			postParams.put("keyBundle", keyBundle);

		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		return postParams;
	}

	public static boolean importKeyBundle(String keyBundle, String password){
		try {
			byte[] keyBundleBytes = Crypto.base64ToByteArrayDefault(keyBundle);

			StinglePhotosApplication.getCrypto().importKeyBundle(keyBundleBytes, password);
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public static boolean importServerPublicKey(String publicKey){
		try {
			byte[] pk = Crypto.base64ToByteArrayDefault(publicKey);

			StinglePhotosApplication.getCrypto().importServerPublicKey(pk);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public static void deleteLocalKeys(){
		StinglePhotosApplication.getCrypto().deleteKeys();
	}
}
