package org.stingle.photos.Util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.exifinterface.media.ExifInterface;

import org.stingle.photos.CameraX.CameraImageSize;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Helpers {
	public static final String GENERAL_FILE_PREFIX = "FILE_";
	public static final String IMAGE_FILE_PREFIX = "IMG_";
	public static final String VIDEO_FILE_PREFIX = "VID_";
	protected static final int SHARE_DECRYPT = 0;
	private static PowerManager.WakeLock wakelock;


	public static void acquireWakeLock(Activity activity){
		/*PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
		if(pm != null) {
			wakelock = pm.newWakeLock(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, activity.getString(R.string.app_name) + "decrypt");
			wakelock.acquire(1000 * 60 * 10);
		}*/
		activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

	}

	public static void releaseWakeLock(Activity activity){
		/*if(wakelock != null){
			wakelock.release();
			wakelock = null;
		}*/
		activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	public static void deleteTmpDir(Context context) {
		File dir = new File(FileManager.getHomeDir(context) + "/" + ".tmp");
		if (dir.isDirectory()) {
			String[] children = dir.list();
			if(children != null) {
				for (int i = 0; i < children.length; i++) {
					new File(dir, children[i]).delete();
				}
			}
		}
	}

	public static String getNewEncFilename() {
		String filename = Crypto.byteArrayToBase64(StinglePhotosApplication.getCrypto().getRandomData(StinglePhotosApplication.FILENAME_LENGTH));

		return filename + StinglePhotosApplication.FILE_EXTENSION;
	}

	public static String getTimestampedFilename(String prefix, String extension) {
		if(extension == null){
			extension = "";
		}
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
		String imageFileName = prefix + timeStamp;

		return imageFileName + extension;
	}

	public static void showAlertDialog(Context context, String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		builder.setNegativeButton(context.getString(R.string.ok), null);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		AlertDialog dialog = builder.create();
		dialog.show();
	}


	public static void showInfoDialog(Context context, String message) {
		showInfoDialog(context, message, null);
	}

	public static void showInfoDialog(Context context, String message, DialogInterface.OnClickListener onClick) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		if(onClick != null){
			builder.setNegativeButton(context.getString(R.string.ok), onClick);
		}
		else {
			builder.setNegativeButton(context.getString(R.string.ok), null);
		}
		builder.setIcon(android.R.drawable.ic_dialog_info);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public static void showConfirmDialog(Context context, String message, DialogInterface.OnClickListener yes, DialogInterface.OnClickListener no) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		builder.setPositiveButton(context.getString(R.string.yes), yes);
		builder.setNegativeButton(context.getString(R.string.no), no);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public static ProgressDialog showProgressDialog(Context context, String message, DialogInterface.OnCancelListener onCancel){
		ProgressDialog progressDialog = new ProgressDialog(context);
		if(onCancel != null) {
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(onCancel);
		}
		else{
			progressDialog.setCancelable(false);
		}
		progressDialog.setMessage(message);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.show();

		return progressDialog;
	}


	public static ProgressDialog showProgressDialogWithBar(Context context, String message, int max, DialogInterface.OnCancelListener onCancel){
		ProgressDialog progressDialog = new ProgressDialog(context);
		if(onCancel != null) {
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(onCancel);
		}
		progressDialog.setMessage(message);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMax(max);
		progressDialog.show();

		return progressDialog;
	}


	public static Bitmap generateThumbnail(Context context, byte[] data, String encFilename, String realFileName, byte[] fileId, int type, int videoDuration) throws IOException, CryptoException {
		Bitmap bitmap = decodeBitmap(data, getThumbSize(context));
		
		//Bitmap thumbBitmap = null;
		if (bitmap != null) {
			//thumbBitmap = Helpers.getThumbFromBitmap(bitmap, getThumbSize(activity));

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);

			FileOutputStream out = new FileOutputStream(FileManager.getThumbsDir(context) + "/" + encFilename);
			StinglePhotosApplication.getCrypto().encryptFile(out, stream.toByteArray(), realFileName, type, fileId, videoDuration);
			out.close();

			//Helpers.getAESCrypt(activity).encrypt(stream.toByteArray(), out);
		}
		return bitmap;
	}

	public static int convertDpToPixels(Context context, int dp){
		float scale = context.getResources().getDisplayMetrics().density;
		return (int) (dp * scale + 0.5f);
	}

	public static int getThumbSize(Context context){
		return getThumbSize(context, 3);
	}
	public static int getThumbSize(Context context, int colls){
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);

		if(metrics.widthPixels <= metrics.heightPixels){
			return (int) Math.floor(metrics.widthPixels / colls);
		}
		else{
			return (int) Math.floor(metrics.heightPixels / colls);
		}
	}

	public static Bitmap getThumbFromBitmap(Bitmap bitmap, int squareSide) {
		if(bitmap == null){
			return null;
		}
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

	public static int getExifRotation(File file){

		
		return 0;
	}

	public static int getExifRotation(InputStream stream){
		try {
			ExifInterface exif = new ExifInterface(stream);
			return exif.getRotationDegrees();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

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
			Integer rotation = getExifRotation(new BufferedInputStream(new ByteArrayInputStream(data)));
			
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
	
	public static String getRealPathFromURI(Activity activity, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = activity.getContentResolver().query(contentUri, proj, null, null, null);
        if(cursor != null){
	        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	        cursor.moveToFirst();
	        return cursor.getString(column_index);
        }
        
        return null;
    }
	
	public static String decryptFilename(String filePath){
		try {
			FileInputStream in = new FileInputStream(filePath);

			return StinglePhotosApplication.getCrypto().getFilename(in);
		}
		catch (IOException e){
			return "";
		}
	}


	public static ArrayList<CameraImageSize> parseVideoOutputs(Context context, StreamConfigurationMap map){
		ArrayList<CameraImageSize> videoSizes = new ArrayList<>();

		android.util.Size [] cameraVideoSizes = map.getOutputSizes(MediaRecorder.class);
		for(android.util.Size size : cameraVideoSizes) {
			/*if( size.getWidth() > 4096 || size.getHeight() > 2160 || size.getHeight() < 480) {
				continue; // Nexus 6 returns these, even though not supported?!
			}*/
			videoSizes.add(new CameraImageSize(context, size.getWidth(), size.getHeight()));
		}
		Collections.sort(videoSizes, new CameraImageSize.SizeSorter());

		return videoSizes;
	}

	public static ArrayList<CameraImageSize> parsePhotoOutputs(Context context, StreamConfigurationMap map){
		ArrayList<CameraImageSize> photoSizes = new ArrayList<>();

		android.util.Size [] cameraPhotoSizes = map.getOutputSizes(ImageFormat.JPEG);
		for(android.util.Size size : cameraPhotoSizes) {
			CameraImageSize photoSize = new CameraImageSize(context, size.getWidth(), size.getHeight());
			if(photoSize.megapixel < 0.9){
				continue;
			}
			photoSizes.add(photoSize);
		}
		Collections.sort(photoSizes, new CameraImageSize.SizeSorter());

		return photoSizes;
	}

	public final static boolean isValidEmail(CharSequence target) {
		if (TextUtils.isEmpty(target)) {
			return false;
		} else {
			return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
		}
	}

	public static void storePreference(Context context, String key, String value){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		preferences.edit().putString(key, value).commit();
	}
	public static void storePreference(Context context, String key, int value){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		preferences.edit().putInt(key, value).commit();
	}
	public static void storePreference(Context context, String key, long value){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		preferences.edit().putLong(key, value).commit();
	}
	public static void storePreference(Context context, String key, boolean value){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		preferences.edit().putBoolean(key, value).commit();
	}
	public static String getPreference(Context context, String key, String defaultValue){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		return preferences.getString(key, defaultValue);
	}
	public static int getPreference(Context context, String key, int defaultValue){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		return preferences.getInt(key, defaultValue);
	}
	public static long getPreference(Context context, String key, long defaultValue){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		return preferences.getLong(key, defaultValue);
	}
	public static boolean getPreference(Context context, String key, boolean defaultValue){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		return preferences.getBoolean(key, defaultValue);
	}
	public static void deletePreference(Context context, String key){
		SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
		preferences.edit().remove(key).commit();
	}

	public static String formatVideoDuration(int duration){
		return String.format(Locale.getDefault(), "%2d:%02d",
				TimeUnit.SECONDS.toMinutes(duration),
				TimeUnit.SECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(duration))
		);
	}

	public static int bytesToMb(long bytes){
		return Math.round(bytes / (1024 * 1024));
	}

	public static boolean isUploadSpaceAvailable(Context context, int uploadSize){
		int availableSpace = getAvailableUploadSpace(context);

		if(availableSpace > uploadSize){
			return true;
		}
		return false;
	}

	public static int getAvailableUploadSpace(Context context){
		int spaceUsed = getPreference(context, SyncManager.PREF_LAST_SPACE_USED, 0);
		int spaceQuota = getPreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, 0);

		return spaceQuota - spaceUsed;
	}

	public static String formatSpaceUnits(int mb){
		if(mb < 1024){
			return String.valueOf(mb) + " MB";
		}
		else if(mb >= 1024 && mb < 1024*1024){
			double gb = Math.round((mb/1024.0) * 100.0)/100.0;
			return gb + " GB";
		}
		else if(mb >= 1024*1024){
			double tb = Math.round((mb/1048576.0) * 100.0)/100.0;
			return tb + " TB";
		}

		return String.valueOf(mb);
	}

	public static void insertFileIntoDB(Context context, String filename) throws IOException, CryptoException {
		long nowDate = System.currentTimeMillis();
		FilesTrashDb db = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_FILES);

		String headers = Crypto.getFileHeadersFromFile(FileManager.getHomeDir(context) + "/" + filename, FileManager.getThumbsDir(context) + "/" + filename);
		db.insertFile(filename, true, false, FilesTrashDb.INITIAL_VERSION, nowDate, nowDate, headers);

		db.close();
	}

	public static void blockScreenshotsIfEnabled(Activity activity){
		boolean blockScreenshots = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(StinglePhotosApplication.BLOCK_SCREENSHOTS, false);
		if(blockScreenshots) {
			activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
		}
	}



}
