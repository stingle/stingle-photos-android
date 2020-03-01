package org.stingle.photos.Files;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileManager {


	static final public String SHARE_CACHE_DIR = "share";
	static final public String DECRYPT_DIR = "StinglePhotosDecrypted";

	public static byte[] getAndCacheThumb(Context context, String filename, int folder) throws IOException {
		return getAndCacheThumb(context,filename, folder,null);
	}

	public static byte[] getAndCacheThumb(Context context, String filename, int folder, String cachePath) throws IOException {

		File cacheDir;
		if(cachePath != null){
			cacheDir = new File(cachePath);
		}
		else {
			cacheDir = new File(context.getCacheDir().getPath() + "/thumbCache");

		}
		File cachedFile = new File(cacheDir.getPath() + "/" + filename);

		if(cachedFile.exists()){
			FileInputStream in = new FileInputStream(cachedFile);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];

			int numRead;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}
			in.close();
			return out.toByteArray();
		}


		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", filename);
		postParams.put("thumb", "1");
		postParams.put("folder", String.valueOf(folder));
		byte[] encFile = new byte[0];

		try {
			encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);
		}
		catch (NoSuchAlgorithmException | KeyManagementException e) {

		}

		if(encFile == null || encFile.length == 0){
			return null;
		}

		byte[] fileBeginning = Arrays.copyOfRange(encFile, 0, Crypto.FILE_BEGGINIG_LEN);
		if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
			return null;
		}

		if(!cacheDir.exists()){
			cacheDir.mkdirs();
		}


		FileOutputStream out = new FileOutputStream(cachedFile);
		out.write(encFile);
		out.close();

		return encFile;
	}

	public static String getHomeDir(Context context) {
		/*Uri homeUri = getHomeUri(activity);
		if(homeUri != null){
			return homeUri.getPath();
		}
		else {*/

			String externalStorage = Environment.getExternalStorageDirectory().getPath();

			String homeDirPath = externalStorage + "/" + context.getString(R.string.default_home_folder_name) + "/" + Helpers.getPreference(context, StinglePhotosApplication.USER_HOME_FOLDER, "default");

			File homeDir = new File(homeDirPath);
			if (!homeDir.exists()) {
				homeDir.mkdirs();
			}

			return homeDir.getPath();
		//}
	}

	public static Uri getHomeUri(Context context) {
		List<UriPermission> persistedUriPermissions = context.getContentResolver().getPersistedUriPermissions();
		if (persistedUriPermissions.size() > 0) {
			UriPermission uriPermission = persistedUriPermissions.get(0);
			return uriPermission.getUri();
		}
		return null;
	}

	public static String ensureLastSlash(String path){
		if(path != null && !path.endsWith("/")){
			return path + "/";
		}
		return path;
	}

	public static String getThumbsDir(Context context) {
		String thumbDirPath = getHomeDir(context) + "/" + context.getString(R.string.default_thumb_folder_name);

		File thumbDir = new File(thumbDirPath);
		if(!thumbDir.exists()){
			thumbDir.mkdirs();
		}

		return thumbDir.getPath();
	}

	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName) {
		return findNewFileNameIfNeeded(context, filePath, fileName, null);
	}

	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName, Integer number) {
		if (number == null) {
			number = 1;
		}

		File file = new File(ensureLastSlash(filePath) + fileName);
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
		return ensureLastSlash(filePath) + fileName;
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

	public static void checkIsMainFolderWritable(final Activity activity){
        String homeDir = getHomeDir(activity);

        File homeDirFile = new File(homeDir);

        if(!homeDirFile.exists() || !homeDirFile.canWrite()){
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.home_folder_problem_title));

            builder.setMessage(activity.getString(R.string.home_folder_problem));
            builder.setNegativeButton(activity.getString(R.string.ok), null);
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

	public static boolean requestSDCardPermission(final Activity activity){
		/*if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
				new AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.sdcard_perm_explain))
						.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, StinglePhotosApplication.REQUEST_SD_CARD_PERMISSION);
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
				activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, StinglePhotosApplication.REQUEST_SD_CARD_PERMISSION);
			}
			return false;
		}*/
		if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				new AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.sdcard_perm_explain))
						.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, StinglePhotosApplication.REQUEST_SD_CARD_PERMISSION);
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
				activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, StinglePhotosApplication.REQUEST_SD_CARD_PERMISSION);
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

	public static int getFileTypeFromUri(String path){
		int fileType = Crypto.FILE_TYPE_GENERAL;
		if(isImageFile(path)){
			fileType = Crypto.FILE_TYPE_PHOTO;
		}
		else if(isVideoFile(path)){
			fileType = Crypto.FILE_TYPE_VIDEO;
		}

		return fileType;
	}

	public static int getFileTypeFromUri(Context context, Uri uri){
		String mimeType = getMimeType(context, uri);

		int fileType = Crypto.FILE_TYPE_PHOTO;
		if(mimeType != null) {
			if (mimeType.startsWith("image")) {
				fileType = Crypto.FILE_TYPE_PHOTO;
			} else if (mimeType.startsWith("video")) {
				fileType = Crypto.FILE_TYPE_VIDEO;
			}
		}

		return fileType;
	}

	public static String getMimeType(Context context, Uri uri) {
		String mimeType = null;
		if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
			ContentResolver cr = context.getContentResolver();
			mimeType = cr.getType(uri);
		} else {
			String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri
					.toString());
			mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
					fileExtension.toLowerCase());
		}
		return mimeType;
	}

	public static int getVideoDurationFromUri(Context context, Uri uri){
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		int duration = 0;
		
		try {
			retriever.setDataSource(context, uri);
			String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
			duration = (int) (Long.parseLong(time) / 1000);

			retriever.release();
		}
		catch (RuntimeException e){ }

		return duration;
	}

	public static void deleteTempFiles(Context context){
		File file = new File(context.getCacheDir().getPath() + "/"+FileManager.SHARE_CACHE_DIR);
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File f : files) {
					if (!f.isDirectory()) {
						f.delete();
					}
				}
			}
		}
	}

	public static String getCameraTmpDir(Context context) {
		File cacheDir = context.getCacheDir();
		File tmpDir = new File(cacheDir.getAbsolutePath() + "/camera_tmp/");
		tmpDir.mkdirs();
		return tmpDir.getAbsolutePath() + "/";
	}

	public static abstract class OnFinish{
		public abstract void onFinish();
	}
}
