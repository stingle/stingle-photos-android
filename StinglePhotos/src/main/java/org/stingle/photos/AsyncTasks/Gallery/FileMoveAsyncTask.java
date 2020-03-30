package org.stingle.photos.AsyncTasks.Gallery;

import android.content.Context;
import android.os.AsyncTask;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class FileMoveAsyncTask extends AsyncTask<Void, Void, Boolean> {

	private WeakReference<Context> context;
	private ArrayList<StingleFile> files = null;
	private final OnAsyncTaskFinish onFinishListener;
	private Crypto crypto;

	private int fromFolder = -1;
	private int toFolder = -1;
	private String fromFolderId = null;
	private String toFolderId = null;
	private boolean isMoving = false;

	public FileMoveAsyncTask(Context context, ArrayList<StingleFile> files, OnAsyncTaskFinish onFinishListener) {
		this.context = new WeakReference<>(context);;
		this.files = files;
		this.onFinishListener = onFinishListener;
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	public FileMoveAsyncTask setFromFolder(int folder){
		this.fromFolder = folder;
		return this;
	}
	public FileMoveAsyncTask setToFolder(int folder){
		this.toFolder = folder;
		return this;
	}
	public FileMoveAsyncTask setToFolderId(String folderId){
		this.toFolderId = folderId;
		return this;
	}
	public FileMoveAsyncTask setFromFolderId(String folderId){
		this.fromFolderId = folderId;
		return this;
	}
	public FileMoveAsyncTask setIsMoving(boolean isMoving){
		this.isMoving = isMoving;
		return this;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		try {
			Context myContext = context.get();
			if(myContext == null){
				return false;
			}
			if(fromFolder == -1 || toFolder == -1){
				return false;
			}
			if(files == null){
				return false;
			}

			AlbumsDb albumsDb = new AlbumsDb(myContext);
			AlbumFilesDb albumFilesDb = new AlbumFilesDb(myContext);

			if(fromFolder == SyncManager.FOLDER_MAIN && toFolder == SyncManager.FOLDER_ALBUM) {

				if(toFolderId == null){
					return false;
				}

				FilesTrashDb filesTrashDb = new FilesTrashDb(myContext, StingleDbContract.Columns.TABLE_NAME_FILES);

				StingleDbAlbum album = albumsDb.getAlbumById(toFolderId);

				for (StingleFile file : files) {
					String newHeaders = crypto.reencryptFileHeaders(file.headers, Crypto.base64ToByteArray(album.albumPK), null, null);
					albumFilesDb.insertAlbumFile(album.albumId, file.filename, file.isLocal, file.isRemote, file.version, newHeaders, file.dateCreated, System.currentTimeMillis());
					if(isMoving) {
						filesTrashDb.deleteFile(file.filename);
					}
				}

				filesTrashDb.close();
			}
			else if(fromFolder == SyncManager.FOLDER_ALBUM && (toFolder == SyncManager.FOLDER_MAIN || toFolder == SyncManager.FOLDER_TRASH)) {

				if(fromFolderId == null){
					return false;
				}

				FilesTrashDb filesTrashDb = new FilesTrashDb(myContext, (toFolder == SyncManager.FOLDER_TRASH ? StingleDbContract.Columns.TABLE_NAME_TRASH : StingleDbContract.Columns.TABLE_NAME_FILES));

				StingleDbAlbum album = albumsDb.getAlbumById(fromFolderId);
				Crypto.AlbumData albumData = crypto.parseAlbumData(album.data);

				for (StingleFile file : files) {
					String newHeaders = crypto.reencryptFileHeaders(file.headers, crypto.getPublicKey(), albumData.privateKey, Crypto.base64ToByteArray(album.albumPK));
					filesTrashDb.insertFile(file.filename, file.isLocal, file.isRemote, file.version, file.dateCreated, System.currentTimeMillis(), newHeaders);
					if(isMoving) {
						albumFilesDb.deleteAlbumFile(file.id);
					}
				}

				filesTrashDb.close();
			}
			else if(fromFolder == SyncManager.FOLDER_ALBUM && toFolder == SyncManager.FOLDER_ALBUM) {

				if(fromFolderId == null || toFolderId == null){
					return false;
				}

				StingleDbAlbum fromAlbum = albumsDb.getAlbumById(fromFolderId);
				Crypto.AlbumData fromAlbumData = crypto.parseAlbumData(fromAlbum.data);
				StingleDbAlbum toAlbum = albumsDb.getAlbumById(toFolderId);

				for (StingleFile file : files) {
					String newHeaders = crypto.reencryptFileHeaders(file.headers, Crypto.base64ToByteArray(toAlbum.albumPK), fromAlbumData.privateKey, Crypto.base64ToByteArray(fromAlbum.albumPK));
					albumFilesDb.insertAlbumFile(toAlbum.albumId, file.filename, file.isLocal, file.isRemote, file.version, newHeaders, file.dateCreated, System.currentTimeMillis());
					if(isMoving) {
						albumFilesDb.deleteAlbumFile(file.id);
					}
				}
			}
			else if(fromFolder == SyncManager.FOLDER_MAIN && toFolder == SyncManager.FOLDER_TRASH) {
				FilesTrashDb filesDb = new FilesTrashDb(myContext, StingleDbContract.Columns.TABLE_NAME_FILES);
				FilesTrashDb trashDb = new FilesTrashDb(myContext, StingleDbContract.Columns.TABLE_NAME_TRASH);

				for (StingleFile file : files) {
					file.dateModified = System.currentTimeMillis();
					trashDb.insertFile(file);
					filesDb.deleteFile(file.filename);
				}

				filesDb.close();
				trashDb.close();
			}
			else if(fromFolder == SyncManager.FOLDER_TRASH && toFolder == SyncManager.FOLDER_MAIN) {
				FilesTrashDb filesDb = new FilesTrashDb(myContext, StingleDbContract.Columns.TABLE_NAME_FILES);
				FilesTrashDb trashDb = new FilesTrashDb(myContext, StingleDbContract.Columns.TABLE_NAME_TRASH);

				for (StingleFile file : files) {
					file.dateModified = System.currentTimeMillis();
					filesDb.insertFile(file);
					trashDb.deleteFile(file.filename);
				}

				filesDb.close();
				trashDb.close();
			}

			albumsDb.close();
			albumFilesDb.close();

		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		if(result){
			onFinishListener.onFinish();
		}
		else{
			onFinishListener.onFail();
		}

	}
}
