package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.widget.ImageView;

import org.stingle.photos.Util.Helpers;

public class ShowThumbInImageView extends AsyncTask<Void, Void, Bitmap> {

	private Context context;
	private  Uri uri;
	private ImageView imageView;
	private OnAsyncTaskFinish onFinish;
	private boolean isVideo = false;

	public ShowThumbInImageView(Context context, Uri uri, ImageView imageView, boolean isVideo){
		this.context = context;
		this.uri = uri;
		this.imageView = imageView;
		this.isVideo = isVideo;
	}

	public ShowThumbInImageView setOnFinish(OnAsyncTaskFinish onFinish){
		this.onFinish = onFinish;
		return this;
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		if(uri == null){
			return null;
		}
		try {
			if(isVideo){
				return Helpers.getVideoThumbnail(context, uri);
			}
			else {
				Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(
						context.getContentResolver(), Long.parseLong(uri.getPathSegments().get(uri.getPathSegments().size() - 1)),
						MediaStore.Images.Thumbnails.MINI_KIND,
						null);

				if (bitmap != null) {
					int thumbSize = Helpers.getThumbSize(context);
					return Helpers.getThumbFromBitmap(bitmap, thumbSize);
				}
			}

		} catch (Exception e) {
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
