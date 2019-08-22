package com.fenritz.safecam.Gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Files.FileManager;
import com.fenritz.safecam.Sync.SyncManager;
import com.fenritz.safecam.Util.Helpers;
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

		StingleDbFile file = db.getFileAtPosition(Integer.parseInt(position));
		if(file.isLocal) {
			try {
				File fileToDec = new File(FileManager.getThumbsDir(context) +"/"+ file.filename);
				FileInputStream input = new FileInputStream(fileToDec);
				byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(input);

				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

					FileInputStream streamForHeader = new FileInputStream(fileToDec);
					Crypto.Header header = SafeCameraApplication.getCrypto().getFileHeader(streamForHeader);
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


			int folder = SyncManager.FOLDER_MAIN;
			if(folderStr.equals("t")){
				folder = SyncManager.FOLDER_TRASH;
			}

			try {
				//byte[] encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);
				byte[] encFile = FileManager.getAndCacheThumb(context, file.filename, folder);

				if(encFile == null || encFile.length == 0){
					callback.onSuccess(new Result(BitmapFactory.decodeResource(context.getResources(), R.drawable.file), Picasso.LoadedFrom.NETWORK));
				}

				//Log.d("encFile", new String(encFile, "UTF-8"));

					/*FileOutputStream tmp = new FileOutputStream(Helpers.getHomeDir(context) + "/qaq.sp");
					tmp.write(encFile);
					tmp.close();*/

				byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(encFile);

				if (decryptedData != null) {

					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);
					Crypto.Header header = SafeCameraApplication.getCrypto().getFileHeader(encFile);

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
			} catch (IOException e) {
				e.printStackTrace();
			} catch (CryptoException e) {
				e.printStackTrace();
			}
		}
	}
}
