package org.stingle.photos.ViewItem;

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
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.StingleDb;
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
import org.stingle.photos.Widget.AnimatedGifImageView;
import org.stingle.photos.Widget.ImageHolderLayout;
import org.stingle.photos.Widget.photoview.PhotoViewAttacher;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

public class ViewItemAsyncTask extends AsyncTask<Void, Integer, ViewItemAsyncTask.ViewItemTaskResult> {

	private Context context;

	private int position = 0;
	private final ImageHolderLayout parent;
	private final ContentLoadingProgressBar loading;
	private final ViewPagerAdapter adapter;
	private final FilesDb db;
	private int folder = SyncManager.FOLDER_MAIN;
	private String folderId = null;

	private final OnClickListener onClickListener;
	private final MemoryCache memCache;
	private boolean zoomable = false;
	private boolean isGif = false;
	private final View.OnTouchListener touchListener;

	private int fileType;
	private Crypto crypto;
	private Crypto.Header videoFileHeader = null;


	public ViewItemAsyncTask(Context context, ViewPagerAdapter adapter, int position, ImageHolderLayout parent, ContentLoadingProgressBar loading, FilesDb db, int folder, String folderId, OnClickListener onClickListener, View.OnTouchListener touchListener, MemoryCache memCache) {
		super();
		this.context = context;
		this.adapter = adapter;
		this.position = position;
		this.parent = parent;
		this.loading = loading;
		this.db = db;
		this.folder = folder;
		this.folderId = folderId;
		this.onClickListener = onClickListener;
		this.touchListener = touchListener;
		this.memCache = memCache;
		this.crypto = StinglePhotosApplication.getCrypto();

		if (position < 0) {
			position = 0;
		}
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected ViewItemAsyncTask.ViewItemTaskResult doInBackground(Void... params) {
		ViewItemTaskResult result = new ViewItemTaskResult();

		result.folder = folder;

		int sort = StingleDb.SORT_DESC;
		if(folder == SyncManager.FOLDER_ALBUM){
			sort = StingleDb.SORT_ASC;
		}

		StingleFile dbFile = db.getFileAtPosition(position, folderId, sort);
		result.filename = dbFile.filename;
		result.headers = dbFile.headers;
		try {
			if (dbFile.isLocal) {
				File file = new File(FileManager.getHomeDir(context) + "/" + dbFile.filename);

				Crypto.Header fileHeader = StinglePhotosApplication.getCrypto().getFileHeader(new FileInputStream(file));
				fileType = fileHeader.fileType;
				result.fileType = fileType;

				if (fileHeader.filename.toLowerCase().endsWith(".gif")) {
					isGif = true;
				}

				if (fileType == Crypto.FILE_TYPE_PHOTO) {
					FileInputStream input = new FileInputStream(file);

					byte[] decryptedData = CryptoHelpers.decryptDbFile(context, folder, folderId, dbFile.headers, false, input);

					if (decryptedData != null) {
						if (isGif) {
							result.bitmapBytes = decryptedData;
						} else {
							result.bitmap = Helpers.decodeBitmap(decryptedData, getSize(context));
						}
					}
				}
			} else if (dbFile.isRemote) {
				byte[] encThumb = FileManager.getAndCacheThumb(context, dbFile.filename, folder);
				if (encThumb == null) {
					return result;
				}
				Crypto.Header fileHeader = StinglePhotosApplication.getCrypto().getFileHeader(new ByteArrayInputStream(encThumb));
				fileType = fileHeader.fileType;
				result.fileType = fileType;
				result.isRemote = true;

				if (fileHeader.filename.toLowerCase().endsWith(".gif")) {
					isGif = true;
				}

				if (fileType == Crypto.FILE_TYPE_PHOTO) {
					byte[] decryptedData = CryptoHelpers.decryptDbFile(context, folder, folderId, dbFile.headers, true, encThumb);

					if (decryptedData != null) {
						result.bitmap = Helpers.decodeBitmap(decryptedData, getSize(context));
					}
				} else if (fileType == Crypto.FILE_TYPE_VIDEO) {

					HashMap<String, String> postParams = new HashMap<String, String>();

					postParams.put("token", KeyManagement.getApiToken(context));
					postParams.put("file", dbFile.filename);
					postParams.put("folder", String.valueOf(folder));

					JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.get_url_path), postParams);
					StingleResponse response = new StingleResponse(this.context, json, false);

					if (response.isStatusOk()) {
						String url = response.get("url");

						if (url != null) {
							result.url = url;
						}
					}
				}
			}
			if (fileType == Crypto.FILE_TYPE_VIDEO) {
				if (folder == SyncManager.FOLDER_ALBUM) {
					AlbumsDb albumsDb = new AlbumsDb(context);
					StingleDbAlbum album = albumsDb.getAlbumById(folderId);
					Crypto.AlbumData albumData = StinglePhotosApplication.getCrypto().parseAlbumData(album.data);
					this.videoFileHeader = crypto.getFileHeaderFromHeadersStr(dbFile.headers, albumData.privateKey, Crypto.base64ToByteArray(album.albumPK));

				} else {
					this.videoFileHeader = crypto.getFileHeaderFromHeadersStr(dbFile.headers);
				}
			}
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}
		return result;
	}

	public static int getSize(Context context) {
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);

		int size;
		if (metrics.widthPixels <= metrics.heightPixels) {
			size = (int) Math.floor(metrics.widthPixels * 2);
		} else {
			size = (int) Math.floor(metrics.heightPixels * 2);
		}

		return size;
	}

	@Override
	protected void onPostExecute(ViewItemAsyncTask.ViewItemTaskResult result) {
		super.onPostExecute(result);

		parent.removeAllViews();
		parent.setFileType(result.fileType);
		ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

		if (result.fileType == Crypto.FILE_TYPE_PHOTO) {
			if (isGif) {
				AnimatedGifImageView gifMovie = new AnimatedGifImageView(context);
				PhotoViewAttacher attacher = new PhotoViewAttacher(gifMovie);

				if (result.bitmap != null) {
					gifMovie.setImageBitmap(result.bitmap);
				}

				if (touchListener != null) {
					parent.setOnTouchListener(touchListener);
				}

				if (onClickListener != null) {
					gifMovie.setOnClickListener(onClickListener);
				}

				gifMovie.setLayoutParams(params);

				if (result.bitmapBytes != null) {
					gifMovie.setAnimatedGif(result.bitmapBytes, AnimatedGifImageView.TYPE.FIT_CENTER);
					loading.setVisibility(View.INVISIBLE);
				}

				gifMovie.setPadding(3, 3, 3, 3);
				attacher.update();

				if (result.isRemote) {
					GetOriginalRemotePhotoTask getOriginalTask = new GetOriginalRemotePhotoTask(context, result);
					getOriginalTask.setGif(true);
					getOriginalTask.setAnimatedImage(gifMovie);
					getOriginalTask.setLoading(loading);
					getOriginalTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}

				parent.addView(gifMovie);
			} else {
				ImageView image = new ImageView(context);
				PhotoViewAttacher attacher = new PhotoViewAttacher(image);
				if (touchListener != null) {
					parent.setOnTouchListener(touchListener);
				}
				if (onClickListener != null) {
					attacher.setOnClickListener(onClickListener);
				}

				image.setLayoutParams(params);

				if (result.bitmap != null) {
					image.setImageBitmap(result.bitmap);
				} else {
					image.setImageResource(R.drawable.no);
				}

				image.setPadding(3, 3, 3, 3);

				attacher.update();

				if (result.isRemote) {
					GetOriginalRemotePhotoTask getOriginalTask = new GetOriginalRemotePhotoTask(context, result);
					getOriginalTask.setImage(image);
					getOriginalTask.setAttacher(attacher);
					getOriginalTask.setGif(false);
					getOriginalTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
				loading.setVisibility(View.INVISIBLE);
				parent.addView(image);
			}
		} else if (result.fileType == Crypto.FILE_TYPE_VIDEO) {
			PlayerView playerView = new PlayerView(context);
			playerView.setLayoutParams(params);
			playerView.setKeepScreenOn(true);

			parent.removeAllViews();
			parent.addView(playerView);

			if (touchListener != null) {
				parent.setOnTouchListener(touchListener);
			}
			if (onClickListener != null) {
				parent.setOnClickListener(onClickListener);
			}

			SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(
					new DefaultRenderersFactory(context),
					new DefaultTrackSelector(), new DefaultLoadControl());
			adapter.addPlayer(position, player);


			playerView.setPlayer(player);
			player.addListener(getPlayerEventListener());

			MediaSource mediaSource = null;
			if (result.isRemote) {
				if (result.url != null) {
					Uri uri = Uri.parse(result.url);
					Log.d("url", result.url);

					StingleHttpDataSource http = new StingleHttpDataSource("stingle", null);
					StingleDataSourceFactory stingle = new StingleDataSourceFactory(context, http, videoFileHeader);

					mediaSource = new ExtractorMediaSource.Factory(stingle).createMediaSource(uri);
				}
			} else {
				Uri uri = Uri.fromFile(new File(FileManager.getHomeDir(context) + "/" + result.filename));
				FileDataSource file = new FileDataSource();
				StingleDataSourceFactory stingle = new StingleDataSourceFactory(context, file, videoFileHeader);
				mediaSource = new ExtractorMediaSource.Factory(stingle).createMediaSource(uri);
			}

			if (mediaSource != null) {
				player.setPlayWhenReady(false);

				/*if(adapter.getCurrentPosition() == position) {
					player.setPlayWhenReady(true);
				}
				else{
					player.setPlayWhenReady(false);
				}*/
				playerView.setShowShuffleButton(false);
				player.prepare(mediaSource, true, false);
				loading.setVisibility(View.INVISIBLE);
			}
		}
	}

	private Player.EventListener getPlayerEventListener() {
		return new Player.EventListener() {
			@Override
			public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

			}

			@Override
			public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

			}

			@Override
			public void onLoadingChanged(boolean isLoading) {

			}

			@Override
			public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
				if (playbackState == ExoPlayer.STATE_BUFFERING) {
					loading.setVisibility(View.VISIBLE);
					Log.d("buffering", "yes");
				} else {
					loading.setVisibility(View.INVISIBLE);
					Log.d("buffering", "no");
				}
			}

			@Override
			public void onRepeatModeChanged(int repeatMode) {

			}

			@Override
			public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

			}

			@Override
			public void onPlayerError(ExoPlaybackException error) {

			}

			@Override
			public void onPositionDiscontinuity(int reason) {

			}

			@Override
			public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

			}

			@Override
			public void onSeekProcessed() {

			}
		};
	}

	public class ViewItemTaskResult {
		public int fileType = Crypto.FILE_TYPE_PHOTO;
		public String filename = null;
		public Bitmap bitmap = null;
		public byte[] bitmapBytes = null;
		public boolean isRemote = false;
		public String url = null;
		public int folder = SyncManager.FOLDER_MAIN;
		public String folderId = null;
		public String headers = null;
	}
}
