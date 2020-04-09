package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ShowEncThumbInImageView extends AsyncTask<Void, Void, Bitmap> {

	private Context context;
	private String filename;
	private ImageView imageView;
	private Integer thumbSize = null;
	private int set = SyncManager.GALLERY;
	private String albumId = null;
	private String headers = null;
	private boolean isRemote = false;
	private OnAsyncTaskFinish onFinish;

	public ShowEncThumbInImageView(Context context, String filename, ImageView imageView){
		this.context = context;
		this.filename = filename;
		this.imageView = imageView;
	}

	public ShowEncThumbInImageView setThumbSize(int size){
		thumbSize = size;
		return this;
	}
	public ShowEncThumbInImageView setSet(int set){
		this.set = set;
		return this;
	}
	public ShowEncThumbInImageView setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}
	public ShowEncThumbInImageView setHeaders(String headers){
		this.headers = headers;
		return this;
	}

	public ShowEncThumbInImageView setIsRemote(boolean isRemote){
		this.isRemote = isRemote;
		return this;
	}

	public ShowEncThumbInImageView setOnFinish(OnAsyncTaskFinish onFinish){
		this.onFinish = onFinish;
		return this;
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		if(filename == null || !LoginManager.isKeyInMemory()){
			return null;
		}
		try {
			if(thumbSize == null){
				thumbSize = Helpers.getThumbSize(context);
			}

			byte[] decryptedData = null;
			if(!isRemote) {
				File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + filename);
				decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, headers, true, new FileInputStream(fileToDec));
			}
			else{
				byte[] encFile = FileManager.getAndCacheThumb(context, filename, set);

				if (encFile == null || encFile.length == 0) {
					return null;
				}

				decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, headers, true, encFile);
			}

			if (decryptedData != null) {
				return Helpers.getThumbFromBitmap(Helpers.decodeBitmap(decryptedData, thumbSize), thumbSize);
			}

		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}
		return null;
	}



	@Override
	protected void onPostExecute(Bitmap bitmap) {
		super.onPostExecute(bitmap);

		if(bitmap != null){
			imageView.setImageBitmap(bitmap);
			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}
}
