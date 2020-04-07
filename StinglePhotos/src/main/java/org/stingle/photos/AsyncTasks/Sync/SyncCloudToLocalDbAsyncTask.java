package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Objects.StingleDbFolder;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.Query.FolderFilesDb;
import org.stingle.photos.Db.Query.FoldersDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public class SyncCloudToLocalDbAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private SyncManager.OnFinish onFinish = null;
	private final GalleryTrashDb galleryDb;
	private final GalleryTrashDb trashDb;
	private final FoldersDb folderDb;
	private final FolderFilesDb folderFilesDb;
	private long lastSeenTime = 0;

	public SyncCloudToLocalDbAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
		galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);
		trashDb = new GalleryTrashDb(context, SyncManager.TRASH);
		folderDb = new FoldersDb(context);
		folderFilesDb = new FolderFilesDb(context);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}
		lastSeenTime = Helpers.getPreference(myContext, SyncManager.PREF_LAST_SEEN_TIME, (long)0);
		boolean needToUpdateUI = false;

		try{
			needToUpdateUI = getFileList(myContext);
			Helpers.storePreference(myContext, SyncManager.PREF_LAST_SEEN_TIME, lastSeenTime);
		}
		catch (JSONException e){
			e.printStackTrace();
		}

		return needToUpdateUI;
	}

	protected boolean getFileList(Context context) throws JSONException {
		boolean needToUpdateUI = false;
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("lastSeenTime", String.valueOf(lastSeenTime));

		JSONObject resp = HttpsClient.postFunc(
				StinglePhotosApplication.getApiUrl() + context.getString(R.string.get_updates_path),
				postParams
		);
		StingleResponse response = new StingleResponse(context, resp, false);
		if(response.isStatusOk()){
			if (processDeleteEvents(context, response.get("deletes"))) {
				needToUpdateUI = true;
			}
			if (processFilesInFolder(context, response.get("files"), SyncManager.GALLERY)) {
				needToUpdateUI = true;
			}
			if (processFilesInFolder(context, response.get("trash"), SyncManager.TRASH)) {
				needToUpdateUI = true;
			}
			if (processFolders(context, response.get("folders"))) {
				needToUpdateUI = true;
			}
			if (processFilesInFolder(context, response.get("folderFiles"), SyncManager.FOLDER)) {
				needToUpdateUI = true;
			}


			String spaceUsedStr = response.get("spaceUsed");
			String spaceQuotaStr = response.get("spaceQuota");


			if (spaceUsedStr != null && spaceUsedStr.length() > 0) {
				int spaceUsed = Integer.parseInt(spaceUsedStr);
				int oldSpaceUsed = Helpers.getPreference(context, SyncManager.PREF_LAST_SPACE_USED, 0);
				if (spaceUsed != oldSpaceUsed) {
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_USED, spaceUsed);
					needToUpdateUI = true;
				}
			}

			if (spaceQuotaStr != null && spaceQuotaStr.length() > 0) {
				int spaceQuota = Integer.parseInt(spaceQuotaStr);
				int oldSpaceQuota = Helpers.getPreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, 0);
				if (spaceQuota != oldSpaceQuota) {
					Helpers.storePreference(context, SyncManager.PREF_LAST_SPACE_QUOTA, spaceQuota);
					needToUpdateUI = true;
				}
			}

		}

		return needToUpdateUI;
	}

	private boolean processFilesInFolder(Context context, String filesStr, int folder) throws JSONException {
		boolean result = false;
		if(filesStr != null && filesStr.length() > 0){
			JSONArray files = new JSONArray(filesStr);
			for(int i=0; i<files.length(); i++){
				JSONObject file = files.optJSONObject(i);
				if(file != null){
					StingleDbFile dbFile = new StingleDbFile(file);
					Log.d("receivedFile", folder + " - " + dbFile.filename);
					processFile(context, dbFile, folder);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean processFolders(Context context, String folderStr) throws JSONException {
		boolean result = false;
		if(folderStr != null && folderStr.length() > 0){
			JSONArray folders = new JSONArray(folderStr);
			for(int i=0; i<folders.length(); i++){
				JSONObject folder = folders.optJSONObject(i);
				if(folder != null){
					StingleDbFolder dbFolder = new StingleDbFolder(folder);
					Log.d("receivedFolder", dbFolder.folderId);
					processFolder(context, dbFolder);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean processDeleteEvents(Context context, String delsStr) throws JSONException {
		boolean result = false;
		if(delsStr != null && delsStr.length() > 0){
			JSONArray deletes = new JSONArray(delsStr);
			for(int i=0; i<deletes.length(); i++){
				JSONObject deleteEvent = deletes.optJSONObject(i);
				if(deleteEvent != null){
					Log.d("receivedDelete", deleteEvent.getString("file"));
					processDeleteEvent(context, deleteEvent);
					result = true;
				}
			}
		}
		return result;
	}

	private boolean processFile(Context context, StingleDbFile remoteFile, int folder){
		FilesDb myDb;
		if (folder == SyncManager.GALLERY){
			myDb = galleryDb;
		}
		else if (folder == SyncManager.TRASH){
			myDb = trashDb;
		}
		else if (folder == SyncManager.FOLDER){
			myDb = folderFilesDb;
		}
		else{
			return false;
		}

		StingleDbFile file = myDb.getFileIfExists(remoteFile.filename, remoteFile.folderId);

		File dir = new File(FileManager.getHomeDir(context));
		File fsFile = new File(dir.getPath() + "/" + remoteFile.filename);

		remoteFile.isLocal = false;
		if(fsFile.exists()) {
			remoteFile.isLocal = true;
		}

		if(file == null){
			remoteFile.isRemote = true;
			myDb.insertFile(remoteFile);
		}
		else {
			boolean needUpdate = false;
			boolean needDownload = false;
			if (file.dateModified != remoteFile.dateModified) {
				file.dateModified = remoteFile.dateModified;
				needUpdate = true;
			}
			if(!file.isRemote) {
				file.isRemote = true;
				needUpdate = true;
			}
			if(file.isLocal != remoteFile.isLocal) {
				file.isLocal = remoteFile.isLocal;
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

		return true;
	}

	private boolean processFolder(Context context, StingleDbFolder remoteFolder){

		StingleDbFolder folder = folderDb.getFolderById(remoteFolder.folderId);

		if(folder == null){
			folderDb.insertFolder(remoteFolder);
		}
		else {
			if (folder.dateModified != remoteFolder.dateModified) {
				folder.dateModified = remoteFolder.dateModified;
				folderDb.updateFolder(folder);
			}
		}

		if(remoteFolder.dateModified > lastSeenTime) {
			lastSeenTime = remoteFolder.dateModified;
		}

		return true;
	}

	protected void processDeleteEvent(Context context, JSONObject event) throws JSONException {

		String filename = event.getString("file");
		String folderId = event.getString("folderId");
		Integer type = event.getInt("type");
		Long date = event.getLong("date");

		if(type == SyncManager.DELETE_EVENT_MAIN) {
			StingleDbFile file = galleryDb.getFileIfExists(filename);
			if(file != null) {
				galleryDb.deleteFile(file.filename);
			}
		}
		else if(type == SyncManager.DELETE_EVENT_TRASH) {
			StingleDbFile file = trashDb.getFileIfExists(filename);
			if(file != null) {
				trashDb.deleteFile(file.filename);
			}
		}
		else if(type == SyncManager.DELETE_EVENT_FOLDER) {
			StingleDbFolder folder = folderDb.getFolderById(folderId);
			if(folder != null) {
				folderDb.deleteFolder(folderId);
			}
		}
		else if(type == SyncManager.DELETE_EVENT_FOLDER_FILE) {
			StingleDbFile file = folderFilesDb.getFileIfExists(filename);
			if(file != null) {
				folderFilesDb.deleteFolderFile(file.filename, folderId);
			}
		}
		else if(type == SyncManager.DELETE_EVENT_DELETE) {
			StingleDbFile file = trashDb.getFileIfExists(filename);
			if(file != null) {
				trashDb.deleteFile(file.filename);
			}

			boolean needToDeleteFiles = true;

			if (galleryDb.getFileIfExists(filename) != null || folderFilesDb.getFileIfExists(filename) != null){
				needToDeleteFiles = false;
			}

			if(needToDeleteFiles) {
				String homeDir = FileManager.getHomeDir(context);
				String thumbDir = FileManager.getThumbsDir(context);
				File mainFile = new File(homeDir + "/" + filename);
				File thumbFile = new File(thumbDir + "/" + filename);

				if (mainFile.exists()) {
					mainFile.delete();
				}
				if (thumbFile.exists()) {
					thumbFile.delete();
				}
			}
		}

		if(date > lastSeenTime) {
			lastSeenTime = date;
		}
	}

	@Override
	protected void onPostExecute(Boolean needToUpdateUI) {
		super.onPostExecute(needToUpdateUI);
		galleryDb.close();
		trashDb.close();
		folderDb.close();
		folderFilesDb.close();

		if(onFinish != null){
			onFinish.onFinish(needToUpdateUI);
		}
	}
}
