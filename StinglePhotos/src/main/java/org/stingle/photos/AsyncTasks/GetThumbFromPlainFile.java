package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.widget.ImageView;

import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class GetThumbFromPlainFile extends AsyncTask<Void, Void, Bitmap> {

	private Context context;
	private File file;
	private Integer thumbSize = null;
	private boolean isVideo = false;
	private OnAsyncTaskFinish onFinish;

	public GetThumbFromPlainFile(Context context, File file){
		this.context = context;
		this.file = file;
	}

	public GetThumbFromPlainFile setThumbSize(int size){
		thumbSize = size;
		return this;
	}

	public GetThumbFromPlainFile setIsVideo(boolean isVideo){
		this.isVideo = isVideo;
		return this;
	}

	public GetThumbFromPlainFile setOnFinish(OnAsyncTaskFinish onFinish){
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
			if(onFinish != null){
				onFinish.onFinish(bitmap);
			}
		}
	}
}
