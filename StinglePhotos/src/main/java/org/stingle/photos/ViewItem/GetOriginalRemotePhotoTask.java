package org.stingle.photos.ViewItem;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.core.widget.ContentLoadingProgressBar;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Widget.AnimatedGifImageView;
import org.stingle.photos.Widget.photoview.PhotoViewAttacher;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class GetOriginalRemotePhotoTask extends AsyncTask<Void, Integer, byte[]> {

	private Context context;
	private ViewItemAsyncTask.ViewItemTaskResult result;
	private ImageView image;
	private AnimatedGifImageView animatedImage;
	private PhotoViewAttacher attacher;

	private ContentLoadingProgressBar loading;

	private boolean isGif = false;

	public GetOriginalRemotePhotoTask(Context context, ViewItemAsyncTask.ViewItemTaskResult result) {
		super();
		this.context = context;
		this.result = result;
	}

	public void setImage(ImageView image) {
		this.image = image;
	}

	public void setAnimatedImage(AnimatedGifImageView animatedImage) {
		this.animatedImage = animatedImage;
	}
	public void setGif(boolean gif) {
		isGif = gif;
	}

	public void setAttacher(PhotoViewAttacher attacher) {
		this.attacher = attacher;
	}

	public void setLoading(ContentLoadingProgressBar loading) {
		this.loading = loading;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected byte[] doInBackground(Void... params) {
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

			return StinglePhotosApplication.getCrypto().decryptFile(encFile, this);
		}
		catch (NoSuchAlgorithmException | KeyManagementException | IOException | CryptoException e) {

		}

		return null;
	}


	@Override
	protected void onPostExecute(byte[] data) {
		super.onPostExecute(data);

		if(data != null) {
			if(isGif){
				if(animatedImage != null) {
					animatedImage.setAnimatedGif(data, AnimatedGifImageView.TYPE.FIT_CENTER);
					if(loading != null){
						loading.setVisibility(View.INVISIBLE);
					}
				}
			}
			else {
				Bitmap bitmap = Helpers.decodeBitmap(data, ViewItemAsyncTask.getSize(context));
				Log.d("update", " qaq");
				//image.setImageResource(R.drawable.no);
				image.setImageBitmap(bitmap);

				//float scale = attacher.getScale();
				if (attacher != null) {
					attacher.update();
				}
				//attacher.setScale(scale);
			}
		}
	}


}
