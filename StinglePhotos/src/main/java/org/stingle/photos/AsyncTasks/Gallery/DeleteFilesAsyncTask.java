package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class DeleteFilesAsyncTask extends AsyncTask<Void, Void, Void> {

	protected Context context;
	protected ArrayList<StingleDbFile> files;
	protected SyncManager.OnFinish onFinish;

	public DeleteFilesAsyncTask(Context context, ArrayList<StingleDbFile> files, SyncManager.OnFinish onFinish){
		this.context = context;
		this.files = files;
		this.onFinish = onFinish;
	}

	@Override
	protected Void doInBackground(Void... params) {
		GalleryTrashDb trashDb = new GalleryTrashDb(context, SyncManager.TRASH);
		String homeDir = FileManager.getHomeDir(context);
		String thumbDir = FileManager.getThumbsDir(context);

		ArrayList<String> filenamesToNotify = new ArrayList<String>();

		for(StingleDbFile file : files) {
			if (file.isRemote) {
				filenamesToNotify.add(file.filename);
			}
		}

		if (filenamesToNotify.size() > 0 && !notifyCloudAboutDelete(filenamesToNotify)) {
			trashDb.close();
			return null;
		}

		for(StingleDbFile file : files) {
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

			JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.delete_file_path), postParams);
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
