package com.fenritz.safecamera.util;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import javax.crypto.Cipher;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Environment;
import android.util.Log;

import com.fenritz.safecamera.R;

public class Helpers {
	public static final String JPEG_FILE_PREFIX = "IMG_";
	
	public static String getMainDir(){
		File sdcard = Environment.getExternalStorageDirectory();
		return sdcard.toString() + "/SafeCamera/";
	}
	
	public static AESCrypt getAESCrypt(String password) throws NoSuchAlgorithmException, NoSuchProviderException, AESCryptException {
		return new AESCrypt(password);
	}
	
	public static String getFilename(String prefix) {
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = prefix + timeStamp;

		return imageFileName;
	}
	
	public static void printMaxKeySizes() {
		try {
			Set<String> algorithms = Security.getAlgorithms("Cipher");
			for (String algorithm : algorithms) {
				int max = Cipher.getMaxAllowedKeyLength(algorithm);
				Log.d("keys", String.format("%s: %dbit", algorithm, max));
			}
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	public static void showAlertDialog(Activity activity, String message){
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message);
        builder.setNegativeButton(activity.getString(R.string.ok), null);
        AlertDialog dialog = builder.create();
        dialog.show();
	}
}
