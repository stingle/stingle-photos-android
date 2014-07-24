package com.fenritz.safecam.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.fenritz.safecam.GalleryActivity;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraActivity;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.util.AsyncTasks.ReEncryptFiles;

public class Helpers {
	public static final String JPEG_FILE_PREFIX = "IMG_";
	protected static final int SHARE_AS_IS = 0;
	protected static final int SHARE_REENCRYPT = 1;
	protected static final int SHARE_DECRYPT = 2;
	
	public static final int HASH_SYNC_NOTHING_DONE = 0;
	public static final int HASH_SYNC_UPDATED_PREF = 1;
	public static final int HASH_SYNC_WROTE_FILE = 2;
	
	public static boolean isDemo(Context context){
		PackageManager manager = context.getPackageManager();
		if(manager.checkSignatures(context.getString(R.string.main_package_name), context.getString(R.string.key_package_name)) == PackageManager.SIGNATURE_MATCH) {
		    return false;
		}
		return true;
	}
	
	public static boolean checkLoginedState(Activity activity) {
		return checkLoginedState(activity, null, true);
	}
	public static boolean checkLoginedState(Activity activity, Bundle extraData) {
		return checkLoginedState(activity, extraData, true);
	}
	public static boolean checkLoginedState(Activity activity, Bundle extraData, boolean redirect) {
		String key = ((SafeCameraApplication) activity.getApplicationContext()).getKey();

		if (key == null) {
			if(redirect){
				doLogout(activity);
				redirectToLogin(activity, extraData);
			}
			return false;
		}

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		int lockTimeout = Integer.valueOf(sharedPrefs.getString("lock_time", "60")) * 1000;

		long currentTimestamp = System.currentTimeMillis();
		long lockedTime = activity.getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, Context.MODE_PRIVATE).getLong(SafeCameraActivity.LAST_LOCK_TIME, 0);;

		if (lockedTime != 0) {
			if (currentTimestamp - lockedTime > lockTimeout) {
				doLogout(activity);
				if(redirect){
					redirectToLogin(activity, extraData);
				}
				return false;
			}
		}
		
