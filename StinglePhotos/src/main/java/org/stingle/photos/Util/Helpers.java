package org.stingle.photos.Util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.view.WindowManager;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.stingle.photos.Camera.CameraImageSize;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Db.StingleDbHelper;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.stingle.photos.Sync.SyncManager;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
		/*PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		if(pm != null) {
			wakelock = pm.newWakeLock(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, context.getString(R.string.app_name) + "decrypt");
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

	public static void registerForBroadcastReceiver(final Activity activity) {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("org.stingle.photos.ACTION_LOGOUT");
		activity.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				activity.finish();
			}
		}, intentFilter);
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


	public static Bitmap generateThumbnail(Context context, byte[] data, String encFilename, String realFileName, byte[] fileId, int type, int videoDuration) throws FileNotFoundException {
		Bitmap bitmap = decodeBitmap(data, getThumbSize(context));
		
		//Bitmap thumbBitmap = null;
		if (bitmap != null) {
			//thumbBitmap = Helpers.getThumbFromBitmap(bitmap, getThumbSize(activity));

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);

			FileOutputStream out = new FileOutputStream(FileManager.getThumbsDir(context) + "/" + encFilename);
			try {
				StinglePhotosApplication.getCrypto().encryptFile(out, stream.toByteArray(), realFileName, type, fileId, videoDuration);
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (CryptoException e) {
				e.printStackTrace();
			}

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


	public static ArrayList<CameraImageSize> parseVideoOutputs(Context context, StreamConfigurationMap map, CameraCharacteristics characteristics){
		ArrayList<CameraImageSize> videoSizes = new ArrayList<>();
		ArrayList<int[]> aeFpsRanges = new ArrayList<>();
		for (Range<Integer> r : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
			aeFpsRanges.add(new int[] {r.getLower(), r.getUpper()});
		}
		Collections.sort(aeFpsRanges, new CameraImageSize.RangeSorter());

		int minFps = 9999;
		int minFpsCapture = 9999;
		Log.d("fps-range-len", String.valueOf(aeFpsRanges.size()));
		for(int[] r : aeFpsRanges) {
			Log.d("fps-range", String.valueOf(r[0]) + " - " + String.valueOf(r[1]));
			if(r[0] >= 30){
				minFpsCapture = Math.min(minFpsCapture, r[0]);
			}
			minFps = Math.min(minFps, r[0]);
		}
		if(minFpsCapture == 9999){
			minFpsCapture = 30;
		}

		android.util.Size [] cameraVideoSizes = map.getOutputSizes(MediaRecorder.class);
		for(android.util.Size size : cameraVideoSizes) {
			if( size.getWidth() > 4096 || size.getHeight() > 2160 || size.getHeight() < 480) {
				continue; // Nexus 6 returns these, even though not supported?!
			}
			long mfd = map.getOutputMinFrameDuration(MediaRecorder.class, size);
			int  maxFps = (int)((1.0 / mfd) * 1000000000L);

			Range<Integer> fpsRange = new Range<Integer>(minFps, maxFps);
			if(minFpsCapture != maxFps) {
				videoSizes.add(new CameraImageSize(context, size.getWidth(), size.getHeight(), fpsRange, maxFps));
				videoSizes.add(new CameraImageSize(context, size.getWidth(), size.getHeight(), fpsRange, minFpsCapture));
			}
			else {
				videoSizes.add(new CameraImageSize(context, size.getWidth(), size.getHeight(), fpsRange, maxFps));
			}
		}
		Collections.sort(videoSizes, new CameraImageSize.SizeSorter());

		return videoSizes;
	}

	public static ArrayList<CameraImageSize> parsePhotoOutputs(Context context, StreamConfigurationMap map, CameraCharacteristics characteristics){
		ArrayList<CameraImageSize> photoSizes = new ArrayList<>();

		ArrayList<int[]> aeFpsRanges = new ArrayList<>();
		for (Range<Integer> r : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
			aeFpsRanges.add(new int[] {r.getLower(), r.getUpper()});
			Log.d("fps", String.valueOf(r.getLower()) + " - " + String.valueOf(r.getUpper()));
		}
		Collections.sort(aeFpsRanges, new CameraImageSize.RangeSorter());

		int minFps = 9999;
		for(int[] r : aeFpsRanges) {
			minFps = Math.min(minFps, r[0]);
		}

		android.util.Size [] cameraPhotoSizes = map.getOutputSizes(ImageFormat.JPEG);
		for(android.util.Size size : cameraPhotoSizes) {
			long mfd = 0;
			int  maxFps = 30;
			Range<Integer> fpsRange;
			try {
				mfd = map.getOutputMinFrameDuration(MediaRecorder.class, size);
				maxFps = (int)((1.0 / mfd) * 1000000000L);
				fpsRange = new Range<Integer>(minFps, maxFps);
			}
			catch (IllegalArgumentException e){
				fpsRange = new Range<Integer>(minFps, maxFps);
			}

			CameraImageSize photoSize = new CameraImageSize(context, size.getWidth(), size.getHeight(), fpsRange, maxFps);
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

	public static void insertFileIntoDB(Context context, String filename){
		long nowDate = System.currentTimeMillis();
		StingleDbHelper db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);

		try {
			String headers = Crypto.getFileHeaders(FileManager.getHomeDir(context) + "/" + filename, FileManager.getThumbsDir(context) + "/" + filename);
			db.insertFile(filename, true, false, StingleDbHelper.INITIAL_VERSION, nowDate, nowDate, headers);
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		db.close();
	}

	public static void blockScreenshotsIfEnabled(Activity activity){
		boolean blockScreenshots = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(StinglePhotosApplication.BLOCK_SCREENSHOTS, false);
		if(blockScreenshots) {
			activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
		}
	}

}
