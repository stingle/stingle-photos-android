package org.stingle.photos.AsyncTasks.Sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.AsyncTasks.GenericAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadThumbsAsyncTask extends AsyncTask<Void, Void, Void> {

	static final public int WORKERS_LIMIT = 100;
	private final ExecutorService cachedThreadPool;

	private WeakReference<Context> context;
	protected SyncManager.OnFinish onFinish;
	public static NotificationManager mNotifyManager;
	public static Notification.Builder notificationBuilder;
	public static boolean isNotificationActive = false;
	private File thumbDir;
	private File thumbCacheDir;
	private int count = 1;
	private int gallerySize;
	private HashMap<String, HashMap<String, String>> filesArray = new HashMap<>();
	private ArrayList<GenericAsyncTask> workers = new ArrayList<>();

	public DownloadThumbsAsyncTask(Context context, SyncManager.OnFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		thumbDir = new File(FileManager.getThumbsDir(context));
		thumbCacheDir = new File(context.getCacheDir().getPath() + "/" + FileManager.THUMB_CACHE_DIR);
		cachedThreadPool = Executors.newCachedThreadPool();
	}


	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}
		Log.d("downloadThumbs", "Download thumbs START");
		GalleryTrashDb galleryDb = new GalleryTrashDb(myContext, SyncManager.GALLERY);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);
		GalleryTrashDb trashDb = new GalleryTrashDb(myContext, SyncManager.TRASH);


		Cursor result = galleryDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
		Cursor resultAlbums = albumFilesDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);
		Cursor resultTrash = trashDb.getFilesList(GalleryTrashDb.GET_MODE_ONLY_REMOTE, StingleDb.SORT_DESC, null, null);

		gallerySize = result.getCount() + resultAlbums.getCount() + resultTrash.getCount();

		try {
			while (result.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(result);
				downloadThumb(myContext, dbFile, SyncManager.GALLERY);
			}
			result.close();

			while (resultAlbums.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(resultAlbums);
				downloadThumb(myContext, dbFile, SyncManager.ALBUM);
			}
			resultAlbums.close();

			while (resultTrash.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(resultTrash);
				downloadThumb(myContext, dbFile, SyncManager.TRASH);
			}
			resultTrash.close();

			downloadQueue(myContext);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}

		try {
			while (workers.size() > 0){
				Thread.sleep(500);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		removeNotification();

		galleryDb.close();
		albumFilesDb.close();
		trashDb.close();
		Log.d("downloadThumbs", "Download thumbs END");
		return null;
	}


	private void downloadThumb(Context context, StingleDbFile dbFile, int set) throws InterruptedException {
		File thumb = new File(thumbDir + "/" + dbFile.filename);
		File thumbCache = new File(thumbCacheDir.getPath() + "/" + dbFile.filename);
		if(!thumb.exists() && !thumbCache.exists()) {
			filesArray.put(dbFile.filename, new HashMap<String, String>() {{
				put("filename", dbFile.filename);
				put("set", String.valueOf(set));
				put("albumId", dbFile.albumId);
			}});
			if(filesArray.size() >= WORKERS_LIMIT){
				downloadQueue(context);
				while (filesArray.size() > 0){
					Thread.sleep(500);
				}
			}
		}
		else {
			count++;
		}
	}


	private void downloadQueue(Context context){
		if(filesArray.size() == 0){
			return;
		}
		try {
			JSONObject urls = SyncManager.getDownloadLinks(context, filesArray, true);

			if(urls != null){
				Iterator<String> keysIterator = urls.keys();
				while (keysIterator.hasNext())
				{
					String filename = keysIterator.next();
					String url = urls.getString(filename);
					HashMap<String, String> fileFromArray = filesArray.get(filename);

					Log.e("thumbDownload",  "START - SIZE - " + workers.size() + " - " + filename);

					GenericAsyncTask task = new GenericAsyncTask(context);
					workers.add(task);
					task.setWork(new GenericAsyncTask.GenericTaskWork() {
						@Override
						public Object execute(Context context) {
							try {
								if (!thumbCacheDir.exists()) {
									thumbCacheDir.mkdirs();
								}
								File thumbCache = new File(thumbCacheDir.getPath() + "/" + filename);

								HttpsClient.getFileAsByteArray(url, null, new FileOutputStream(thumbCache), false);

								File cachedFileResult = new File(thumbCacheDir.getPath() + "/" + filename);
								if (!cachedFileResult.exists()) {
									Log.e("downloadThumbs", "File " + cachedFileResult.getPath() + " failed to download!");
									return false;
								}
								else {
									byte[] fileBeginning = new byte[Crypto.FILE_BEGGINIG_LEN];
									FileInputStream outTest = new FileInputStream(cachedFileResult);
									outTest.read(fileBeginning);

									if (fileBeginning.length == 0) {
										return false;
									}

									if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
										return false;
									}


									//GalleryActions.refreshGalleryItem(context, filename, Integer.parseInt(fileFromArray.get("set")), fileFromArray.get("albumId"));



									return true;
								}
							} catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
								e.printStackTrace();
							}
							return false;
						}
					});

					task.setOnFinish(new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							workers.remove(task);
							filesArray.remove(filename);
							incrementProgress();
							Log.e("thumbDownload",  "FINISH - SIZE - " + workers.size() + " - " + filename);
						}

					});

					task.executeOnExecutor(cachedThreadPool);
				}
			}

		} catch (NoSuchAlgorithmException | KeyManagementException | IOException | JSONException e) {
			e.printStackTrace();
		}
	}

	private void incrementProgress(){
		count++;
		updateNotification(gallerySize, count);
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
		removeNotification();
	}

	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);

		if(onFinish != null){
			onFinish.onFinish(true);
		}
	}

	private void showNotification() {
		if(isNotificationActive){
			return;
		}
		Context myContext = context.get();
		if(myContext == null){
			return;
		}

		isNotificationActive = true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "org.stingle.photos.download";
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, myContext.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW);
			chan.setLightColor(myContext.getColor(R.color.primaryLightColor));
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) myContext.getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			notificationBuilder = new Notification.Builder(myContext, NOTIFICATION_CHANNEL_ID);
		}
		else{
			notificationBuilder = new Notification.Builder(myContext);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(myContext, 0,
				new Intent(myContext, GalleryActivity.class), 0);

		Notification notification = notificationBuilder
				.setSmallIcon(R.drawable.ic_cloud_download)  // the status icon
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.build();

		mNotifyManager.notify(R.string.download_thumb_service_started, notification);
	}

	private void updateNotification(int totalItemsNumber, int downloadedFilesCount){
		Context myContext = context.get();
		if(myContext == null){
			return;
		}
		showNotification();
		notificationBuilder.setProgress(totalItemsNumber, downloadedFilesCount, false);
		notificationBuilder.setContentTitle(myContext.getString(R.string.downloading_thumb, String.valueOf(downloadedFilesCount), String.valueOf(totalItemsNumber)));
		mNotifyManager.notify(R.string.download_thumb_service_started, notificationBuilder.build());
	}

	private void removeNotification(){
		mNotifyManager.cancel(R.string.download_thumb_service_started);
	}

}
