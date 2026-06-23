package org.stingle.photos.Auth;

import android.content.Context;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.StinglePhotosApplication;

import java.io.IOException;
import java.util.HashMap;

public class KeyManagement {

	public static String getApiToken(Context context){
		// Token is encrypted at rest with an AndroidKeyStore key. See TokenStore.
		return TokenStore.getApiToken(context);
	}

	public static void setApiToken(Context context, String token){
		TokenStore.setApiToken(context, token);
	}

	public static void removeApiToken(Context context){
		TokenStore.removeApiToken(context);
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
			String keyBundle = Crypto.byteArrayToBase64(keyBundleBytes);
			postParams.put("keyBundle", keyBundle);

		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		return postParams;
	}

	public static boolean importKeyBundle(String keyBundle, String password){
		try {
			byte[] keyBundleBytes = Crypto.base64ToByteArray(keyBundle);

			StinglePhotosApplication.getCrypto().importKeyBundle(keyBundleBytes, password);
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public static boolean importServerPublicKey(String publicKey){
		try {
			byte[] pk = Crypto.base64ToByteArray(publicKey);

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
