package org.stingle.photos.AsyncTasks;

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

import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class ImportFilesAsyncTask extends AsyncTask<Void, Integer, Void> {

	private WeakReference<AppCompatActivity> activity;
	private ArrayList<Uri> uris;
	private int set;
	private String albumId;
	private FileManager.OnFinish onFinish;
	private ProgressDialog progress;


	public ImportFilesAsyncTask(AppCompatActivity activity, ArrayList<Uri> uris, int set, String albumId, FileManager.OnFinish onFinish){
		this.activity = new WeakReference<>(activity);
		this.uris = uris;
		this.set = set;
		this.albumId = albumId;
		this.onFinish = onFinish;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return;
		}
		progress = Helpers.showProgressDialogWithBar(myActivity, myActivity.getString(R.string.importing_files), uris.size(), new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialogInterface) {
				Helpers.releaseWakeLock(myActivity);
				ImportFilesAsyncTask.this.cancel(false);
				onFinish.onFinish();
			}
		});
		Helpers.acquireWakeLock(myActivity);
	}

	@Override
	protected Void doInBackground(Void... params) {
		AppCompatActivity myActivity = activity.get();
		if(myActivity == null){
			return null;
		}
		int index = 0;
		for (Uri uri : uris) {
			try {
				int fileType = FileManager.getFileTypeFromUri(myActivity, uri);
				InputStream in = myActivity.getContentResolver().openInputStream(uri);

				Cursor returnCursor = myActivity.getContentResolver().query(uri, null, null, null, null);
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
				String encFilePath = FileManager.getHomeDir(myActivity) + "/" + encFilename;

				int videoDuration = 0;
				if(fileType == Crypto.FILE_TYPE_VIDEO) {
					videoDuration = FileManager.getVideoDurationFromUri(myActivity, uri);
				}

				byte[] fileId = StinglePhotosApplication.getCrypto().getNewFileId();

				if(fileType == Crypto.FILE_TYPE_PHOTO) {

					InputStream thumbIn = myActivity.getContentResolver().openInputStream(uri);
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					int numRead = 0;
					byte[] buf = new byte[1024];
					while ((numRead = thumbIn.read(buf)) >= 0) {
						bytes.write(buf, 0, numRead);
					}
					thumbIn.close();

					Helpers.generateThumbnail(myActivity, bytes.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_PHOTO, videoDuration);
				}
				else if(fileType == Crypto.FILE_TYPE_VIDEO){
					Bitmap thumb = getVideoThumbnail(myActivity, uri);
					if(thumb != null) {
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						thumb.compress(Bitmap.CompressFormat.PNG, 100, bos);

						Helpers.generateThumbnail(myActivity, bos.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_VIDEO, videoDuration);
					}
				}

				FileOutputStream outputStream = new FileOutputStream(encFilePath);
				StinglePhotosApplication.getCrypto().encryptFile(in, outputStream, filename, fileType, fileSize, fileId, videoDuration, null, this);
				long nowDate = System.currentTimeMillis();
				String headers = Crypto.getFileHeadersFromFile(encFilePath, FileManager.getThumbsDir(myActivity) + "/" + encFilename);

				if(set == SyncManager.GALLERY) {
					GalleryTrashDb db = new GalleryTrashDb(myActivity, SyncManager.GALLERY);
					db.insertFile(encFilename, true, false, GalleryTrashDb.INITIAL_VERSION, nowDate, nowDate, headers);
					db.close();
				}
				else if(set == SyncManager.ALBUM){
					Crypto crypto = StinglePhotosApplication.getCrypto();
					AlbumsDb albumsDb = new AlbumsDb(myActivity);
					AlbumFilesDb albumFilesDb = new AlbumFilesDb(myActivity);
					StingleDbAlbum album = albumsDb.getAlbumById(albumId);
					if(album != null) {
						String newHeaders = crypto.reencryptFileHeaders(headers, Crypto.base64ToByteArray(album.albumPK), null, null);
						albumFilesDb.insertAlbumFile(album.albumId, encFilename, true, false, GalleryTrashDb.INITIAL_VERSION, newHeaders, nowDate, nowDate);
					}
					albumsDb.close();
					albumFilesDb.close();
				}
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

		if(uri == null){
			return null;
		}

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
		AppCompatActivity myActivity = activity.get();
		if(myActivity != null){
			Helpers.releaseWakeLock(myActivity);
		}

	}
}
