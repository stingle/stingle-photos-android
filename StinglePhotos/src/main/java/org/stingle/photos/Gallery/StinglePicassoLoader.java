package org.stingle.photos.Gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.StingleDbFile;
import org.stingle.photos.Db.StingleDbHelper;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


public class StinglePicassoLoader extends RequestHandler {

	private Context context;
	private StingleDbHelper db;
	private int thumbSize;

	public StinglePicassoLoader(Context context, StingleDbHelper db, int thumbSize){
		this.context = context;
		this.db = db;
		this.thumbSize = thumbSize;
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

		int folder = SyncManager.FOLDER_MAIN;
		if(folderStr.equals("t")){
			folder = SyncManager.FOLDER_TRASH;
		}

		StingleDbFile file = db.getFileAtPosition(Integer.parseInt(position));

		if(file.isLocal) {
			try {
				File fileToDec = new File(FileManager.getThumbsDir(context) +"/"+ file.filename);
				FileInputStream input = new FileInputStream(fileToDec);
				byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(input);

				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

					FileInputStream streamForHeader = new FileInputStream(fileToDec);
					Crypto.Header header = StinglePhotosApplication.getCrypto().getFileHeader(streamForHeader);
					streamForHeader.close();

					Result result = new Result(bitmap, Picasso.LoadedFrom.DISK);
					result.addProperty("fileType", header.fileType);
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
		else if(!file.isLocal && file.isRemote){

			try {
				byte[] encFile = FileManager.getAndCacheThumb(context, file.filename, folder);

				if(encFile == null || encFile.length == 0){
					callback.onSuccess(new Result(BitmapFactory.decodeResource(context.getResources(), R.drawable.file), Picasso.LoadedFrom.NETWORK));
				}

				byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(encFile);

				if (decryptedData != null) {

					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);
					Crypto.Header header = StinglePhotosApplication.getCrypto().getFileHeader(encFile);

					if(bitmap == null) {
						bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.file);
					}

					Result result = new Result(bitmap, Picasso.LoadedFrom.NETWORK);
					result.addProperty("fileType", header.fileType);
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
