package org.stingle.photos.Sync;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import static android.content.Context.BATTERY_SERVICE;

public class UploadToCloud {

	private Context context;
	private File dir;
	private File thumbDir;
	private UploadProgress progress = null;
	private AsyncTask<?,?,?> task;
	private int uploadedFilesCount = 0;

	public UploadToCloud(Context context, UploadProgress progress, AsyncTask<?,?,?> task){
		this.context = context;
		this.progress = progress;
		this.task = task;
		dir = new File(FileManager.getHomeDir(context));
		thumbDir = new File(FileManager.getThumbsDir(context));
	}

	public void upload(){
		boolean isUploadSuspended = Helpers.getPreference(context, SyncManager.PREF_SUSPEND_UPLOAD, false);
		if(isUploadSuspended){
			int lastAvailableSpace = Helpers.getPreference(context, SyncManager.PREF_LAST_AVAILABLE_SPACE, 0);
			int availableSpace = Helpers.getAvailableUploadSpace(context);
			if(availableSpace > lastAvailableSpace || availableSpace > 0){
				Helpers.storePreference(context, SyncManager.PREF_SUSPEND_UPLOAD, false);
				Helpers.deletePreference(context, SyncManager.PREF_LAST_AVAILABLE_SPACE);
				isUploadSuspended = false;
				Log.d("upload", "resuming upload");
			}
		}

		int allowedStatus = isUploadAllowed(context);
		if(isUploadSuspended) {
			Log.d("upload", "upload is disabled, no space");
			SyncManager.setSyncStatus(context, SyncManager.STATUS_NO_SPACE_LEFT);
			return;
		}
		else if(allowedStatus != 0) {
			Log.d("upload", "upload is disabled, not allowed");
			SyncManager.setSyncStatus(context, allowedStatus);
			return;
		}

		if(progress != null){
			progress.setTotalItemsNumber(getFilesCountToUpload(SyncManager.GALLERY) + getFilesCountToUpload(SyncManager.TRASH) + getFilesCountToUpload(SyncManager.ALBUM));
		}

		SyncManager.setSyncStatus(context, SyncManager.STATUS_UPLOADING);

		uploadSet(SyncManager.GALLERY);
		uploadSet(SyncManager.TRASH);
		uploadSet(SyncManager.ALBUM);
		SyncManager.setSyncStatus(context, SyncManager.STATUS_IDLE);
	}

	private int isUploadAllowed(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		if(!SyncManager.isBackupEnabled(context)){
			return SyncManager.STATUS_DISABLED;
		}

		boolean isOnlyOnWifi = prefs.getBoolean(SyncManager.PREF_BACKUP_ONLY_WIFI, false);
		int uploadBatteryLevel = prefs.getInt(SyncManager.PREF_BACKUP_BATTERY_LEVEL, 30);

		if(isOnlyOnWifi) {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if(cm != null) {
				NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
				if (activeNetwork != null) {
					// connected to the internet
					if (activeNetwork.getType() != ConnectivityManager.TYPE_WIFI) {
						return SyncManager.STATUS_NOT_WIFI;
					}
				} else {
					return SyncManager.STATUS_NOT_WIFI;
				}
			}
		}

		if(uploadBatteryLevel > 0){
			IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = context.registerReceiver(null, iFilter);
			if(batteryStatus != null) {
				int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
				boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
						status == BatteryManager.BATTERY_STATUS_FULL;

				BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
				if(bm != null) {
					int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

					Log.d("battery level", String.valueOf(batLevel));

					if (!isCharging && batLevel < uploadBatteryLevel) {
						return SyncManager.STATUS_BATTERY_LOW;
					}
				}
			}
		}

		return 0;
	}

	protected int getFilesCountToUpload(int set){
		FilesDb db;
		if(set == SyncManager.GALLERY || set == SyncManager.TRASH){
			db = new GalleryTrashDb(context, set);
		}
		else if (set == SyncManager.ALBUM){
			db = new AlbumFilesDb(context);
		}
		else{
			return 0;
		}

		Cursor result = db.getFilesList(GalleryTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_ASC, "", null);
		int uploadCount = result.getCount();
		result.close();

		Cursor reuploadResult = db.getReuploadFilesList();
		int reuploadCount = reuploadResult.getCount();
		reuploadResult.close();

		db.close();

		return uploadCount + reuploadCount;
	}