		return true;
	}

	public static void setLockedTime(Context context) {
		context.getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(SafeCameraActivity.LAST_LOCK_TIME, System.currentTimeMillis()).commit();
	}

	public static void disableLockTimer(Context context) {
		context.getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(SafeCameraActivity.LAST_LOCK_TIME, 0).commit();
	}

	public static void logout(Activity activity){
		doLogout(activity);
		redirectToLogin(activity, null);
	}
	
	private static void redirectToLogin(Activity activity, Bundle extraData) {
		Intent intent = new Intent();
		intent.setClass(activity, SafeCameraActivity.class);
		intent.putExtra(SafeCameraActivity.ACTION_JUST_LOGIN, true);
		intent.putExtra(SafeCameraActivity.PARAM_EXTRA_DATA, extraData);
		activity.startActivityForResult(intent, GalleryActivity.REQUEST_LOGIN);
	}
	
	private static void doLogout(Activity activity) {
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction("com.fenritz.safecam.ACTION_LOGOUT");
		activity.sendBroadcast(broadcastIntent);

		((SafeCameraApplication) activity.getApplication()).setKey(null);
		
		deleteTmpDir(activity);
	}

	public static void deleteTmpDir(Context context) {
		File dir = new File(Helpers.getHomeDir(context) + "/" + ".tmp");
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				new File(dir, children[i]).delete();
			}
		}
	}

	public static void registerForBroadcastReceiver(final Activity activity) {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		activity.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				activity.finish();
			}
		}, intentFilter);
	}

	public static AESCrypt getAESCrypt(Context context) {
		return getAESCrypt(null, context);
	}

	public static AESCrypt getAESCrypt(String pKey, Context context) {
		String keyToUse;
		if (pKey != null) {
			keyToUse = pKey;
		}
		else{
			// Lilitiky dmboya
			keyToUse = ((SafeCameraApplication) context.getApplicationContext()).getKey();
		}

		return new AESCrypt(keyToUse);
	}

	public static String getFilename(Context context, String prefix) {
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = prefix + timeStamp;

		return imageFileName + ".jpg";
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

	public static void showAlertDialog(Activity activity, String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(message);
		builder.setNegativeButton(activity.getString(R.string.ok), null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public static String getHomeDir(Context context) {
		String sdcardPath = Environment.getExternalStorageDirectory().getPath();
		
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		String defaultHomeDir = sdcardPath + "/" + context.getString(R.string.default_home_folder_name);
		String homeDirPath = sharedPrefs.getString("home_folder", defaultHomeDir);
		if(new File(homeDirPath).exists()){
			return homeDirPath;
		}
		return defaultHomeDir;
	}

	public static String getThumbsDir(Context context) {
		return getThumbsDir(context, null);
	}
	
	public static String getThumbsDir(Context context, String homeDir) {
		if(homeDir != null){
			return homeDir + "/" + context.getString(R.string.default_thumb_folder_name);
		}
		else{
			return getHomeDir(context) + "/" + context.getString(R.string.default_thumb_folder_name);
		}
	}

	public static void createFolders(Context context) {
		createFolders(context, null);
	}
	
	public static void createFolders(Context context, String homeDir) {
		File dir = new File(getThumbsDir(context, homeDir));
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdirs();
		}
		
		File tmpFile;
		if(homeDir != null){
			tmpFile = new File(homeDir + "/.tmp/");
		}
		else{
			tmpFile = new File(Helpers.getHomeDir(context) + "/.tmp/");
		}
		if (!tmpFile.exists() || !tmpFile.isDirectory()) {
			tmpFile.mkdirs();
		}
	}
	
	
	public static void writeLoginHashToFile(Context context, String hash){
		writeLoginHashToFile(context, hash, null);
	}
	public static void writeLoginHashToFile(Context context, String hash, String homeDir){
		File hashFile;
		if(homeDir != null){
			hashFile = new File(homeDir + "/.hash");
		}
		else{
			hashFile = new File(Helpers.getHomeDir(context) + "/.hash");
		}
		
		try {
			FileOutputStream out = new FileOutputStream(hashFile);
			out.write(hash.getBytes());
			out.close();
		}
		catch (FileNotFoundException e) {}
		catch (IOException e) {}
	}
	
	public static void removeLoginHashFile(Context context){
		File hashFile = new File(Helpers.getHomeDir(context) + "/.hash");
		if(hashFile.exists() && hashFile.isFile()){
			hashFile.delete();
		}
	}
	
	public static String readLoginHashFromFile(Context context){
		return readLoginHashFromFile(context, null);
	}
	public static String readLoginHashFromFile(Context context, String homeDir){
		File hashFile;
		
		if(homeDir != null){
			hashFile = new File(homeDir + "/.hash");
		}
		else{
			hashFile = new File(Helpers.getHomeDir(context) + "/.hash");
		}
		
		if(hashFile.exists() && hashFile.isFile()){
			try {
				FileInputStream in = new FileInputStream(hashFile);
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				int numRead = 0;
				byte[] buf = new byte[1024];
				while ((numRead = in.read(buf)) >= 0) {
					bytes.write(buf, 0, numRead);
				}
				in.close();
				
				return bytes.toString();
			}
			catch (FileNotFoundException e) {}
			catch (IOException e) {}
		}
		
		return null;
	}
	
	
	public static int synchronizePasswordHash(Context context){
		return synchronizePasswordHash(context, null);
	}
	public static int synchronizePasswordHash(Context context, String homeDir){
		SharedPreferences preferences = context.getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, Activity.MODE_PRIVATE);
		String fileHash = readLoginHashFromFile(context, homeDir);
		String preferenceHash = null;
		if (preferences.contains(SafeCameraActivity.PASSWORD)){
			preferenceHash = preferences.getString(SafeCameraActivity.PASSWORD, null);
		}
		
		if((preferenceHash == null && fileHash != null) || (preferenceHash != null && fileHash != null && !preferenceHash.equals(fileHash))) {
			preferences.edit().putString(SafeCameraActivity.PASSWORD, fileHash).commit();
			return HASH_SYNC_UPDATED_PREF;
		}
		else if(preferenceHash != null && fileHash == null) {
			writeLoginHashToFile(context, preferenceHash, homeDir);
			return HASH_SYNC_WROTE_FILE;
		}
		
		return HASH_SYNC_NOTHING_DONE;
	}

	public static Bitmap generateThumbnail(Context context, byte[] data, String fileName) throws FileNotFoundException {
		Bitmap bitmap = decodeBitmap(data, getThumbSize(context));
		
		Bitmap thumbBitmap = null;
		if (bitmap != null) {
			thumbBitmap = Helpers.getThumbFromBitmap(bitmap, getThumbSize(context));

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			thumbBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);

			FileOutputStream out = new FileOutputStream(Helpers.getThumbsDir(context) + "/" + fileName);
			Helpers.getAESCrypt(context).encrypt(stream.toByteArray(), out);
		}
		return thumbBitmap;
	}
	
	public static int getThumbSize(Context context){
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);
		
		if(metrics.widthPixels <= metrics.heightPixels){
			return (int) Math.floor(metrics.widthPixels / 3);
		}
		else{
			return (int) Math.floor(metrics.heightPixels / 3);
		}
	}

	public static Bitmap getThumbFromBitmap(Bitmap bitmap, int squareSide) {
		int imgWidth = bitmap.getWidth();
		int imgHeight = bitmap.getHeight();

		int cropX, cropY, cropWidth, cropHeight;
		if (imgWidth >= imgHeight) {
			cropX = imgWidth / 2 - imgHeight / 2;
			cropY = 0;
			cropWidth = imgHeight;
			cropHeight = imgHeight;
		}
		else {
			cropX = 0;
			cropY = imgHeight / 2 - imgWidth / 2;
			cropWidth = imgWidth;
			cropHeight = imgWidth;
		}

		Bitmap cropedImg = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight);

		return Bitmap.createScaledBitmap(cropedImg, squareSide, squareSide, true);
	}

	public static int getExifRotation(byte[] data){
		try {
			return getRotationFromMetadata(Sanselan.getMetadata(data));
		}
		catch (ImageReadException e) {}
		catch (IOException e) {}
		
		return 0;
	}
	
	public static int getExifRotation(File file){
		try {
			return getRotationFromMetadata(Sanselan.getMetadata(file));
		}
		catch (ImageReadException e) {}
		catch (IOException e) {}
		
		return 0;
	}
	
	public static int getAltExifRotation(BufferedInputStream stream){
		try{
			return getRotationFromMetadata(ImageMetadataReader.readMetadata(stream, false));
		}
		catch (ImageProcessingException e) {}
		catch (IOException e) {}
		
		return 0;
	}
	
	public static int getAltExifRotation(File file){
		try{
			return getRotationFromMetadata(ImageMetadataReader.readMetadata(new BufferedInputStream(new FileInputStream(file)), false));
		}
		catch (ImageProcessingException e) {}
		catch (IOException e) {}
		
		return 0;
	}
	
	private static int getRotationFromMetadata(IImageMetadata meta){
		int currentRotation = 1;
		try {
			JpegImageMetadata metaJpg = null;
			TiffField orientationField = null;
			
			if(meta instanceof JpegImageMetadata){
				metaJpg = (JpegImageMetadata) meta;
			}
			
			if (null != metaJpg){
				orientationField =  metaJpg.findEXIFValue(TiffConstants.EXIF_TAG_ORIENTATION);
				if(orientationField != null){
					currentRotation = orientationField.getIntValue();
				}
			}
		}
		catch (ImageReadException e1) {}
		
		switch(currentRotation){
			case 3:
				//It's 180 deg now
				return 180;
			case 6:
				//It's 90 deg now
				return 90;
			case 8:
				//It's 270 deg now
				return 270;
			default:
				//It's 0 deg now
				return 0;
		}
	}
	
	private static int getRotationFromMetadata(Metadata metadata){
		try {
			ExifIFD0Directory directory = metadata.getDirectory(ExifIFD0Directory.class);
			if(directory != null){
				int exifRotation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
				
				switch(exifRotation){
					case 3:
						//It's 180 deg now
						return 180;
					case 6:
						//It's 90 deg now
						return 90;
					case 8:
						//It's 270 deg now
						return 270;
					default:
						//It's 0 deg now
						return 0;
				}
			}
		}
		catch (MetadataException e) {}
		return 0;
	}
	
	public static Bitmap getRotatedBitmap(Bitmap bitmap, int deg){
		if(bitmap != null){
			Matrix matrix = new Matrix();
			matrix.postRotate(deg);

			return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		}
		return null;
	}
	
	public static Bitmap decodeBitmap(byte[] data, int requiredSize) {
		return decodeBitmap(data, requiredSize, false);
	}
	
	public static Bitmap decodeBitmap(byte[] data, int requiredSize, boolean isFront) {
		if(data != null){
			Integer rotation = getAltExifRotation(new BufferedInputStream(new ByteArrayInputStream(data)));
			
			if(rotation == 90 && isFront){
				rotation = 270;
			}
			else if(rotation == 270 && isFront){
				rotation = 90;
			}
			
			// Decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(data, 0, data.length, o);
	
			// Find the correct scale value. It should be the power of 2.
			requiredSize = requiredSize * requiredSize;
			int scale = 1;
		    while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) > requiredSize) {
		    	scale++;
		    }
			
			// Decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			
			if(rotation != null){
				return getRotatedBitmap(BitmapFactory.decodeByteArray(data, 0, data.length, o2), rotation);
			}
			else{
				return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
			}
		}
		return null;
	}
	
	public static Bitmap decodeFile(InputStream stream, int requiredSize) {
		Integer rotation = getAltExifRotation(new BufferedInputStream(stream));
		
		// Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(stream, null, o);

		// Find the correct scale value. It should be the power of 2.
		requiredSize = requiredSize * requiredSize;
		int scale = 1;
	    while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) > requiredSize) {
	    	scale++;
	    }

		// Decode with inSampleSize
		BitmapFactory.Options o2 = new BitmapFactory.Options();
		o2.inSampleSize = scale;
		
		//stream.reset();
		if(rotation != null){
			return getRotatedBitmap(BitmapFactory.decodeStream(stream, null, o2), rotation);
		}
		else{
			return BitmapFactory.decodeStream(stream, null, o2);
		}
	}
	
	public static String getThumbFileName(File file){
		return getThumbFileName(file.getPath());
	}
	
	public static String getThumbFileName(String filePath){
		try{
			return AESCrypt.byteToHex(AESCrypt.getHash(filePath, "SHA-1"));
		}
		catch(AESCryptException e){
			return filePath;
		}
	}
	
	public static String getRealPathFromURI(Activity activity, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = activity.managedQuery(contentUri, proj, null, null, null);
        if(cursor != null){
	        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	        cursor.moveToFirst();
	        return cursor.getString(column_index);
        }
        
        return null;
    }
	
	public static String getNewDestinationPath(Context context, String path, String fileName){
		return getNewDestinationPath(context, path, fileName, null);
	}
	
	public static String getNewDestinationPath(Context context, String path, String fileName, AESCrypt crypt){
		return path + "/" + getNextAvailableFilePrefix(path) + encryptFilename(context, fileName, crypt);
	}
	
	public static String getNextAvailableFilePrefix(String path){
		File dir = new File(path);
		File[] folderFiles = dir.listFiles();

		int maxNumber = 0;
		
		if(folderFiles != null){
			for (File file : folderFiles) {
				String fileName = file.getName();
				Pattern p = Pattern.compile("^zzSC\\-\\d+\\_.+");
				Matcher m = p.matcher(fileName);
				if (m.find()) {
					try{
						int num = Integer.parseInt(fileName.substring(5, fileName.indexOf("_")));
						if(num > maxNumber){
							maxNumber = num;
						}
					}
					catch(NumberFormatException e){}
				}
			}
		}
		
		return "zzSC-" + String.valueOf(maxNumber+1) + "_";
	}
	
	public static String encryptFilename(Context context, String fileName){
		return encryptFilename(context, fileName, null);
	}
	
	public static String encryptFilename(Context context, String fileName, AESCrypt crypt){
		return encryptString(context, fileName, crypt) + context.getString(R.string.file_extension);
	}
	
	public static String encryptString(Context context, String fileName){
		return encryptString(context, fileName, null);
	}
	
	public static String encryptString(Context context, String fileName, AESCrypt crypt){
		if(crypt != null){
			return crypt.encrypt(fileName);
		}
		else{
			return getAESCrypt(context).encrypt(fileName);
		}
	}
	
	public static String decryptFilename(Context context, String fileName){
		return decryptFilename(context, fileName, null);
	}
	
	public static String decryptFilename(Context context, String fileName, AESCrypt crypt){
		String encryptedString = fileName;
		
		int extensionIndex = fileName.indexOf(context.getString(R.string.file_extension));
		if(extensionIndex > 0){
			encryptedString = fileName.substring(0, extensionIndex);
		}
		
		if(encryptedString.length() >= 4 && encryptedString.substring(0, 4).equals("zzSC")){
			encryptedString = encryptedString.substring(fileName.indexOf("_")+1);
		}
		
		String decryptedFilename;
		if(crypt != null){
			decryptedFilename = crypt.decrypt(encryptedString);
		}
		else{
			decryptedFilename = getAESCrypt(context).decrypt(encryptedString);
		}

		
		if(decryptedFilename == null){
			String extension = context.getString(R.string.file_extension);
			
			if(fileName.endsWith(extension)){
				fileName = fileName.substring(0, fileName.length() - extension.length());
			}
			
			return fileName;
		}
		return decryptedFilename;
	}
	
	/*public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName) {
		return findNewFileNameIfNeeded(context, filePath, fileName, null);
	}
	
	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName, Integer number) {
		if (number == null) {
			number = 1;
		}

		File file;
		boolean isEncryptedFile = false;
		if(fileName.endsWith(context.getString(R.string.file_extension))){
			file = new File(filePath + "/" + fileName);
			isEncryptedFile = true;
		}
		else{
			file = new File(filePath + "/" + fileName + context.getString(R.string.file_extension));
		}
		if (file.exists()) {
			int lastDotIndex = fileName.lastIndexOf(".");
			String fileNameWithoutExt;
			String originalExtension = ""; 
			if (lastDotIndex > 0) {
				fileNameWithoutExt = fileName.substring(0, lastDotIndex);
				if(!isEncryptedFile){
					originalExtension = fileName.substring(lastDotIndex);
				}
			}
			else {
				fileNameWithoutExt = fileName;
			}

			Pattern p = Pattern.compile(".+_\\d{1,3}$");
			Matcher m = p.matcher(fileNameWithoutExt);
			if (m.find()) {
				fileNameWithoutExt = fileNameWithoutExt.substring(0, fileName.lastIndexOf("_"));
			}
			String finalFilaname;
			if(isEncryptedFile){
				finalFilaname = fileNameWithoutExt + "_" + String.valueOf(number) + originalExtension + context.getString(R.string.file_extension);
			}
			else{
				finalFilaname = fileNameWithoutExt + "_" + String.valueOf(number) + originalExtension;
			}
			return findNewFileNameIfNeeded(context, filePath, finalFilaname, ++number);
		}
		return filePath + "/" + fileName;
	}*/
	
	public static void share(final Activity activity, final ArrayList<File> files, final OnAsyncTaskFinish onDecrypt) {
		CharSequence[] listEntries = activity.getResources().getStringArray(R.array.beforeShareActions);

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(activity.getString(R.string.before_sharing));
		builder.setItems(listEntries, new DialogInterface.OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(DialogInterface dialog, int item) {
				switch (item) {
					case SHARE_AS_IS:
						shareFiles(activity, files);
						if(onDecrypt != null){
							onDecrypt.onFinish();
						}
						break;
					case SHARE_REENCRYPT:
						AlertDialog.Builder passwordDialog = new AlertDialog.Builder(activity);

						LayoutInflater layoutInflater = LayoutInflater.from(activity);
						final View enterPasswordView = layoutInflater.inflate(R.layout.dialog_reencrypt_password, null);

						passwordDialog.setPositiveButton(activity.getString(android.R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								String password = ((EditText) enterPasswordView.findViewById(R.id.password)).getText().toString();
								String password2 = ((EditText) enterPasswordView.findViewById(R.id.password2)).getText().toString();

								if (password.equals(password2)) {
									HashMap<String, Object> params = new HashMap<String, Object>();

									params.put("newPassword", password);
									params.put("files", files);

									OnAsyncTaskFinish onReencrypt = new OnAsyncTaskFinish() {
										@Override
										public void onFinish(java.util.ArrayList<File> processedFiles) {
											if (processedFiles != null && processedFiles.size() > 0) {
												shareFiles(activity, processedFiles);
											}
											if(onDecrypt != null){
												onDecrypt.onFinish();
											}
										};
									};
									new ReEncryptFiles(activity, onReencrypt).execute(params);
								}
								else {
									Toast.makeText(activity, activity.getString(R.string.password_not_match), Toast.LENGTH_LONG).show();
								}
							}
						});

						passwordDialog.setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
							
							public void onClick(DialogInterface dialog, int which) {
								if(onDecrypt != null){
									onDecrypt.onFinish();
								}
							}
						});

						passwordDialog.setView(enterPasswordView);
						passwordDialog.setTitle(activity.getString(R.string.enter_reencrypt_password));

						passwordDialog.show();

						break;
					case SHARE_DECRYPT:
						String filePath = Helpers.getHomeDir(activity) + "/" + ".tmp";
						File destinationFolder = new File(filePath);
						destinationFolder.mkdirs();

						AsyncTasks.OnAsyncTaskFinish finalOnDecrypt = new AsyncTasks.OnAsyncTaskFinish() {
							@Override
							public void onFinish(java.util.ArrayList<File> processedFiles) {
								if (processedFiles != null && processedFiles.size() > 0) {
									shareFiles(activity, processedFiles);
								}
								if(onDecrypt != null){
									onDecrypt.onFinish();
								}
							};
						};
						
						new AsyncTasks.DecryptFiles(activity, filePath, finalOnDecrypt).execute(files);

						break;
				}

				dialog.dismiss();
			}
		}).show();
	}
	
	public static void shareFiles(Activity activity, ArrayList<File> fileToShare) {
		if (fileToShare.size() == 1) {
			Intent share = new Intent(Intent.ACTION_SEND);
			share.setType("*/*");

			share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + fileToShare.get(0).getPath()));
			activity.startActivity(Intent.createChooser(share, "Share Image"));
		}
		else if (fileToShare.size() > 1) {
			Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);
			share.setType("*/*");

			ArrayList<Uri> uris = new ArrayList<Uri>();
			for (int i = 0; i < fileToShare.size(); i++) {
				uris.add(Uri.parse("file://" + fileToShare.get(i).getPath()));
			}

			share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
			activity.startActivity(Intent.createChooser(share, activity.getString(R.string.share)));
		}
	}
	
	public static void fixBackgroundRepeat(View view) {
	    Drawable bg = view.getBackground();
	    if (bg != null) {
	        if (bg instanceof BitmapDrawable) {
	            BitmapDrawable bmp = (BitmapDrawable) bg;
	            bmp.mutate(); // make sure that we aren't sharing state anymore
	            bmp.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
	        }
	    }
	}
	
	public static void warnProVersion(final Activity activity) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(activity.getString(R.string.pro_version_warning));
		
		builder.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse("market://details?id=" + activity.getString(R.string.key_package_name)));
				activity.startActivity(intent);
			}
		});
		builder.setNegativeButton(activity.getString(R.string.later), null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}
}
