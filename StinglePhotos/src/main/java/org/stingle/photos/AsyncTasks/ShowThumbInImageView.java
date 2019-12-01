package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.widget.ImageView;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Camera2Activity;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class ShowThumbInImageView extends AsyncTask<Void, Void, Bitmap> {

	private Context context;
	private File file;
	private ImageView imageView;
	private Integer thumbSize = null;
	private boolean isVideo = false;
	private OnAsyncTaskFinish onFinish;

	public ShowThumbInImageView(Context context, File file, ImageView imageView){
		this.context = context;
		this.file = file;
		this.imageView = imageView;
	}

	public ShowThumbInImageView setThumbSize(int size){
		thumbSize = size;
		return this;
	}

	public ShowThumbInImageView setIsVideo(boolean isVideo){
		this.isVideo = isVideo;
		return this;
	}

	public ShowThumbInImageView setOnFinish(OnAsyncTaskFinish onFinish){
		this.onFinish = onFinish;
		return this;
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		if (!file.exists()) {
			return null;
		}
		if(thumbSize == null){
			thumbSize = Helpers.getThumbSize(context);
		}

		if(!isVideo) {
			try {
				InputStream in = new FileInputStream(file.getAbsolutePath());
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				int numRead = 0;
				byte[] buf = new byte[1024];
				while ((numRead = in.read(buf)) >= 0) {
					bytes.write(buf, 0, numRead);
				}
				in.close();

				Bitmap bitmap = Helpers.getThumbFromBitmap(Helpers.decodeBitmap(bytes.toByteArray(), thumbSize), thumbSize);

				if (bitmap != null) {
					return bitmap;
				}

			} catch (IOException ignored) {
			}
		}
		else {
			Bitmap thumb = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
			return Helpers.getThumbFromBitmap(thumb, thumbSize);
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
