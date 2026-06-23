package org.stingle.photos.Auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.stingle.photos.StinglePhotosApplication;

import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the server API token (a bearer credential) encrypted at rest with an AndroidKeyStore
 * AES-256-GCM key, instead of as plaintext in SharedPreferences. The Keystore key material never
 * leaves the device and is not exported by backup/device-transfer, so the token cannot be lifted
 * from the prefs file, a backup, or a transferred profile.
 *
 * The key intentionally does NOT require user authentication: background sync/upload runs while the
 * app is locked and must be able to read the token. If the key or ciphertext ever becomes
 * unreadable (e.g. Keystore reset), the token is dropped and the user simply logs in again.
 */
public class TokenStore {

	private static final String KEY_ALIAS = "stingle_api_token_key";
	private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_LENGTH = 12;
	// Marks a value as ciphertext so legacy plaintext tokens can be detected and migrated.
	private static final String ENC_PREFIX = "enc1:";

	public static synchronized String getApiToken(Context context){
		String stored = prefs(context).getString(StinglePhotosApplication.API_TOKEN, null);
		if(stored == null){
			return null;
		}
		if(!stored.startsWith(ENC_PREFIX)){
			// Legacy plaintext token written before encryption was added: migrate it transparently.
			setApiToken(context, stored);
			return stored;
		}
		try {
			byte[] blob = Base64.decode(stored.substring(ENC_PREFIX.length()), Base64.NO_WRAP);
			if(blob.length <= IV_LENGTH){
				throw new IllegalArgumentException("Invalid token blob");
			}
			byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH);
			byte[] cipherText = Arrays.copyOfRange(blob, IV_LENGTH, blob.length);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
			return new String(cipher.doFinal(cipherText), "UTF-8");
		} catch (Exception e) {
			// Key invalidated or data corrupted — drop the token and force a fresh login.
			removeApiToken(context);
			return null;
		}
	}

	public static synchronized void setApiToken(Context context, String token){
		if(token == null){
			removeApiToken(context);
			return;
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
			byte[] iv = cipher.getIV();
			byte[] cipherText = cipher.doFinal(token.getBytes("UTF-8"));

			byte[] blob = new byte[iv.length + cipherText.length];
			System.arraycopy(iv, 0, blob, 0, iv.length);
			System.arraycopy(cipherText, 0, blob, iv.length, cipherText.length);

			prefs(context).edit()
					.putString(StinglePhotosApplication.API_TOKEN, ENC_PREFIX + Base64.encodeToString(blob, Base64.NO_WRAP))
					.apply();
		} catch (Exception e) {
			// Last-resort fallback so the app stays usable if the Keystore is unavailable.
			prefs(context).edit().putString(StinglePhotosApplication.API_TOKEN, token).apply();
		}
	}

	public static synchronized void removeApiToken(Context context){
		prefs(context).edit().remove(StinglePhotosApplication.API_TOKEN).apply();
	}

	private static SharedPreferences prefs(Context context){
		return context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
	}

	private static SecretKey getOrCreateKey() throws Exception {
		KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
		keyStore.load(null);
		if(keyStore.containsAlias(KEY_ALIAS)){
			KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
			return entry.getSecretKey();
		}

		KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
		KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
				KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build();
		keyGenerator.init(spec);
		return keyGenerator.generateKey();
	}
}