	protected void uploadSet(int set){
		FilesDb db;
		if(set == SyncManager.GALLERY || set == SyncManager.TRASH){
			db = new GalleryTrashDb(context, set);
		}
		else if (set == SyncManager.ALBUM){
			db = new AlbumFilesDb(context);
		}
		else{
			return;
		}

		Cursor result = db.getFilesList(GalleryTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_ASC, null, null);
		while(result.moveToNext()) {
			if(task != null && task.isCancelled()){
				break;
			}
			uploadedFilesCount++;
			if(progress != null){
				progress.uploadProgress(uploadedFilesCount);
			}
			uploadFile(set, db, result, false);
		}
		result.close();

		Cursor reuploadResult = db.getReuploadFilesList();
		while(reuploadResult.moveToNext()) {
			if(task != null && task.isCancelled()){
				break;
			}
			uploadedFilesCount++;
			if(progress != null){
				progress.uploadProgress(uploadedFilesCount);
			}
			uploadFile(set, db, reuploadResult, true);
		}
		reuploadResult.close();

		db.close();
	}

	protected void uploadFile(int set, FilesDb db, Cursor result, boolean isReupload){
		String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FILENAME));
		String version = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_VERSION));
		String dateCreated = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED));
		String dateModified = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));
		String headers = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_HEADERS));
		String albumId = "";
		try {
			albumId = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_ALBUM_ID));
		}
		catch (IllegalArgumentException ignored) {}

		if(progress != null){
			progress.currentFile(filename, headers, set, albumId);
		}

		Log.d("uploadingFile", filename);
		File file = new File(dir.getPath() + "/" + filename);
		File thumb = new File(thumbDir.getPath() + "/" + filename);

		int overallSize = Helpers.bytesToMb(file.length() + thumb.length());
		if(!Helpers.isUploadSpaceAvailable(context, overallSize)){
			Helpers.storePreference(context, SyncManager.PREF_LAST_AVAILABLE_SPACE, Helpers.getAvailableUploadSpace(context));
			Helpers.storePreference(context, SyncManager.PREF_SUSPEND_UPLOAD, true);
			Log.d("not_uploading", "space is over, not uploading file " + file.getName());
			return;
		}

		HttpsClient.FileToUpload fileToUpload = new HttpsClient.FileToUpload("file", file.getPath(), SyncManager.SP_FILE_MIME_TYPE);
		HttpsClient.FileToUpload thumbToUpload = new HttpsClient.FileToUpload("thumb", thumb.getPath(), SyncManager.SP_FILE_MIME_TYPE);

		ArrayList<HttpsClient.FileToUpload> filesToUpload = new ArrayList<>();
		filesToUpload.add(fileToUpload);
		filesToUpload.add(thumbToUpload);

		HashMap<String, String> postParams = new HashMap<>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("set", String.valueOf(set));
		postParams.put("albumId", albumId);
		postParams.put("version", version);
		postParams.put("dateCreated", dateCreated);
		postParams.put("dateModified", dateModified);
		postParams.put("headers", headers);

		JSONObject resp = HttpsClient.multipartUpload(
				StinglePhotosApplication.getApiUrl() + context.getString(R.string.upload_file_path),
				postParams,
				filesToUpload
		);
		StingleResponse response = new StingleResponse(this.context, resp, false);
		if(response.isStatusOk()){
			db.markFileAsRemote(filename);

			String spaceUsedStr = response.get("spaceUsed");
			String spaceQuotaStr = response.get("spaceQuota");

			if(spaceUsedStr != null && spaceUsedStr.length() > 0){
				int spaceUsed = Integer.parseInt(spaceUsedStr);
				if(spaceUsed >= 0){
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_USED, spaceUsed);
				}
			}

			if(spaceQuotaStr != null && spaceQuotaStr.length() > 0){
				int spaceQuota = Integer.parseInt(spaceQuotaStr);
				if(spaceQuota >= 0){
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, spaceQuota);
				}
			}

			if(progress != null){
				progress.fileUploadFinished(filename, set, albumId);
			}
		}

		if(isReupload){
			db.markFileAsReuploaded(filename);
		}
	}

	public abstract static class UploadProgress{
		public int totalItemsNumber = 0;
		public String currentFile;
		public String headers;
		public int set;
		public String albumId;
		public int uploadedFilesCount;

		public void setTotalItemsNumber(int number){
			totalItemsNumber = number;
		}

		public void currentFile(String filename, String headers, int set, String albumId){
			this.currentFile = filename;
			this.headers = headers;
			this.set = set;
			this.albumId = albumId;
		}
		public void fileUploadFinished(String filename, int set, String albumId){

		}
		public void uploadProgress(int uploadedFilesCount){
			this.uploadedFilesCount = uploadedFilesCount;
		}
	}
}
