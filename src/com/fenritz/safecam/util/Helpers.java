package com.fenritz.safecam.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

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
		int lockTimeout = Integer.valueOf(sharedPrefs.getString("lock_time", "300")) * 1000;

		long currentTimestamp = System.currentTimeMillis();
		long lockedTime = ((SafeCameraApplication) activity.getApplicationContext()).getLockedTime();

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
		((SafeCameraApplication) context.getApplicationContext()).setLockedTime(System.currentTimeMillis());
	}

	public static void disableLockTimer(Context context) {
		((SafeCameraApplication) context.getApplicationContext()).setLockedTime(0);
	}

	public static void logout(Activity activity){
		doLogout(activity);
		redirectToLogin(activity, null);
	}
	
	private static void redirectToLogin(Activity activity, Bundle extraData) {
		Intent intent = new Intent();
		intent.setClass(activity, SafeCameraActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(SafeCameraActivity.ACTION_JUST_LOGIN, true);
		intent.putExtra(SafeCameraActivity.PARAM_EXTRA_DATA, extraData);
		activity.startActivity(intent);
	}
	
	private static void doLogout(Activity activity) {
		Intent broadcastIntent = new Intent();
		broadcastIntent.setAction("com.package.ACTION_LOGOUT");
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
		intentFilter.addAction("com.package.ACTION_LOGOUT");
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

		return imageFileName + ".jpg" + context.getString(R.string.file_extension);
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
		return sdcardPath + "/" + sharedPrefs.getString("home_folder", context.getString(R.string.default_home_folder_name));
	}

	public static String getThumbsDir(Context context) {
		return getHomeDir(context) + "/" + context.getString(R.string.default_thumb_folder_name);
	}

	public static void createFolders(Context context) {
		File dir = new File(getThumbsDir(context));
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdirs();
		}
		
		File tmpFile = new File(Helpers.getHomeDir(context) + "/.tmp/");
		if (!tmpFile.exists() || !tmpFile.isDirectory()) {
			tmpFile.mkdirs();
		}
	}

	public static Bitmap generateThumbnail(Context context, byte[] data, String fileName) throws FileNotFoundException {
		Bitmap bitmap = decodeBitmap(data, 75);
		
		Bitmap thumbBitmap = null;
		if (bitmap != null) {
			thumbBitmap = Helpers.getThumbFromBitmap(bitmap, Integer.valueOf(context.getString(R.string.thumb_size)));

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			thumbBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
			byte[] imageByteArray = stream.toByteArray();

			FileOutputStream out = new FileOutputStream(Helpers.getThumbsDir(context) + "/" + fileName);
			Helpers.getAESCrypt(context).encrypt(imageByteArray, out);
		}
		return thumbBitmap;
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

	public static Bitmap decodeFile(File f, int requiredSize) {
		try {
			// Decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(new FileInputStream(f), null, o);

			// Find the correct scale value. It should be the power of 2.
			int scale = 1;
			while (o.outWidth / scale / 2 >= requiredSize && o.outHeight / scale / 2 >= requiredSize) {
				scale *= 2;
			}

			// Decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
		}
		catch (FileNotFoundException e) { }
		return null;
	}
	
	public static Bitmap decodeBitmap(byte[] data, int requiredSize) {
		if(data != null){
			// Decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(data, 0, data.length, o);
	
			// Find the correct scale value. It should be the power of 2.
			int scale = 1;
			while (o.outWidth / scale / 2 >= requiredSize && o.outHeight / scale / 2 >= requiredSize) {
				scale *= 2;
			}
			
			// Decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
		}
		return null;
	}
	
	public static String getRealPathFromURI(Activity activity, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = activity.managedQuery(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
	
	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName) {
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
	}
	
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
										};
									};
									new ReEncryptFiles(activity, onReencrypt).execute(params);
								}
								else {
									Toast.makeText(activity, activity.getString(R.string.password_not_match), Toast.LENGTH_LONG).show();
								}
							}
						});

						passwordDialog.setNegativeButton(activity.getString(R.string.cancel), null);

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
}
