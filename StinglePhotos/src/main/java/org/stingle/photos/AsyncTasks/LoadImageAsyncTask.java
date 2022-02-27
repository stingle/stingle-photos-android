package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.core.widget.ContentLoadingProgressBar;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.FileDataSource;

import org.json.JSONObject;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;
import org.stingle.photos.Video.StingleDataSourceFactory;
import org.stingle.photos.Video.StingleHttpDataSource;
import org.stingle.photos.ViewItem.GetOriginalRemotePhotoTask;
import org.stingle.photos.ViewItem.ViewPagerAdapter;
import org.stingle.photos.ViewItemActivity;
import org.stingle.photos.Widget.AnimatedGifImageView;
import org.stingle.photos.Widget.ImageHolderLayout;
import org.stingle.photos.Widget.photoview.PhotoViewAttacher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class LoadImageAsyncTask extends AsyncTask<Void, Void, Bitmap> {

	private final WeakReference<Context> contextRef;
	private final WeakReference<Callback<Bitmap>> callbackReference;

	private FilesDb db;
	private int position;
	private int set;
	private String albumId;

	private int fileType;
	private Crypto crypto;
	private Crypto.Header videoFileHeader = null;

	public LoadImageAsyncTask(Context context, Callback<Bitmap> callback, int position, int set, String albumId) {
		this.contextRef = new WeakReference<>(context);
		this.callbackReference = new WeakReference<>(callback);

		this.position = position;
		this.set = set;
		this.albumId = albumId;
		this.crypto = StinglePhotosApplication.getCrypto();

		if (this.position < 0) {
			this.position = 0;
		}

		switch (set) {
			case SyncManager.GALLERY:
				this.db = new GalleryTrashDb(context, SyncManager.GALLERY);
				break;
			case SyncManager.TRASH:
				this.db = new GalleryTrashDb(context, SyncManager.TRASH);
				break;
			case SyncManager.ALBUM:
				this.db = new AlbumFilesDb(context);
				break;
		}
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		Context context = contextRef.get();
		if(context == null){
			return null;
		}

		int sort = StingleDb.SORT_DESC;

		StingleDbFile dbFile = db.getFileAtPosition(position, albumId, sort);
		if(dbFile == null){
			return null;
		}
		try {
			File cachedFile = FileManager.getCachedFile(context, dbFile.filename);
			if (dbFile.isLocal || cachedFile != null) {
				File file;
				if(cachedFile != null){
					file = cachedFile;
				}
				else{
					 file = new File(FileManager.getHomeDir(context) + "/" + dbFile.filename);
				}

				Crypto.Header fileHeader = CryptoHelpers.decryptFileHeaders(context, set, albumId, dbFile.headers, false);
				fileType = fileHeader.fileType;

				boolean isGif = fileHeader.filename.toLowerCase().endsWith(".gif");

				if (fileType == Crypto.FILE_TYPE_PHOTO) {
					FileInputStream input = new FileInputStream(file);

					byte[] decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, dbFile.headers, false, input);

					if (decryptedData.length > 0) {
						if (!isGif) {
							return Helpers.decodeBitmap(decryptedData, getSize(context));
						}
					}
				}
			} else if (dbFile.isRemote) {
				byte[] encThumb = FileManager.getAndCacheThumb(context, dbFile.filename, set);
				if (encThumb == null) {
					return null;
				}
				Crypto.Header fileHeader = CryptoHelpers.decryptFileHeaders(context, set, albumId, dbFile.headers, true);
				if(fileHeader == null){
					return null;
				}
				fileType = fileHeader.fileType;

				if (fileType == Crypto.FILE_TYPE_PHOTO) {
					byte[] decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, dbFile.headers, true, encThumb);

					if (decryptedData.length > 0) {
						return Helpers.decodeBitmap(decryptedData, getSize(context));
					}
				}
			}
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	protected void onPostExecute(Bitmap bitmap) {
		super.onPostExecute(bitmap);

		Callback<Bitmap> callback = callbackReference.get();
		if (callback != null) {
			callback.call(bitmap);
		}
	}

	public static int getSize(Context context) {
		return 4096;
	}
}
