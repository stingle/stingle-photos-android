package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.stingle.photos.AsyncTasks.MultithreadDownloaderAsyncTask;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.lang.ref.WeakReference;

public class DownloadThumbsAsyncTask extends AsyncTask<Void, Void, Void> {

	public static final String PREF_IS_DWN_THUMBS_IS_DONE = "thumbs_dwn_is_done";
	private final WeakReference<Context> context;
	protected SyncManager.OnFinish onFinish;
	private MultithreadDownloaderAsyncTask downloader;

	public DownloadThumbsAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
	}

	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}
		Log.d("downloadThumbs", "Download thumbs FULL START");
		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);


		Cursor result = galleryDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
		Cursor resultAlbums = albumFilesDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
		Cursor resultTrash = trashDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);

		downloader = new MultithreadDownloaderAsyncTask(myContext, new SyncManager.OnFinish() {
			@Override
			public void onFinish(Boolean needToUpdateUI) {
				Helpers.storePreference(myContext, PREF_IS_DWN_THUMBS_IS_DONE, true);
			}
		});

		try {
			int gallerySize = result.getCount() + resultAlbums.getCount() + resultTrash.getCount();
			downloader.setBatchSize(gallerySize);
		}
		catch (IllegalStateException ignored){}

		downloader.setIsDownloadingThumbs(true);
		downloader.setMessageStringId(R.string.downloading_thumb);
		downloader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		while (result.moveToNext()) {
			StingleDbFile dbFile = new StingleDbFile(result);
			downloader.addDownloadJob(dbFile, SyncManager.GALLERY);
		}
		result.close();

		while (resultAlbums.moveToNext()) {
			StingleDbFile dbFile = new StingleDbFile(resultAlbums);
			downloader.addDownloadJob(dbFile, SyncManager.ALBUM);
		}
		resultAlbums.close();

		while (resultTrash.moveToNext()) {
			StingleDbFile dbFile = new StingleDbFile(resultTrash);
			downloader.addDownloadJob(dbFile, SyncManager.TRASH);
		}
		resultTrash.close();

		downloader.setInputFinished();

		galleryDb.close();
		albumFilesDb.close();
		trashDb.close();
		Log.d("downloadThumbs", "Download thumbs FULL END");

		return null;
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
		if(downloader != null){
			downloader.cancel(true);
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
