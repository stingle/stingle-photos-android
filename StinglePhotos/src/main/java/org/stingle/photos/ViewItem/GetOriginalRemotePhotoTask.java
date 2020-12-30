package org.stingle.photos.ViewItem;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.core.widget.ContentLoadingProgressBar;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Widget.AnimatedGifImageView;
import org.stingle.photos.Widget.photoview.PhotoViewAttacher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class GetOriginalRemotePhotoTask extends AsyncTask<Void, Integer, byte[]> {

	private WeakReference<Context> contextRef;
	private ViewItemAsyncTask.ViewItemTaskResult result;
	private ImageView image;
	private AnimatedGifImageView animatedImage;
	private PhotoViewAttacher attacher;
	private OnAsyncTaskFinish onFinish;

	private ContentLoadingProgressBar loading;

	private boolean isGif = false;

	public GetOriginalRemotePhotoTask(Context context, ViewItemAsyncTask.ViewItemTaskResult result) {
		super();
		this.contextRef = new WeakReference<>(context);
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
	public void setOnFinish(OnAsyncTaskFinish onFinish) {
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected byte[] doInBackground(Void... params) {
		Context context = contextRef.get();
		if(context == null){
			return null;
		}
		File cacheDir = new File(context.getCacheDir().getPath() + "/" + FileManager.FILE_CACHE_DIR);
		if(!cacheDir.exists()){
			cacheDir.mkdirs();
		}
		File cachedFile = new File(cacheDir.getPath() + "/" + result.filename);

		try {
			byte[] encFile;
			if(cachedFile.exists()){
				FileInputStream in = new FileInputStream(cachedFile);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[4096];

				int numRead;
				while ((numRead = in.read(buf)) >= 0) {
					out.write(buf, 0, numRead);
				}
				in.close();
				encFile = out.toByteArray();
			}
			else {
				HashMap<String, String> postParams = new HashMap<String, String>();

				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("file", result.filename);
				postParams.put("thumb", "0");
				postParams.put("set", String.valueOf(result.set));


				encFile = HttpsClient.getFileAsByteArray(StinglePhotosApplication.getApiUrl() + context.getString(R.string.download_file_path), postParams);
				if (encFile == null || encFile.length == 0) {
					return null;
				}

				byte[] fileBeginning = Arrays.copyOfRange(encFile, 0, Crypto.FILE_BEGGINIG_LEN);
				if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
					return null;
				}

				FileOutputStream out = new FileOutputStream(cachedFile);
				out.write(encFile);
				out.close();
			}

			if (encFile == null || encFile.length == 0) {
				return null;
			}

			return CryptoHelpers.decryptDbFile(context, result.set, result.albumId, result.headers, false, encFile, this);
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
				Context context = contextRef.get();
				if(context == null){
					return;
				}
				Bitmap bitmap = Helpers.decodeBitmap(data, ViewItemAsyncTask.getSize(context));
				//image.setImageResource(R.drawable.no);
				image.setImageBitmap(bitmap);

				//float scale = attacher.getScale();
				if (attacher != null) {
					attacher.update();
				}
				//attacher.setScale(scale);
			}
			Log.d("originalImageGet", "finished");
		}

		if(onFinish != null){
			onFinish.onFinish();
		}
	}


}
