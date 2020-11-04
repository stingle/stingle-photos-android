package org.stingle.photos.AsyncTasks;

import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

public class FreeUpSpaceAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private final File dir;
	private final File thumbDir;
	private final File cacheDir;
	private WeakReference<AppCompatActivity> activity;
	private final OnAsyncTaskFinish onFinishListener;

	public FreeUpSpaceAsyncTask(AppCompatActivity activity, OnAsyncTaskFinish onFinishListener) {
		this.activity = new WeakReference<>(activity);;
		this.onFinishListener = onFinishListener;

		dir = new File(FileManager.getHomeDir(activity));
		thumbDir = new File(FileManager.getThumbsDir(activity));
		cacheDir = new File(activity.getCacheDir().getPath() + "/" + FileManager.THUMB_CACHE_DIR);
	}

	@Override
	protected void onPreExecute() {
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		Helpers.acquireWakeLock(myActivity);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return null;
		}

		GalleryTrashDb galleryDb = new GalleryTrashDb(myActivity, SyncManager.GALLERY);
		GalleryTrashDb trashDb = new GalleryTrashDb(myActivity, SyncManager.TRASH);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myActivity);


		boolean result = deleteLocalFiles(galleryDb) && deleteLocalFiles(trashDb) && deleteLocalFiles(albumFilesDb);

		galleryDb.close();
		trashDb.close();
		albumFilesDb.close();

		return result;
	}

	private boolean deleteLocalFiles(FilesDb db){
		try {

			Cursor result = db.getFilesList(FilesDb.GET_MODE_LOCAL_AND_REMOTE, StingleDb.SORT_ASC, null, null);
			while (result.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(result);
				File file = new File(dir.getPath() + "/" + dbFile.filename);

				if(moveFile(thumbDir.getPath(), dbFile.filename, cacheDir.getPath())){
					if(file.exists()){
						if(!file.delete()){
							Log.d("free_up", dbFile.filename + " - FAIL");
							return false;
						}
					}
					Log.d("free_up", dbFile.filename + " - SUCCESS");
					dbFile.isLocal = false;
					db.updateFile(dbFile);
				}
				else{
					Log.d("free_up", dbFile.filename + " - FAIL");
					return false;
				}
			}
			result.close();
		}
		catch (Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private boolean moveFile(String inputPath, String inputFile, String outputPath) {

		InputStream in = null;
		OutputStream out = null;
		try {

			//create output directory if it doesn't exist
			File dir = new File (outputPath);
			if (!dir.exists()){
				dir.mkdirs();
			}

			File inFile = new File(inputPath + "/" + inputFile);

			if(!inFile.exists()){
				return true;
			}

			in = new FileInputStream(inFile);
			out = new FileOutputStream(outputPath + "/"  + inputFile);

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			in.close();
			in = null;

			// write the output file
			out.flush();
			out.close();
			out = null;

			// delete the original file
			if((new File(inputPath + "/" + inputFile)).delete()){
				return true;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

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
