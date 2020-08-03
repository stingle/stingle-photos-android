package org.stingle.photos.Sync;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.SyncAsyncTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncService extends Service {
	final Messenger mMessenger = new Messenger(new IncomingHandler());
	private ArrayList<Messenger> mClients = new ArrayList<Messenger>();

	static final public int MSG_REGISTER_CLIENT = 1;
	static final public int MSG_UNREGISTER_CLIENT = 2;
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

	protected int currentStatus = STATUS_IDLE;


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
		return mMessenger.getBinder();
	}



	private void sendStringToUi(int type, String key, String value) {
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				Bundle b = new Bundle();
				b.putString(key, value);
				Message msg = Message.obtain(null, type);
				msg.setData(b);
				mClients.get(i).send(msg);
			}
			catch (RemoteException e) {
				mClients.remove(i);
			}
		}
	}

	private void sendBundleToUi(int type, Bundle bundle) {
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				Message msg = Message.obtain(null, type);
				msg.setData(bundle);
				mClients.get(i).send(msg);
			}
			catch (RemoteException e) {
				mClients.remove(i);
			}
		}
	}

	private void sendIntToUi(int type, String key, int value) {
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				Bundle b = new Bundle();
				b.putInt(key, value);
				Message msg = Message.obtain(null, type);
				msg.setData(b);
				mClients.get(i).send(msg);
			}
			catch (RemoteException e) {
				mClients.remove(i);
			}
		}
	}

	private void sendIntToUi(int type, HashMap<String, Integer> values) {
		if(values == null){
			return;
		}
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				Bundle b = new Bundle();
				for(String key : values.keySet()) {
					Integer value = values.get(key);
					if(value != null) {
						b.putInt(key, value);
					}
				}
				Message msg = Message.obtain(null, type);
				msg.setData(b);
				mClients.get(i).send(msg);
			}
			catch (RemoteException e) {
				mClients.remove(i);
			}
		}
	}

	private void sendMessageToUI(int type) {
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				mClients.get(i).send(Message.obtain(null, type));
			}
			catch (RemoteException e) {
				mClients.remove(i);
			}
		}
	}

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_REGISTER_CLIENT:
					mClients.add(msg.replyTo);
					break;
				case MSG_UNREGISTER_CLIENT:
					mClients.remove(msg.replyTo);
					break;
				case MSG_START_SYNC:
					startSync();
					break;
				case MSG_STOP_SYNC:
					stopSync();
					break;
				case MSG_RESTART_SYNC:
					restartSync();
					break;
				case MSG_GET_SYNC_STATUS:
					/*if(progress != null) {
						for (int i = mClients.size() - 1; i >= 0; i--) {
							try {
								Bundle b = new Bundle();
								b.putInt("totalItemsNumber", progress.totalItemsNumber);
								b.putString("currentFile", progress.currentFile);
								b.putString("headers", progress.headers);
								b.putInt("set", progress.set);
								b.putString("albumId", progress.albumId);
								b.putInt("uploadedFilesCount", progress.uploadedFilesCount);
								b.putInt("syncStatus", currentStatus);
								Message msgToSend = Message.obtain(null, MSG_RESP_SYNC_STATUS);
								msgToSend.setData(b);
								mClients.get(i).send(msgToSend);
							} catch (RemoteException e) {
								mClients.remove(i);
							}
						}
					}*/
					break;

				default:
					super.handleMessage(msg);
			}
		}
	}


}
