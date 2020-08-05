package org.stingle.photos.Sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.SyncAsyncTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncService extends Service {
	static final public int MSG_SYNC_CURRENT_FILE = 3;
	static final public int MSG_SYNC_UPLOAD_PROGRESS = 4;
	static final public int MSG_SYNC_FINISHED = 5;
	static final public int MSG_START_SYNC = 6;
	static final public int MSG_GET_SYNC_STATUS = 7;
	static final public int MSG_RESP_SYNC_STATUS = 8;
	static final public int MSG_SYNC_STATUS_CHANGE = 9;
	static final public int MSG_REFRESH_GALLERY = 10;
	static final public int MSG_REFRESH_GALLERY_ITEM = 11;
	static final public int MSG_STOP_SYNC = 12;
	static final public int MSG_RESTART_SYNC = 13;

	static final public int STATUS_IDLE = 0;
	static final public int STATUS_REFRESHING = 1;
	static final public int STATUS_UPLOADING = 2;
	static final public int STATUS_NO_SPACE_LEFT = 3;
	static final public int STATUS_DISABLED = 4;
	static final public int STATUS_NOT_WIFI = 5;
	static final public int STATUS_BATTERY_LOW = 6;

	private static SyncService instance = null;
	private ExecutorService cachedThreadPool;

	public static boolean isInstanceCreated() {
		return instance != null;
	}

	public static SyncService getInstance() {
		return instance;
	}

	private static SyncAsyncTask syncTask = null;

	public SyncService() {
	}

	@Override
	public void onCreate() {
		super.onCreate();

		instance = this;
		cachedThreadPool = Executors.newCachedThreadPool();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		stopSync();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(intent != null){
			if(intent.hasExtra("START_SYNC")) {
				startSync();
			}
			else if(intent.hasExtra("STOP_SYNC")) {
				stopSync();
			}
			else if(intent.hasExtra("RESTART_SYNC")) {
				restartSync();
			}
		}
		return START_STICKY;
	}

	public void startSync(){

		syncTask = new SyncAsyncTask(this, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				syncTask = null;
			}
		});
		syncTask.executeOnExecutor(cachedThreadPool);
	}

	public void stopSync(){
		if(syncTask != null){
			syncTask.cancel(true);
		}
	}

	public void restartSync(){
		stopSync();
		startSync();
	}





	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


}
