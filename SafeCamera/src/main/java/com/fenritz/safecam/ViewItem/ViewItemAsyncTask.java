package com.fenritz.safecam.ViewItem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Util.MemoryCache;
import com.fenritz.safecam.Video.StingleDataSourceFactory;
import com.fenritz.safecam.Widget.AnimatedGifImageView;
import com.fenritz.safecam.Widget.ImageHolderLayout;
import com.fenritz.safecam.Widget.photoview.PhotoViewAttacher;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.FileDataSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ViewItemAsyncTask extends AsyncTask<Void, Integer, ViewItemAsyncTask.ViewItemTaskResult> {

	private Context context;

	private int position = 0;
	private final ImageHolderLayout parent;
	private final ViewPagerAdapter adapter;
	private final StingleDbHelper db;

	private final OnClickListener onClickListener;
	private final MemoryCache memCache;
	private boolean zoomable = false;
	private boolean isGif = false;
	private final View.OnTouchListener touchListener;

	private int fileType;


	public ViewItemAsyncTask(Context context, ViewPagerAdapter adapter, int position, ImageHolderLayout parent, StingleDbHelper db, OnClickListener onClickListener, View.OnTouchListener touchListener, MemoryCache memCache) {
		super();
		this.context = context;
		this.adapter = adapter;
		this.position = position;
		this.parent = parent;
		this.db = db;
		this.onClickListener = onClickListener;
		this.memCache = memCache;
		this.touchListener = touchListener;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected ViewItemAsyncTask.ViewItemTaskResult doInBackground(Void... params) {
		ViewItemTaskResult result = new ViewItemTaskResult();

		StingleDbFile dbFile = db.getFileAtPosition(position);
		if(dbFile.isLocal){
			File file = new File(Helpers.getHomeDir(context) + "/" + dbFile.filename);
			try {
				Crypto.Header fileHeader = SafeCameraApplication.getCrypto().getFileHeader(new FileInputStream(file));
				fileType = fileHeader.fileType;
			}
			catch (IOException | CryptoException e) {
				e.printStackTrace();
			}

			if(fileType == Crypto.FILE_TYPE_PHOTO) {
				result.fileType = Crypto.FILE_TYPE_PHOTO;
				try {
					FileInputStream input = new FileInputStream(file);

					//byte[] decryptedData = Helpers.getAESCrypt(context).decrypt(input, progress, this);
					byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(input, null, this);

					if (decryptedData != null) {
						result.bitmap = Helpers.decodeBitmap(decryptedData, getSize());
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (CryptoException e) {
					e.printStackTrace();
				}

			}
			else if(fileType == Crypto.FILE_TYPE_VIDEO){
				result.fileType = Crypto.FILE_TYPE_VIDEO;
				result.filename = dbFile.filename;
			}
		}
		return result;
	}

	private int getSize(){
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);

		int size;
		if(metrics.widthPixels <= metrics.heightPixels){
			size = (int) Math.floor(metrics.widthPixels * 4);
		}
		else{
			size = (int) Math.floor(metrics.heightPixels * 4);
		}

		return size;
	}

	@Override
	protected void onPostExecute(ViewItemAsyncTask.ViewItemTaskResult result) {
		super.onPostExecute(result);

		parent.removeAllViews();
		parent.setFileType(result.fileType);
		ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

		if(result.fileType == Crypto.FILE_TYPE_PHOTO) {
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

			parent.addView(image);
		}
		else if (result.fileType == Crypto.FILE_TYPE_VIDEO){
			PlayerView playerView = new PlayerView(context);
			playerView.setLayoutParams(params);

			parent.removeAllViews();
			parent.addView(playerView);
			if(touchListener != null){
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

			//String path = Helpers.getHomeDir(this) + "/vid1.sc";
			Uri uri = Uri.fromFile(new File(Helpers.getHomeDir(context) + "/" + result.filename));

			//Uri uri = Uri.parse("https://www.safecamera.org/vid1.sc");

			//StingleHttpDataSource http = new StingleHttpDataSource("stingle", null);
			FileDataSource file = new FileDataSource();
			StingleDataSourceFactory stingle = new StingleDataSourceFactory(context, file);

			MediaSource mediaSource = new ExtractorMediaSource.Factory(stingle).createMediaSource(uri);
			player.setPlayWhenReady(false);
			/*if(adapter.getCurrentPosition() == position) {
				player.setPlayWhenReady(true);
			}
			else{
				player.setPlayWhenReady(false);
			}*/
			playerView.setShowShuffleButton(false);
			player.prepare(mediaSource, true, false);

		}
	}

	public class ViewItemTaskResult{
		public int fileType = Crypto.FILE_TYPE_PHOTO;
		public String filename = null;
		public Bitmap bitmap = null;
	}
}
