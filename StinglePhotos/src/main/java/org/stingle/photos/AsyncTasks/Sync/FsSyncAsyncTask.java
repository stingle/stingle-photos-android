package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.io.IOException;

public class FsSyncAsyncTask extends AsyncTask<Void, Void, Boolean> {

	protected Context context;
	protected SyncManager.OnFinish onFinish;

	public FsSyncAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = context;
		this.onFinish = onFinish;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		boolean result1 = fsSyncFolder(SyncManager.FOLDER_MAIN);
		boolean result2 = fsSyncFolder(SyncManager.FOLDER_TRASH);

		return result1 || result2;
	}

	protected boolean fsSyncFolder(int folder){
		boolean needToUpdateUI = false;
		FilesTrashDb db = new FilesTrashDb(context, (folder == SyncManager.FOLDER_TRASH ? StingleDbContract.Columns.TABLE_NAME_TRASH : StingleDbContract.Columns.TABLE_NAME_FILES));
		File dir = new File(FileManager.getHomeDir(this.context));

		Cursor result = db.getFilesList(FilesTrashDb.GET_MODE_ALL, StingleDb.SORT_ASC, null, null);

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

		if(folder == SyncManager.FOLDER_MAIN) {
			File[] currentFolderFiles = dir.listFiles();

			FilesTrashDb trashDb = new FilesTrashDb(context, StingleDbContract.Columns.TABLE_NAME_TRASH);

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
