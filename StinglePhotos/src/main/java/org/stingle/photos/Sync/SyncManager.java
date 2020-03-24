package org.stingle.photos.Sync;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
	public static final int FOLDER_SHARE = 2;

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

	public static class FsSyncAsyncTask extends AsyncTask<Void, Void, Boolean> {

		protected Context context;
		protected OnFinish onFinish;

		public FsSyncAsyncTask(Context context, OnFinish onFinish){
			this.context = context;
			this.onFinish = onFinish;
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			boolean result1 = fsSyncFolder(FOLDER_MAIN);
			boolean result2 = fsSyncFolder(FOLDER_TRASH);

			return result1 || result2;
		}

		protected boolean fsSyncFolder(int folder){
			boolean needToUpdateUI = false;
			FilesTrashDb db = new FilesTrashDb(context, (folder == FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));
			File dir = new File(FileManager.getHomeDir(this.context));

			Cursor result = db.getFilesList(FilesTrashDb.GET_MODE_ALL, StingleDb.SORT_ASC);

			while(result.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(result);
				File file = new File(dir.getPath() + "/" + dbFile.filename);
				if(file.exists()) {
					dbFile.isLocal = true;
					db.updateFile(dbFile);
				}
				else{
					if(dbFile.isRemote){
						if(dbFile.isLocal) {
							dbFile.isLocal = false;
							db.updateFile(dbFile);
						}
					}
					else {
						db.deleteFile(dbFile.filename);
					}
				}
			}

			if(folder == FOLDER_MAIN) {
				File[] currentFolderFiles = dir.listFiles();

				FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_TRASH);

				if(currentFolderFiles != null) {
					for (File file : currentFolderFiles) {
						if (file.isFile() && file.getName().endsWith(StinglePhotosApplication.FILE_EXTENSION) && db.getFileIfExists(file.getName()) == null && trashDb.getFileIfExists(file.getName()) == null) {
							try {
								String headers = Crypto.getFileHeadersFromFile(file.getAbsolutePath(), FileManager.getThumbsDir(context) + "/" + file.getName());
								db.insertFile(file.getName(), true, false, FilesTrashDb.INITIAL_VERSION, file.lastModified(), file.lastModified(), headers);
								needToUpdateUI = true;
							} catch (IOException | CryptoException e) {
								e.printStackTrace();
							}

						}
					}
				}
			}
			result.close();
			db.close();

			return needToUpdateUI;
		}

		@Override
		protected void onPostExecute(Boolean needToUpdateUI) {
			super.onPostExecute(needToUpdateUI);

			if(onFinish != null){
				onFinish.onFinish(needToUpdateUI);
			}
		}
	}

	public static class UploadToCloudAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected File dir;
		protected File thumbDir;
		protected OnFinish onFinish = null;
		protected UploadProgress progress = null;
		protected int uploadedFilesCount = 0;


		public UploadToCloudAsyncTask(Context context, UploadProgress progress, OnFinish onFinish){
			this.context = context;
			this.progress = progress;
			this.onFinish = onFinish;
			dir = new File(FileManager.getHomeDir(context));
			thumbDir = new File(FileManager.getThumbsDir(context));
		}

		@Override
		protected Void doInBackground(Void... params) {
			if(progress != null){
				progress.setTotalItemsNumber(getFilesCountToUpload(FOLDER_MAIN) + getFilesCountToUpload(FOLDER_TRASH));
			}

			uploadFolder(FOLDER_MAIN);
			uploadFolder(FOLDER_TRASH);

			return null;
		}

		protected int getFilesCountToUpload(int folder){
			FilesTrashDb db = new FilesTrashDb(context, (folder == FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));

			Cursor result = db.getFilesList(FilesTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_ASC);
			int uploadCount = result.getCount();
			result.close();

			Cursor reuploadResult = db.getReuploadFilesList();
			int reuploadCount = reuploadResult.getCount();
			reuploadResult.close();

			db.close();

			return uploadCount + reuploadCount;
		}

		protected void uploadFolder(int folder){
			FilesTrashDb db = new FilesTrashDb(context, (folder == FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));

			Cursor result = db.getFilesList(FilesTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_ASC);
			while(result.moveToNext()) {
				if(isCancelled()){
					break;
				}
				uploadedFilesCount++;
				if(progress != null){
					progress.uploadProgress(uploadedFilesCount);
				}
				uploadFile(folder, db, result, false);
			}
			result.close();

			Cursor reuploadResult = db.getReuploadFilesList();
			while(reuploadResult.moveToNext()) {
				if(isCancelled()){
					break;
				}
				uploadedFilesCount++;
				if(progress != null){
					progress.uploadProgress(uploadedFilesCount);
				}
				uploadFile(folder, db, reuploadResult, true);
			}
			reuploadResult.close();

			db.close();
		}

		protected void uploadFile(int folder, FilesTrashDb db, Cursor result, boolean isReupload){
			String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_FILENAME));
			String version = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_VERSION));
			String dateCreated = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_DATE_CREATED));
			String dateModified = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_DATE_MODIFIED));
			String headers = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Files.COLUMN_NAME_HEADERS));

			if(progress != null){
				progress.currentFile(filename);
			}

			Log.d("uploadingFile", filename);
			File file = new File(dir.getPath() + "/" + filename);
			File thumb = new File(thumbDir.getPath() + "/" + filename);

			int overallSize = Helpers.bytesToMb(file.length() + thumb.length());
			if(!Helpers.isUploadSpaceAvailable(context, overallSize)){
				Helpers.storePreference(context, PREF_LAST_AVAILABLE_SPACE, Helpers.getAvailableUploadSpace(context));
				Helpers.storePreference(context, PREF_SUSPEND_UPLOAD, true);
				Log.d("not_uploading", "space is over, not uploading file " + file.getName());
				return;
			}

			HttpsClient.FileToUpload fileToUpload = new HttpsClient.FileToUpload("file", file.getPath(), SP_FILE_MIME_TYPE);
			HttpsClient.FileToUpload thumbToUpload = new HttpsClient.FileToUpload("thumb", thumb.getPath(), SP_FILE_MIME_TYPE);

			ArrayList<HttpsClient.FileToUpload> filesToUpload = new ArrayList<HttpsClient.FileToUpload>();
			filesToUpload.add(fileToUpload);
			filesToUpload.add(thumbToUpload);

			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("folder", String.valueOf(folder));
			postParams.put("version", version);
			postParams.put("dateCreated", dateCreated);
			postParams.put("dateModified", dateModified);
			postParams.put("headers", headers);

			JSONObject resp = HttpsClient.multipartUpload(
					context.getString(R.string.api_server_url) + context.getString(R.string.upload_file_path),
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
						Helpers.storePreference(context, PREF_LAST_SPACE_USED, spaceUsed);
					}
				}

				if(spaceQuotaStr != null && spaceQuotaStr.length() > 0){
					int spaceQuota = Integer.parseInt(spaceQuotaStr);
					if(spaceQuota >= 0){
						Helpers.storePreference(context, PREF_LAST_SPACE_QUOTA, spaceQuota);
					}
				}

				if(progress != null){
					progress.fileUploadFinished(filename, folder);
				}
			}

			if(isReupload){
				db.markFileAsReuploaded(filename);
			}
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish(false);
			}
		}

		public abstract static class UploadProgress{
			public int totalItemsNumber = 0;
			public String currentFile;
			public int uploadedFilesCount;

			public void setTotalItemsNumber(int number){
				totalItemsNumber = number;
			}

			public void currentFile(String filename){
				this.currentFile = filename;
			}
			public void fileUploadFinished(String filename, int folder){

			}
			public void uploadProgress(int uploadedFilesCount){
				this.uploadedFilesCount = uploadedFilesCount;
			}
		}
	}

	public static class SyncCloudToLocalDbAsyncTask extends AsyncTask<Void, Void, Boolean> {

		protected Context context;
		protected OnFinish onFinish = null;
		protected FilesTrashDb db;
		protected FilesTrashDb trashDb;
		protected long lastSeenTime = 0;
		protected long lastDelSeenTime = 0;

		public SyncCloudToLocalDbAsyncTask(Context context, OnFinish onFinish){
			this.context = context;
			this.onFinish = onFinish;
			db = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_FILES);
			trashDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_TRASH);
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			lastSeenTime = Helpers.getPreference(context, PREF_LAST_SEEN_TIME, (long)0);
			lastDelSeenTime = Helpers.getPreference(context, PREF_LAST_DEL_SEEN_TIME, (long)0);

			boolean needToUpdateUI = getFileList();

			Helpers.storePreference(context, PREF_LAST_SEEN_TIME, lastSeenTime);
			Helpers.storePreference(context, PREF_LAST_DEL_SEEN_TIME, lastDelSeenTime);

			return needToUpdateUI;
		}

		protected boolean getFileList(){
			boolean needToUpdateUI = false;
			HashMap<String, String> postParams = new HashMap<String, String>();

			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("lastSeenTime", String.valueOf(lastSeenTime));
			postParams.put("lastDelSeenTime", String.valueOf(lastDelSeenTime));


			JSONObject resp = HttpsClient.postFunc(
					context.getString(R.string.api_server_url) + context.getString(R.string.get_updates_path),
					postParams
			);
			StingleResponse response = new StingleResponse(this.context, resp, false);
			if(response.isStatusOk()){
				String delsStr = response.get("deletes");
				if(delsStr != null && delsStr.length() > 0){
					try {
						JSONArray deletes = new JSONArray(delsStr);
						for(int i=0; i<deletes.length(); i++){
							JSONObject deleteEvent = deletes.optJSONObject(i);
							if(deleteEvent != null){
								processDeleteEvent(deleteEvent);
								needToUpdateUI = true;
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				String filesStr = response.get("files");
				if(filesStr != null && filesStr.length() > 0){
					try {
						JSONArray files = new JSONArray(filesStr);
						for(int i=0; i<files.length(); i++){
							JSONObject file = files.optJSONObject(i);
							if(file != null){
								StingleDbFile dbFile = new StingleDbFile(file);
								Log.d("receivedFile", dbFile.filename);
								processFile(dbFile, FOLDER_MAIN);
								needToUpdateUI = true;
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				String trashStr = response.get("trash");
				if(trashStr != null && trashStr.length() > 0){
					try {
						JSONArray files = new JSONArray(trashStr);
						for(int i=0; i<files.length(); i++){
							JSONObject file = files.optJSONObject(i);
							if(file != null){
								StingleDbFile dbFile = new StingleDbFile(file);
								Log.d("receivedTrash", dbFile.filename);
								processFile(dbFile, FOLDER_TRASH);
								needToUpdateUI = true;
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				String spaceUsedStr = response.get("spaceUsed");
				String spaceQuotaStr = response.get("spaceQuota");


				if(spaceUsedStr != null && spaceUsedStr.length() > 0){
					int spaceUsed = Integer.parseInt(spaceUsedStr);
					int oldSpaceUsed = Helpers.getPreference(context, PREF_LAST_SPACE_USED, 0);
					if(spaceUsed != oldSpaceUsed){
						Helpers.storePreference(context, PREF_LAST_SPACE_USED, spaceUsed);
						needToUpdateUI = true;
					}
				}

				if(spaceQuotaStr != null && spaceQuotaStr.length() > 0){
					int spaceQuota = Integer.parseInt(spaceQuotaStr);
					int oldSpaceQuota = Helpers.getPreference(context, PREF_LAST_SPACE_QUOTA, 0);
					if(spaceQuota != oldSpaceQuota){
						Helpers.storePreference(context, PREF_LAST_SPACE_QUOTA, spaceQuota);
						needToUpdateUI = true;
					}
				}

			}

			return needToUpdateUI;
		}

		protected void processFile(StingleDbFile remoteFile, int folder){
			FilesTrashDb myDb = db;
			if (folder == FOLDER_TRASH){
				myDb = trashDb;
			}

			StingleDbFile file = myDb.getFileIfExists(remoteFile.filename);

			if(file == null){
				myDb.insertFile(remoteFile.filename, false, true, remoteFile.version, remoteFile.dateCreated, remoteFile.dateModified, remoteFile.headers);
			}
			else {
				boolean needUpdate = false;
				boolean needDownload = false;
				if (file.dateModified != remoteFile.dateModified) {
					file.dateModified = remoteFile.dateModified;
					needUpdate = true;
				}
				if(file.isRemote != true) {
					file.isRemote = true;
					needUpdate = true;
				}
				if (file.version < remoteFile.version) {
					file.version = remoteFile.version;
					needUpdate = true;
					needDownload = true;
				}
				if(needUpdate) {
					myDb.updateFile(file);
				}
				if(needDownload){
					String homeDir = FileManager.getHomeDir(context);
					String thumbDir = FileManager.getThumbsDir(context);
					String mainFilePath = homeDir + "/" + file.filename;
					String thumbPath = thumbDir + "/" + file.filename;

					downloadFile(context, file.filename, mainFilePath, false);
					downloadFile(context, file.filename, thumbPath, true);
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

					String homeDir = FileManager.getHomeDir(context);
					String thumbDir = FileManager.getThumbsDir(context);
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
		protected void onPostExecute(Boolean needToUpdateUI) {
			super.onPostExecute(needToUpdateUI);
			db.close();

			if(onFinish != null){
				onFinish.onFinish(needToUpdateUI);
			}
		}
	}

	public static class MoveToTrashAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected ArrayList<StingleFile> files;
		protected OnFinish onFinish;

		public MoveToTrashAsyncTask(Context context, ArrayList<StingleFile> files, OnFinish onFinish){
			this.context = context;
			this.files = files;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... params) {
			FilesTrashDb db = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_FILES);
			FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_TRASH);

			ArrayList<String> filenamesToNotify = new ArrayList<String>();

			for(StingleFile file : files) {
				if (file.isRemote) {
					filenamesToNotify.add(file.filename);
				}
			}

			if (filenamesToNotify.size() > 0 && !notifyCloudAboutTrash(filenamesToNotify)) {
				db.close();
				trashDb.close();
				return null;
			}

			for(StingleFile file : files) {
				db.deleteFile(file.filename);
				file.dateModified = System.currentTimeMillis();
				trashDb.insertFile(file);
			}


			db.close();
			trashDb.close();
			return null;
		}

		protected boolean notifyCloudAboutTrash(ArrayList<String> filenamesToNotify){
			HashMap<String, String> params = new HashMap<>();

			params.put("count", String.valueOf(filenamesToNotify.size()));
			for(int i=0; i < filenamesToNotify.size(); i++) {
				params.put("filename" + i, filenamesToNotify.get(i));
			}

			try {
				HashMap<String, String> postParams = new HashMap<>();
				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

				JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.trash_file_path), postParams);
				StingleResponse response = new StingleResponse(this.context, json, false);

				if (response.isStatusOk()) {
					return true;
				}
				return false;
			}
			catch (CryptoException e){
				return false;
			}
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish(true);
			}
		}
	}


	public static class RestoreFromTrashAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected ArrayList<StingleFile> files;
		protected OnFinish onFinish;

		public RestoreFromTrashAsyncTask(Context context, ArrayList<StingleFile> files, OnFinish onFinish){
			this.context = context;
			this.files = files;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... params) {
			FilesTrashDb db = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_FILES);
			FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_TRASH);

			ArrayList<String> filenamesToNotify = new ArrayList<String>();

			for(StingleFile file : files) {
				if (file.isRemote) {
					filenamesToNotify.add(file.filename);
				}
			}

			if (filenamesToNotify.size() > 0 && !notifyCloudAboutRestore(filenamesToNotify)) {
				db.close();
				trashDb.close();
				return null;
			}

			for(StingleFile file : files) {
				trashDb.deleteFile(file.filename);
				file.dateModified = System.currentTimeMillis();
				db.insertFile(file);
			}


			db.close();
			trashDb.close();
			return null;
		}

		protected boolean notifyCloudAboutRestore(ArrayList<String> filenamesToNotify){
			HashMap<String, String> params = new HashMap<>();

			params.put("count", String.valueOf(filenamesToNotify.size()));
			for(int i=0; i < filenamesToNotify.size(); i++) {
				params.put("filename" + i, filenamesToNotify.get(i));
			}

			try {
				HashMap<String, String> postParams = new HashMap<>();
				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

				JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.restore_file_path), postParams);
				StingleResponse response = new StingleResponse(this.context, json, false);

				if (response.isStatusOk()) {
					return true;
				}
				return false;
			}
			catch (CryptoException e){
				return false;
			}
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish(true);
			}
		}
	}

	public static class DeleteFilesAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected ArrayList<StingleFile> files;
		protected OnFinish onFinish;

		public DeleteFilesAsyncTask(Context context, ArrayList<StingleFile> files, OnFinish onFinish){
			this.context = context;
			this.files = files;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... params) {
			FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_TRASH);
			String homeDir = FileManager.getHomeDir(context);
			String thumbDir = FileManager.getThumbsDir(context);

			ArrayList<String> filenamesToNotify = new ArrayList<String>();

			for(StingleFile file : files) {
				if (file.isRemote) {
					filenamesToNotify.add(file.filename);
				}
			}

			if (filenamesToNotify.size() > 0 && !notifyCloudAboutDelete(filenamesToNotify)) {
				trashDb.close();
				return null;
			}

			for(StingleFile file : files) {
				File mainFile = new File(homeDir + "/" + file.filename);
				File thumbFile = new File(thumbDir + "/" + file.filename);

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
			HashMap<String, String> params = new HashMap<String, String>();

			params.put("count", String.valueOf(filenamesToNotify.size()));
			for(int i=0; i < filenamesToNotify.size(); i++) {
				params.put("filename" + i, filenamesToNotify.get(i));
			}

			try {
				HashMap<String, String> postParams = new HashMap<>();
				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

				JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.delete_file_path), postParams);
				StingleResponse response = new StingleResponse(this.context, json, false);

				if (response.isStatusOk()) {
					return true;
				}
				return false;
			}
			catch (CryptoException e){
				return false;
			}
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish(true);
			}
		}
	}

	public static class EmptyTrashAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;
		protected OnFinish onFinish;

		public EmptyTrashAsyncTask(Context context, OnFinish onFinish){
			this.context = context;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... params) {
			if(!notifyCloudAboutEmptyTrash()){
				return null;
			}

			FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_TRASH);
			String homeDir = FileManager.getHomeDir(context);
			String thumbDir = FileManager.getThumbsDir(context);

			Cursor result = trashDb.getFilesList(FilesTrashDb.GET_MODE_ALL, StingleDb.SORT_ASC);

			while(result.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(result);
				if(dbFile.isLocal){
					File mainFile = new File(homeDir + "/" + dbFile.filename);
					if(mainFile.exists()){
						mainFile.delete();
					}
				}
				File thumbFile = new File(thumbDir + "/" + dbFile.filename);
				if(thumbFile.exists()){
					thumbFile.delete();
				}
				trashDb.deleteFile(dbFile.filename);
			}


			result.close();
			trashDb.close();
			return null;
		}

		protected boolean notifyCloudAboutEmptyTrash(){
			HashMap<String, String> params = new HashMap<>();
			long timestamp = System.currentTimeMillis();
			params.put("time", String.valueOf(timestamp));

			try {
				HashMap<String, String> postParams = new HashMap<>();
				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

				JSONObject json = HttpsClient.postFunc(context.getString(R.string.api_server_url) + context.getString(R.string.empty_trash_path), postParams);
				StingleResponse response = new StingleResponse(this.context, json, false);

				if (response.isStatusOk()) {
					return true;
				}
				return false;
			}
			catch (CryptoException e){
				return false;
			}
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(onFinish != null){
				onFinish.onFinish(true);
			}
		}
	}

	public static boolean downloadFile(Context context, String filename, String outputPath, boolean isThumb){
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", filename);
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

		FilesTrashDb filesDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_FILES);
		filesDb.truncateTable();
		filesDb.close();

		FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_TRASH);
		trashDb.truncateTable();
		trashDb.close();
	}
}
