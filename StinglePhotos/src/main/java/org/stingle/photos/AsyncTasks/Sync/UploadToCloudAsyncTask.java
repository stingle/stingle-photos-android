package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Db.Query.FolderFilesDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class UploadToCloudAsyncTask extends AsyncTask<Void, Void, Void> {

	protected Context context;
	protected File dir;
	protected File thumbDir;
	protected SyncManager.OnFinish onFinish = null;
	protected UploadProgress progress = null;
	protected int uploadedFilesCount = 0;


	public UploadToCloudAsyncTask(Context context, UploadProgress progress, SyncManager.OnFinish onFinish){
		this.context = context;
		this.progress = progress;
		this.onFinish = onFinish;
		dir = new File(FileManager.getHomeDir(context));
		thumbDir = new File(FileManager.getThumbsDir(context));
	}

	@Override
	protected Void doInBackground(Void... params) {
		if(progress != null){
			progress.setTotalItemsNumber(getFilesCountToUpload(SyncManager.GALLERY) + getFilesCountToUpload(SyncManager.TRASH) + getFilesCountToUpload(SyncManager.FOLDER));
		}

		uploadFolder(SyncManager.GALLERY);
		uploadFolder(SyncManager.TRASH);
		uploadFolder(SyncManager.FOLDER);

		return null;
	}

	protected int getFilesCountToUpload(int folder){
		FilesDb db;
		if(folder == SyncManager.GALLERY || folder == SyncManager.TRASH){
			db = new FilesTrashDb(context, (folder == SyncManager.TRASH ? StingleDbContract.Columns.TABLE_NAME_TRASH : StingleDbContract.Columns.TABLE_NAME_FILES));
		}
		else if (folder == SyncManager.FOLDER){
			db = new FolderFilesDb(context);
		}
		else{
			return 0;
		}

		Cursor result = db.getFilesList(FilesTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_ASC, "", null);
		int uploadCount = result.getCount();
		result.close();

		Cursor reuploadResult = db.getReuploadFilesList();
		int reuploadCount = reuploadResult.getCount();
		reuploadResult.close();

		db.close();

		return uploadCount + reuploadCount;
	}

	protected void uploadFolder(int folder){
		FilesDb db;
		if(folder == SyncManager.GALLERY || folder == SyncManager.TRASH){
			db = new FilesTrashDb(context, (folder == SyncManager.TRASH ? StingleDbContract.Columns.TABLE_NAME_TRASH : StingleDbContract.Columns.TABLE_NAME_FILES));
		}
		else if (folder == SyncManager.FOLDER){
			db = new FolderFilesDb(context);
		}
		else{
			return;
		}

		Cursor result = db.getFilesList(FilesTrashDb.GET_MODE_ONLY_LOCAL, StingleDb.SORT_ASC, null, null);
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

	protected void uploadFile(int folder, FilesDb db, Cursor result, boolean isReupload){
		String filename = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FILENAME));
		String version = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_VERSION));
		String dateCreated = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_CREATED));
		String dateModified = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_DATE_MODIFIED));
		String headers = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_HEADERS));
		String folderId = "";
		try {
			folderId = result.getString(result.getColumnIndexOrThrow(StingleDbContract.Columns.COLUMN_NAME_FOLDER_ID));
		}
		catch (IllegalArgumentException ignored) {}

		if(progress != null){
			progress.currentFile(filename, headers, folder, folderId);
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

		ArrayList<HttpsClient.FileToUpload> filesToUpload = new ArrayList<HttpsClient.FileToUpload>();
		filesToUpload.add(fileToUpload);
		filesToUpload.add(thumbToUpload);

		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("folder", String.valueOf(folder));
		postParams.put("folderId", folderId);
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
		public String headers;
		public int folder;
		public String folderId;
		public int uploadedFilesCount;

		public void setTotalItemsNumber(int number){
			totalItemsNumber = number;
		}

		public void currentFile(String filename, String headers, int folder, String folderId){
			this.currentFile = filename;
			this.headers = headers;
			this.folder = folder;
			this.folderId = folderId;
		}
		public void fileUploadFinished(String filename, int folder){

		}
		public void uploadProgress(int uploadedFilesCount){
			this.uploadedFilesCount = uploadedFilesCount;
		}
	}
}
