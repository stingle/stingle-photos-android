package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


public class StinglePicassoLoader extends RequestHandler {

	private Context context;
	private FilesDb db;
	private int thumbSize;
	private Crypto crypto;
	private String folderId = null;

	public StinglePicassoLoader(Context context, FilesDb db, int thumbSize, String folderId){
		this.context = context;
		this.db = db;
		this.thumbSize = thumbSize;
		this.folderId = folderId;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	public boolean canHandleRequest(com.squareup.picasso3.Request data) {
		if(data.uri.toString().startsWith("p")){
			return true;
		}
		return false;
	}

	@Override
	public void load(@NonNull Picasso picasso, @NonNull com.squareup.picasso3.Request request, @NonNull Callback callback) throws IOException {
		/*try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			//Log.e("interrupted", String.valueOf(position));
		}*/
		String uri = request.uri.toString();
		String folderStr = uri.substring(1, 2);
		String position = uri.substring(2);

		int sort = StingleDb.SORT_DESC;
		int folder = SyncManager.FOLDER_MAIN;
		if(folderStr.equals("t")){
			folder = SyncManager.FOLDER_TRASH;
		}
		else if(folderStr.equals("a")){
			folder = SyncManager.FOLDER_ALBUM;
			sort = StingleDb.SORT_ASC;
		}

		StingleFile file = db.getFileAtPosition(Integer.parseInt(position), folderId, sort);

		if(file.isLocal) {
			try {
				File fileToDec = new File(FileManager.getThumbsDir(context) +"/"+ file.filename);
				FileInputStream input = new FileInputStream(fileToDec);

				byte[] decryptedData = CryptoHelpers.decryptDbFile(context, folder, folderId, file.headers, true, input);

				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

					FileInputStream streamForHeader = new FileInputStream(fileToDec);
					Crypto.Header header = crypto.getFileHeader(streamForHeader);
					streamForHeader.close();

					Result result = new Result(bitmap, Picasso.LoadedFrom.DISK);

					GalleryAdapterPisasso.FileProps props = new GalleryAdapterPisasso.FileProps();

					props.fileType = header.fileType;
					props.videoDuration = header.videoDuration;
					props.isUploaded = file.isRemote;

					result.addProperty("fileProps", props);

					callback.onSuccess(result);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (CryptoException e) {
				e.printStackTrace();
			}

		}
		else if(file.isRemote){

			try {
				byte[] encFile = FileManager.getAndCacheThumb(context, file.filename, folder);

				if(encFile == null || encFile.length == 0){
					callback.onSuccess(new Result(BitmapFactory.decodeResource(context.getResources(), R.drawable.file), Picasso.LoadedFrom.NETWORK));
				}

				byte[] decryptedData = CryptoHelpers.decryptDbFile(context, folder, folderId, file.headers, true, encFile);

				if (decryptedData != null) {

					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);
					Crypto.Header header = crypto.getFileHeader(encFile);

					if(bitmap == null) {
						bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.file);
					}

					Result result = new Result(bitmap, Picasso.LoadedFrom.NETWORK);

					GalleryAdapterPisasso.FileProps props = new GalleryAdapterPisasso.FileProps();

					props.fileType = header.fileType;
					props.videoDuration = header.videoDuration;
					props.isUploaded = file.isRemote;

					result.addProperty("fileProps", props);
					
					callback.onSuccess(result);
				}
				else{
					callback.onSuccess(new Result(BitmapFactory.decodeResource(context.getResources(), R.drawable.file), Picasso.LoadedFrom.NETWORK));
				}
			} catch (IOException | CryptoException e) {
				callback.onError(e);
				e.printStackTrace();
			}
		}
	}
}
