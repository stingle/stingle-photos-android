package org.stingle.photos.Gallery.Albums;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.AsyncTasks.Gallery.SetAlbumCoverAsyncTask;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class AlbumsPicassoLoader extends RequestHandler {

	private Context context;
	private AlbumsDb db;
	private AlbumFilesDb filesDb;
	private int view;
	private int thumbSize;
	private Crypto crypto;

	LruCache<Integer, StingleDbAlbum> albumsCache;
	private LruCache<Integer, Crypto.AlbumData> albumsDataCache;

	public AlbumsPicassoLoader(Context context, AlbumsDb db, AlbumFilesDb filesDb, int view, int thumbSize, LruCache<Integer, StingleDbAlbum> albumsCache, LruCache<Integer, Crypto.AlbumData> albumsDataCache) {
		this.context = context;
		this.db = db;
		this.filesDb = filesDb;
		this.thumbSize = thumbSize;
		this.view = view;

		this.crypto = StinglePhotosApplication.getCrypto();

		this.albumsCache = albumsCache;
		this.albumsDataCache = albumsDataCache;
	}


	@Override
	public boolean canHandleRequest(com.squareup.picasso3.Request data) {
		if (data.uri.toString().startsWith("a")) {
			return true;
		}
		return false;
	}

	@Override
	public void load(@NonNull Picasso picasso, @NonNull com.squareup.picasso3.Request request, @NonNull Callback callback) throws IOException {
		String uri = request.uri.toString();
		String position = uri.substring(1);
		int dbPos = Integer.parseInt(position);

		try {
			StingleDbAlbum album;
			album = albumsCache.get(dbPos);
			if (album == null) {
				album = db.getAlbumAtPosition(dbPos, AlbumsDb.SORT_BY_MODIFIED_DATE, StingleDb.SORT_DESC, AlbumsFragment.getAlbumIsHiddenByView(view), AlbumsFragment.getAlbumIsSharedByView(view));
				albumsCache.put(dbPos, album);
			}
			Crypto.AlbumData albumData;
			albumData = albumsDataCache.get(dbPos);
			if (albumData == null) {
				albumData = StinglePhotosApplication.getCrypto().parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);
				albumsDataCache.put(dbPos, albumData);
			}

			Result result = null;

			StingleDbFile albumDbFile = null;
			boolean isBlankCover = false;

			if(album.cover != null) {
				albumDbFile = filesDb.getFileIfExists(album.cover, album.albumId);
			}

			if(albumDbFile == null) {
				if (album.cover.equals(SetAlbumCoverAsyncTask.ALBUM_COVER_BLANK_TEXT)) {
					isBlankCover = true;
				}
				else {
					albumDbFile = filesDb.getFileAtPosition(0, album.albumId, StingleDb.SORT_DESC);
				}
			}

			if (isBlankCover) {
				returnNoImage(callback);
				return;
			}

			if (albumDbFile != null) {
				byte[] decryptedData;
				File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + albumDbFile.filename);
				if (albumDbFile.isLocal && fileToDec.exists()) {
					FileInputStream input = new FileInputStream(fileToDec);
					decryptedData = crypto.decryptFile(input, crypto.getThumbHeaderFromHeadersStr(albumDbFile.headers, albumData.privateKey, albumData.publicKey));
				} else {
					byte[] encFile = FileManager.getAndCacheThumb(context, albumDbFile.filename, SyncManager.ALBUM);
					decryptedData = crypto.decryptFile(encFile, crypto.getThumbHeaderFromHeadersStr(albumDbFile.headers, albumData.privateKey, albumData.publicKey));
				}

				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
					bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

					result = new Result(bitmap, Picasso.LoadedFrom.DISK);

				}
			}

			if (result == null) {
				returnNoImage(callback);
				return;
			}

			callback.onSuccess(result);

		} catch (IOException | CryptoException e) {
			e.printStackTrace();
			returnNoImage(callback);
		}
	}

	private void returnNoImage(Callback callback){
		Drawable drawable = context.getDrawable(R.drawable.ic_no_image);
		if (drawable == null) {
			return;
		}

		Result result = new Result(drawable, Picasso.LoadedFrom.DISK);
		callback.onSuccess(result);
	}

}
