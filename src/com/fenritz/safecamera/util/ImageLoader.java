package com.fenritz.safecamera.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Map;
import java.util.Stack;
import java.util.WeakHashMap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ProgressBar;

import com.fenritz.safecamera.R;
import com.fenritz.safecamera.SafeCameraActivity;

public class ImageLoader {

	public MemoryCache memoryCache = new MemoryCache();
	int stub_id;

	private final Map<ImageView, String> imageViews = Collections.synchronizedMap(new WeakHashMap<ImageView, String>());

	public ImageLoader(Context context) {
		// Make the background thead low priority. This way it will not affect
		// the UI performance
		photoLoaderThread.setPriority(Thread.NORM_PRIORITY - 1);
	}

	public void DisplayImage(String filePath, Activity activity, ImageView imageView, int stid) {

		stub_id = stid;
		imageViews.put(imageView, filePath);
		Bitmap bitmap = memoryCache.get(filePath);
		if (bitmap != null) {
			imageView.setImageBitmap(bitmap);
		}
		else {
			queuePhoto(filePath, activity, imageView);
			imageView.setImageResource(R.drawable.loading);
			imageView.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.loading));
			imageView.setScaleType(ScaleType.CENTER);
			// imageView.setImageResource(stub_id);
		}
	}

	private void queuePhoto(String filePath, Activity activity, ImageView imageView) {
		// This ImageView may be used for other images before. So there may be
		// some old tasks in the queue. We need to discard them.
		photosQueue.Clean(imageView);
		PhotoToLoad p = new PhotoToLoad(filePath, imageView);
		synchronized (photosQueue.photosToLoad) {
			photosQueue.photosToLoad.push(p);
			photosQueue.photosToLoad.notifyAll();
		}

		// start thread if it's not started yet
		if (photoLoaderThread.getState() == Thread.State.NEW) photoLoaderThread.start();
	}

	private Bitmap getBitmap(String filePath) {
		File thumbFile = new File(filePath);
		if(thumbFile.exists() && thumbFile.isFile()) {
			try {
				
				FileInputStream input = new FileInputStream(filePath);
				byte[] decryptedData = Helpers.getAESCrypt().decrypt(input);

				if (decryptedData != null) {
					Bitmap bitmap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.length);
					if(bitmap != null){
						return bitmap;
					}
					
				}
				else{
					Log.d("sc", "Unable to decrypt: " + filePath);
				}
				
			}
			catch (FileNotFoundException e) { }
		}
		
		return null;
	}

	// Task for the queue
	private class PhotoToLoad {
		public String filePath;
		public ImageView imageView;

		public PhotoToLoad(String pFilePath, ImageView pImageView) {
			filePath = pFilePath;
			imageView = pImageView;
		}
	}

	PhotosQueue photosQueue = new PhotosQueue();

	public void stopThread() {
		photoLoaderThread.interrupt();
	}

	// stores list of photos to download
	class PhotosQueue {
		private final Stack<PhotoToLoad> photosToLoad = new Stack<PhotoToLoad>();

		// removes all instances of this ImageView
		public void Clean(ImageView image) {
			for (int j = 0; j < photosToLoad.size();) {
				if (photosToLoad.get(j).imageView == image)
					photosToLoad.remove(j);
				else
					++j;
			}
		}
	}

	class PhotosLoader extends Thread {
		@Override
		public void run() {
			try {
				while (true) {
					// thread waits until there are any images to load in the
					// queue
					if (photosQueue.photosToLoad.size() == 0) synchronized (photosQueue.photosToLoad) {
						photosQueue.photosToLoad.wait();
					}
					if (photosQueue.photosToLoad.size() != 0) {
						PhotoToLoad photoToLoad;
						synchronized (photosQueue.photosToLoad) {
							photoToLoad = photosQueue.photosToLoad.pop();
						}
						String tag = imageViews.get(photoToLoad.imageView);
						if (tag != null && tag.equals(photoToLoad.filePath)) {
							Activity a = (Activity) photoToLoad.imageView.getContext();
							
							ProgressBar mProgress = new ProgressBar(a);
							
							Bitmap bmp = getBitmap(photoToLoad.filePath);
							BitmapDisplayer bd = new BitmapDisplayer(bmp, photoToLoad.imageView);
							memoryCache.put(photoToLoad.filePath, bmp);
							
							a.runOnUiThread(bd);
						}
					}
					if (Thread.interrupted()) break;
				}
			}
			catch (InterruptedException e) {
				// allow thread to exit
			}
		}
	}

	PhotosLoader photoLoaderThread = new PhotosLoader();

	// Used to display bitmap in the UI thread
	class BitmapDisplayer implements Runnable {
		Bitmap bitmap;
		ImageView imageView;

		public BitmapDisplayer(Bitmap b, ImageView i) {
			bitmap = b;
			imageView = i;
		}

		public void run() {
			if (bitmap != null) {
				imageView.setImageBitmap(bitmap);
			}
			else {
				imageView.setImageResource(stub_id);
			}
			imageView.clearAnimation();
			imageView.setScaleType(ScaleType.FIT_CENTER);
		}
	}

	public void clearCache() {
		memoryCache.clear();
	}

}
