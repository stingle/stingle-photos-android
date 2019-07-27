package com.fenritz.safecam.Sync;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

public class FileManager {

	public static byte[] getAndCacheThumb(Context context, String filename) throws IOException {

		File cacheDir = new File(context.getCacheDir().getPath() + "/thumbCache");
		File cachedFile = new File(context.getCacheDir().getPath() + "/thumbCache/" + filename);

		if(cachedFile.exists()){
			FileInputStream in = new FileInputStream(cachedFile);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];

			int numRead;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}
			in.close();
			return out.toByteArray();
		}


		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", filename);
		postParams.put("thumb", "1");

		byte[] encFile = new byte[0];

		try {
			encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);
		}
		catch (NoSuchAlgorithmException | KeyManagementException e) {

		}

		if(encFile == null || encFile.length == 0){
			return null;
		}


		if(!cacheDir.exists()){
			cacheDir.mkdirs();
		}


		FileOutputStream out = new FileOutputStream(cachedFile);
		out.write(encFile);
		out.close();

		return encFile;
	}

	public static class ImportFilesAsyncTask extends AsyncTask<Void, Integer, Void> {

		protected Context context;
		protected ArrayList<Uri> uris;
		protected OnFinish onFinish;
		protected ProgressDialog progress;

		public ImportFilesAsyncTask(Context context, ArrayList<Uri> uris, OnFinish onFinish){
			this.context = context;
			this.uris = uris;
			this.onFinish = onFinish;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			progress = Helpers.showProgressDialogWithBar(context, context.getString(R.string.importing_files), uris.size(), null);
		}

		@Override
		protected Void doInBackground(Void... params) {
			int index = 0;
			for (Uri uri : uris) {
				try {
					int fileType = Helpers.getFileType(context, uri);
					InputStream in = context.getContentResolver().openInputStream(uri);

					Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
					/*
					 * Get the column indexes of the data in the Cursor,
					 * move to the first row in the Cursor, get the data,
					 * and display it.
					 */
					int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
					returnCursor.moveToFirst();
					String filename = returnCursor.getString(nameIndex);
					long fileSize = returnCursor.getLong(sizeIndex);


					String encFilename = Helpers.getNewEncFilename();
					String encFilePath = Helpers.getHomeDir(context) + "/" + encFilename;

					FileOutputStream outputStream = new FileOutputStream(encFilePath);

					byte[] fileId = SafeCameraApplication.getCrypto().encryptFile(in, outputStream, filename, fileType, fileSize);

					if(fileType == Crypto.FILE_TYPE_PHOTO) {

						InputStream thumbIn = context.getContentResolver().openInputStream(uri);
						ByteArrayOutputStream bytes = new ByteArrayOutputStream();
						int numRead = 0;
						byte[] buf = new byte[1024];
						while ((numRead = thumbIn.read(buf)) >= 0) {
							bytes.write(buf, 0, numRead);
						}
						thumbIn.close();

						//System.gc();

						Helpers.generateThumbnail(context, bytes.toByteArray(), encFilename, fileId, Crypto.FILE_TYPE_PHOTO);
					}
					else if(fileType == Crypto.FILE_TYPE_VIDEO){

						/*String[] filePathColumn = {MediaStore.Images.Media.DATA};
						Cursor cursor = context.getContentResolver().query(uri, filePathColumn, null, null, null);
						cursor.moveToFirst();
						int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
						String picturePath = cursor.getString(columnIndex);
						cursor.close();

						Bitmap thumb = ThumbnailUtils.createVideoThumbnail(picturePath, MediaStore.Video.Thumbnails.MINI_KIND);
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						thumb.compress(Bitmap.CompressFormat.PNG, 0, bos);*/
						Bitmap thumb = getVideoThumbnail(context, uri);
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						thumb.compress(Bitmap.CompressFormat.PNG, 100, bos);

						Helpers.generateThumbnail(context, bos.toByteArray(), encFilename, fileId, Crypto.FILE_TYPE_VIDEO);
					}

					long nowDate = System.currentTimeMillis();
					StingleDbHelper db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);
					db.insertFile(encFilename, true, false, StingleDbHelper.INITIAL_VERSION, nowDate, nowDate);
					db.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				catch (CryptoException e) {
					e.printStackTrace();
				}
				publishProgress(index+1);
				index++;
			}

			return null;
		}

		private Bitmap getVideoThumbnail(Context context, Uri uri) throws IllegalArgumentException,
				SecurityException{
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			retriever.setDataSource(context,uri);
			return retriever.getFrameAtTime();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			progress.setProgress(values[0]);

		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progress.dismiss();

			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}

	public static abstract class OnFinish{
		public abstract void onFinish();
	}
}
