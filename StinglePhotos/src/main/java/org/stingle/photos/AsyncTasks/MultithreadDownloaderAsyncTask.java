package org.stingle.photos.AsyncTasks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
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
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MultithreadDownloaderAsyncTask extends AsyncTask<Void, Void, Void> {

	static final public int WORKERS_LIMIT = 100;
	static final public int AVAILABLE_URLS_LIMIT = 200;
	static final public int URLS_GET_THRESHOLD = 50;
	static final public int FILES_ARRAY_SIZE_LIMIT = 1000;
	private final ExecutorService cachedThreadPool;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private final WeakReference<Context> context;
	protected OnAsyncTaskFinish onFinish;
	public static NotificationManager mNotifyManager;
	public static Notification.Builder notificationBuilder;
	public static boolean isNotificationActive = false;
	private final File filesDir;
	private final File thumbDir;
	private final File thumbCacheDir;
	private final File filesCacheDir;
	private int count = 1;
	private int batchSize;
	private boolean isDownloadingThumbs = true;
	private boolean isFinishedQueueInput = false;
	private Integer messageStringId;
	private ArrayList<GenericAsyncTask> workers = new ArrayList<>();
	private ArrayDeque<HashMap<String, String>> filesArray = new ArrayDeque<>();
	private ArrayDeque<HashMap<String, String>> queuedUrls = new ArrayDeque<>();
	private CountDownLatch downloadJobLatch;
	private boolean isFailedDownloads = false;

	public MultithreadDownloaderAsyncTask(Context context, OnAsyncTaskFinish onFinish){
		this.context = new WeakReference<>(context);
		this.onFinish = onFinish;
		mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		filesDir = new File(FileManager.getHomeDir(context));
		thumbDir = new File(FileManager.getThumbsDir(context));
		thumbCacheDir = new File(context.getCacheDir().getPath() + "/" + FileManager.THUMB_CACHE_DIR);
		filesCacheDir = new File(context.getCacheDir().getPath() + "/" + FileManager.FILE_CACHE_DIR);
		cachedThreadPool = Executors.newCachedThreadPool();
	}

	public MultithreadDownloaderAsyncTask setIsDownloadingThumbs(boolean isDownloadingThumbs){
		this.isDownloadingThumbs = isDownloadingThumbs;
		return this;
	}

	public MultithreadDownloaderAsyncTask setBatchSize(int batchSize){
		this.batchSize = batchSize;
		return this;
	}

	public MultithreadDownloaderAsyncTask setMessageStringId(int id){
		messageStringId = id;
		return this;
	}

	public void setInputFinished(){
		isFinishedQueueInput = true;
	}

	@Override
	protected Void doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return null;
		}
		Log.d("multithreadDwn", "Downloader START");


		scheduler.scheduleAtFixedRate(() -> {
			Log.d("multithreadDwn", "Downloader RUN - " +
					"isFinishedQueueInput - " + isFinishedQueueInput +
					" | workers.size() - " + workers.size() +
					" | filesArray.size() - " + filesArray.size() +
					" | queuedUrls.size() - " + queuedUrls.size());
			fillUrls(myContext);
			startDownloadThreads(myContext);

			if (isFinishedQueueInput && workers.size() == 0 && filesArray.size() == 0 && queuedUrls.size() == 0) {
				Log.d("multithreadDwn", "Downloader END");
				removeNotification();
				if(onFinish != null){
					onFinish.onFinish(!isFailedDownloads && isFinishedQueueInput);
				}
				scheduler.shutdownNow();
			}
		}, 1000, 600, MILLISECONDS);

		return null;
	}

	public synchronized CountDownLatch addDownloadJob(StingleDbFile dbFile, int set) {
		boolean needToDownload = false;
		if(isDownloadingThumbs) {
			File thumb = new File(thumbDir + "/" + dbFile.filename);
			File thumbCache = new File(thumbCacheDir.getPath() + "/" + dbFile.filename);
			if (!thumb.exists() && !thumbCache.exists()) {
				needToDownload = true;
			}
		}
		else {
			File file = new File(filesDir.getPath() + "/" + dbFile.filename);
			File fileCache = new File(filesCacheDir.getPath() + "/" + dbFile.filename);
			if (!file.exists() && !fileCache.exists()) {
				needToDownload = true;
			}
		}

		if(needToDownload){
			filesArray.add(new HashMap<String, String>() {{
				put("filename", dbFile.filename);
				put("set", String.valueOf(set));
			}});
		}
		else {
			count++;
		}

		if(filesArray.size() >= FILES_ARRAY_SIZE_LIMIT){
			downloadJobLatch = new CountDownLatch(1);
			return downloadJobLatch;
		}

		return null;
	}


	private void fillUrls(Context context){
		if(filesArray.size() == 0){
			return;
		}
		if(AVAILABLE_URLS_LIMIT - queuedUrls.size() < URLS_GET_THRESHOLD){
			return;
		}
		try {
			ArrayList<HashMap<String, String>> filesToDownloadNow = new ArrayList<>();
			int thisBatchSize = AVAILABLE_URLS_LIMIT - queuedUrls.size();
			Log.d("multithreadDwn", "Download urls - " + thisBatchSize);
			for (int i = 0; i < thisBatchSize;i++) {
				HashMap<String, String> item = filesArray.poll();
				if(item == null){
					break;
				}
				filesToDownloadNow.add(item);
			}
			if(filesToDownloadNow.size() == 0){
				return;
			}
			JSONObject urls = SyncManager.getDownloadLinks(context, filesToDownloadNow, isDownloadingThumbs);

			if(urls != null) {
				Iterator<String> keysIterator = urls.keys();
				while (keysIterator.hasNext()) {
					String filename = keysIterator.next();
					String url = urls.getString(filename);
					queuedUrls.add(new HashMap<String, String>() {{
						put("filename", filename);
						put("url", url);
					}});
				}
			}

		} catch (NoSuchAlgorithmException | KeyManagementException | IOException | JSONException e) {
			e.printStackTrace();
		}

		if(filesArray.size() <= FILES_ARRAY_SIZE_LIMIT){
			if(downloadJobLatch != null) {
				downloadJobLatch.countDown();
			}
		}
	}

	private void startDownloadThreads(Context context){
		if(queuedUrls.size() == 0){
			return;
		}

		while(workers.size() < WORKERS_LIMIT && queuedUrls.size() > 0) {
			HashMap<String, String> urlItem = queuedUrls.poll();
			String url = urlItem.get("url");
			String filename = urlItem.get("filename");

			Log.d("multithreadDwn", "START - SIZE - " + workers.size() + " - " + filename);

			GenericAsyncTask task = new GenericAsyncTask(context);
			workers.add(task);
			task.setAltData(filename);
			task.setWork(new GenericAsyncTask.GenericTaskWork() {
				@Override
				public Object execute(Context context) {

					File cacheFileTmp;
					File cachedFileResultTmp;
					File cacheFile;
					if (isDownloadingThumbs) {
						if (!thumbCacheDir.exists()) {
							thumbCacheDir.mkdirs();
						}
						cacheFileTmp = new File(thumbCacheDir.getPath() + "/" + filename  + ".tmp");
						cachedFileResultTmp = new File(thumbCacheDir.getPath() + "/" + filename  + ".tmp");
						cacheFile = new File(thumbCacheDir.getPath() + "/" + filename);
					} else {
						if (!filesCacheDir.exists()) {
							filesCacheDir.mkdirs();
						}
						cacheFileTmp = new File(filesCacheDir.getPath() + "/" + filename  + ".tmp");
						cachedFileResultTmp = new File(filesCacheDir.getPath() + "/" + filename  + ".tmp");
						cacheFile = new File(filesCacheDir.getPath() + "/" + filename);
					}

					try {
						HttpsClient.getFileAsByteArray(url, null, new FileOutputStream(cacheFileTmp), false);

						if (!cachedFileResultTmp.exists()) {
							Log.d("multithreadDwn", "File " + cachedFileResultTmp.getPath() + " failed to download!");
							isFailedDownloads = true;
							return false;
						} else {
							byte[] fileBeginning = new byte[Crypto.FILE_BEGGINIG_LEN];
							FileInputStream outTest = new FileInputStream(cachedFileResultTmp);
							outTest.read(fileBeginning);

							if (fileBeginning.length == 0 || !new String(fileBeginning, StandardCharsets.UTF_8).equals(Crypto.FILE_BEGGINING)) {
								Log.d("multithreadDwn", "File " + cachedFileResultTmp.getPath() + " has invalid header!");
								cachedFileResultTmp.delete();
								isFailedDownloads = true;
								return false;
							}

							if(!cacheFile.exists()){
								cacheFileTmp.renameTo(cacheFile);
							}
							else{
								Log.d("multithreadDwn", "File " + cacheFile.getPath() + " existed, deleting tmp file!");
								cacheFileTmp.delete();
							}

							return true;
						}
					} catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
						if(cachedFileResultTmp.exists()){
							cachedFileResultTmp.delete();
							isFailedDownloads = true;
						}
						e.printStackTrace();
					}
					return false;
				}
			});

			task.setOnFinish(new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					workers.remove(task);
					incrementProgress();
					Log.d("multithreadDwn", "FINISH - SIZE - " + workers.size() + " - " + filename);
				}

			});

			task.executeOnExecutor(cachedThreadPool);
		}
	}

	private void incrementProgress(){
		count++;
		updateNotification(batchSize, count);
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
		isFinishedQueueInput = true;
		removeNotification();
	}

	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);
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

	long lastNotificationUpdateTime = 0;

	private void updateNotification(int totalItemsNumber, int downloadedFilesCount){
		if(messageStringId == null){
			return;
		}

		long now = System.currentTimeMillis();
		if(now - lastNotificationUpdateTime < 400){
			return;
		}
		lastNotificationUpdateTime = now;

		Context myContext = context.get();
		if(myContext == null){
			return;
		}
		showNotification();
		notificationBuilder.setProgress(totalItemsNumber, downloadedFilesCount, false);
		notificationBuilder.setContentTitle(myContext.getString(messageStringId, String.valueOf(downloadedFilesCount), String.valueOf(totalItemsNumber)));
		mNotifyManager.notify(R.string.download_thumb_service_started, notificationBuilder.build());
	}

	private void removeNotification(){
		if(messageStringId == null){
			return;
		}
		mNotifyManager.cancel(R.string.download_thumb_service_started);
	}

}
