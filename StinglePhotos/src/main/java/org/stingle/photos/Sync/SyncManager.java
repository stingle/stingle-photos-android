package org.stingle.photos.Sync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.AsyncTasks.Sync.FsSyncAsyncTask;
import org.stingle.photos.AsyncTasks.Sync.SyncCloudToLocalDbAsyncTask;
import org.stingle.photos.AsyncTasks.Sync.UploadToCloudAsyncTask;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.Executor;

public class SyncManager {

	protected Context context;
	protected SQLiteDatabase db;
	public static final String PREF_LAST_SEEN_TIME = "file_last_seen_time";
	public static final String PREF_LAST_DEL_SEEN_TIME = "file_last_del_seen_time";
	public static final String PREF_LAST_SPACE_USED = "last_space_used";
	public static final String PREF_LAST_SPACE_QUOTA = "last_space_quota";
	public static final String PREF_SUSPEND_UPLOAD = "suspend_upload";
	public static final String PREF_LAST_AVAILABLE_SPACE = "last_available_space";

	public static final String PREF_BACKUP_ENABLED = "enable_backup";
	public static final String PREF_BACKUP_ONLY_WIFI = "upload_only_on_wifi";
	public static final String PREF_BACKUP_BATTERY_LEVEL = "upload_battery_level";

	public static final String SP_FILE_MIME_TYPE = "application/stinglephoto";

	public static final int FOLDER_MAIN = 0;
	public static final int FOLDER_TRASH = 1;
	public static final int FOLDER_ALBUM = 2;
	public static final int FOLDER_SHARE = 3;

	public static final int DELETE_EVENT_TRASH = 1;
	public static final int DELETE_EVENT_RESTORE = 2;
	public static final int DELETE_EVENT_DELETE = 3;

	public SyncManager(Context context){
		this.context = context;
	}

	public static void syncFSToDB(Context context, OnFinish onFinish, Executor executor){
		if(executor == null){
			executor = AsyncTask.THREAD_POOL_EXECUTOR;
		}
		(new FsSyncAsyncTask(context, onFinish)).executeOnExecutor(executor);
	}
	public static UploadToCloudAsyncTask uploadToCloud(Context context, UploadToCloudAsyncTask.UploadProgress progress, OnFinish onFinish, Executor executor){
		if(executor == null){
			executor = AsyncTask.THREAD_POOL_EXECUTOR;
		}
		UploadToCloudAsyncTask uploadTask = new UploadToCloudAsyncTask(context, progress, onFinish);
		uploadTask.executeOnExecutor(executor);
		return uploadTask;

	}
	public static void syncCloudToLocalDb(Context context, OnFinish onFinish, Executor executor){
		if(executor == null){
			executor = AsyncTask.THREAD_POOL_EXECUTOR;
		}
		(new SyncCloudToLocalDbAsyncTask(context, onFinish)).executeOnExecutor(executor);
	}


	public static boolean downloadFile(Context context, String filename, String outputPath, boolean isThumb, int folder){
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", filename);
		postParams.put("folder", String.valueOf(folder));
		if(isThumb) {
			postParams.put("thumb", "1");
		}

		try {
			HttpsClient.downloadFile(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams, outputPath);
			return true;
		}
		catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {

		}
		return false;
	}

	public static abstract class OnFinish{
		public abstract void onFinish(Boolean needToUpdateUI);
	}

	public static void resetAndStopSync(Context context){
		Helpers.deletePreference(context, SyncManager.PREF_LAST_SEEN_TIME);
		Helpers.deletePreference(context, SyncManager. PREF_LAST_DEL_SEEN_TIME);

		FilesTrashDb filesDb = new FilesTrashDb(context, StingleDbContract.Columns.TABLE_NAME_FILES);
		filesDb.truncateTable();
		filesDb.close();

		FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Columns.TABLE_NAME_TRASH);
		trashDb.truncateTable();
		trashDb.close();
	}

	public static boolean notifyCloudAboutAlbumAdd(Context context, String albumId, String data, String albumPK, long dateCreated, long dateModified){
		HashMap<String, String> params = new HashMap<>();

		params.put("albumId", albumId);
		params.put("data", data);
		params.put("albumPK", albumPK);
		params.put("dateCreated", String.valueOf(dateCreated));
		params.put("dateModified", String.valueOf(dateModified));

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.add_album), postParams);
			StingleResponse response = new StingleResponse(context, json, false);

			if (response.isStatusOk()) {
				return true;
			}
			return false;
		}
		catch (CryptoException e){
			return false;
		}

	}

	public static boolean notifyCloudAboutFileMove(Context context){
		HashMap<String, String> params = new HashMap<>();
		long timestamp = System.currentTimeMillis();
		params.put("time", String.valueOf(timestamp));

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.empty_trash_path), postParams);
			StingleResponse response = new StingleResponse(context, json, false);

			if (response.isStatusOk()) {
				return true;
			}
			return false;
		}
		catch (CryptoException e){
			return false;
		}
	}
}
