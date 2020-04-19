package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Sync.SyncManager;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class AddAlbumAsyncTask extends AsyncTask<Void, Void, StingleDbAlbum> {

	private WeakReference<Context> context;
	private String albumName;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	public AddAlbumAsyncTask(Context context, String albumName, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.albumName = albumName;
		this.onFinishListener = onFinishListener;
	}

	@Override
	protected StingleDbAlbum doInBackground(Void... params) {
		try {
			Context myContext = context.get();
			if(myContext == null){
				return null;
			}

			return SyncManager.addAlbum(myContext, albumName);

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void onPostExecute(StingleDbAlbum album) {
		super.onPostExecute(album);

		if(album != null){
			onFinishListener.onFinish(album);
		}
		else{
			onFinishListener.onFail();
		}

	}
}
