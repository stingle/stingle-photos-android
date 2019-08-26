package com.fenritz.safecam.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.fenritz.safecam.Auth.LoginManager;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Files.FileManager;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ShowThumbInImageView extends AsyncTask<Void, Void, Bitmap> {

	protected Context context;
	protected String filename;
	protected ImageView imageView;

	public ShowThumbInImageView(Context context, String filename, ImageView imageView){
		this.context = context;
		this.filename = filename;
		this.imageView = imageView;
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		if(filename == null || !LoginManager.isKeyInMemory()){
			return null;
		}
		try {
			File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + filename);
			FileInputStream input = new FileInputStream(fileToDec);
			byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(input);

			if (decryptedData != null) {
				return Helpers.decodeBitmap(decryptedData, Helpers.getThumbSize(context));
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
		}
	}
}
