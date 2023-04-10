package org.stingle.photos.AsyncTasks.Sync;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.stingle.photos.AsyncTasks.MultithreadDownloaderAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AutoCloseableCursor;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;

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
		try {
			Log.d("downloadThumbs", "Download thumbs FULL START");
			GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
			AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
			GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);


			try (
					AutoCloseableCursor resultAutoCloseableCursor = galleryDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
					AutoCloseableCursor resultAlbumsAutoCloseableCursor = albumFilesDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
					AutoCloseableCursor resultTrashAutoCloseableCursor = trashDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null)
			) {
				Cursor result = resultAutoCloseableCursor.getCursor();
				Cursor resultAlbums = resultAlbumsAutoCloseableCursor.getCursor();
				Cursor resultTrash = resultTrashAutoCloseableCursor.getCursor();
				downloader = new MultithreadDownloaderAsyncTask(myContext, new OnAsyncTaskFinish() {
					@Override
					public void onFinish(Boolean result) {
						if (result) {
							Helpers.storePreference(myContext, PREF_IS_DWN_THUMBS_IS_DONE, true);
						}
						if (onFinish != null) {
							onFinish.onFinish(true);
						}
					}
				});

				try {
					int gallerySize = result.getCount() + resultAlbums.getCount() + resultTrash.getCount();
					downloader.setBatchSize(gallerySize);
				} catch (IllegalStateException ignored) {
				}

				downloader.setIsDownloadingThumbs(true);
				downloader.setMessageStringId(R.string.downloading_thumb);
				// Post the AsyncTask execution to the main thread
				new Handler(Looper.getMainLooper()).post(() -> downloader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));

				while (result.moveToNext()) {
					StingleDbFile dbFile = new StingleDbFile(result);
					CountDownLatch latch = downloader.addDownloadJob(dbFile, SyncManager.GALLERY);
					if (latch != null) {
						try {
							Log.d("downloadThumbs", "Waiting for latch");
							latch.await();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				result.close();

				while (resultAlbums.moveToNext()) {
					StingleDbFile dbFile = new StingleDbFile(resultAlbums);
					CountDownLatch latch = downloader.addDownloadJob(dbFile, SyncManager.ALBUM);
					if (latch != null) {
						try {
							latch.await();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

				}
				resultAlbums.close();

				while (resultTrash.moveToNext()) {
					StingleDbFile dbFile = new StingleDbFile(resultTrash);
					CountDownLatch latch = downloader.addDownloadJob(dbFile, SyncManager.TRASH);
					if (latch != null) {
						try {
							latch.await();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				resultTrash.close();

				Log.d("downloadThumbs", "Download thumbs Set Finished input end");
				downloader.setInputFinished();

				galleryDb.close();
				albumFilesDb.close();
				trashDb.close();
				Log.d("downloadThumbs", "Download thumbs FULL END");

			} catch (Exception e) {
				if (downloader != null) {
					downloader.setInputFinished();
				}
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
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
	}

}
