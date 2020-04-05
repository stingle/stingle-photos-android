package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDbContract;
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
	private final FilesTrashDb mainDb;
	private final FilesTrashDb trashDb;
	private final AlbumsDb albumsDb;
	private final AlbumFilesDb albumFilesDb;
	private long lastSeenTime = 0;

	public SyncCloudToLocalDbAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
		mainDb = new FilesTrashDb(context, StingleDbContract.Columns.TABLE_NAME_FILES);
		trashDb = new FilesTrashDb(context, StingleDbContract.Columns.TABLE_NAME_TRASH);
		albumsDb = new AlbumsDb(context);
		albumFilesDb = new AlbumFilesDb(context);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}
		lastSeenTime = Helpers.getPreference(myContext, SyncManager.PREF_LAST_SEEN_TIME, (long)0);

		boolean needToUpdateUI = getFileList(myContext);

		Helpers.storePreference(myContext, SyncManager.PREF_LAST_SEEN_TIME, lastSeenTime);

		return needToUpdateUI;
	}

	protected boolean getFileList(Context context){
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
			if(processDeleteEvents(context, response.get("deletes"))){
				needToUpdateUI = true;
			}
			if(processFilesInFolder(context, response.get("files"), SyncManager.FOLDER_MAIN)){
				needToUpdateUI = true;
			}
			if(processFilesInFolder(context, response.get("trash"), SyncManager.FOLDER_TRASH)){
				needToUpdateUI = true;
			}
			if(processAlbums(context, response.get("albums"))){
				needToUpdateUI = true;
			}
			if(processFilesInFolder(context, response.get("albumFiles"), SyncManager.FOLDER_ALBUM)){
				needToUpdateUI = true;
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

	private boolean processFilesInFolder(Context context, String filesStr, int folder){
		boolean result = false;
		if(filesStr != null && filesStr.length() > 0){
			try {
				JSONArray files = new JSONArray(filesStr);
				for(int i=0; i<files.length(); i++){
					JSONObject file = files.optJSONObject(i);
					if(file != null){
						StingleDbFile dbFile = new StingleDbFile(file);
						Log.d("receivedFile", dbFile.filename);
						processFile(context, dbFile, folder);
						result = true;
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	private boolean processAlbums(Context context, String albumsStr){
		boolean result = false;
		if(albumsStr != null && albumsStr.length() > 0){
			try {
				JSONArray albums = new JSONArray(albumsStr);
				for(int i=0; i<albums.length(); i++){
					JSONObject album = albums.optJSONObject(i);
					if(album != null){
						StingleDbAlbum dbAlbum = new StingleDbAlbum(album);
						Log.d("receivedAlbum", dbAlbum.albumId);
						processAlbum(context, dbAlbum);
						result = true;
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	private boolean processDeleteEvents(Context context, String delsStr){
		boolean result = false;
		if(delsStr != null && delsStr.length() > 0){
			try {
				JSONArray deletes = new JSONArray(delsStr);
				for(int i=0; i<deletes.length(); i++){
					JSONObject deleteEvent = deletes.optJSONObject(i);
					if(deleteEvent != null){
						processDeleteEvent(context, deleteEvent);
						result = true;
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	private boolean processFile(Context context, StingleDbFile remoteFile, int folder){
		FilesDb myDb;
		if (folder == SyncManager.FOLDER_MAIN){
			myDb = mainDb;
		}
		else if (folder == SyncManager.FOLDER_TRASH){
			myDb = trashDb;
		}
		else if (folder == SyncManager.FOLDER_ALBUM){
			myDb = albumFilesDb;
		}
		else{
			return false;
		}

		StingleDbFile file = myDb.getFileIfExists(remoteFile.filename, remoteFile.albumId);

		if(file == null){
			remoteFile.isLocal = false;
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

	private boolean processAlbum(Context context, StingleDbAlbum remoteAlbum){

		StingleDbAlbum album = albumsDb.getAlbumById(remoteAlbum.albumId);

		if(album == null){
			albumsDb.insertAlbum(remoteAlbum);
		}
		else {
			if (album.dateModified != remoteAlbum.dateModified) {
				album.dateModified = remoteAlbum.dateModified;
				albumsDb.updateAlbum(album);
			}
		}

		if(remoteAlbum.dateModified > lastSeenTime) {
			lastSeenTime = remoteAlbum.dateModified;
		}

		return true;
	}

	protected void processDeleteEvent(Context context, JSONObject event){

		try {
			String filename = event.getString("file");
			String albumId = event.getString("albumId");
			Integer type = event.getInt("type");
			Long date = event.getLong("date");

			if(type == SyncManager.DELETE_EVENT_MAIN) {
				StingleDbFile file = mainDb.getFileIfExists(filename);
				if(file != null) {
					mainDb.deleteFile(file.filename);
				}
			}
			else if(type == SyncManager.DELETE_EVENT_TRASH) {
				StingleDbFile file = trashDb.getFileIfExists(filename);
				if(file != null) {
					trashDb.deleteFile(file.filename);
				}
			}
			else if(type == SyncManager.DELETE_EVENT_ALBUM) {
				StingleDbAlbum album = albumsDb.getAlbumById(albumId);
				if(album != null) {
					albumsDb.deleteAlbum(albumId);
				}
			}
			else if(type == SyncManager.DELETE_EVENT_ALBUM_FILE) {
				StingleDbFile file = albumFilesDb.getFileIfExists(filename);
				if(file != null) {
					albumFilesDb.deleteAlbumFile(file.filename, albumId);
				}
			}
			else if(type == SyncManager.DELETE_EVENT_DELETE) {
				StingleDbFile file = trashDb.getFileIfExists(filename);
				if(file != null) {
					trashDb.deleteFile(file.filename);
				}

				boolean needToDeleteFiles = true;

				if (mainDb.getFileIfExists(filename) != null || albumFilesDb.getFileIfExists(filename) != null){
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

		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onPostExecute(Boolean needToUpdateUI) {
		super.onPostExecute(needToUpdateUI);
		mainDb.close();
		trashDb.close();
		albumsDb.close();
		albumFilesDb.close();

		if(onFinish != null){
			onFinish.onFinish(needToUpdateUI);
		}
	}
}
