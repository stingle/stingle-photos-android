package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.AlbumsDb;
import org.stingle.photos.StinglePhotosApplication;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public class AddAlbumAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private String albumName;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	public AddAlbumAsyncTask(Context context, String albumName, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.albumName = albumName;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		try {
			Context myContext = context.get();
			if(myContext == null){
				return false;
			}
			HashMap<String, byte[]> albumInfo = crypto.generateEncryptedAlbumData(crypto.getPublicKey(), albumName);

			AlbumsDb db = new AlbumsDb(myContext);

			db.insertAlbum(Crypto.byteArrayToBase64Default(albumInfo.get("data")), Crypto.byteArrayToBase64Default(albumInfo.get("pk")), System.currentTimeMillis());
			db.close();

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result){
			onFinishListener.onFinish();
		}
		else{
			onFinishListener.onFail();
		}

	}
}
