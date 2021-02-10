package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class MigrateFilesAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private ProgressDialog spinner;
	private WeakReference<AppCompatActivity> activity;
	private final OnAsyncTaskFinish onFinishListener;

	public MigrateFilesAsyncTask(AppCompatActivity activity, OnAsyncTaskFinish onFinishListener) {
		this.activity = new WeakReference<>(activity);;
		this.onFinishListener = onFinishListener;
	}

	@Override
	protected void onPreExecute() {
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		Helpers.acquireWakeLock(myActivity);
		spinner = Helpers.showProgressDialogWithBar(myActivity, myActivity.getString(R.string.upgrading), myActivity.getString(R.string.upgrading_desc), 100, null);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return null;
		}
		File newDir = myActivity.getExternalFilesDir(null);
		File oldDir = new File(FileManager.getOldHomeDir(myActivity));

		spinner.setMax(countTotalItems(oldDir));

		try {
			moveFolder(oldDir, newDir);
			if(!FileManager.deleteRecursive(oldDir)){
				return false;
			}
		}
		catch (IOException e){
			return false;
		}

		return true;
	}

	private int countTotalItems(File dir){
		int count = 0;
		File[] dirFiles = dir.listFiles();
		if(dirFiles != null) {
			for (File file : dirFiles){
				if(file.isDirectory()){
					count += countTotalItems(file);
				}
				count++;
			}
		}
		return count;
	}

	private void moveFolder(File from, File to) throws IOException{
		if(from.exists() && from.isDirectory()){
			if(!to.exists()){
				to.mkdirs();
			}

			File[] files = from.listFiles();
			if(files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
						moveFolder(file, new File(to.getAbsolutePath() + "/" + file.getName()));
					} else {
						if (!FileManager.moveFile(file.getParent(), file.getName(), to.getPath())) {
							throw new IOException("Failed to move file " + file.getPath());
						}
						spinner.setProgress(spinner.getProgress() + 1);
						Log.d("MovedFile", file.getPath() + " - " + to.getPath());
					}
				}
			}
		}
	}



	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		spinner.dismiss();
		if(onFinishListener != null) {
			if (result) {
				onFinishListener.onFinish();
			} else {
				onFinishListener.onFail();
			}
		}

		AppCompatActivity myActivity = activity.get();
		if(myActivity != null){
			Helpers.releaseWakeLock(myActivity);
		}
	}
}
