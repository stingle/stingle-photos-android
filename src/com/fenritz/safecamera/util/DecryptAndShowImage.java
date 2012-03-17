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
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;

import com.fenritz.safecamera.util.AESCrypt.CryptoProgress;

public class DecryptAndShowImage extends AsyncTask<Void, Integer, Bitmap> {
	
	private final String filePath;
	
	private Context context; 
	private final ViewGroup parent;
	private ProgressBar progressBar;
	private final OnClickListener onClickListener;
	private final MemoryCache memCache;
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent){
		this(pFilePath, pParent, null, null);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, MemoryCache pMemCache){
		this(pFilePath, pParent, null, pMemCache);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener){
		this(pFilePath, pParent, pOnClickListener, null);
	}
	
	public DecryptAndShowImage(String pFilePath, ViewGroup pParent, OnClickListener pOnClickListener, MemoryCache pMemCache){
		super();
		filePath = pFilePath;
		parent = pParent;
		onClickListener = pOnClickListener;
		memCache = pMemCache;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		context = parent.getContext();
		
		progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
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
		Bitmap cachedBitmap;
		if (memCache != null && (cachedBitmap = memCache.get(filePath)) != null) {
			return cachedBitmap;
		}
		else{
			File thumbFile = new File(filePath);
			if(thumbFile.exists() && thumbFile.isFile()) {
				try {
					FileInputStream input = new FileInputStream(filePath);
					AESCrypt.CryptoProgress progress = new CryptoProgress(input.getChannel().size()){
						@Override
						public void setProgress(long pCurrent) {
							super.setProgress(pCurrent);
							int newProgress = this.getProgress();
							publishProgress(newProgress);
						}
					};
					byte[] decryptedData = Helpers.getAESCrypt().decrypt(input, progress);
	
					if (decryptedData != null) {
						Bitmap bitmap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.length);
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
		}
		return null;
	}
	
	@Override
	protected void onPostExecute(Bitmap bitmap) {
		super.onPostExecute(bitmap);
		
		ImageView image = new ImageView(context);
		
		image.setImageBitmap(bitmap);
		
		if(onClickListener != null){
			image.setOnClickListener(onClickListener);
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
