package com.fenritz.safecam.Util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
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
import com.fenritz.safecam.Camera.CameraImageSize;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.LoginActivity;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.SettingsActivity;
import com.fenritz.safecam.Util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.Util.StorageUtils.StorageInfo;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helpers {
	public static final String GENERAL_FILE_PREFIX = "FILE_";
	public static final String IMAGE_FILE_PREFIX = "IMG_";
	public static final String VIDEO_FILE_PREFIX = "VID_";
	protected static final int SHARE_DECRYPT = 0;
	
	public static void deleteTmpDir(Context context) {
		File dir = new File(Helpers.getHomeDir(context) + "/" + ".tmp");
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
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		activity.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				activity.finish();
			}
		}, intentFilter);
	}

	public static String getNewEncFilename() {
		String filename = Crypto.byteArrayToBase64(SafeCameraApplication.getCrypto().getRandomData(SafeCameraApplication.FILENAME_LENGTH));

		return filename + SafeCameraApplication.FILE_EXTENSION;
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
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message);
		builder.setNegativeButton(context.getString(R.string.ok), null);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public static String getDefaultHomeDir(){
		List<StorageInfo> storageList = StorageUtils.getStorageList();
		if(storageList.size() > 0){
			return storageList.get(0).path;
		}
		
		return null;
	}
	
	public static String getHomeDirParentPath(Context context){
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		String defaultHomeDir = Helpers.getDefaultHomeDir();
		
		String currentHomeDir = sharedPrefs.getString("home_folder", defaultHomeDir);
		String customHomeDir = sharedPrefs.getString("home_folder_location", null);
		
		if(currentHomeDir != null && currentHomeDir.equals(SettingsActivity.CUSTOM_HOME_VALUE)){
			currentHomeDir = customHomeDir;
		}
		
		return ensureLastSlash(currentHomeDir);
	}
	
	public static String getHomeDir(Context context) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		String homeDirPath = getHomeDirParentPath(context) + sharedPrefs.getString("home_folder_name", context.getString(R.string.default_home_folder_name));
		
		if(!new File(homeDirPath).exists()){
			Helpers.createFolders(context, homeDirPath);
		}
		
		return homeDirPath;
	}
	
	public static String ensureLastSlash(String path){
		if(path != null && !path.endsWith("/")){
			return path + "/";
		}
		return path;
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


	public static Bitmap generateThumbnail(Context context, byte[] data, String fileName, byte[] fileId) throws FileNotFoundException {
		Bitmap bitmap = decodeBitmap(data, getThumbSize(context));
		
		Bitmap thumbBitmap = null;
		if (bitmap != null) {
			thumbBitmap = Helpers.getThumbFromBitmap(bitmap, getThumbSize(context));

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			thumbBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);

			FileOutputStream out = new FileOutputStream(Helpers.getThumbsDir(context) + "/" + fileName);
			try {
				SafeCameraApplication.getCrypto().encryptFile(out, stream.toByteArray(), fileName, Crypto.FILE_TYPE_PHOTO, fileId);
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (CryptoException e) {
				e.printStackTrace();
			}

			//Helpers.getAESCrypt(context).encrypt(stream.toByteArray(), out);
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

			return SafeCameraApplication.getCrypto().getFilename(in);
		}
		catch (IOException e){
			return "";
		}
	}
	
	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName) {
		return findNewFileNameIfNeeded(context, filePath, fileName, null);
	}
	
	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName, Integer number) {
		if (number == null) {
			number = 1;
		}

		File file = new File(Helpers.ensureLastSlash(filePath) + fileName);
		if (file.exists()) {
			int lastDotIndex = fileName.lastIndexOf(".");
			String fileNameWithoutExt;
			String originalExtension = ""; 
			if (lastDotIndex > 0) {
				fileNameWithoutExt = fileName.substring(0, lastDotIndex);
				originalExtension = fileName.substring(lastDotIndex);
			}
			else {
				fileNameWithoutExt = fileName;
			}

			Pattern p = Pattern.compile(".+_\\d{1,3}$");
			Matcher m = p.matcher(fileNameWithoutExt);
			if (m.find()) {
				fileNameWithoutExt = fileNameWithoutExt.substring(0, fileName.lastIndexOf("_"));
			}
			
			String finalFilaname = fileNameWithoutExt + "_" + String.valueOf(number) + originalExtension;
			
			return findNewFileNameIfNeeded(context, filePath, finalFilaname, ++number);
		}
		return Helpers.ensureLastSlash(filePath) + fileName;
	}
	
	public static void share(final Activity activity, final ArrayList<File> files, final OnAsyncTaskFinish onDecrypt) {
		CharSequence[] listEntries = activity.getResources().getStringArray(R.array.beforeShareActions);

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(activity.getString(R.string.before_sharing));
		builder.setItems(listEntries, new DialogInterface.OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(DialogInterface dialog, int item) {
				switch (item) {
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
	
	public static void scanFile(final Context context, File file) {
	    try {
	    	if( file.isFile() ) {
		        MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null, null);
	    	}
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	}
	
	public static void rescanDeletedFile(Context context, File file){
		// Set up the projection (we only need the ID)
		String[] projection = { MediaStore.Images.Media._ID };

		// Match on the file path
		String selection = MediaStore.Images.Media.DATA + " = ?";
		String[] selectionArgs = new String[] { file.getAbsolutePath() };

		// Query for the ID of the media matching the file path
		Uri queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		ContentResolver contentResolver = context.getContentResolver();
		Cursor c = contentResolver.query(queryUri, projection, selection, selectionArgs, null);
		if (c.moveToFirst()) {
		    // We found the ID. Deleting the item via the content provider will also remove the file
		    long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
		    Uri deleteUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
		    contentResolver.delete(deleteUri, null, null);
		} else {
		    // File not found in media store DB
		}
		c.close();
	}
	
	public static void decryptSelected(final Activity activity, ArrayList<File> selectedFiles) {
		decryptSelected(activity, selectedFiles, null);
	}
	
	@SuppressWarnings("unchecked")
	public static void decryptSelected(final Activity activity, ArrayList<File> selectedFiles, final AsyncTasks.OnAsyncTaskFinish finishTask) {
		SharedPreferences preferences = activity.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Activity.MODE_PRIVATE);
		
		String filePath = Helpers.getHomeDirParentPath(activity) + preferences.getString("dec_folder", activity.getString(R.string.dec_folder_def));
		File destinationFolder = new File(filePath);
		destinationFolder.mkdirs();
		new AsyncTasks.DecryptFiles(activity, filePath, new OnAsyncTaskFinish() {
			@Override
			public void onFinish(java.util.ArrayList<File> decryptedFiles) {
				if(decryptedFiles != null){
					for(File file : decryptedFiles){
						activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
					}
				}
				finishTask.onFinish();
			}
		}).execute(selectedFiles);
	}

    public static void checkIsMainFolderWritable(final Activity activity){
        String homeDir = getHomeDir(activity);

        File homeDirFile = new File(homeDir);

        if(!homeDirFile.exists() || !homeDirFile.canWrite()){
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.home_folder_problem_title));

            builder.setMessage(activity.getString(R.string.home_folder_problem));
            builder.setPositiveButton(activity.getString(R.string.yes), new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int whichButton) {

                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
                    String defaultHomeDir = Helpers.getDefaultHomeDir();

                    sharedPrefs.edit().putString("home_folder", defaultHomeDir).putString("home_folder_location", null).commit();

                    Helpers.createFolders(activity);
                }
            });
            builder.setNegativeButton(activity.getString(R.string.no), null);
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

	public static boolean requestSDCardPermission(final Activity activity){
		if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
				new AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.sdcard_perm_explain))
						.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, LoginActivity.REQUEST_SD_CARD_PERMISSION);
							}
						})
						.setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										activity.finish();
									}
								}
						)
						.create()
						.show();

			} else {
				activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, LoginActivity.REQUEST_SD_CARD_PERMISSION);
			}
			return false;
		}
		if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				new AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.sdcard_perm_explain))
						.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, LoginActivity.REQUEST_SD_CARD_PERMISSION);
							}
						})
						.setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										activity.finish();
									}
								}
						)
						.create()
						.show();

			} else {
				activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, LoginActivity.REQUEST_SD_CARD_PERMISSION);
			}
			return false;
		}
		return true;
	}

	public static boolean isImageFile(String path) {
		String mimeType = URLConnection.guessContentTypeFromName(path);
		return mimeType != null && mimeType.startsWith("image");
	}
	public static boolean isVideoFile(String path) {
		String mimeType = URLConnection.guessContentTypeFromName(path);
		return mimeType != null && mimeType.startsWith("video");
	}

	public static int getFileType(String path){
		int fileType = Crypto.FILE_TYPE_GENERAL;
		if(Helpers.isImageFile(path)){
			fileType = Crypto.FILE_TYPE_PHOTO;
		}
		else if(Helpers.isVideoFile(path)){
			fileType = Crypto.FILE_TYPE_VIDEO;
		}

		return fileType;
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
}
