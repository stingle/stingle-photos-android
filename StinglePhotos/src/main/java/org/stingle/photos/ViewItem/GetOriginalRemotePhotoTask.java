package org.stingle.photos.ViewItem;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Widget.photoview.PhotoViewAttacher;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class GetOriginalRemotePhotoTask extends AsyncTask<Void, Integer, Bitmap> {

	private Context context;
	private ViewItemAsyncTask.ViewItemTaskResult result;
	private ImageView image;
	private PhotoViewAttacher attacher;

	public GetOriginalRemotePhotoTask(Context context, ViewItemAsyncTask.ViewItemTaskResult result, ImageView image, PhotoViewAttacher attacher) {
		super();
		this.context = context;
		this.result = result;
		this.image = image;
		this.attacher = attacher;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", result.filename);
		postParams.put("thumb", "0");
		postParams.put("folder", String.valueOf(result.folder));

		try {
			byte[] encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);
			if(encFile == null || encFile.length == 0){
				return null;
			}

			byte[] fileBeginning = Arrays.copyOfRange(encFile, 0, Crypto.FILE_BEGGINIG_LEN);
			Log.d("beg", new String(fileBeginning, "UTF-8"));
			if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
				return null;
			}

			byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(encFile, this);

			return Helpers.decodeBitmap(decryptedData, ViewItemAsyncTask.getSize(context));
		}
		catch (NoSuchAlgorithmException | KeyManagementException | IOException | CryptoException e) {

		}

		return null;
	}


	@Override
	protected void onPostExecute(Bitmap bitmap) {
		super.onPostExecute(bitmap);

		if(bitmap != null) {
			Log.d("update", " qaq");
			//image.setImageResource(R.drawable.no);
			image.setImageBitmap(bitmap);

			float scale = attacher.getScale();
			attacher.update();
			attacher.setScale(scale);
		}
		else{
			Log.d("noupdate", "shit qaq");
		}
	}


}
