package org.stingle.photos.Crypto;

import org.json.JSONObject;
import org.stingle.photos.StinglePhotosApplication;

import java.util.HashMap;

public class CryptoHelpers {

	public static String encryptParamsForServer(HashMap<String, String> params) throws CryptoException {
		return  encryptParamsForServer(params, null, null);
	}

	public static String encryptParamsForServer(HashMap<String, String> params, byte[] serverPK, byte[] privateKey) throws CryptoException {
		JSONObject json = new JSONObject(params);

		if(serverPK == null){
			serverPK = StinglePhotosApplication.getCrypto().getServerPublicKey();
		}
		if(privateKey == null){
			privateKey = StinglePhotosApplication.getKey();
		}

		return Crypto.byteArrayToBase64Default(
				StinglePhotosApplication.getCrypto().encryptCryptoBox(
						json.toString().getBytes(),
						serverPK,
						privateKey
				)
		);
	}
}
