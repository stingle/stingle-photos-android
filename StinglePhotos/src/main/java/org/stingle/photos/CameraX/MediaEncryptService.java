package org.stingle.photos.CameraX;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaEncryptService extends Service {


	private NotificationManager mNM;
	private ExecutorService cachedThreadPool;
	private LocalBroadcastManager lbm;
	private boolean isCameraRunning = true;
	private ConcurrentHashMap<String, AsyncTask> tasksStack = new ConcurrentHashMap<String, AsyncTask>();

	private static String PROC_EXT = "_proc";

	public MediaEncryptService() {
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		showNotification();
		cachedThreadPool = Executors.newCachedThreadPool();
		lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(onNewMedia, new IntentFilter("NEW_MEDIA"));
		lbm.registerReceiver(onCameraStatus, new IntentFilter("CAMERA_STATUS"));
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		/*(new EncryptVideosTask(this, new EncryptVideosTask.OnFinish() {
			@Override
			public void onFinish() {
				stopSelf();
			}
		})).executeOnExecutor(cachedThreadPool);*/


		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		lbm.unregisterReceiver(onNewMedia);
	}

	private BroadcastReceiver onNewMedia = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String filePath = intent.getStringExtra("file");
			if(filePath != null){
				File file = new File(filePath);
				if(!tasksStack.containsKey(file.getName())) {
					AsyncTask<Void, Void, Void> task = new EncryptMediaTask(MediaEncryptService.this, file, new EncryptMediaTask.OnFinish() {
						@Override
						public void onFinish() {
							Log.i("encrypt_n", "encrypted " + file.getName());
							lbm.sendBroadcast(new Intent("MEDIA_ENC_FINISH"));
							tasksStack.remove(file.getName());

							if (!isCameraRunning && tasksStack.size() == 0) {
								triggerSyncAndStop();
							}
						}
					});
					task.executeOnExecutor(cachedThreadPool);

					tasksStack.put(file.getName(), task);
				}
			}
		}
	};
	private BroadcastReceiver onCameraStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			isCameraRunning = intent.getBooleanExtra("isRunning", true);
			boolean wasRecoringVideo = intent.getBooleanExtra("wasRecoringVideo", false);

			if (!isCameraRunning && tasksStack.size() == 0) {

				File tmpDir = new File(FileManager.getCameraTmpDir(MediaEncryptService.this));
				File[] files = tmpDir.listFiles((dir, name) -> {
					if(name.endsWith(PROC_EXT)) {
						return false;
					}
					return true;
				});
				if (files.length > 0) {
					ArrayList<File> filesList = new ArrayList<>(Arrays.asList(files));
					for(File file : filesList) {
						if (tasksStack.containsKey(file.getName())){
							filesList.remove(file);
						}
					}
					if(wasRecoringVideo) {
						(new Handler()).postDelayed(() -> encryptFileList(filesList), 3000);
					}
					else{
						encryptFileList(filesList);
					}
				} else {
					triggerSyncAndStop();
				}
			}
		}
	};

	private void encryptFileList(ArrayList<File> filesList){
		(new EncryptMediaTask(MediaEncryptService.this, filesList, new EncryptMediaTask.OnFinish() {
			@Override
			public void onFinish() {
				Log.i("encrypt_l", "encrypted " + filesList.toString());
				lbm.sendBroadcast(new Intent("MEDIA_ENC_FINISH"));
				triggerSyncAndStop();
			}
		})).executeOnExecutor(cachedThreadPool);
	}


	private void triggerSyncAndStop(){
		SyncManager.startSync(this);
		stopSelf();
	}

	private static class EncryptMediaTask extends AsyncTask<Void, Void, Void>{

		private Context context;
		private List<File> files;
		private EncryptMediaTask.OnFinish onFinish;
		private LocalBroadcastManager lbm;

		EncryptMediaTask(Context context, File file, EncryptMediaTask.OnFinish onFinish){
			this(context, Arrays.asList(file), onFinish);
		}

		EncryptMediaTask(Context context, List<File> files, EncryptMediaTask.OnFinish onFinish){
			this.context = context;
			this.files = files;
			this.onFinish = onFinish;

			lbm = LocalBroadcastManager.getInstance(context);
		}

		@Override
		protected Void doInBackground(Void... voids) {
			for(File origfile : this.files) {
				File file = new File(origfile.getAbsolutePath() + PROC_EXT);
				try {
					String realFilename = origfile.getName();
					int type = FileManager.getFileTypeFromUri(origfile.getAbsolutePath());

					if(!origfile.renameTo(new File(origfile.getAbsolutePath() + PROC_EXT))){
						return null;
					}

					if (type != Crypto.FILE_TYPE_PHOTO && type != Crypto.FILE_TYPE_VIDEO) {
						return null;
					}

					String encFilename = Helpers.getNewEncFilename();
					byte[] fileId = StinglePhotosApplication.getCrypto().getNewFileId();
					int videoDuration = 0;


					if (type == Crypto.FILE_TYPE_PHOTO) {
						InputStream thumbIn = new FileInputStream(file);
						ByteArrayOutputStream bytes = new ByteArrayOutputStream();
						int numRead = 0;
						byte[] buf = new byte[1024];
						while ((numRead = thumbIn.read(buf)) >= 0) {
							bytes.write(buf, 0, numRead);
						}
						thumbIn.close();

						Helpers.generateThumbnail(context, bytes.toByteArray(), encFilename, realFilename, fileId, Crypto.FILE_TYPE_PHOTO, 0);
					} else if (type == Crypto.FILE_TYPE_VIDEO) {
						videoDuration = FileManager.getVideoDurationFromUri(context, Uri.fromFile(file));

						Bitmap thumb = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
						if(thumb != null) {
							ByteArrayOutputStream bos = new ByteArrayOutputStream();
							thumb.compress(Bitmap.CompressFormat.JPEG, 70, bos);

							Helpers.generateThumbnail(context, bos.toByteArray(), encFilename, realFilename, fileId, Crypto.FILE_TYPE_VIDEO, videoDuration);
						}
						else{
							throw new IOException("Failed to generate video thumbnail");
						}
					}

					String encFilePath = FileManager.getHomeDir(context) + "/" + encFilename;
					FileInputStream in = new FileInputStream(file);
					FileOutputStream out = new FileOutputStream(encFilePath);
					StinglePhotosApplication.getCrypto().encryptFile(in, out, realFilename, type, file.length(), fileId, videoDuration);

					Helpers.insertFileIntoDB(context, encFilename);
					Log.d("insertFileIntoDB", encFilename);

					file.delete();

				} catch (IOException | CryptoException e) {
					e.printStackTrace();
					String path = file.getAbsolutePath();
					String newPath = path.substring(0, path.length() - PROC_EXT.length());
					file.renameTo(new File(newPath));
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			super.onPostExecute(aVoid);

			if(onFinish != null){
				onFinish.onFinish();
			}
		}
		public static abstract class OnFinish{
			public abstract void onFinish();
		}
	}


	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


	private void showNotification() {
		Notification.Builder notificationBuilder;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "org.stingle.photos";
			String channelName = getString(R.string.encryption_service);
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
			chan.setLightColor(getColor(R.color.primaryLightColor));
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			notificationBuilder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
		}
		else{
			notificationBuilder = new Notification.Builder(this);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, GalleryActivity.class), PendingIntent.FLAG_IMMUTABLE);

		Notification notification = notificationBuilder
				.setSmallIcon(R.drawable.ic_sp)  // the status icon
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setContentTitle(getText(R.string.encryption_service))  // the label of the entry
				.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
				.build();

		try {
			startForeground(R.string.envideo_service_started, notification);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
