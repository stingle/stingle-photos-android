package org.stingle.photos.Sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.widget.ContentLoadingProgressBar;

import org.stingle.photos.Camera2Activity;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Db.StingleDbHelper;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoEncryptService extends Service {


	private NotificationManager mNM;
	private ExecutorService cachedThreadPool;

	public VideoEncryptService() {
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		showNotification();
		cachedThreadPool = Executors.newCachedThreadPool();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		(new EncryptVideosTask(this, new EncryptVideosTask.OnFinish() {
			@Override
			public void onFinish() {
				stopSelf();
			}
		})).executeOnExecutor(cachedThreadPool);


		return START_NOT_STICKY;
	}

	private static class EncryptVideosTask extends AsyncTask<Void, Void, Void>{

		private Context context;
		private EncryptVideosTask.OnFinish onFinish;

		EncryptVideosTask(Context context, EncryptVideosTask.OnFinish onFinish){
			this.context = context;
			this.onFinish = onFinish;
		}

		@Override
		protected Void doInBackground(Void... voids) {
			File cacheDir = context.getCacheDir();

			File tmpDir = new File(cacheDir.getAbsolutePath() + "/tmpvideo/");
			for(File file : tmpDir.listFiles()){
				try {
					String videoFilenameEnc = Helpers.getNewEncFilename();
					FileInputStream in = new FileInputStream(file);
					String encFilePath = FileManager.getHomeDir(context) + "/" + videoFilenameEnc;
					FileOutputStream out = new FileOutputStream(encFilePath);

					int videoDuration = FileManager.getVideoDurationFromUri(context, Uri.fromFile(file));

					byte[] fileId = StinglePhotosApplication.getCrypto().encryptFile(in, out, file.getName(), Crypto.FILE_TYPE_VIDEO, in.getChannel().size(), videoDuration);

					out.close();

					Bitmap thumb = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					thumb.compress(Bitmap.CompressFormat.PNG, 0, bos);

					Helpers.generateThumbnail(context, bos.toByteArray(), videoFilenameEnc, file.getName(), fileId, Crypto.FILE_TYPE_VIDEO, videoDuration);
					Helpers.insertFileIntoDB(context, videoFilenameEnc);
					file.delete();
				} catch (IOException | CryptoException e) {
					e.printStackTrace();
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

			Intent broadcastIntent = new Intent();
			broadcastIntent.setAction("org.stingle.photos.VIDEO_ENC_FINISH");
			context.sendBroadcast(broadcastIntent);
		}
		public static abstract class OnFinish{
			public abstract void onFinish();
		}
	}


	@Nullable
	@android.support.annotation.Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


	private void showNotification() {
		Notification.Builder notificationBuilder;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "org.stingle.photos";
			String channelName = "Video Encrypt Service";
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
				new Intent(this, GalleryActivity.class), 0);

		Notification notification = notificationBuilder
				.setSmallIcon(R.drawable.ic_sp)  // the status icon
				.setWhen(System.currentTimeMillis())  // the time stamp
				.setContentTitle(getText(R.string.encrypting_video))  // the label of the entry
				.setContentIntent(contentIntent)  // The intent to send when the entry is clicked
				.build();

		startForeground(R.string.envideo_service_started, notification);
	}
}
