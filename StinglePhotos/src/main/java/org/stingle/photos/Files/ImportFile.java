package org.stingle.photos.Files;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImportFile {

	public static boolean importFile(Context context, Uri uri, int set, String albumId, AsyncTask<?,?,?> task) {
		return importFile(context, uri, set, albumId, null, task);
	}

	public static boolean importFile(Context context, Uri uri, int set, String albumId, Long date, AsyncTask<?,?,?> task) {
		try {
			int fileType = FileManager.getFileTypeFromUri(context, uri);

			Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
			String filename = null;
			long fileSize = 0;
			if (returnCursor != null) {
				try {
					int nameIndex = returnCursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
					int sizeIndex = returnCursor.getColumnIndexOrThrow(OpenableColumns.SIZE);
					returnCursor.moveToFirst();
					filename = returnCursor.getString(nameIndex);
					fileSize = returnCursor.getLong(sizeIndex);

					if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
						try {
							int pendingIndex = returnCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING);
							String isPending = returnCursor.getString(pendingIndex);
							Log.e("isPending", isPending);
							if (!isPending.equals("0")) {
								return false;
							}
						}
						catch (Exception ignored){}
					}

				}
				catch (Exception e){
					return false;
				}
			} else {
				String path = uri.getPath();
				if(path != null) {
					File fileToImport = new File(path);
					if (fileToImport.exists()) {
						filename = fileToImport.getName();
						fileSize = fileToImport.length();
					}
				}
			}

			if (filename == null && returnCursor != null) {
				int column_index = returnCursor.getColumnIndex(MediaStore.Images.Media.DATA);
				Uri filePathUri = Uri.parse(returnCursor.getString(column_index));
				String lastSegment = filePathUri.getLastPathSegment();
				if(lastSegment != null) {
					filename = lastSegment.toString();
				}
			}

			if(returnCursor != null) {
				returnCursor.close();
			}


			String encFilename = Helpers.getNewEncFilename();
			String encFilePath = FileManager.getHomeDir(context) + "/" + encFilename;

			int videoDuration = 0;
			if (fileType == Crypto.FILE_TYPE_VIDEO) {
				videoDuration = FileManager.getVideoDurationFromUri(context, uri);
			}

			byte[] fileId = StinglePhotosApplication.getCrypto().getNewFileId();
			Crypto.Header thumbHeader = null;


			if (fileType == Crypto.FILE_TYPE_PHOTO) {

				InputStream thumbIn = context.getContentResolver().openInputStream(uri);
				if(thumbIn == null){
					return false;
				}
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				int numRead = 0;
				byte[] buf = new byte[1024];
				while ((numRead = thumbIn.read(buf)) >= 0) {
					bytes.write(buf, 0, numRead);
				}
				thumbIn.close();

				thumbHeader = Helpers.generateThumbnail(context, bytes.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_PHOTO, videoDuration);
			} else if (fileType == Crypto.FILE_TYPE_VIDEO) {
				Bitmap thumb = Helpers.getVideoThumbnail(context, uri);
				if (thumb != null) {
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					thumb.compress(Bitmap.CompressFormat.PNG, 100, bos);

					thumbHeader = Helpers.generateThumbnail(context, bos.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_VIDEO, videoDuration);
				}
			}

			InputStream in = context.getContentResolver().openInputStream(uri);
			FileOutputStream outputStream = new FileOutputStream(encFilePath);
			Crypto.Header fileHeader =  StinglePhotosApplication.getCrypto().encryptFile(in, outputStream, filename, fileType, fileSize, fileId, videoDuration, null, task);

			if(fileHeader == null || thumbHeader == null){
				return false;
			}

			in.close();
			outputStream.close();

			long nowDate = System.currentTimeMillis();
			if(date == null) {
				date = nowDate;

				SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
				if(settings.getBoolean("preserve_import_dates", false)){
					long dateTaken = 0;
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
						dateTaken = FileManager.queryForDateTaken(context, uri);
					}

					if(dateTaken == 0){
						dateTaken = FileManager.getDateTakenFromUriMetadata(context, uri, fileType);
					}

					if(dateTaken > 0){
						date = dateTaken;
					}
				}
			}

			String headers = Crypto.getFileHeadersFromFile(encFilePath, FileManager.getThumbsDir(context) + "/" + encFilename);

			if (set == SyncManager.GALLERY) {
				GalleryTrashDb db = new GalleryTrashDb(context, SyncManager.GALLERY);
				db.insertFile(encFilename, true, false, GalleryTrashDb.INITIAL_VERSION, date, nowDate, headers);
				db.close();
			} else if (set == SyncManager.ALBUM) {
				Crypto crypto = StinglePhotosApplication.getCrypto();
				AlbumsDb albumsDb = new AlbumsDb(context);
				AlbumFilesDb albumFilesDb = new AlbumFilesDb(context);
				StingleDbAlbum album = albumsDb.getAlbumById(albumId);
				if (album != null) {
					String newHeaders = crypto.encryptFileHeaders(fileHeader, thumbHeader, Crypto.base64ToByteArray(album.publicKey));
					albumFilesDb.insertAlbumFile(album.albumId, encFilename, true, false, GalleryTrashDb.INITIAL_VERSION, newHeaders, date, nowDate);
					album.dateModified = System.currentTimeMillis();
					albumsDb.updateAlbum(album);
				}
				else{
					albumsDb.close();
					albumFilesDb.close();
					return false;
				}
				albumsDb.close();
				albumFilesDb.close();
			}
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

}
