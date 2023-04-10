package org.stingle.photos.Sync.SyncSteps;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AutoCloseableCursor;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.IOException;

public class FSSync {
	public static boolean sync(Context context){
		if(!LoginManager.isLoggedIn(context)) {
			return false;
		}
		boolean isFirstSyncDone = Helpers.getPreference(context, SyncManager.PREF_FIRST_SYNC_DONE, false);
		if(isFirstSyncDone){
			return false;
		};
		Log.d("fsSync", "START");

		boolean needToUpdateUI = false;

		GalleryTrashDb galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);
		GalleryTrashDb trashDb = new GalleryTrashDb(context, SyncManager.TRASH);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(context);


		File dir = new File(FileManager.getHomeDir(context));

		File[] currentFSFiles = dir.listFiles();

		if(currentFSFiles != null) {
			for (File file : currentFSFiles) {
				boolean isOurFile = file.isFile() && file.getName().endsWith(StinglePhotosApplication.FILE_EXTENSION);
				boolean existsInGallery = galleryDb.getFileIfExists(file.getName()) != null;
				boolean existsInTrash = trashDb.getFileIfExists(file.getName()) != null;
				boolean existsInAlbums = albumFilesDb.getFileIfExists(file.getName()) != null;

				if (isOurFile && !existsInGallery && !existsInTrash && !existsInAlbums) {
					try {
						String headers = Crypto.getFileHeadersFromFile(file.getAbsolutePath(), FileManager.getThumbsDir(context) + "/" + file.getName());
						galleryDb.insertFile(file.getName(), true, false, GalleryTrashDb.INITIAL_VERSION, file.lastModified(), System.currentTimeMillis(), headers);
						Log.d("fsSync-add", file.getName());
						needToUpdateUI = true;
					} catch (IOException | CryptoException e) {
						e.printStackTrace();
					}

				}
			}
		}

		try(AutoCloseableCursor autoCloseableCursor = galleryDb.getFilesList(GalleryTrashDb.GET_MODE_LOCAL, StingleDb.SORT_ASC, null, null)) {
			Cursor result = autoCloseableCursor.getCursor();
			while (result.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(result);
				File file = new File(FileManager.getHomeDir(context) + "/" + dbFile.filename);
				File thumb = new File(FileManager.getThumbsDir(context) + "/" + dbFile.filename);
				if (!file.exists() || !thumb.exists()) {
					dbFile.isLocal = false;
					galleryDb.updateFile(dbFile);
				}
			}
			result.close();
		}

		try(AutoCloseableCursor autoCloseableCursor = albumFilesDb.getFilesList(GalleryTrashDb.GET_MODE_LOCAL, StingleDb.SORT_ASC, null, null)) {
			Cursor albumsResult = autoCloseableCursor.getCursor();
			while (albumsResult.moveToNext()) {
				StingleDbFile dbFile = new StingleDbFile(albumsResult);
				File file = new File(FileManager.getHomeDir(context) + "/" + dbFile.filename);
				File thumb = new File(FileManager.getThumbsDir(context) + "/" + dbFile.filename);
				if (!file.exists() || !thumb.exists()) {
					dbFile.isLocal = false;
					albumFilesDb.updateFile(dbFile);
				}
			}
			albumsResult.close();
		}


		galleryDb.close();
		trashDb.close();
		albumFilesDb.close();

		Helpers.storePreference(context, SyncManager.PREF_FIRST_SYNC_DONE, true);

		return needToUpdateUI;
	}
}
