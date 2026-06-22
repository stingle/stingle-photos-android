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

import androidx.annotation.OptIn;
import androidx.core.widget.ContentLoadingProgressBar;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
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

@OptIn(markerClass = UnstableApi.class)
public class ViewItemAsyncTask extends AsyncTask<Void, Integer, ViewItemAsyncTask.ViewItemTaskResult> {

	private WeakReference<Context> contextRef;

	private int position = 0;
	private final WeakReference<ImageHolderLayout> parentRef;
	private final WeakReference<ContentLoadingProgressBar> loadingRef;
	private final WeakReference<ContentLoadingProgressBar> originalPhotoLoadingBarRef;
	private final ViewPagerAdapter adapter;
	private final FilesDb db;
	private int set = SyncManager.GALLERY;
	private String albumId = null;

	private final OnClickListener onClickListener;
	private final MemoryCache memCache;
	private boolean zoomable = false;
	private boolean isGif = false;
	private final View.OnTouchListener touchListener;

	private int fileType;
	private Crypto crypto;
	private Crypto.Header videoFileHeader = null;

	private static ArrayList<GetOriginalRemotePhotoTask> getOriginalRemotePhotoTasks = new ArrayList<>();
	private final static int GET_ORIGINAL_TASKS_LIMIT = 3;


