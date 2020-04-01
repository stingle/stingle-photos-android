package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public class AddAlbumAsyncTask extends AsyncTask<Void, Void, StingleDbAlbum> {

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
	protected StingleDbAlbum doInBackground(Void... params) {
		try {
			Context myContext = context.get();
			if(myContext == null){
				return null;
			}
			HashMap<String, String> albumInfo = crypto.generateEncryptedAlbumData(crypto.getPublicKey(), albumName);

			AlbumsDb db = new AlbumsDb(myContext);
			long now = System.currentTimeMillis();
			String newAbumId = CryptoHelpers.getRandomString(AlbumsDb.ALBUM_ID_LEN);

			if(!SyncManager.notifyCloudAboutAlbumAdd(myContext, newAbumId, albumInfo.get("data"), albumInfo.get("pk"), now,  now)){
				return null;
			}

			db.insertAlbum(newAbumId, albumInfo.get("data"), albumInfo.get("pk"), now, now);
			StingleDbAlbum newAlbum = db.getAlbumById(newAbumId);
			db.close();

			return newAlbum;

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
