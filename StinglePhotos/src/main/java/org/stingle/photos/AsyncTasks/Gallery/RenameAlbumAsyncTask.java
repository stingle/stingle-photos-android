package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class RenameAlbumAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private String albumId;
	private String albumName;
	private final OnAsyncTaskFinish onFinishListener;

	public RenameAlbumAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;

		this.onFinishListener = onFinishListener;
	}

	public RenameAlbumAsyncTask setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}
	public RenameAlbumAsyncTask setAlbumName(String albumName){
		this.albumName = albumName;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		Context myContext = context.get();
		if(myContext == null){
			return false;
		}

		AlbumsDb db = new AlbumsDb(myContext);

		StingleDbAlbum album = db.getAlbumById(albumId);

		if(album == null || !album.isOwner || albumName == null || albumName.length() == 0){
			return false;
		}

		Crypto crypto = StinglePhotosApplication.getCrypto();
		try {
			Crypto.AlbumData albumData = crypto.parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);

			albumData.metadata.name = albumName;
			String newMetadata = Crypto.byteArrayToBase64(crypto.encryptAlbumMetadata(albumData.metadata, albumData.publicKey));

			album.metadata = newMetadata;

			boolean notifyResult = SyncManager.notifyCloudAboutAlbumRename(myContext, album);
			if(notifyResult){
				db.updateAlbum(album);
				db.close();
				return true;
			}
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		db.close();
		return false;
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
