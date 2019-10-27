package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ShowThumbInImageView extends AsyncTask<Void, Void, Bitmap> {

	private Context context;
	private String filename;
	private ImageView imageView;
	private Integer thumbSize;
	private OnAsyncTaskFinish onFinish;

	public ShowThumbInImageView(Context context, String filename, ImageView imageView){
		this.context = context;
		this.filename = filename;
		this.imageView = imageView;
	}

	public void setThumbSize(int size){
		thumbSize = size;
	}

	public void setOnFinish(OnAsyncTaskFinish onFinish){
		this.onFinish = onFinish;
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
			File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + filename);
			FileInputStream input = new FileInputStream(fileToDec);
			byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(input);

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
