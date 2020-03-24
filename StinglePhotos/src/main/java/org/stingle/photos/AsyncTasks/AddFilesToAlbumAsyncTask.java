package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.StinglePhotosApplication;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class AddFilesToAlbumAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private StingleDbAlbum album;
	private ArrayList<StingleFile> files;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	public AddFilesToAlbumAsyncTask(Context context, StingleDbAlbum album, ArrayList<StingleFile> files, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.album = album;
		this.files = files;
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

			AlbumFilesDb db = new AlbumFilesDb(myContext);

			for(StingleFile file : files) {
				long now = System.currentTimeMillis();
				String newHeaders = crypto.reencryptFileHeaders(file.headers, Crypto.base64ToByteArrayDefault(album.albumPK), null, null);
				db.insertAlbumFile(album.id, file.filename, file.isLocal, file.isRemote, file.version, newHeaders, now, now);
			}
			db.close();

		} catch (IOException | CryptoException e) {
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
