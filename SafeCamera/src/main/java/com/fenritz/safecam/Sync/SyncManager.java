package com.fenritz.safecam.Sync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Util.Helpers;

import java.io.File;

public class SyncManager {

	protected Context context;
	protected SQLiteDatabase db;

	public SyncManager(Context context){
		this.context = context;

		StingleDbHelper dbHelper = new StingleDbHelper(context);
		SQLiteDatabase db = dbHelper.getWritableDatabase();

	}

	public static void syncFSToDB(Context context){
		(new FsSyncAsyncTask(context)).execute();
	}

	public static class FsSyncAsyncTask extends AsyncTask<Void, Void, Void> {

		protected Context context;

		public FsSyncAsyncTask(Context context){
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params) {
			StingleDbHelper db = new StingleDbHelper(context);
			File dir = new File(Helpers.getHomeDir(this.context));

			File[] currentFolderFiles = dir.listFiles();

			for (File file : currentFolderFiles) {
				if (file.isFile() && file.getName().endsWith(SafeCameraApplication.FILE_EXTENSION)) {
					db.insertFile(file.getName(), true, false, String.valueOf(file.lastModified()));
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

		}
	}
}
