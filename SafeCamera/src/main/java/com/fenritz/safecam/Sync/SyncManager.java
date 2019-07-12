package com.fenritz.safecam.Sync;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Util.Helpers;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class SyncManager {

	protected Context context;
	protected SQLiteDatabase db;
	public static final String PREF_LAST_SEEN_TIME = "file_last_seen_time";
	public static final String SP_FILE_MIME_TYPE = "application/stinglephoto";

	public SyncManager(Context context){
		this.context = context;

		StingleDbHelper dbHelper = new StingleDbHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();

	}

	public static void syncFSToDB(Context context){
		(new FsSyncAsyncTask(context)).execute();
	}
	public static void uploadToCloud(Context context){
		(new UploadToCloudAsyncTask(context)).execute();
	}
	public static void syncCloudToLocalDb(Context context){
		(new SyncCloudToLocalDbAsyncTask(context)).execute();
	}

	public static class FsSyncAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;

		public FsSyncAsyncTask(Context context){
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params) {
			StingleDbHelper db = new StingleDbHelper(context);
			File dir = new File(Helpers.getHomeDir(this.context));

			Cursor result = db.getFilesList(StingleDbHelper.GET_MODE_LOCAL);

			while(result.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(result);
				File file = new File(dir.getPath() + "/" + dbFile.filename);
				if(!file.exists()){
					if(dbFile.isRemote){
						dbFile.isLocal = false;
						db.updateFile(dbFile);
					}
					else {
						db.deleteFile(dbFile.filename);
					}
				}
			}

			File[] currentFolderFiles = dir.listFiles();

			for (File file : currentFolderFiles) {
				if (file.isFile() && file.getName().endsWith(SafeCameraApplication.FILE_EXTENSION)) {
					db.insertFile(file.getName(), true, false, file.lastModified(), file.lastModified());
				}
			}
			db.close();
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

		}
	}

	public static class UploadToCloudAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;

		public UploadToCloudAsyncTask(Context context){
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params) {
			final StingleDbHelper db = new StingleDbHelper(context);

			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));

			File dir = new File(Helpers.getHomeDir(context));
			File thumbDir = new File(Helpers.getThumbsDir(context));
			final ArrayList<String> files = new ArrayList<String>();

			Cursor result = db.getFilesList(StingleDbHelper.GET_MODE_ONLY_LOCAL);

			while(result.moveToNext()) {
				String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
				files.add(filename);
			}
			result.close();

			for(String file : files) {
				HttpsClient.FileToUpload fileToUpload = new HttpsClient.FileToUpload("file", dir.getPath() + "/" + file, SP_FILE_MIME_TYPE);
				HttpsClient.FileToUpload thumbToUpload = new HttpsClient.FileToUpload("thumb", thumbDir.getPath() + "/" + file, SP_FILE_MIME_TYPE);

				ArrayList<HttpsClient.FileToUpload> filesToUpload = new ArrayList<HttpsClient.FileToUpload>();
				filesToUpload.add(fileToUpload);
				filesToUpload.add(thumbToUpload);

				JSONObject resp = HttpsClient.multipartUpload(
						context.getString(R.string.api_server_url) + context.getString(R.string.upload_file_path),
						postParams,
						filesToUpload
				);
				StingleResponse response = new StingleResponse(this.context, resp);
				if(response.isStatusOk()){
					db.markFileAsRemote(file);
				}
			}

			db.close();
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

		}
	}

	public static class SyncCloudToLocalDbAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected StingleDbHelper db;
		protected long lastSeenTime = 0;

		public SyncCloudToLocalDbAsyncTask(Context context){
			this.context = context;
			db = new StingleDbHelper(context);
		}

		@Override
		protected Void doInBackground(Void... params) {
			lastSeenTime = Helpers.getPreference(context, PREF_LAST_SEEN_TIME, (long)0);

			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("lastSeenTime", String.valueOf(lastSeenTime));


			JSONObject resp = HttpsClient.postFunc(
					context.getString(R.string.api_server_url) + context.getString(R.string.get_server_files_path),
					postParams
			);
			StingleResponse response = new StingleResponse(this.context, resp);
			if(response.isStatusOk()){
				String filesStr = response.get("files");
				if(filesStr != null){
					try {
						JSONArray files = new JSONArray(filesStr);
						for(int i=0; i<files.length(); i++){
							JSONObject file = files.optJSONObject(i);
							if(file != null){
								processFile(new StingleDbFile(file));
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
			Helpers.storePreference(context, PREF_LAST_SEEN_TIME, lastSeenTime);

			return null;
		}

		protected void processFile(StingleDbFile remoteFile){
			StingleDbFile file = db.getFileIfExists(remoteFile.filename);

			if(file == null){
				db.insertFile(remoteFile.filename, false, true, remoteFile.dateCreated, remoteFile.dateModified);
			}
			else {
				if (file.dateModified != remoteFile.dateModified) {
					file.dateModified = remoteFile.dateModified;
					db.updateFile(file);
				}
			}

			if(remoteFile.dateCreated > lastSeenTime) {
				lastSeenTime = remoteFile.dateCreated;
			}
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			db.close();
		}
	}
}
