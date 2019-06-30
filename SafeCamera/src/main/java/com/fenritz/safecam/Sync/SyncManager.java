package com.fenritz.safecam.Sync;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Util.Helpers;
import com.google.gson.JsonObject;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class SyncManager {

	protected Context context;
	protected SQLiteDatabase db;

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

	public static class FsSyncAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;

		public FsSyncAsyncTask(Context context){
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params) {
			StingleDbHelper db = new StingleDbHelper(context);
			File dir = new File(Helpers.getHomeDir(this.context));

			Cursor result = db.getFilesList(StingleDbHelper.GET_MODE_ONLY_LOCAL);

			while(result.moveToNext()) {
				String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
				File file = new File(dir.getPath() + "/" + filename);
				if(!file.exists()){
					db.deleteFile(filename);
				}
			}

			File[] currentFolderFiles = dir.listFiles();

			for (File file : currentFolderFiles) {
				if (file.isFile() && file.getName().endsWith(SafeCameraApplication.FILE_EXTENSION)) {
					db.insertFile(file.getName(), true, false, String.valueOf(file.lastModified()));
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
			final ArrayList<String> files = new ArrayList<String>();

			Cursor result = db.getFilesList(StingleDbHelper.GET_MODE_ONLY_LOCAL);

			while(result.moveToNext()) {
				String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
				files.add(filename);
			}
			result.close();

			for(String file : files) {
				JSONObject resp = HttpsClient.multipartRequest(
						context.getString(R.string.api_server_url) + context.getString(R.string.upload_file_path),
						postParams,
						dir.getPath() + "/" + file,
						"file",
						"application/stinglephoto"
				);
				StingleResponse response = new StingleResponse(this.context, resp);
				if(response.isStatusOk()){
					db.updateFile(file, true, true, null, null);
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
}
