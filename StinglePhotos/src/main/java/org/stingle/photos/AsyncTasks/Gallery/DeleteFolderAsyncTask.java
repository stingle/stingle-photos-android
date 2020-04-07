package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.FolderFilesDb;
import org.stingle.photos.Db.Query.FoldersDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DeleteFolderAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	private String folderId = null;
	private FoldersDb foldersDb;


	public DeleteFolderAsyncTask(Context context, String folderId, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.folderId = folderId;;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(folderId == null){
			return false;
		}

		foldersDb = new FoldersDb(myContext);

		FolderFilesDb folderFilesDb = new FolderFilesDb(myContext);
		ArrayList<StingleDbFile> files = new ArrayList<>();

		Cursor result = folderFilesDb.getFilesList(FilesDb.GET_MODE_ALL, StingleDb.SORT_ASC, null, folderId);
		while(result.moveToNext()) {
			files.add(new StingleDbFile(result));
		}
		folderFilesDb.close();

		boolean moveResult = SyncManager.moveFiles(myContext, files, SyncManager.FOLDER, SyncManager.TRASH, folderId, null, true);

		if(moveResult){
			if(SyncManager.notifyCloudAboutFolderDelete(context.get(), folderId)){
				foldersDb.deleteFolder(folderId);
				foldersDb.close();
				return true;
			}
		}

		foldersDb.close();
		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result){
			onFinishListener.onFinish();
		}
		else{
			onFinishListener.onFail();
		}

	}
}
