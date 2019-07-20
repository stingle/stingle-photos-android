package com.fenritz.safecam.Sync;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.DashboardActivity;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.SetUpActivity;
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
	public static final String PREF_LAST_DEL_SEEN_TIME = "file_last_del_seen_time";
	public static final String SP_FILE_MIME_TYPE = "application/stinglephoto";

	public static final int FOLDER_MAIN = 0;
	public static final int FOLDER_TRASH = 1;

	public static final int DELETE_EVENT_TRASH = 1;
	public static final int DELETE_EVENT_RESTORE = 2;
	public static final int DELETE_EVENT_DELETE = 3;

	public SyncManager(Context context){
		this.context = context;

		StingleDbHelper dbHelper = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);
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
			StingleDbHelper db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);
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
			uploadFolder(FOLDER_MAIN);
			uploadFolder(FOLDER_TRASH);


			return null;
		}

		protected void uploadFolder(int folder){
			final StingleDbHelper db = new StingleDbHelper(context, (folder == FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));

			File dir = new File(Helpers.getHomeDir(context));
			File thumbDir = new File(Helpers.getThumbsDir(context));

			Cursor result = db.getFilesList(StingleDbHelper.GET_MODE_ONLY_LOCAL);

			while(result.moveToNext()) {
				String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
				String dateCreated = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED));
				String dateModified = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED));

				Log.d("uploadingFile", filename);
				HttpsClient.FileToUpload fileToUpload = new HttpsClient.FileToUpload("file", dir.getPath() + "/" + filename, SP_FILE_MIME_TYPE);
				HttpsClient.FileToUpload thumbToUpload = new HttpsClient.FileToUpload("thumb", thumbDir.getPath() + "/" + filename, SP_FILE_MIME_TYPE);

				ArrayList<HttpsClient.FileToUpload> filesToUpload = new ArrayList<HttpsClient.FileToUpload>();
				filesToUpload.add(fileToUpload);
				filesToUpload.add(thumbToUpload);

				HashMap<String, String> postParams = new HashMap<String, String>();

				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("folder", String.valueOf(folder));
				postParams.put("dateCreated", dateCreated);
				postParams.put("dateModified", dateModified);

				JSONObject resp = HttpsClient.multipartUpload(
						context.getString(R.string.api_server_url) + context.getString(R.string.upload_file_path),
						postParams,
						filesToUpload
				);
				StingleResponse response = new StingleResponse(this.context, resp, false);
				if(response.isStatusOk()){
					db.markFileAsRemote(filename);
				}
			}
			result.close();

			db.close();
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

		}
	}

	public static class SyncCloudToLocalDbAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected StingleDbHelper db;
		protected StingleDbHelper trashDb;
		protected long lastSeenTime = 0;
		protected long lastDelSeenTime = 0;

		public SyncCloudToLocalDbAsyncTask(Context context){
			this.context = context;
			db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);
			trashDb = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_TRASH);
		}

		@Override
		protected Void doInBackground(Void... params) {
			lastSeenTime = Helpers.getPreference(context, PREF_LAST_SEEN_TIME, (long)0);
			lastDelSeenTime = Helpers.getPreference(context, PREF_LAST_DEL_SEEN_TIME, (long)0);

			getFileList();

			Helpers.storePreference(context, PREF_LAST_SEEN_TIME, lastSeenTime);
			Helpers.storePreference(context, PREF_LAST_DEL_SEEN_TIME, lastDelSeenTime);

			return null;
		}

		protected void getFileList(){
			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("lastSeenTime", String.valueOf(lastSeenTime));
			postParams.put("lastDelSeenTime", String.valueOf(lastDelSeenTime));


			JSONObject resp = HttpsClient.postFunc(
					context.getString(R.string.api_server_url) + context.getString(R.string.get_server_files_path),
					postParams
			);
			StingleResponse response = new StingleResponse(this.context, resp, false);
			if(response.isStatusOk()){
				String delsStr = response.get("deletes");
				if(delsStr != null){
					try {
						JSONArray deletes = new JSONArray(delsStr);
						for(int i=0; i<deletes.length(); i++){
							JSONObject deleteEvent = deletes.optJSONObject(i);
							if(deleteEvent != null){
								processDeleteEvent(deleteEvent);
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				String filesStr = response.get("files");
				if(filesStr != null){
					try {
						JSONArray files = new JSONArray(filesStr);
						for(int i=0; i<files.length(); i++){
							JSONObject file = files.optJSONObject(i);
							if(file != null){
								processFile(new StingleDbFile(file), FOLDER_MAIN);
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				String trashStr = response.get("trash");
				if(trashStr != null){
					try {
						JSONArray files = new JSONArray(trashStr);
						for(int i=0; i<files.length(); i++){
							JSONObject file = files.optJSONObject(i);
							if(file != null){
								processFile(new StingleDbFile(file), FOLDER_TRASH);
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}


			}
		}

		protected void processFile(StingleDbFile remoteFile, int folder){
			StingleDbHelper myDb = db;
			if (folder == FOLDER_TRASH){
				myDb = trashDb;
			}

			StingleDbFile file = myDb.getFileIfExists(remoteFile.filename);

			if(file == null){
				myDb.insertFile(remoteFile.filename, false, true, remoteFile.dateCreated, remoteFile.dateModified);
			}
			else {
				boolean needUpdate = false;
				if (file.dateModified != remoteFile.dateModified) {
					file.dateModified = remoteFile.dateModified;
					needUpdate = true;
				}
				if(file.isRemote != true) {
					file.isRemote = true;
					needUpdate = true;
				}
				if(needUpdate) {
					myDb.updateFile(file);
				}
			}

			if(remoteFile.dateModified > lastSeenTime) {
				lastSeenTime = remoteFile.dateModified;
			}
		}

		protected void processDeleteEvent(JSONObject event){

			try {
				String filename = event.getString("file");
				Integer type = event.getInt("type");
				Long date = event.getLong("date");

				if(type == DELETE_EVENT_TRASH) {
					StingleDbFile file = db.getFileIfExists(filename);
					if(file != null) {
						db.deleteFile(file.filename);
						trashDb.insertFile(file);
					}
				}
				else if(type == DELETE_EVENT_RESTORE) {
					StingleDbFile file = trashDb.getFileIfExists(filename);
					if(file != null) {
						trashDb.deleteFile(file.filename);
						db.insertFile(file);
					}
				}
				else if(type == DELETE_EVENT_DELETE) {
					StingleDbFile file = db.getFileIfExists(filename);
					if(file != null) {
						db.deleteFile(file.filename);
					}
					file = trashDb.getFileIfExists(filename);
					if(file != null) {
						trashDb.deleteFile(file.filename);
					}

					String homeDir = Helpers.getHomeDir(context);
					String thumbDir = Helpers.getThumbsDir(context);
					File mainFile = new File(homeDir + "/" + filename);
					File thumbFile = new File(thumbDir + "/" + filename);

					if(mainFile.exists()){
						mainFile.delete();
					}
					if(thumbFile.exists()){
						thumbFile.delete();
					}
				}

				if(date > lastDelSeenTime) {
					lastDelSeenTime = date;
				}

			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			db.close();
		}
	}

	public static class MoveToTrashAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected ArrayList<String> filenames;
		protected OnFinish onFinish;

		public MoveToTrashAsyncTask(Context context, ArrayList<String> filenames, OnFinish onFinish){
			this.context = context;
			this.filenames = filenames;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... params) {
			StingleDbHelper db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);
			StingleDbHelper trashDb = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_TRASH);

			ArrayList<String> filenamesToNotify = new ArrayList<String>();

			for(String filename : filenames) {
				StingleDbFile file = db.getFileIfExists(filename);
				if (file != null) {
					if (file.isRemote) {
						filenamesToNotify.add(file.filename);
					}
				}
			}

			if (filenamesToNotify.size() > 0 && !notifyCloudAboutTrash(filenamesToNotify)) {
				db.close();
				trashDb.close();
				return null;
			}

			for(String filename : filenames) {
				StingleDbFile file = db.getFileIfExists(filename);
				if(file != null) {
					db.deleteFile(file.filename);
					file.dateModified = System.currentTimeMillis();
					trashDb.insertFile(file);
				}
			}


			db.close();
			trashDb.close();
			return null;
		}

		protected boolean notifyCloudAboutTrash(ArrayList<String> filenamesToNotify){
			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("count", String.valueOf(filenamesToNotify.size()));
			for(int i=0; i < filenamesToNotify.size(); i++) {
				postParams.put("filename" + String.valueOf(i), filenamesToNotify.get(i));
			}

			JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.trash_file_path), postParams);
			StingleResponse response = new StingleResponse(this.context, json, false);

			if(response.isStatusOk()) {
				return true;
			}
			return false;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}


	public static class RestoreFromTrashAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected ArrayList<String> filenames;
		protected OnFinish onFinish;

		public RestoreFromTrashAsyncTask(Context context, ArrayList<String> filenames, OnFinish onFinish){
			this.context = context;
			this.filenames = filenames;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... params) {
			StingleDbHelper db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);
			StingleDbHelper trashDb = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_TRASH);

			ArrayList<String> filenamesToNotify = new ArrayList<String>();

			for(String filename : filenames) {
				StingleDbFile file = trashDb.getFileIfExists(filename);
				if (file != null) {
					if (file.isRemote) {
						filenamesToNotify.add(filename);
					}
				}
			}

			if (filenamesToNotify.size() > 0 && !notifyCloudAboutRestore(filenamesToNotify)) {
				db.close();
				trashDb.close();
				return null;
			}

			for(String filename : filenames) {
				StingleDbFile file = trashDb.getFileIfExists(filename);
				if(file != null) {
					trashDb.deleteFile(file.filename);
					file.dateModified = System.currentTimeMillis();
					db.insertFile(file);
				}
			}


			db.close();
			trashDb.close();
			return null;
		}

		protected boolean notifyCloudAboutRestore(ArrayList<String> filenamesToNotify){
			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("count", String.valueOf(filenamesToNotify.size()));
			for(int i=0; i < filenamesToNotify.size(); i++) {
				postParams.put("filename" + String.valueOf(i), filenamesToNotify.get(i));
			}

			JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.restore_file_path), postParams);
			StingleResponse response = new StingleResponse(this.context, json, false);

			if(response.isStatusOk()) {
				return true;
			}
			return false;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}

	public static class DeleteFilesAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected ArrayList<String> filenames;
		protected OnFinish onFinish;

		public DeleteFilesAsyncTask(Context context, ArrayList<String> filenames, OnFinish onFinish){
			this.context = context;
			this.filenames = filenames;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... params) {
			StingleDbHelper trashDb = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_TRASH);
			String homeDir = Helpers.getHomeDir(context);
			String thumbDir = Helpers.getThumbsDir(context);

			ArrayList<String> filenamesToNotify = new ArrayList<String>();

			for(String filename : filenames) {
				StingleDbFile file = trashDb.getFileIfExists(filename);
				if (file != null) {
					if (file.isRemote) {
						filenamesToNotify.add(file.filename);
					}
				}
			}

			if (filenamesToNotify.size() > 0 && !notifyCloudAboutDelete(filenamesToNotify)) {
				trashDb.close();
				return null;
			}

			for(String filename : filenames) {
				StingleDbFile file = trashDb.getFileIfExists(filename);
				File mainFile = new File(homeDir + "/" + filename);
				File thumbFile = new File(thumbDir + "/" + filename);

				if(mainFile.exists()){
					mainFile.delete();
				}
				if(thumbFile.exists()){
					thumbFile.delete();
				}

				if(file != null) {
					trashDb.deleteFile(file.filename);
				}
			}


			trashDb.close();
			return null;
		}

		protected boolean notifyCloudAboutDelete(ArrayList<String> filenamesToNotify){
			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("count", String.valueOf(filenamesToNotify.size()));
			for(int i=0; i < filenamesToNotify.size(); i++) {
				postParams.put("filename" + String.valueOf(i), filenamesToNotify.get(i));
			}

			JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.delete_file_path), postParams);
			StingleResponse response = new StingleResponse(this.context, json, false);

			if (response.isStatusOk()) {
				return true;
			}
			return false;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}

	public static abstract class OnFinish{
		public abstract void onFinish();
	}
}
