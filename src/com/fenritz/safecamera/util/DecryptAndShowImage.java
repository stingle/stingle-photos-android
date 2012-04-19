package com.fenritz.safecamera.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;

import com.fenritz.safecamera.R;
import com.fenritz.safecamera.util.AESCrypt.CryptoProgress;
import com.fenritz.safecamera.widget.TouchImageView;

public class DecryptAndShowImage extends AsyncTask<Void, Integer, Bitmap> {
	
	public static final int IMAGE_ID = 10001;
	
	private final String filePath;
	
	private Context context; 
	private final ViewGroup parent;
	private ProgressBar progressBar;
	private final OnClickListener onClickListener;
	private final OnLongClickListener onLongClickListener;
	private final MemoryCache memCache;
	private boolean zoomable = false;
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent){
		this(pFilePath, pParent, null, null, null, false);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, MemoryCache pMemCache){
		this(pFilePath, pParent, null, null, pMemCache, false);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener, OnLongClickListener pOnLongClickListener){
		this(pFilePath, pParent, pOnClickListener, pOnLongClickListener, null, false);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener, OnLongClickListener pOnLongClickListener, MemoryCache pMemCache, boolean pZoomable){
		super();
		filePath = pFilePath;
		parent = pParent;
		onClickListener = pOnClickListener;
		onLongClickListener = pOnLongClickListener;
		memCache = pMemCache;
		zoomable = pZoomable;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		context = parent.getContext();
		
		progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
		//progressBar = new ProgressBar(context);
		progressBar.setProgress(0);
		LayoutParams layoutParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		layoutParams.gravity = Gravity.CENTER;
		layoutParams.setMargins(10, 10, 10, 10);
		progressBar.setLayoutParams(layoutParams);
		
		parent.removeAllViews();
		parent.addView(progressBar);
	}
	
	@Override
	protected Bitmap doInBackground(Void... params) {
		if (memCache != null) {
			Bitmap cachedBitmap = memCache.get(filePath);
			if(cachedBitmap != null){
				return cachedBitmap;
			}
		}
		File thumbFile = new File(filePath);
		if(thumbFile.exists() && thumbFile.isFile()) {
			try {
				FileInputStream input = new FileInputStream(filePath);
				AESCrypt.CryptoProgress progress = new CryptoProgress(input.getChannel().size()){
					@Override
					public void setProgress(long pCurrent) {
						super.setProgress(pCurrent);
						int newProgress = this.getProgressPercents();
						publishProgress(newProgress);
					}
				};
				
				byte[] decryptedData = Helpers.getAESCrypt(parent.getContext()).decrypt(input, progress, this);

				if (decryptedData != null) {
					Bitmap bitmap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.length);
					decryptedData = null;
					if(bitmap != null){
						if(memCache != null){
							memCache.put(filePath, bitmap);
						}
						return bitmap;
					}
					
				}
				else{
					Log.d("sc", "Unable to decrypt: " + filePath);
				}
				
			}
			catch (FileNotFoundException e) { }
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
		if(zoomable){
			image = new TouchImageView(context);
		}
		else{
			image = new ImageView(context);
		}
		
		image.setId(IMAGE_ID);
		if(bitmap != null){
			image.setImageBitmap(bitmap);
		}
		else{
			image.setImageResource(R.drawable.no);
		}
		image.setPadding(3, 3, 3, 3);
		
		if(onClickListener != null){
			image.setOnClickListener(onClickListener);
		}
		
		if(onLongClickListener != null){
			image.setOnLongClickListener(onLongClickListener);
		}
		
		parent.removeView(progressBar);
		parent.addView(image);
	}
	
	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
		
		progressBar.setProgress(values[0]);
	}
}
