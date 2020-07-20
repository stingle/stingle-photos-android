package org.stingle.photos.Files;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

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
		try {
			int fileType = FileManager.getFileTypeFromUri(context, uri);
			InputStream in = context.getContentResolver().openInputStream(uri);

			Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
			String filename = null;
			long fileSize = 0;
			if (returnCursor != null) {
				int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
				returnCursor.moveToFirst();
				filename = returnCursor.getString(nameIndex);
				fileSize = returnCursor.getLong(sizeIndex);
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

				Helpers.generateThumbnail(context, bytes.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_PHOTO, videoDuration);
			} else if (fileType == Crypto.FILE_TYPE_VIDEO) {
				Bitmap thumb = getVideoThumbnail(context, uri);
				if (thumb != null) {
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					thumb.compress(Bitmap.CompressFormat.PNG, 100, bos);

					Helpers.generateThumbnail(context, bos.toByteArray(), encFilename, filename, fileId, Crypto.FILE_TYPE_VIDEO, videoDuration);
				}
			}

			FileOutputStream outputStream = new FileOutputStream(encFilePath);
			StinglePhotosApplication.getCrypto().encryptFile(in, outputStream, filename, fileType, fileSize, fileId, videoDuration, null, task);
			long nowDate = System.currentTimeMillis();
			String headers = Crypto.getFileHeadersFromFile(encFilePath, FileManager.getThumbsDir(context) + "/" + encFilename);

			if (set == SyncManager.GALLERY) {
				GalleryTrashDb db = new GalleryTrashDb(context, SyncManager.GALLERY);
				db.insertFile(encFilename, true, false, GalleryTrashDb.INITIAL_VERSION, nowDate, nowDate, headers);
				db.close();
			} else if (set == SyncManager.ALBUM) {
				Crypto crypto = StinglePhotosApplication.getCrypto();
				AlbumsDb albumsDb = new AlbumsDb(context);
				AlbumFilesDb albumFilesDb = new AlbumFilesDb(context);
				StingleDbAlbum album = albumsDb.getAlbumById(albumId);
				if (album != null) {
					String newHeaders = crypto.reencryptFileHeaders(headers, Crypto.base64ToByteArray(album.publicKey), null, null);
					albumFilesDb.insertAlbumFile(album.albumId, encFilename, true, false, GalleryTrashDb.INITIAL_VERSION, newHeaders, nowDate, nowDate);
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

	private static Bitmap getVideoThumbnail(Context context, Uri uri) throws IllegalArgumentException,
			SecurityException{
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();

		if(uri == null){
			return null;
		}

		retriever.setDataSource(context,uri);
		return retriever.getFrameAtTime();
	}
}