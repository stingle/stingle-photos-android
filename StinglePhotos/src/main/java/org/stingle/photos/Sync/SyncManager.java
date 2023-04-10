package org.stingle.photos.Sync;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.DownloadThumbsAsyncTask;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.AutoCloseableCursor;
import org.stingle.photos.Db.Query.ContactsDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.Query.ImportedIdsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class SyncManager {


	protected Context context;
	protected SQLiteDatabase db;

	static final public int STATUS_IDLE = 0;
	static final public int STATUS_REFRESHING = 1;
	static final public int STATUS_UPLOADING = 2;
	static final public int STATUS_NO_SPACE_LEFT = 3;
	static final public int STATUS_DISABLED = 4;
	static final public int STATUS_NOT_WIFI = 5;
	static final public int STATUS_BATTERY_LOW = 6;
	static final public int STATUS_IMPORTING = 7;

	public static final String PREF_LAST_SEEN_TIME = "file_last_seen_time";
	public static final String PREF_TRASH_LAST_SEEN_TIME = "trash_last_seen_time";
	public static final String PREF_ALBUMS_LAST_SEEN_TIME = "albums_last_seen_time";
	public static final String PREF_ALBUM_FILES_LAST_SEEN_TIME = "album_files_last_seen_time";
	public static final String PREF_LAST_DEL_SEEN_TIME = "file_last_del_seen_time";
	public static final String PREF_LAST_CONTACTS_SEEN_TIME = "contacts_last_seen_time";

	public static final String PREF_LAST_SPACE_USED = "last_space_used";
	public static final String PREF_LAST_SPACE_QUOTA = "last_space_quota";
	public static final String PREF_SUSPEND_UPLOAD = "suspend_upload";
	public static final String PREF_LAST_AVAILABLE_SPACE = "last_available_space";
	public static final String PREF_FIRST_SYNC_DONE = "first_sync_done";

	public static final String PREF_BACKUP_ENABLED = "enable_backup";
	public static final String PREF_BACKUP_ONLY_WIFI = "upload_only_on_wifi";
	public static final String PREF_BACKUP_BATTERY_LEVEL = "upload_battery_level";

	public static final String PREF_IMPORT_ENABLED = "enable_import";
	public static final String PREF_IMPORT_DELETE = "auto_import_delete";
	public static final String PREF_IMPORT_DELETE_APP = "delete_after_import";
	public static final String PREF_IMPORT_FROM = "import_from";
	public static final String PREF_IMPORT_SETUP = "import_setup";
	public static final String PREF_IS_BACKUP_PHRASE_SEEN = "is_backup_phrase_seen";

	public static final String LAST_IMPORTED_FILE_DATE = "last_imported_date";

	public static final String SP_FILE_MIME_TYPE = "application/stinglephoto";

	public static final int GALLERY = 0;
	public static final int TRASH = 1;
	public static final int ALBUM = 2;

	public static final int DELETE_EVENT_MAIN = 1;
	public static final int DELETE_EVENT_TRASH = 2;
	public static final int DELETE_EVENT_DELETE = 3;
	public static final int DELETE_EVENT_ALBUM = 4;
	public static final int DELETE_EVENT_ALBUM_FILE = 5;
	public static final int DELETE_EVENT_CONTACT = 6;

	public SyncManager(Context context) {
		this.context = context;
	}


	public static void setSyncStatus(Context context, int status){
		setSyncStatus(context, status, null);
	}
	public static void setSyncStatus(Context context, int status, Bundle params){
		StinglePhotosApplication.syncStatus = status;
		StinglePhotosApplication.syncStatusParams = params;
		Intent intent = new Intent("SYNC_STATUS");
		if(params != null) {
			intent.putExtras(params);
		}
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	}

	public static void startSync(Context context){
		startSync(context, SyncAsyncTask.MODE_FULL, null);
	}

	public static void startSync(Context context, int mode){
		startSync(context, mode, null);
	}

	public static void startSync(Context context, int mode, OnAsyncTaskFinish onFinishListener){
		Log.d("SYNC_STATUS", StinglePhotosApplication.syncStatus + "");
		if(StinglePhotosApplication.syncStatus != SyncManager.STATUS_IDLE || (SyncAsyncTask.instance != null && !SyncAsyncTask.instance.isCancelled())){
			Log.d("SYNC_ACTIONS", "Schedule restart");
			StinglePhotosApplication.syncRestartAfterFinish = true;
			StinglePhotosApplication.syncRestartAfterFinishMode = mode;
			if(SyncAsyncTask.instance == null || SyncAsyncTask.instance.isCancelled()){
				Log.d("SYNC_ACTIONS", "Actually started sync async task");
				(new SyncAsyncTask(context, mode)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			return;
		}
		Log.d("SYNC_ACTIONS", "Normal start of sync");
		(new SyncAsyncTask(context, mode, onFinishListener)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public static void stopSync(Context context){
		if(SyncAsyncTask.instance != null){
			SyncAsyncTask.instance.cancel(true);
			SyncManager.setSyncStatus(context, SyncManager.STATUS_IDLE);
		}
		SyncAsyncTask.killDownloadThumbs(context);
	}

	public static boolean downloadFile(Context context, String filename, String outputPath, boolean isThumb, int set, HttpsClient.OnUpdateProgress onProgress) throws NoSuchAlgorithmException, IOException, KeyManagementException {
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", filename);
		postParams.put("set", String.valueOf(set));
		if (isThumb) {
			postParams.put("thumb", "1");
		}

		HttpsClient.downloadFile(StinglePhotosApplication.getApiUrl() + context.getString(R.string.download_file_path), postParams, outputPath, onProgress);
		File dwnFile = new File(outputPath);
		if (!dwnFile.exists() || dwnFile.length() == 0) {
			return false;
		}

		FileInputStream is = new FileInputStream(dwnFile);
		byte[] fileBeginning = new byte[Crypto.FILE_BEGGINIG_LEN];
		is.read(fileBeginning);
		if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
			return false;
		}
		return true;
	}

	public static byte[] downloadFile(Context context, String filename, boolean isThumb, int set) throws NoSuchAlgorithmException, IOException, KeyManagementException {
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", filename);
		postParams.put("set", String.valueOf(set));
		if (isThumb) {
			postParams.put("thumb", "1");
		}

		byte[] encFile = HttpsClient.getFileAsByteArray(StinglePhotosApplication.getApiUrl() + context.getString(R.string.download_file_path), postParams);
		if (encFile == null || encFile.length == 0) {
			return null;
		}

		byte[] fileBeginning = Arrays.copyOfRange(encFile, 0, Crypto.FILE_BEGGINIG_LEN);
		if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
			return null;
		}
		return encFile;
	}

	public static JSONObject getDownloadLinks(Context context, ArrayList<HashMap<String, String>> files, boolean isThumb) throws NoSuchAlgorithmException, IOException, KeyManagementException {
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		int count = 0;
		for (HashMap<String, String> file : files) {
			postParams.put("files[" + count + "][filename]", file.get("filename"));
			postParams.put("files[" + count + "][set]", file.get("set"));
			count++;
		}
		postParams.put("is_thumb", isThumb ? "1" : "0");

		JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.get_download_urls), postParams);
		StingleResponse response = new StingleResponse(context, json, false);

		JSONObject urls = response.getObject("urls");

		if(urls != null && urls.length() > 0){
			return urls;
		}
		return null;
	}

	public static abstract class OnFinish {
		public abstract void onFinish(Boolean needToUpdateUI);
	}

	public static void resetAndStopSync(Context context) {
		Helpers.deletePreference(context, SyncManager.PREF_LAST_SEEN_TIME);
		Helpers.deletePreference(context, SyncManager.PREF_LAST_DEL_SEEN_TIME);
		Helpers.deletePreference(context, SyncManager.PREF_TRASH_LAST_SEEN_TIME);
		Helpers.deletePreference(context, SyncManager.PREF_ALBUMS_LAST_SEEN_TIME);
		Helpers.deletePreference(context, SyncManager.PREF_ALBUM_FILES_LAST_SEEN_TIME);
		Helpers.deletePreference(context, SyncManager.PREF_LAST_CONTACTS_SEEN_TIME);
		Helpers.deletePreference(context, SyncManager.PREF_FIRST_SYNC_DONE);
		Helpers.deletePreference(context, SyncManager.PREF_IS_BACKUP_PHRASE_SEEN);
		Helpers.deletePreference(context, StinglePhotosApplication.PREF_APP_START_COUNT);
		Helpers.deletePreference(context, DownloadThumbsAsyncTask.PREF_IS_DWN_THUMBS_IS_DONE);

		GalleryTrashDb galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);
		galleryDb.truncateTable();
		galleryDb.close();

		GalleryTrashDb trashDb = new GalleryTrashDb(context, SyncManager.TRASH);
		trashDb.truncateTable();
		trashDb.close();

		AlbumFilesDb albumFilesDb = new AlbumFilesDb(context);
		albumFilesDb.truncateTable();
		albumFilesDb.close();

		AlbumsDb albumsDb = new AlbumsDb(context);
		albumsDb.truncateTable();
		albumsDb.close();

		ContactsDb contactsDb = new ContactsDb(context);
		contactsDb.truncateTable();
		contactsDb.close();

		// Auto import reset
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.remove(SyncManager.PREF_IMPORT_ENABLED)
				.remove(SyncManager.PREF_IMPORT_DELETE)
				.remove(SyncManager.PREF_IMPORT_FROM)
				.apply();

		ImportedIdsDb importedIdsDb = new ImportedIdsDb(context);
		importedIdsDb.truncateTable();
		importedIdsDb.close();
		Helpers.deletePreference(context, SyncManager.PREF_IMPORT_SETUP);
		SyncAsyncTask.killDownloadThumbs(context);
	}

	public static boolean moveFiles(Context context, ArrayList<StingleDbFile> files, int fromSet, int toSet, String fromAlbumId, String toAlbumId, boolean isMoving) {
		if(files == null || files.size() == 0){
			return true;
		}
		try {
			Crypto crypto = StinglePhotosApplication.getCrypto();
			AlbumsDb albumsDb = new AlbumsDb(context);
			AlbumFilesDb albumFilesDb = new AlbumFilesDb(context);

			if (fromSet == SyncManager.GALLERY && toSet == SyncManager.ALBUM) {

				if (toAlbumId == null) {
					return false;
				}

				GalleryTrashDb galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);

				StingleDbAlbum album = albumsDb.getAlbumById(toAlbumId);

				if(album == null){
					return false;
				}

				HashMap<String, String> newHeaders = new HashMap<>();
				for (StingleDbFile file : files) {
					newHeaders.put(file.filename, crypto.reencryptFileHeaders(file.headers, Crypto.base64ToByteArray(album.publicKey), null, null));
				}

				if (!SyncManager.notifyCloudAboutFileMove(context, files, fromSet, toSet, fromAlbumId, toAlbumId, isMoving, newHeaders)) {
					return false;
				}

				for (StingleDbFile file : files) {
					albumFilesDb.insertAlbumFile(album.albumId, file.filename, file.isLocal, file.isRemote, file.version, newHeaders.get(file.filename), file.dateCreated, System.currentTimeMillis());
					if (isMoving) {
						galleryDb.deleteFile(file.filename);
					}
				}
				album.dateModified = System.currentTimeMillis();
				albumsDb.updateAlbum(album);

				galleryDb.close();
			} else if (fromSet == SyncManager.ALBUM && (toSet == SyncManager.GALLERY || toSet == SyncManager.TRASH)) {

				if (fromAlbumId == null) {
					return false;
				}

				GalleryTrashDb galleryTrashDb = new GalleryTrashDb(context, toSet);

				StingleDbAlbum album = albumsDb.getAlbumById(fromAlbumId);
				Crypto.AlbumData albumData = crypto.parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);

				HashMap<String, String> newHeaders = new HashMap<>();
				for (StingleDbFile file : files) {
					newHeaders.put(file.filename, crypto.reencryptFileHeaders(file.headers, crypto.getPublicKey(), albumData.privateKey, albumData.publicKey));
				}

				if (!SyncManager.notifyCloudAboutFileMove(context, files, fromSet, toSet, fromAlbumId, toAlbumId, isMoving, newHeaders)) {
					return false;
				}

				for (StingleDbFile file : files) {
					galleryTrashDb.insertFile(file.filename, file.isLocal, file.isRemote, file.version, file.dateCreated, System.currentTimeMillis(), newHeaders.get(file.filename));
					if (isMoving) {
						albumFilesDb.deleteAlbumFile(file.id);
					}
				}

				galleryTrashDb.close();
			} else if (fromSet == SyncManager.ALBUM && toSet == SyncManager.ALBUM) {

				if (fromAlbumId == null || toAlbumId == null) {
					return false;
				}

				StingleDbAlbum fromDbAlbum = albumsDb.getAlbumById(fromAlbumId);
				Crypto.AlbumData fromAlbumData = crypto.parseAlbumData(fromDbAlbum.publicKey, fromDbAlbum.encPrivateKey, fromDbAlbum.metadata);
				StingleDbAlbum toDbAlbum = albumsDb.getAlbumById(toAlbumId);

				HashMap<String, String> newHeaders = new HashMap<>();
				for (StingleDbFile file : files) {
					newHeaders.put(file.filename, crypto.reencryptFileHeaders(file.headers, Crypto.base64ToByteArray(toDbAlbum.publicKey), fromAlbumData.privateKey, fromAlbumData.publicKey));
				}

				if (!SyncManager.notifyCloudAboutFileMove(context, files, fromSet, toSet, fromAlbumId, toAlbumId, isMoving, newHeaders)) {
					return false;
				}

				for (StingleDbFile file : files) {
					albumFilesDb.insertAlbumFile(toDbAlbum.albumId, file.filename, file.isLocal, file.isRemote, file.version, newHeaders.get(file.filename), file.dateCreated, System.currentTimeMillis());
					if (isMoving) {
						albumFilesDb.deleteAlbumFile(file.id);
					}
				}
				toDbAlbum.dateModified = System.currentTimeMillis();
				albumsDb.updateAlbum(toDbAlbum);

			} else if (fromSet == SyncManager.GALLERY && toSet == SyncManager.TRASH) {
				GalleryTrashDb galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);
				GalleryTrashDb trashDb = new GalleryTrashDb(context, SyncManager.TRASH);

				if (!SyncManager.notifyCloudAboutFileMove(context, files, fromSet, toSet, fromAlbumId, toAlbumId, isMoving, null)) {
					return false;
				}

				for (StingleDbFile file : files) {
					file.dateModified = System.currentTimeMillis();
					trashDb.insertFile(file);
					galleryDb.deleteFile(file.filename);
				}

				galleryDb.close();
				trashDb.close();
			} else if (fromSet == SyncManager.TRASH && toSet == SyncManager.GALLERY) {
				GalleryTrashDb galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);
				GalleryTrashDb trashDb = new GalleryTrashDb(context, SyncManager.TRASH);

				if (!SyncManager.notifyCloudAboutFileMove(context, files, fromSet, toSet, fromAlbumId, toAlbumId, isMoving, null)) {
					return false;
				}

				for (StingleDbFile file : files) {
					file.dateModified = System.currentTimeMillis();
					galleryDb.insertFile(file);
					trashDb.deleteFile(file.filename);
				}

				galleryDb.close();
				trashDb.close();
			}

			albumsDb.close();
			albumFilesDb.close();

		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public static boolean notifyCloudAboutAlbumAdd(Context context, StingleDbAlbum album) {
		HashMap<String, String> params = new HashMap<>();

		params.put("albumId", album.albumId);
		params.put("encPrivateKey", album.encPrivateKey);
		params.put("publicKey", album.publicKey);
		params.put("metadata", album.metadata);
		params.put("dateCreated", String.valueOf(album.dateCreated));
		params.put("dateModified", String.valueOf(album.dateModified));

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.add_album), postParams);
			StingleResponse response = new StingleResponse(context, json, false);

			if (response.isStatusOk()) {
				return true;
			}
			return false;
		} catch (CryptoException e) {
			return false;
		}

	}

	public static boolean notifyCloudAboutAlbumDelete(Context context, String albumId) {
		HashMap<String, String> params = new HashMap<>();

		params.put("albumId", albumId);

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.delete_album_path), postParams);
			StingleResponse response = new StingleResponse(context, json, false);

			if (response.isStatusOk()) {
				return true;
			}
			return false;
		} catch (CryptoException e) {
			return false;
		}

	}

	public static boolean notifyCloudAboutFileMove(Context context, ArrayList<StingleDbFile> files, int setFrom, int setTo, String albumIdFrom, String albumIdTo, boolean isMoving, HashMap<String, String> headers) {
		if(files == null){
			return false;
		}

		HashMap<String, String> params = new HashMap<>();

		params.put("setFrom", String.valueOf(setFrom));
		params.put("setTo", String.valueOf(setTo));
		params.put("albumIdFrom", albumIdFrom);
		params.put("albumIdTo", albumIdTo);
		params.put("isMoving", (isMoving ? "1" : "0"));
		params.put("count", String.valueOf(files.size()));

		boolean atLeastOneFileAdded = false;
		for (int i = 0; i < files.size(); i++) {
			if(files.get(i).isRemote) {
				atLeastOneFileAdded = true;
				params.put("filename" + i, files.get(i).filename);
				if (headers != null) {
					params.put("headers" + i, headers.get(files.get(i).filename));
				}
			}
		}

		if(!atLeastOneFileAdded){
			return true;
		}

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.move_file), postParams);
			StingleResponse response = new StingleResponse(context, json, true);

			if (response.isStatusOk()) {
				return true;
			}
			return false;
		} catch (CryptoException e) {
			return false;
		}

	}

	public static boolean notifyCloudAboutDelete(Context context, ArrayList<String> filenamesToNotify){
		HashMap<String, String> params = new HashMap<String, String>();

		params.put("count", String.valueOf(filenamesToNotify.size()));
		for(int i=0; i < filenamesToNotify.size(); i++) {
			params.put("filename" + i, filenamesToNotify.get(i));
		}

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.delete_file_path), postParams);
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

	public static boolean notifyCloudAboutEmptyTrash(Context context){
		HashMap<String, String> params = new HashMap<>();
		long timestamp = System.currentTimeMillis();
		params.put("time", String.valueOf(timestamp));

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.empty_trash_path), postParams);
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

	public static StingleDbAlbum addAlbum(Context context, String albumName) throws IOException {
		Crypto crypto = StinglePhotosApplication.getCrypto();
		Crypto.AlbumEncData encData = crypto.generateEncryptedAlbumData(crypto.getPublicKey(), new Crypto.AlbumMetadata(albumName));

		AlbumsDb db = new AlbumsDb(context);
		long now = System.currentTimeMillis();

		StingleDbAlbum album = new StingleDbAlbum();
		album.encPrivateKey = encData.encPrivateKey;
		album.publicKey = encData.publicKey;
		album.metadata = encData.metadata;
		album.isOwner = true;
		album.dateCreated = now;
		album.dateModified = now;

		if(!SyncManager.notifyCloudAboutAlbumAdd(context, album)){
			return null;
		}

		db.insertAlbum(album);
		StingleDbAlbum newAlbum = db.getAlbumById(album.albumId);
		db.close();

		return newAlbum;
	}

	public static StingleContact getContactData(Context context, String email) {
		HashMap<String, String> params = new HashMap<>();
		params.put("email", email);

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.get_contact), postParams);
			StingleResponse response = new StingleResponse(context, json, false);

			if (response.isStatusOk()) {
				Log.d("contact", response.get("contact"));
				return new StingleContact(new JSONObject(response.get("contact")));
			}
			return null;
		}
		catch (CryptoException | JSONException e){
			return null;
		}

	}

	public static boolean notifyCloudAboutShare(Context context, StingleDbAlbum album, HashMap<String, String> sharingKeys) {
		HashMap<String, String> params = new HashMap<>();

		params.put("album", album.toJSON());
		params.put("sharingKeys", (new JSONObject(sharingKeys)).toString());

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.share_url), postParams);
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

	public static boolean notifyCloudAboutAlbumEditPerms(Context context, StingleDbAlbum album) {
		HashMap<String, String> params = new HashMap<>();

		params.put("album", album.toJSON());

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.album_edit_perms_url), postParams);
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

	public static boolean notifyCloudAboutMemberRemove(Context context, StingleDbAlbum album, Long memberUserId) {
		HashMap<String, String> params = new HashMap<>();

		params.put("album", album.toJSON());
		params.put("memberUserId", String.valueOf(memberUserId));

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.album_remove_member_url), postParams);
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
	public static boolean notifyCloudAboutAlbumUnshare(Context context, StingleDbAlbum album) {
		HashMap<String, String> params = new HashMap<>();

		params.put("albumId", album.albumId);

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.album_unshare_url), postParams);
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
	public static boolean notifyCloudAboutAlbumRename(Context context, StingleDbAlbum album) {
		HashMap<String, String> params = new HashMap<>();

		params.put("albumId", album.albumId);
		params.put("metadata", album.metadata);

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.album_rename_url), postParams);
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

	public static boolean notifyCloudAboutAlbumLeave(Context context, StingleDbAlbum album) {
		HashMap<String, String> params = new HashMap<>();

		params.put("albumId", album.albumId);

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.album_leave_url), postParams);
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

	public static boolean notifyCloudAboutCoverChange(Context context, StingleDbAlbum album) {
		HashMap<String, String> params = new HashMap<>();

		params.put("albumId", album.albumId);
		params.put("cover", album.cover);

		try {
			HashMap<String, String> postParams = new HashMap<>();
			postParams.put("token", KeyManagement.getApiToken(context));
			postParams.put("params", CryptoHelpers.encryptParamsForServer(params));

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.album_cover_change_url), postParams);
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

	public static boolean areFilesAlreadyUploaded(Context context, String albumId){
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(context);

		try(AutoCloseableCursor autoCloseableCursor = albumFilesDb.getFilesList(FilesDb.GET_MODE_ALL, StingleDb.SORT_ASC, null, albumId)) {
			Cursor result = autoCloseableCursor.getCursor();
			while (result.moveToNext()) {
				StingleDbFile file = new StingleDbFile(result);
				if (!file.isRemote) {
					albumFilesDb.close();
					return false;
				}
			}
		}
		return true;
	}

	public static boolean areFilesAlreadyUploaded(ArrayList<StingleDbFile> files){
		for(StingleDbFile file : files){
			if(!file.isRemote){
				return false;
			}
		}
		return true;
	}

	public static boolean isBackupEnabled(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SyncManager.PREF_BACKUP_ENABLED, true);
	}
	public static boolean isImportEnabled(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SyncManager.PREF_IMPORT_ENABLED, false);
	}
	public static boolean isImportDeleteEnabled(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SyncManager.PREF_IMPORT_DELETE, false);
	}
	public static String getImportFrom(Context context){
		return PreferenceManager.getDefaultSharedPreferences(context).getString(SyncManager.PREF_IMPORT_FROM, "camera_folder");
	}

	public static void startPeriodicWork(Context context){
		Constraints constraints = new Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build();


		PeriodicWorkRequest periodicSyncDataWork =
				new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
						.addTag("sync")
						.setConstraints(constraints)
						// setting a backoff on case the work needs to retry
						.setBackoffCriteria(BackoffPolicy.LINEAR, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
						.build();
		WorkManager workManager = WorkManager.getInstance(context);
		workManager.enqueueUniquePeriodicWork(
				"SPSYnc",
				ExistingPeriodicWorkPolicy.KEEP, //Existing Periodic Work policy
				periodicSyncDataWork //work request
		);

	}

}
