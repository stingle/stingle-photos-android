package org.stingle.photos.Util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Query.GalleryTrashDb;
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
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Helpers {
	public static final String GENERAL_FILE_PREFIX = "FILE_";
	public static final String IMAGE_FILE_PREFIX = "IMG_";
	public static final String VIDEO_FILE_PREFIX = "VID_";
	protected static final int SHARE_DECRYPT = 0;
	public static final int THUMB_SIZE = 800;
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
		String filename = Crypto.byteArrayToBase64UrlSafe(StinglePhotosApplication.getCrypto().getRandomData(StinglePhotosApplication.FILENAME_LENGTH));

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

	public static String getDateFromTimestamp(long time){
		Calendar cal = Calendar.getInstance(Locale.ENGLISH);
		cal.setTimeInMillis(time);
		return DateFormat.format("yyyy-MM-dd hh:mm:ss", cal).toString();
	}

	public static void showAlertDialog(Context context, String title, String message) {
		showAlertDialog(context, title, message, null, null);
	}

	public static void showAlertDialog(Context context, String title, String message, Integer icon, DialogInterface.OnClickListener onclick) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setNegativeButton(context.getString(R.string.ok), onclick);
		if(icon == null){
			icon = R.drawable.ic_warning;
		}
		builder.setIcon(icon);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public static void showPromptDialog(Context context, String title, String message, Integer icon, View input, DialogInterface.OnClickListener onclick) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
		builder.setTitle(title);
		builder.setMessage(message);

		builder.setView(input);
		builder.setNegativeButton(context.getString(R.string.ok), onclick);
		if(icon == null){
			icon = R.drawable.ic_warning;
		}
		builder.setIcon(icon);
		AlertDialog dialog = builder.create();
		dialog.show();
	}


	public static void showInfoDialog(Context context, String title, String message) {
		showInfoDialog(context, title, message, null, null);
	}

	public static void showInfoDialog(Context context, String title, String message, Integer icon, DialogInterface.OnClickListener onClick) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
		builder.setTitle(title);
		builder.setMessage(message);
		if(onClick != null){
			builder.setPositiveButton(context.getString(R.string.ok), onClick);
		}
		else {
			builder.setNegativeButton(context.getString(R.string.ok), null);
		}
		if(icon == null){
			icon = R.drawable.ic_info;
		}
		builder.setIcon(icon);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public static void showConfirmDialog(Context context, String title, String message, Integer icon, DialogInterface.OnClickListener yes, DialogInterface.OnClickListener no) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setPositiveButton(context.getString(R.string.yes), yes);
		builder.setNegativeButton(context.getString(R.string.no), no);
		if(icon != null){
			builder.setIcon(icon);
		}
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


	public static ProgressDialog showProgressDialogWithBar(Context context, String title, String message, int max, DialogInterface.OnCancelListener onCancel){
		ProgressDialog progressDialog = new ProgressDialog(context);
		if(onCancel != null) {
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(onCancel);
		}
		else{
			progressDialog.setCancelable(false);
		}
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMax(max);
		progressDialog.show();

		return progressDialog;
	}


	public static Crypto.Header generateThumbnail(Context context, byte[] data, String encFilename, String realFileName, byte[] fileId, int type, int videoDuration) throws IOException, CryptoException {
		Bitmap bitmap = decodeBitmap(data, THUMB_SIZE);
		
		if (bitmap != null) {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);

			FileOutputStream out = new FileOutputStream(FileManager.getThumbsDir(context) + "/" + encFilename);
			Crypto.Header header = StinglePhotosApplication.getCrypto().encryptFile(out, stream.toByteArray(), realFileName, type, fileId, videoDuration);
			out.close();

			return header;
		}
		return null;
	}

	public static int convertDpToPixels(Context context, int dp){
		float scale = context.getResources().getDisplayMetrics().density;
		return (int) (dp * scale + 0.5f);
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

	public static int getScreenWidthByColumns(Context context){
		return getScreenWidthByColumns(context, 3);
	}
	public static int getScreenWidthByColumns(Context context, int columns){
		if(columns == 0){
			return 0;
		}
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);

		if(metrics.widthPixels <= metrics.heightPixels){
			return (int) Math.floor(metrics.widthPixels / columns);
		}
		else{
			return (int) Math.floor(metrics.heightPixels / columns);
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

	public final static boolean isValidEmail(CharSequence target) {
		if (TextUtils.isEmpty(target)) {
			return false;
		} else {
			return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
		}
	}

	public static void storePreference(Context context, String key, String value){
		context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply();
	}

	public static void storePreference(Context context, String key, int value){
		context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putInt(key, value).apply();
	}
	public static void storePreference(Context context, String key, long value){
		context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(key, value).apply();
	}
	public static void storePreference(Context context, String key, boolean value){
		context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
	}
	public static String getPreference(Context context, String key, String defaultValue){
		return context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getString(key, defaultValue);
	}
	public static int getPreference(Context context, String key, int defaultValue){
		return context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getInt(key, defaultValue);
	}
	public static long getPreference(Context context, String key, long defaultValue){
		return context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getLong(key, defaultValue);
	}
	public static boolean getPreference(Context context, String key, boolean defaultValue){
		return context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getBoolean(key, defaultValue);
	}
	public static boolean deletePreference(Context context, String key){
		return context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().remove(key).commit();
	}
	public static void deleteAllPreferences(Context context){
		context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
	}

	public static boolean isServerAddonPresent(Context context, String addon){
		String addonsArrayStr = getPreference(context, StinglePhotosApplication.SERVER_ADDONS, null);
		if(addonsArrayStr != null){
			try {
				JSONArray array = new JSONArray(addonsArrayStr);
				for(int i=0; i<array.length(); i++){
					if(array.getString(i).equals(addon)){
						return true;
					}
				}
			} catch (JSONException e) {

			}
		}
		return false;
	}
	public static boolean isServerAddonsFieldSet(Context context){
		String addonsArrayStr = getPreference(context, StinglePhotosApplication.SERVER_ADDONS, null);
		if(addonsArrayStr != null){
			return true;
		}
		return false;
	}

	public static String formatVideoDuration(int duration){
		String formattedText = "";
		if(duration > 3600){
			formattedText = String.format(Locale.getDefault(), "%2d:%02d:%02d",
					TimeUnit.SECONDS.toHours(duration),
					TimeUnit.SECONDS.toMinutes(duration) - TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(duration)),
					TimeUnit.SECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(duration)));
		}
		else {
			formattedText = String.format(Locale.getDefault(), "%2d:%02d",
					TimeUnit.SECONDS.toMinutes(duration),
					TimeUnit.SECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(duration)));
		}
		return formattedText;
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
		return formatSpaceUnits((double) mb);
	}

	public static String formatSpaceUnits(double mb){
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
		GalleryTrashDb db = new GalleryTrashDb(context, SyncManager.GALLERY);

		String headers = Crypto.getFileHeadersFromFile(FileManager.getHomeDir(context) + "/" + filename, FileManager.getThumbsDir(context) + "/" + filename);
		db.insertFile(filename, true, false, GalleryTrashDb.INITIAL_VERSION, nowDate, nowDate, headers);

		db.close();
	}

	public static void blockScreenshotsIfEnabled(Activity activity){
		boolean blockScreenshots = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(StinglePhotosApplication.BLOCK_SCREENSHOTS, false);
		if(blockScreenshots) {
			activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
		}
	}

	public static boolean isStringsEqual(String string1, String string2){
		if(string1 == string2){
			return true;
		}

		if(string1 != null && string1.equals(string2)){
			return true;
		}

		return false;
	}

	public static void applyTheme(String theme){
		if(theme.equals("auto")) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
		}
		else if(theme.equals("dark")) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		}
		else if(theme.equals("light")) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}
	}

	public static void setLocale(Context context){
		SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
		String localeCode = prefs.getString("locale", "auto");

		if(localeCode.equals("auto")){
			return;
		}

		Resources resources = context.getResources();
		DisplayMetrics dm = resources.getDisplayMetrics();
		Configuration config = resources.getConfiguration();
		Locale locale = new Locale(localeCode.toLowerCase());
		config.setLocale(locale);
		config.setLayoutDirection(locale);
		resources.updateConfiguration(config, dm);
	}

	public static String generateAlbumName() {
		return new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());
	}

	public static String impode(String glue, ArrayList<String> items){
		if(items.size() == 0){
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < items.size() - 1; i++) {
			sb.append(items.get(i));
			sb.append(glue);
		}
		sb.append(items.get(items.size() - 1).trim());
		return sb.toString();
	}

	public static void deleteFolderRecursive(File fileOrDirectory) {
		if(fileOrDirectory == null){
			return;
		}
		if (fileOrDirectory.exists() && fileOrDirectory.isDirectory()) {
			for (File child : fileOrDirectory.listFiles()) {
				deleteFolderRecursive(child);
			}
		}
		fileOrDirectory.delete();
	}

	public static int getAttrColor(Context context, int attr){
		TypedValue typedValue = new TypedValue();

		TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[] { attr });
		int color = a.getColor(0, 0);

		a.recycle();

		return color;
	}

	public static Bitmap getVideoThumbnail(Context context, Uri uri) throws IllegalArgumentException,
			SecurityException{
		try {
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();

			if (uri == null) {
				return null;
			}

			retriever.setDataSource(context, uri);
			return retriever.getFrameAtTime();
		}
		catch (RuntimeException e){

		}
		return null;
	}

	public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
		for (Map.Entry<T, E> entry : map.entrySet()) {
			if (Objects.equals(value, entry.getValue())) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static Bitmap drawableToBitmap (Drawable drawable) {
		Bitmap bitmap = null;

		if (drawable instanceof BitmapDrawable) {
			BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
			if(bitmapDrawable.getBitmap() != null) {
				return bitmapDrawable.getBitmap();
			}
		}

		if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
			bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
		} else {
			bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		}

		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);
		return bitmap;
	}

	public static String capitalize(String str) {
		if(str == null || str.isEmpty()) {
			return str;
		}

		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	public static boolean isValidURL(String urlString) {
		try {
			new URL(urlString).toURI();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
