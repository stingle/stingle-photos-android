package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class StinglePicassoLoader extends RequestHandler {

	private Context context;
	private FilesDb db;
	private int thumbSize;
	private Crypto crypto;
	private String albumId = null;

	public StinglePicassoLoader(Context context, FilesDb db, int thumbSize, String albumId) {
		this.context = context;
		this.db = db;
		this.thumbSize = thumbSize;
		this.albumId = albumId;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	public boolean canHandleRequest(com.squareup.picasso3.Request data) {
		if (data.uri.toString().startsWith("p")) {
			return true;
		}
		return false;
	}

	@Override
	public void load(@NonNull Picasso picasso, @NonNull com.squareup.picasso3.Request request, @NonNull Callback callback) throws IOException {
		String uri = request.uri.toString();
		String setStr = uri.substring(1, 2);
		String position = uri.substring(2);

		int sort = StingleDb.SORT_DESC;
		int set = SyncManager.GALLERY;
		if (setStr.equals("t")) {
			set = SyncManager.TRASH;
		} else if (setStr.equals("a")) {
			set = SyncManager.ALBUM;
			//sort = StingleDb.SORT_ASC;
		}

		StingleDbFile file = db.getFileAtPosition(Integer.parseInt(position), albumId, sort);

		File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + file.filename);
		if (file.isLocal && fileToDec.exists()) {
			try {
				FileInputStream input = new FileInputStream(fileToDec);

				byte[] decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, file.headers, true, input);

				if (decryptedData.length > 0) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

					Crypto.Header header = CryptoHelpers.decryptFileHeaders(context, set, albumId, file.headers, true);

					Result result = new Result(bitmap, Picasso.LoadedFrom.DISK);

					GalleryAdapterPisasso.FileProps props = new GalleryAdapterPisasso.FileProps();

					props.fileType = header.fileType;
					props.videoDuration = header.videoDuration;
					props.isUploaded = file.isRemote;

					result.addProperty("fileProps", props);

					callback.onSuccess(result);
				}
			} catch (IOException | CryptoException e) {
				e.printStackTrace();
				returnNoImage(callback);
			}

		} else if (file.isRemote) {

			try {
				byte[] encFile = FileManager.getAndCacheThumb(context, file.filename, set);

				if (encFile == null || encFile.length == 0) {
					callback.onSuccess(new Result(BitmapFactory.decodeResource(context.getResources(), R.drawable.file), Picasso.LoadedFrom.NETWORK));
				}

				byte[] decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, file.headers, true, encFile);

				if (decryptedData.length > 0) {

					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);
					Crypto.Header header = CryptoHelpers.decryptFileHeaders(context, set, albumId, file.headers, true);

					if (bitmap == null) {
						bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.file);
					}

					Result result = new Result(bitmap, Picasso.LoadedFrom.NETWORK);

					GalleryAdapterPisasso.FileProps props = new GalleryAdapterPisasso.FileProps();

					props.fileType = header.fileType;
					props.videoDuration = header.videoDuration;
					props.isUploaded = file.isRemote;

					result.addProperty("fileProps", props);

					callback.onSuccess(result);
				} else {
					callback.onSuccess(new Result(BitmapFactory.decodeResource(context.getResources(), R.drawable.file), Picasso.LoadedFrom.NETWORK));
				}
			} catch (IOException | CryptoException e) {
				returnNoImage(callback);
				e.printStackTrace();
			}
		}
	}

	private void returnNoImage(Callback callback) {
		Drawable drawable = context.getDrawable(R.drawable.ic_no_image);
		if (drawable == null) {
			return;
		}

		Result result = new Result(drawable, Picasso.LoadedFrom.DISK);
		callback.onSuccess(result);
	}
}
