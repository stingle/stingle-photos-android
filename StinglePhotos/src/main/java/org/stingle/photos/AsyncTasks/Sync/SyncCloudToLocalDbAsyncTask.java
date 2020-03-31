package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.HashMap;

public class SyncCloudToLocalDbAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected Context context;
	protected SyncManager.OnFinish onFinish = null;
	protected FilesTrashDb db;
	protected FilesTrashDb trashDb;
	protected long lastSeenTime = 0;
	protected long lastDelSeenTime = 0;

	public SyncCloudToLocalDbAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = context;
		this.onFinish = onFinish;
		db = new FilesTrashDb(context, StingleDbContract.Columns.TABLE_NAME_FILES);
		trashDb = new FilesTrashDb(context, StingleDbContract.Columns.TABLE_NAME_TRASH);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		lastSeenTime = Helpers.getPreference(context, SyncManager.PREF_LAST_SEEN_TIME, (long)0);
		lastDelSeenTime = Helpers.getPreference(context, SyncManager.PREF_LAST_DEL_SEEN_TIME, (long)0);

		boolean needToUpdateUI = getFileList();

		Helpers.storePreference(context, SyncManager.PREF_LAST_SEEN_TIME, lastSeenTime);
		Helpers.storePreference(context, SyncManager.PREF_LAST_DEL_SEEN_TIME, lastDelSeenTime);

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
							processFile(dbFile, SyncManager.FOLDER_MAIN);
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
							processFile(dbFile, SyncManager.FOLDER_TRASH);
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
				int oldSpaceUsed = Helpers.getPreference(context, SyncManager.PREF_LAST_SPACE_USED, 0);
				if(spaceUsed != oldSpaceUsed){
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_USED, spaceUsed);
					needToUpdateUI = true;
				}
			}

			if(spaceQuotaStr != null && spaceQuotaStr.length() > 0){
				int spaceQuota = Integer.parseInt(spaceQuotaStr);
				int oldSpaceQuota = Helpers.getPreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, 0);
				if(spaceQuota != oldSpaceQuota){
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, spaceQuota);
					needToUpdateUI = true;
				}
			}

		}

		return needToUpdateUI;
	}

	protected void processFile(StingleDbFile remoteFile, int folder){
		FilesTrashDb myDb = db;
		if (folder == SyncManager.FOLDER_TRASH){
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

				SyncManager.downloadFile(context, file.filename, mainFilePath, false, folder);
				SyncManager.downloadFile(context, file.filename, thumbPath, true, folder);
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

			if(type == SyncManager.DELETE_EVENT_TRASH) {
				StingleDbFile file = db.getFileIfExists(filename);
				if(file != null) {
					db.deleteFile(file.filename);
					trashDb.insertFile(file);
				}
			}
			else if(type == SyncManager.DELETE_EVENT_RESTORE) {
				StingleDbFile file = trashDb.getFileIfExists(filename);
				if(file != null) {
					trashDb.deleteFile(file.filename);
					db.insertFile(file);
				}
			}
			else if(type == SyncManager.DELETE_EVENT_DELETE) {
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
