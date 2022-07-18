package org.stingle.photos.AsyncTasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ProgressBar;

import androidx.exifinterface.media.ExifInterface;

import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.util.ImageSaver;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ImportFile;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.UUID;

public class SaveEditedImageAsyncTask extends AsyncTask<Void, Void, Void> {
	private int itemPosition;
	private int set;
	private String albumId;
	private Image image;
	private boolean replace;
	private WeakReference<Runnable> callbackWeakReference;
	private WeakReference<Context> contextWeakReference;

	private ProgressDialog progressDialog;

	public SaveEditedImageAsyncTask(int itemPosition, int set, String albumId, Image image, boolean replace, Context context, Runnable callback) {
		this.itemPosition = itemPosition;
		this.set = set;
		this.albumId = albumId;
		this.image = image;
		this.replace = replace;
		this.contextWeakReference = new WeakReference<>(context);
		this.callbackWeakReference = new WeakReference<>(callback);
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();

		Context context = contextWeakReference.get();
		if (context != null) {
			progressDialog = new ProgressDialog(context);
			progressDialog.setMessage(context.getString(R.string.saving_image));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.setCancelable(false);
			progressDialog.show();
		}
	}

	@Override
	protected Void doInBackground(Void... voids) {
		// 1. Create new file
		// 2. Delete old file from current set

		Bitmap savedImage = ImageSaver.saveImage(image);

		Context context = contextWeakReference.get();
		if (context != null) {
			GalleryTrashDb galleryDb = new GalleryTrashDb(context, SyncManager.GALLERY);
			AlbumFilesDb albumFilesDb = new AlbumFilesDb(context);
			GalleryTrashDb trashDb = new GalleryTrashDb(context, SyncManager.TRASH);

			StingleDbFile file;

			if (set == SyncManager.GALLERY) {
				file = galleryDb.getFileAtPosition(itemPosition, albumId, StingleDb.SORT_DESC);
			} else if (set == SyncManager.ALBUM) {
				file = albumFilesDb.getFileAtPosition(itemPosition, albumId, StingleDb.SORT_DESC);
			} else {
				file = trashDb.getFileAtPosition(itemPosition, albumId, StingleDb.SORT_DESC);
			}

			if (file != null) {
				// Creating new file
				File tmpFile = new File(FileManager.getTmpDir(context), UUID.randomUUID().toString());

				try (FileOutputStream outputStream = new FileOutputStream(tmpFile)) {
					savedImage.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
				} catch (IOException e) {
					e.printStackTrace();
				}

				// TODO: Save exif info

				if (!fileExistsInOtherSets(file, galleryDb, albumFilesDb, trashDb)) {
					if (replace) {
						ImportFile.replaceFile(context, file, Uri.fromFile(tmpFile), set, albumId, file.dateModified, this);
					} else {
						ImportFile.importFile(context, Uri.fromFile(tmpFile), set, albumId, file.dateModified, this);
					}
				} else {
					removeFileFromCurrentSet(file, galleryDb, albumFilesDb, trashDb);
					ImportFile.importFile(context, Uri.fromFile(tmpFile), set, albumId, file.dateModified, this);

					if (set == SyncManager.ALBUM) {
						AlbumsDb albumsDb = new AlbumsDb(context);
						StingleDbAlbum album = albumsDb.getAlbumById(albumId);

						album.dateModified = System.currentTimeMillis();
						albumsDb.updateAlbum(album);

						albumsDb.close();
					}
				}

				tmpFile.delete();
			}

			galleryDb.close();
			albumFilesDb.close();
			trashDb.close();
		}
		
		return null;
	}

	@Override
	protected void onPostExecute(Void unused) {
		super.onPostExecute(unused);

		if (progressDialog != null) {
			progressDialog.dismiss();
		}

		Runnable callback = callbackWeakReference.get();
		if (callback != null) {
			callback.run();
		}
	}

	private boolean fileExistsInOtherSets(StingleDbFile file, GalleryTrashDb galleryDb, AlbumFilesDb albumFilesDb, GalleryTrashDb trashDb) {
		if (set == SyncManager.GALLERY) {
			return albumFilesDb.getFileIfExists(file.filename) != null || trashDb.getFileIfExists(file.filename) != null;
		} else if (set == SyncManager.ALBUM) {
			return galleryDb.getFileIfExists(file.filename) != null || trashDb.getFileIfExists(file.filename) != null;
		} else {
			// Can we safely say that if file exists in trash it only exists there ?

			return galleryDb.getFileIfExists(file.filename) != null || albumFilesDb.getFileIfExists(file.filename) != null;
		}
	}
	
	private void removeFileFromCurrentSet(StingleDbFile file, GalleryTrashDb galleryDb, AlbumFilesDb albumFilesDb, GalleryTrashDb trashDb) {
		if (set == SyncManager.GALLERY) {
			galleryDb.deleteFile(file.filename);
		} else if (set == SyncManager.ALBUM) {
			albumFilesDb.deleteAlbumFile(file.id);
		} else {
			trashDb.deleteFile(file.filename);
		}

		// transfer exif..
		// update db ?
	}
	
	private void deleteFile(StingleDbFile file, Context context) {
		String homeDir = FileManager.getHomeDir(context);
		String thumbDir = FileManager.getThumbsDir(context);
		String thumbCacheDir = FileManager.getThumbCacheDirPath(context);
		String fileCacheDir = FileManager.getFileCacheDirPath(context);
		
		File mainFile = new File(homeDir + "/" + file.filename);
		File thumbFile = new File(thumbDir + "/" + file.filename);
		File thumbCacheFile = new File(thumbCacheDir + "/" + file.filename);
		File fileCacheFile = new File(fileCacheDir + "/" + file.filename);

		if (mainFile.exists()) {
			mainFile.delete();
		}
		if (thumbFile.exists()) {
			thumbFile.delete();
		}
		if (thumbCacheFile.exists()) {
			thumbCacheFile.delete();
		}
		if (fileCacheFile.exists()) {
			fileCacheFile.delete();
		}
	}
}