	public ViewItemAsyncTask(Context context, ViewPagerAdapter adapter, int position, ImageHolderLayout parent, ContentLoadingProgressBar loading, ContentLoadingProgressBar originalPhotoLoading, FilesDb db, int set, String albumId, OnClickListener onClickListener, View.OnTouchListener touchListener, MemoryCache memCache) {
		super();
		this.contextRef = new WeakReference<>(context);
		this.adapter = adapter;
		this.position = position;
		this.parentRef = new WeakReference<>(parent);
		this.loadingRef = new WeakReference<>(loading);
		this.originalPhotoLoadingBarRef = new WeakReference<>(originalPhotoLoading);
		this.db = db;
		this.set = set;
		this.albumId = albumId;
		this.onClickListener = onClickListener;
		this.touchListener = touchListener;
		this.memCache = memCache;
		this.crypto = StinglePhotosApplication.getCrypto();

		if (this.position < 0) {
			this.position = 0;
		}
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected ViewItemAsyncTask.ViewItemTaskResult doInBackground(Void... params) {
		Context context = contextRef.get();
		if(context == null){
			return null;
		}
		ViewItemTaskResult result = new ViewItemTaskResult();

		result.set = set;
		result.albumId = albumId;

		int sort = StingleDb.SORT_DESC;
		/*if(set == SyncManager.ALBUM){
			sort = StingleDb.SORT_ASC;
		}*/

		StingleDbFile dbFile = db.getFileAtPosition(position, albumId, sort);
		if(dbFile == null){
			return null;
		}
		result.filename = dbFile.filename;
		result.headers = dbFile.headers;
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
				result.fileType = fileType;

				if (fileHeader.filename.toLowerCase().endsWith(".gif")) {
					isGif = true;
				}

				if (fileType == Crypto.FILE_TYPE_PHOTO) {
					FileInputStream input = new FileInputStream(file);

					byte[] decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, dbFile.headers, false, input);

					if (decryptedData.length > 0) {
						if (isGif) {
							result.bitmapBytes = decryptedData;
						} else {
							result.bitmap = Helpers.decodeBitmap(decryptedData, getSize(context));
						}
					}
				}
			} else if (dbFile.isRemote) {
				byte[] encThumb = FileManager.getAndCacheThumb(context, dbFile.filename, set);
				if (encThumb == null) {
					return result;
				}
				Crypto.Header fileHeader = CryptoHelpers.decryptFileHeaders(context, set, albumId, dbFile.headers, true);
				if(fileHeader == null){
					return null;
				}
				fileType = fileHeader.fileType;
				result.fileType = fileType;
				result.isRemote = true;

				if (fileHeader.filename.toLowerCase().endsWith(".gif")) {
					isGif = true;
				}

				if (fileType == Crypto.FILE_TYPE_PHOTO) {
					byte[] decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, dbFile.headers, true, encThumb);

					if (decryptedData.length > 0) {
						result.bitmap = Helpers.decodeBitmap(decryptedData, getSize(context));
					}
				} else if (fileType == Crypto.FILE_TYPE_VIDEO) {

					HashMap<String, String> postParams = new HashMap<String, String>();

					postParams.put("token", KeyManagement.getApiToken(context));
					postParams.put("file", dbFile.filename);
					postParams.put("set", String.valueOf(set));

					JSONObject json = HttpsClient.postFunc(StinglePhotosApplication.getApiUrl() + context.getString(R.string.get_url_path), postParams);
					StingleResponse response = new StingleResponse(context, json, false);

					if (response.isStatusOk()) {
						String url = response.get("url");

						if (url != null) {
							result.url = url;
						}
					}
				}
			}
			if (fileType == Crypto.FILE_TYPE_VIDEO) {
				if (set == SyncManager.ALBUM) {
					AlbumsDb albumsDb = new AlbumsDb(context);
					StingleDbAlbum dbAlbum = albumsDb.getAlbumById(albumId);
					Crypto.AlbumData albumData = StinglePhotosApplication.getCrypto().parseAlbumData(dbAlbum.publicKey, dbAlbum.encPrivateKey, dbAlbum.metadata);
					this.videoFileHeader = crypto.getFileHeaderFromHeadersStr(dbFile.headers, albumData.privateKey, albumData.publicKey);

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
		Context context = contextRef.get();
		if(context == null || result == null){
			return;
		}

		ImageHolderLayout parent = parentRef.get();
		ContentLoadingProgressBar loading = loadingRef.get();
		ContentLoadingProgressBar originalPhotoLoadingBar = originalPhotoLoadingBarRef.get();
		if(parent == null || loading == null || originalPhotoLoadingBar == null){
			return;
		}

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
					originalPhotoLoadingBar.setVisibility(View.VISIBLE);

					GetOriginalRemotePhotoTask getOriginalTask = new GetOriginalRemotePhotoTask(context, result);
					getOriginalTask.setImage(image);
					getOriginalTask.setAttacher(attacher);
					getOriginalTask.setGif(false);
					getOriginalTask.setOnFinish(new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							getOriginalRemotePhotoTasks.remove(getOriginalTask);
							Log.d("getOrigTask", "removed myself");
							originalPhotoLoadingBar.setVisibility(View.GONE);
						}
					});
					getOriginalTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

					getOriginalRemotePhotoTasks.add(getOriginalTask);

					if(getOriginalRemotePhotoTasks.size() > GET_ORIGINAL_TASKS_LIMIT){
						Log.d("getOrigTask", "tasks exceeds limit, it's: " + getOriginalRemotePhotoTasks.size() + " will remove " + (getOriginalRemotePhotoTasks.size()-GET_ORIGINAL_TASKS_LIMIT));
						for(int i=0; i < getOriginalRemotePhotoTasks.size()-GET_ORIGINAL_TASKS_LIMIT; i++){
							getOriginalRemotePhotoTasks.get(0).cancel(true);
							getOriginalRemotePhotoTasks.remove(0);
							Log.d("getOrigTask", "removed 1 task");
						}
					}
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


			ExoPlayer player = new ExoPlayer.Builder(context,
					new DefaultRenderersFactory(context))
					.setTrackSelector(new DefaultTrackSelector(context))
					.setLoadControl(new DefaultLoadControl())
					.build();
			adapter.addPlayer(position, player);


			playerView.setPlayer(player);
			player.addListener(getPlayerEventListener());

			MediaSource mediaSource = null;
			if (result.isRemote) {
				if (result.url != null) {
					Uri uri = Uri.parse(result.url);
					Log.d("url", result.url);

					DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
							.setUserAgent("stingle")
							.setAllowCrossProtocolRedirects(true);
					StingleDataSourceFactory stingle = new StingleDataSourceFactory(context, http, videoFileHeader);

					mediaSource = new ProgressiveMediaSource.Factory(stingle).createMediaSource(MediaItem.fromUri(uri));
				}
			} else {
				Uri uri = Uri.fromFile(new File(FileManager.getHomeDir(context) + "/" + result.filename));
				StingleDataSourceFactory stingle = new StingleDataSourceFactory(context, new FileDataSource.Factory(), videoFileHeader);
				mediaSource = new ProgressiveMediaSource.Factory(stingle).createMediaSource(MediaItem.fromUri(uri));
			}

			if (mediaSource != null) {
				// Autoplay only the video on the currently-visible page. This covers both the
				// initial launch (player is built async, after the page is already selected) and
				// swiping to a video before its player exists; off-screen pages stay paused.
				player.setPlayWhenReady(adapter.getCurrentPosition() == position);
				playerView.setShowShuffleButton(false);
				playerView.setControllerHideOnTouch(false);
				if(context instanceof ViewItemActivity) {
					ViewItemActivity activity = (ViewItemActivity)context;
					playerView.getVideoSurfaceView().setOnClickListener(v -> {
						if (activity.getSupportActionBar().isShowing()){
							playerView.hideController();
						}
						else{
							playerView.showController();
						}
						if (onClickListener != null) {
							onClickListener.onClick(v);
						}
					});
				}
				player.setMediaSource(mediaSource);
				player.prepare();
				loading.setVisibility(View.INVISIBLE);
			}
		}
	}

	private Player.Listener getPlayerEventListener() {
		ContentLoadingProgressBar loading = loadingRef.get();
		if(loading == null){
			return null;
		}
		return new Player.Listener() {
			@Override
			public void onPlaybackStateChanged(int playbackState) {
				if (playbackState == Player.STATE_BUFFERING) {
					loading.setVisibility(View.VISIBLE);
					Log.d("buffering", "yes");
				} else {
					loading.setVisibility(View.INVISIBLE);
					Log.d("buffering", "no");
				}
			}
		};
	}

	public static void removeRemainingGetOriginalTasks(){
		for(int i=0; i < getOriginalRemotePhotoTasks.size(); i++){
			getOriginalRemotePhotoTasks.get(0).cancel(true);
			getOriginalRemotePhotoTasks.remove(0);
			Log.d("getOrigTask", "removed all 1 task");
		}
	}

	public static class ViewItemTaskResult {
		public int fileType = Crypto.FILE_TYPE_PHOTO;
		public String filename = null;
		public Bitmap bitmap = null;
		public byte[] bitmapBytes = null;
		public boolean isRemote = false;
		public String url = null;
		public int set = SyncManager.GALLERY;
		public String albumId = null;
		public String headers = null;
	}
}
