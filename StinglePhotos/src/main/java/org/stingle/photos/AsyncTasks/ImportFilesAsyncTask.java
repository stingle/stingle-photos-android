package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ImportFile;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class ImportFilesAsyncTask extends AsyncTask<Void, Integer, Void> {

	private WeakReference<AppCompatActivity> activity;
	private ArrayList<Uri> uris;
	private int set;
	private String albumId;
	private FileManager.OnFinish onFinish;
	private ProgressDialog progress;
	private Long largestDate = 0L;


	public ImportFilesAsyncTask(AppCompatActivity activity, ArrayList<Uri> uris, int set, String albumId, FileManager.OnFinish onFinish){
		this.activity = new WeakReference<>(activity);
		this.uris = uris;
		this.set = set;
		this.albumId = albumId;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		progress = Helpers.showProgressDialogWithBar(myActivity, myActivity.getString(R.string.importing_files), null, uris.size(), new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialogInterface) {
				Helpers.releaseWakeLock(myActivity);
				ImportFilesAsyncTask.this.cancel(false);
				onFinish.onFinish();
			}
		});
		Helpers.acquireWakeLock(myActivity);
	}

	@Override
	protected Void doInBackground(Void... params) {
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return null;
		}
		int index = 0;

		for (Uri uri : uris) {
			Long date = ImportFile.importFile(myActivity, uri, set, albumId, this);
			if(date != null && date > largestDate){
				largestDate = date;
			}

			publishProgress(index+1);
			index++;
		}

		return null;
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
		progress.setProgress(values[0]);

	}

	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);

		if(progress != null && progress.isShowing()) {
			progress.dismiss();
		}

		if(onFinish != null){
			onFinish.onFinish(largestDate);
		}
		AppCompatActivity myActivity = activity.get();
		if(myActivity != null){
			Helpers.releaseWakeLock(myActivity);
		}

		deleteOriginals();
	}

	private void deleteOriginals(){
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(myActivity);
		String pref = settings.getString("delete_after_import", "0");
		if(pref.equals("never")){
			return;
		}
		else if(pref.equals("ask")){
			Helpers.showConfirmDialog(
					myActivity,
					myActivity.getString(R.string.is_delete_original),
					myActivity.getString(R.string.is_delete_original_desc),
					R.drawable.ic_action_delete,
					(dialog, which) -> (new DeleteUrisAsyncTask(myActivity, uris, null)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR),
					null
			);
		}
		else if(pref.equals("always")){
			(new DeleteUrisAsyncTask(myActivity, uris, null)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}

}
