package com.fenritz.safecam.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;

import com.fenritz.safecam.R;
import com.fenritz.safecam.util.AESCrypt.CryptoProgress;
import com.fenritz.safecam.widget.TouchImageView;

public class DecryptAndShowImage extends AsyncTask<Void, Integer, Bitmap> {

	private final String filePath;

	private Context context;
	private final ViewGroup parent;
	private ProgressBar progressBar;
	private final OnClickListener onClickListener;
	private final OnLongClickListener onLongClickListener;
	private final MemoryCache memCache;
	private boolean zoomable = false;
	private final View.OnTouchListener touchListener;
	

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent) {
		this(pFilePath, pParent, null, null, null, false, null);
	}

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, MemoryCache pMemCache) {
		this(pFilePath, pParent, null, null, pMemCache, false, null);
	}

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener, OnLongClickListener pOnLongClickListener) {
		this(pFilePath, pParent, pOnClickListener, pOnLongClickListener, null, false, null);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener) {
		this(pFilePath, pParent, pOnClickListener, null, null, false, null);
	}

	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener, OnLongClickListener pOnLongClickListener,
			MemoryCache pMemCache, boolean pZoomable, View.OnTouchListener pTouchListener) {
		super();
		filePath = pFilePath;
		parent = pParent;
		onClickListener = pOnClickListener;
		onLongClickListener = pOnLongClickListener;
		memCache = pMemCache;
		zoomable = pZoomable;
		zoomable = pZoomable;
		touchListener = pTouchListener;
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
			parent.setOnClickListener(onClickListener);
		}
		if (onLongClickListener != null) {
			progressBar.setOnLongClickListener(onLongClickListener);
			parent.setOnLongClickListener(onLongClickListener);
		}
		if (touchListener != null) {
			progressBar.setOnTouchListener(touchListener);
			parent.setOnTouchListener(touchListener);
		}

		parent.removeAllViews();
		parent.addView(progressBar);
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		if (memCache != null) {
			Bitmap cachedBitmap = memCache.get(filePath);
			if (cachedBitmap != null) {
				return cachedBitmap;
			}
		}
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

				byte[] decryptedData = Helpers.getAESCrypt(parent.getContext()).decrypt(input, progress, this);

				if (decryptedData != null) {
					Bitmap bitmap = Helpers.decodeBitmap(decryptedData, 300);
					decryptedData = null;
					if (bitmap != null) {
						if (memCache != null) {
							memCache.put(filePath, bitmap);
						}
						return bitmap;
					}

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

	@Override
	protected void onPostExecute(Bitmap bitmap) {
		super.onPostExecute(bitmap);

		ImageView image;
		if (zoomable) {
			image = new TouchImageView(context);
			if(touchListener != null){
				((TouchImageView)image).setTouchListener(touchListener);
			}
		}
		else {
			image = new ImageView(context);
			if(touchListener != null){
				image.setOnTouchListener(touchListener);
			}
		}

		ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
		image.setLayoutParams(params);

		if (bitmap != null) {
			image.setImageBitmap(bitmap);
		}
		else {
			image.setImageResource(R.drawable.no);
		}
		image.setPadding(3, 3, 3, 3);

		if (onClickListener != null) {
			image.setOnClickListener(onClickListener);
		}

		if (onLongClickListener != null) {
			image.setOnLongClickListener(onLongClickListener);
		}

		if(progressBar != null){
			parent.removeView(progressBar);
		}
		parent.addView(image);
		
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
