package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.util.HashMap;

public class EmptyTrashAsyncTask extends AsyncTask<Void, Void, Void> {

	protected Context context;
	protected SyncManager.OnFinish onFinish;

	public EmptyTrashAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = context;
		this.onFinish = onFinish;
	}

	@Override
	protected Void doInBackground(Void... params) {
		if(!notifyCloudAboutEmptyTrash()){
			return null;
		}

		GalleryTrashDb trashDb = new GalleryTrashDb(context, SyncManager.TRASH);
		String homeDir = FileManager.getHomeDir(context);
		String thumbDir = FileManager.getThumbsDir(context);

		Cursor result = trashDb.getFilesList(GalleryTrashDb.GET_MODE_ALL, StingleDb.SORT_ASC, null, null);

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

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.empty_trash_path), postParams);
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
