package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.constraintlayout.solver.widgets.Helper;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Db.StingleDbHelper;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;

public class ImportFilesAsyncTask extends AsyncTask<Void, Integer, Void> {

	protected Context context;
	protected ArrayList<Uri> uris;
	protected FileManager.OnFinish onFinish;
	protected ProgressDialog progress;

	public ImportFilesAsyncTask(Context context, ArrayList<Uri> uris, FileManager.OnFinish onFinish){
		this.context = context;
		this.uris = uris;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();

		progress = Helpers.showProgressDialogWithBar(context, context.getString(R.string.importing_files), uris.size(), new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialogInterface) {
				Helpers.releaseWakeLock((Activity)context);
				ImportFilesAsyncTask.this.cancel(false);
			}
		});
		Helpers.acquireWakeLock((Activity)context);
	}

	@Override
	protected Void doInBackground(Void... params) {
		int index = 0;
		for (Uri uri : uris) {
			try {
				int fileType = FileManager.getFileTypeFromUri(context, uri);
				InputStream in = context.getContentResolver().openInputStream(uri);

				Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
				String filename = null;
				long fileSize = 0;
				if(returnCursor != null) {
					int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
					returnCursor.moveToFirst();
					filename = returnCursor.getString(nameIndex);
					fileSize = returnCursor.getLong(sizeIndex);
				}
				else{
					File fileToImport = new File(uri.getPath());
					if (fileToImport.exists()) {
						filename = fileToImport.getName();
						fileSize = fileToImport.length();
					}
				}

				if (filename == null) {
					int column_index = returnCursor.getColumnIndex(MediaStore.Images.Media.DATA);
					Uri filePathUri = Uri.parse(returnCursor.getString(column_index));
					filename = filePathUri.getLastPathSegment().toString();
				}


				String encFilename = Helpers.getNewEncFilename();
				String encFilePath = FileManager.getHomeDir(context) + "/" + encFilename;

				int videoDuration = 0;
				if(fileType == Crypto.FILE_TYPE_VIDEO) {
					videoDuration = FileManager.getVideoDurationFromUri(context, uri);
				}

				byte[] fileId = StinglePhotosApplication.getCrypto().getNewFileId();

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

					Helpers.generateThumbnail(context, bytes.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_PHOTO, videoDuration);
				}
				else if(fileType == Crypto.FILE_TYPE_VIDEO){

					/*String[] filePathColumn = {MediaStore.Images.Media.DATA};
					Cursor cursor = activity.getContentResolver().query(uri, filePathColumn, null, null, null);
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

					Helpers.generateThumbnail(context, bos.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_VIDEO, videoDuration);
				}

				FileOutputStream outputStream = new FileOutputStream(encFilePath);
				StinglePhotosApplication.getCrypto().encryptFile(in, outputStream, filename, fileType, fileSize, fileId, videoDuration, null, this);
				long nowDate = System.currentTimeMillis();
				StingleDbHelper db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);

				String headers = Crypto.getFileHeaders(encFilePath, FileManager.getThumbsDir(context) + "/" + encFilename);

				db.insertFile(encFilename, true, false, StingleDbHelper.INITIAL_VERSION, nowDate, nowDate, headers);
				db.close();
			} catch (IOException | CryptoException e) {
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
		Helpers.releaseWakeLock((Activity)context);
	}
}
