package com.fenritz.safecam.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;

import com.fenritz.safecam.R;
import com.fenritz.safecam.util.AESCrypt.CryptoProgress;
import com.fenritz.safecam.widget.AnimatedGifImageView;
import com.fenritz.safecam.widget.photoview.PhotoViewAttacher;

public class DecryptAndShowImage extends AsyncTask<Void, Integer, byte[]> {

	private final String filePath;

	private Context context;
	private final ViewGroup parent;
	private ProgressBar progressBar;
	private final OnClickListener onClickListener;
	private final OnLongClickListener onLongClickListener;
	private final MemoryCache memCache;
	private boolean zoomable = false;
	private boolean isGif = false;
	private final View.OnTouchListener touchListener;
	

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent) {
		this(pFilePath, pParent, null, null, null, false, null, false);
	}

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, MemoryCache pMemCache) {
		this(pFilePath, pParent, null, null, pMemCache, false, null, false);
	}

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener, OnLongClickListener pOnLongClickListener) {
		this(pFilePath, pParent, pOnClickListener, pOnLongClickListener, null, false, null, false);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener) {
		this(pFilePath, pParent, pOnClickListener, null, null, false, null, false);
	}

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener, OnLongClickListener pOnLongClickListener,
			MemoryCache pMemCache, boolean pZoomable, View.OnTouchListener pTouchListener, boolean pisGif) {
		super();
		filePath = pFilePath;
		parent = pParent;
		onClickListener = pOnClickListener;
		onLongClickListener = pOnLongClickListener;
		memCache = pMemCache;
		zoomable = pZoomable;
		zoomable = pZoomable;
		touchListener = pTouchListener;
		isGif = pisGif;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		context = parent.getContext();
		
		if (memCache != null) {
			Bitmap cachedBitmap = memCache.get(filePath);
			if (cachedBitmap != null) {
				return;
			}
		}

		progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
		// progressBar = new ProgressBar(context);
		progressBar.setProgress(0);
		LayoutParams layoutParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		layoutParams.gravity = Gravity.CENTER;
		layoutParams.setMargins(10, 10, 10, 10);
		progressBar.setLayoutParams(layoutParams);
		
		if (onClickListener != null) {
			progressBar.setOnClickListener(onClickListener);
			//parent.setOnClickListener(onClickListener);
		}
		if (onLongClickListener != null) {
			progressBar.setOnLongClickListener(onLongClickListener);
			//parent.setOnLongClickListener(onLongClickListener);
		}
		if (touchListener != null) {
			progressBar.setOnTouchListener(touchListener);
			//parent.setOnTouchListener(touchListener);
		}

		parent.removeAllViews();
		System.gc();
		parent.addView(progressBar);
	}

	@Override
	protected byte[] doInBackground(Void... params) {
		/*if (memCache != null) {
			Bitmap cachedBitmap = memCache.get(filePath);
			if (cachedBitmap != null) {
				return cachedBitmap;
			}
		}*/
		
		
		File thumbFile = new File(filePath);
		if (thumbFile.exists() && thumbFile.isFile()) {
			try {
				FileInputStream input = new FileInputStream(filePath);
				AESCrypt.CryptoProgress progress = new CryptoProgress(input.getChannel().size()) {
					@Override
					public void setProgress(long pCurrent) {
						super.setProgress(pCurrent);
						int newProgress = this.getProgressPercents();
						publishProgress(newProgress);
					}
				};

				byte[] decryptedData = Helpers.getAESCrypt(context).decrypt(input, progress, this);

				if (decryptedData != null) {
					return decryptedData;

					

					/*decryptedData = null;
					if (bitmap != null) {
						if (memCache != null) {
							memCache.put(filePath, bitmap);
						}
						return bitmap;
					}*/

				}
				else {
					Log.d("sc", "Unable to decrypt: " + filePath);
				}

			}
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private int getSize(){
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);

		int size;
		if(metrics.widthPixels <= metrics.heightPixels){
			size = (int) Math.floor(metrics.widthPixels * 2);
		}
		else{
			size = (int) Math.floor(metrics.heightPixels * 2);
		}

		return size;
	}

	@SuppressLint("NewApi")
	@Override
	protected void onPostExecute(byte[] bitmap) {
		super.onPostExecute(bitmap);

		ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

		if(isGif){
			//InputStream stream = new ByteArrayInputStream(bitmap);
			AnimatedGifImageView gifMovie = new AnimatedGifImageView(context);
			gifMovie.setAnimatedGif(bitmap, AnimatedGifImageView.TYPE.FIT_CENTER);

			if(touchListener != null){
				gifMovie.setOnTouchListener(touchListener);
			}

			if (onClickListener != null) {
				gifMovie.setOnClickListener(onClickListener);
			}

			gifMovie.setLayoutParams(params);
			gifMovie.setPadding(3, 3, 3, 3);

			if (onLongClickListener != null) {
				gifMovie.setOnLongClickListener(onLongClickListener);
			}

			if(progressBar != null){
				parent.removeView(progressBar);
			}

			parent.addView(gifMovie);
		}
		else {
			ImageView image = new ImageView(context);
			PhotoViewAttacher attacher = null;
			if (zoomable) {
				attacher = new PhotoViewAttacher(image);
				if(touchListener != null){
					parent.setOnTouchListener(touchListener);
				}
				if (onClickListener != null) {
					attacher.setOnClickListener(onClickListener);
				}
			}
			else {
				if(touchListener != null){
					image.setOnTouchListener(touchListener);
				}

				if (onClickListener != null) {
					image.setOnClickListener(onClickListener);
				}
			}


			image.setLayoutParams(params);

			if (bitmap != null) {
				image.setImageBitmap(Helpers.decodeBitmap(bitmap, getSize()));
			}
			else {
				image.setImageResource(R.drawable.no);
			}

			image.setPadding(3, 3, 3, 3);

			if (onLongClickListener != null) {
				image.setOnLongClickListener(onLongClickListener);
			}

			if(progressBar != null){
				parent.removeView(progressBar);
			}

			if (zoomable && attacher!=null) {
				attacher.update();
			}

			parent.addView(image);
		}

		onFinish();
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();
		parent.removeAllViews();
		onFinish();
	}
	
	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);

		progressBar.setProgress(values[0]);
	}
	
	protected void onFinish(){
		
	}
}
