package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Query.FoldersDb;
import org.stingle.photos.Db.Objects.StingleDbFolder;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public class AddFolderAsyncTask extends AsyncTask<Void, Void, StingleDbFolder> {

	private WeakReference<Context> context;
	private String folderName;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	public AddFolderAsyncTask(Context context, String folderName, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.folderName = folderName;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	protected StingleDbFolder doInBackground(Void... params) {
		try {
			Context myContext = context.get();
			if(myContext == null){
				return null;
			}
			HashMap<String, String> folderInfo = crypto.generateEncryptedFolderData(crypto.getPublicKey(), folderName);

			FoldersDb db = new FoldersDb(myContext);
			long now = System.currentTimeMillis();
			String newAbumId = CryptoHelpers.getRandomString(FoldersDb.FOLDER_ID_LEN);

			if(!SyncManager.notifyCloudAboutFolderAdd(myContext, newAbumId, folderInfo.get("data"), folderInfo.get("pk"), now,  now)){
				return null;
			}

			db.insertFolder(newAbumId, folderInfo.get("data"), folderInfo.get("pk"), now, now);
			StingleDbFolder newFolder = db.getFolderById(newAbumId);
			db.close();

			return newFolder;

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void onPostExecute(StingleDbFolder folder) {
		super.onPostExecute(folder);

		if(folder != null){
			onFinishListener.onFinish(folder);
		}
		else{
			onFinishListener.onFail();
		}

	}
}
