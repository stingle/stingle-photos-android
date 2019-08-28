package org.stingle.photos.Util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Video.Thumbnails;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.SecureRandom;
import java.util.ArrayList;

public class AsyncTasks {

	public static abstract class OnAsyncTaskStart {
		public void onStart(){}
	}
	
	
	public static class DecryptPopulateImage extends LimitedThreadAsyncTask<Void, Integer, Bitmap> {

		private final Context context;
		private final String filePath;
		private final ImageView image;
		private OnAsyncTaskFinish onFinish;
		private int size = 200;
		private MemoryCache memCache;


		public DecryptPopulateImage(Context context, String filePath, ImageView image) {
			this.context = context;
			this.filePath = filePath;
			this.image = image;
		}
		
		public void setSize(int size){
			this.size = size;
		}
		
		public void setCache(MemoryCache cache){
			memCache = cache;
		}
		
		public void setOnFinish(OnAsyncTaskFinish onFinish){
			this.onFinish = onFinish;
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			File file = new File(filePath);
			if (file.exists() && file.isFile()) {
				try {
					FileInputStream input = new FileInputStream(file);
					//byte[] decryptedData = Helpers.getAESCrypt(activity).decrypt(input, null, this);
					byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(input);

					if (decryptedData != null) {
						Bitmap bitmap = Helpers.decodeBitmap(decryptedData, size);
						decryptedData = null;
						if (bitmap != null) {
							if(memCache != null){
								memCache.put(filePath, bitmap);
							}
							return bitmap;
						}
					}
					else {
						Log.d("sc", "Unable to decrypt: " + filePath);
					}
				}
				catch (FileNotFoundException e) { }
				catch (IOException e) { }
				catch (CryptoException e) { }
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			super.onPostExecute(bitmap);

			if (bitmap != null) {
				image.setImageBitmap(bitmap);
				if(onFinish != null){
					onFinish.onFinish();
				}
			}
		}
	}
	
	public static class DeleteFiles extends AsyncTask<ArrayList<File>, Bundle, ArrayList<File>> {

		//private ProgressDialog progressDialog;
		
		private AlertDialog dialog;
		private ProgressBar progressBarMain;
		private ProgressBar progressBarSec;
		private TextView currentFileLabel;
		private TextView progressMainPercent;
		private TextView progressMainCount;
		private TextView progressSecPercent;

		
		private final Context activity;
		private final OnAsyncTaskFinish finishListener;
		private PowerManager.WakeLock wl;
		private boolean secureDelete = false;

		public DeleteFiles(Context activity){
			this(activity, null, false);
		}
		
		public DeleteFiles(Context activity, OnAsyncTaskFinish finishListener){
			this(activity, finishListener, false);
		}
		
		public DeleteFiles(Context activity, OnAsyncTaskFinish finishListener, boolean secureDelete){
			this.activity = activity;
			this.finishListener = finishListener;
			this.secureDelete = secureDelete;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setTitle(activity.getString(R.string.deleting_files));

			View deleteProgressView = View.inflate(activity, R.layout.dialog_delete_originals_progress, null);
			progressBarMain = (ProgressBar) deleteProgressView.findViewById(R.id.progressBarMain);
			progressBarSec = (ProgressBar) deleteProgressView.findViewById(R.id.progressBarSec);
			currentFileLabel = (TextView) deleteProgressView.findViewById(R.id.currentFile);
			progressMainPercent = (TextView) deleteProgressView.findViewById(R.id.progressMainPercent);
			progressMainCount = (TextView) deleteProgressView.findViewById(R.id.progressMainCount);
			progressSecPercent = (TextView) deleteProgressView.findViewById(R.id.progressSecPercent);

			if(!secureDelete){
				progressBarSec.setVisibility(View.GONE);
				progressSecPercent.setVisibility(View.GONE);
				currentFileLabel.setVisibility(View.GONE);
			}
			
			progressMainPercent.setText("0%");
			progressSecPercent.setText("0%");

			builder.setView(deleteProgressView);

			builder.setCancelable(true);
			builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					DeleteFiles.this.cancel(false);
				}
			});
			dialog = builder.create();
			dialog.show();
			
			
			
			/*progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					DeleteFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.deleting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			//progressDialog.setIndeterminate(false);
			
			progressDialog.show();*/
			
			PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "delete");
			wl.acquire();
		}

		@Override
		protected ArrayList<File> doInBackground(ArrayList<File>... params) {
			ArrayList<File> deletedFiles = new ArrayList<File>();
			progressBarMain.setMax(params[0].size());
			progressMainCount.setText("0/"+ String.valueOf(params[0].size()));
			
			for (int i = 0; i < params[0].size(); i++) {
				File file = params[0].get(i);
				Bundle fileProgress = new Bundle();
				fileProgress.putString("currentFileName", file.getName());
				publishProgress(fileProgress);
				if (file.exists()){
					if(file.isFile()) {
						deletedFiles.add(file);
						if(secureDelete){
							secureDelete(file);
						}
						else{
							file.delete();
						}
						
						broadcastDeletedFile(file);
						
						File thumb = new File(FileManager.getThumbsDir(activity) + "/" + file.getName());

						if (thumb.exists() && thumb.isFile()) {
							if(secureDelete){
								secureDelete(thumb);
							}
							else{
								thumb.delete();
							}
							
							broadcastDeletedFile(thumb);
						}
					}
				}

				Bundle progress = new Bundle();
				progress.putInt("type", 0);
				progress.putInt("progress", i+1);
				publishProgress(progress);
				
				if (isCancelled()) {
					break;
				}
			}

			return deletedFiles;
		}
		
		private void broadcastDeletedFile(File file){
			FileManager.rescanDeletedFile(activity, file);
		}
		
		@SuppressLint("TrulyRandom")
		protected boolean secureDelete(File file) {
			try{
				if (file.exists()) {
					long length = file.length();
					SecureRandom random = new SecureRandom();
					RandomAccessFile raf = new RandomAccessFile(file, "rws");
					raf.seek(0);
					raf.getFilePointer();
					byte[] data = new byte[64];
					long pos = 0;
					while (pos < length) {
						random.nextBytes(data);
						raf.write(data);
						pos += data.length;
						
						Bundle progress = new Bundle();
						progress.putInt("type", 1);
						progress.putInt("progress", Math.round(pos*100/length));
						publishProgress(progress);
					}
					raf.close();
					if(file.delete()){
						return true;
					}
				}
			}
			catch(IOException e){
				Log.d(StinglePhotosApplication.TAG, "Unable to secure wipe "+file.getAbsolutePath());
			}
			
			return false;
		}
		
		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Bundle... values) {
			super.onProgressUpdate(values);

			if(values[0].containsKey("currentFileName")){
				currentFileLabel.setText(values[0].getString("currentFileName"));
			}
			
			if(values[0].containsKey("type")){
			switch(values[0].getInt("type")){
				case 0:
					progressBarMain.setProgress(values[0].getInt("progress"));
					progressMainPercent.setText(String.valueOf(Math.round(progressBarMain.getProgress()*100/progressBarMain.getMax())) + "%");
					progressMainCount.setText(String.valueOf(progressBarMain.getProgress()) + "/" + String.valueOf(progressBarMain.getMax()));
					break;
				case 1:
					progressBarSec.setProgress(values[0].getInt("progress"));
					progressSecPercent.setText(String.valueOf(Math.round(progressBarSec.getProgress()*100/progressBarSec.getMax())) + "%");
					break;
				}
			}
		}

		@Override
		protected void onPostExecute(ArrayList<File> deletedFiles) {
			super.onPostExecute(deletedFiles);

			dialog.dismiss();
			wl.release();
			
			if (finishListener != null) {
				finishListener.onFinish(deletedFiles);
			}
		}
	}
	
	public static class EncryptFiles extends AsyncTask<String, Integer, Void> {

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final OnAsyncTaskFinish finishListener;
		private final String destinationFolder;
		private PowerManager.WakeLock wl;

		public EncryptFiles(Activity activity, String destinationFolder) {
			this(activity, destinationFolder, null);
		}
		
		public EncryptFiles(Activity activity, String destinationFolder, OnAsyncTaskFinish pFinishListener) {
			this.activity = activity;
			finishListener = pFinishListener;
			this.destinationFolder = destinationFolder;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					EncryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.encrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
			
			PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "encrypt");
			wl.acquire();
		}

		@Override
		protected Void doInBackground(String... params) {
			progressDialog.setMax(params.length);
			for (int i = 0; i < params.length; i++) {
				File origFile = new File(params[i]);

				if (origFile.exists() && origFile.isFile()) {
					FileInputStream inputStream;
					try {
						inputStream = new FileInputStream(origFile);

						String prefix = Helpers.GENERAL_FILE_PREFIX;
						int fileType = FileManager.getFileType(origFile.getAbsolutePath());
						switch (fileType){
							case Crypto.FILE_TYPE_PHOTO:
								prefix = Helpers.IMAGE_FILE_PREFIX;
								break;
							case Crypto.FILE_TYPE_VIDEO:
								prefix = Helpers.VIDEO_FILE_PREFIX;
								break;
						}


						String encFilePath = FileManager.getHomeDir(activity) + "/" + Helpers.getNewEncFilename();

						FileOutputStream outputStream = new FileOutputStream(encFilePath);

						byte[] fileId = StinglePhotosApplication.getCrypto().encryptFile(inputStream, outputStream, origFile.getName(), fileType, origFile.length());
						//Helpers.getAESCrypt(activity).encrypt(inputStream, outputStream, null, this);

						if(FileManager.isImageFile(origFile.getAbsolutePath())) {
							File destFile = new File(encFilePath);

							FileInputStream in = new FileInputStream(origFile);
							ByteArrayOutputStream bytes = new ByteArrayOutputStream();
							int numRead = 0;
							byte[] buf = new byte[1024];
							while ((numRead = in.read(buf)) >= 0) {
								bytes.write(buf, 0, numRead);
							}
							in.close();

							System.gc();

							Helpers.generateThumbnail(activity, bytes.toByteArray(), destFile.getName(), fileId, Crypto.FILE_TYPE_PHOTO);
						}
						else if(FileManager.isVideoFile(origFile.getAbsolutePath())){
							File destFile = new File(encFilePath);

							Bitmap thumb = ThumbnailUtils.createVideoThumbnail(origFile.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
							ByteArrayOutputStream bos = new ByteArrayOutputStream();
							thumb.compress(Bitmap.CompressFormat.PNG, 0, bos);

							Helpers.generateThumbnail(activity, bos.toByteArray(), destFile.getName(), fileId, Crypto.FILE_TYPE_VIDEO);
						}
						
						publishProgress(i+1);
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					catch (IOException e) {
						e.printStackTrace();
					} catch (CryptoException e) {
						e.printStackTrace();
					}
				}
			}

			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			wl.release();
			
			if (finishListener != null) {
				finishListener.onFinish();
			}
		}
	}
	
	public static class RotatePhoto extends AsyncTask<Void, Void, Integer> {

		public static final int STATUS_OK = 0;
		public static final int STATUS_FAIL = 1;
		public static final int STATUS_CANCEL = 2;
		
		public static final int ROTATION_CW = 0;
		public static final int ROTATION_CCW = 1;

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final OnAsyncTaskFinish finishListener;
		private final String filePath;
		private final int direction;
		private PowerManager.WakeLock wl;
		
		
		public RotatePhoto(Activity activity, String filePath, int direction){
			this(activity, filePath, direction, null);
		}
		
		public RotatePhoto(Activity activity, String filePath, int direction, OnAsyncTaskFinish finishListener){
			this.activity = activity;
			this.finishListener = finishListener;
			this.filePath = filePath;
			this.direction = direction;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					RotatePhoto.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.rotating));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.show();
			
			PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "rotate");
			wl.acquire();
		}

		@Override
		protected Integer doInBackground(Void... rparams) {
			int returnStatus = STATUS_FAIL;

			/*try {
				byte[] data = Helpers.getAESCrypt(activity).decrypt(new FileInputStream(filePath));
				int currentRotation = 1;
				
				TiffOutputSet outputSet = null;
				IImageMetadata meta = Sanselan.getMetadata(data);
				
				JpegImageMetadata metaJpg = null;
				TiffField orientationField = null;
				if(meta instanceof JpegImageMetadata){
					metaJpg = (JpegImageMetadata) meta;
				}
				
				if (null != metaJpg)
				{
					orientationField =  metaJpg.findEXIFValue(TiffConstants.EXIF_TAG_ORIENTATION);
					// note that exif might be null if no Exif metadata is found.
					TiffImageMetadata exif = metaJpg.getExif();
					if (null != exif){
						outputSet = exif.getOutputSet();
					}
				}
				if (null == outputSet){
					outputSet = new TiffOutputSet();
				}
				
				if(orientationField != null){
					currentRotation = orientationField.getIntValue();
				}
				
				int newRotation = 1;
				switch(currentRotation){
					case 1:
						//It's 0 deg now
						if(direction == ROTATION_CW){
							//Change to 90
							newRotation = 6;
						}
						else if(direction == ROTATION_CCW){
							//Change to 270
							newRotation = 8;
						}
						break;
					case 3:
						//It's 180 deg now
						if(direction == ROTATION_CW){
							//Change to 270
							newRotation = 8;
						}
						else if(direction == ROTATION_CCW){
							//Change to 90
							newRotation = 6;
						}
						break;
					case 6:
						//It's 90 deg now
						if(direction == ROTATION_CW){
							//Change to 180
							newRotation = 3;
						}
						else if(direction == ROTATION_CCW){
							//Change to 0
							newRotation = 1;
						}
						break;
					case 8:
						//It's 270 deg now
						if(direction == ROTATION_CW){
							//Change to 0
							newRotation = 1;
						}
						else if(direction == ROTATION_CCW){
							//Change to 180
							newRotation = 3;
						}
						break;
				}

				TiffOutputField rotation = TiffOutputField.create(TiffConstants.EXIF_TAG_ORIENTATION, outputSet.byteOrder, newRotation);
				TiffOutputDirectory exifDirectory = outputSet.getOrCreateRootDirectory();
				exifDirectory.removeField(TiffConstants.EXIF_TAG_ORIENTATION);
				exifDirectory.add(rotation);
				
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				(new ExifRewriter()).updateExifMetadataLossless(data, bos, outputSet);
			    data = bos.toByteArray();
			    
			    bos = null;
			    
			    FileOutputStream outputStream = new FileOutputStream(filePath);

				Helpers.getAESCrypt(activity).encrypt(data, outputStream);

				//Helpers.generateThumbnail(activity, data, Helpers.getThumbFileName(filePath));
				
				//new File(Helpers.getThumbsDir(ViewImageActivityOld.this) + "/" + files.get(currentPosition).getName()).delete();
				/*
				
				try {
					BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(data));
					Metadata metadata = ImageMetadataReader.readMetadata(stream, false);
				
					ExifIFD0Directory directory = metadata.getDirectory(ExifIFD0Directory.class);
					if(directory != null){
						int exifRotation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
						
						switch(exifRotation){
							case 3:
								// Rotate 180 deg
								currentRotation = 180;
								break;
							case 6:
								// Rotate 90 deg
								currentRotation = 90;
								break;
							case 8:
								// Rotate 270 deg
								currentRotation = 270;
								break;
						}
					}
					
				}
				catch (ImageProcessingException e) { }
				catch (IOException e) { }
				catch (MetadataException e) { }
				
				
				BitmapFactory.Options opts = new BitmapFactory.Options();
				opts.inDensity=0;
				Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
				data = null;
				
		        Matrix matrix = new Matrix();

		        if(direction == ROTATION_CW){
		        	matrix.postRotate(currentRotation + 90);
		        }
		        else if(direction == ROTATION_CCW){
		        	matrix.postRotate(currentRotation -90);
		        }

		        int density = bitmap.getDensity();

		        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		        bitmap.setDensity(density);

		        ByteArrayOutputStream os = new ByteArrayOutputStream();
		        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
		        
		        data = os.toByteArray();
				
		        FileOutputStream outputStream = new FileOutputStream(filePath);

				Helpers.getAESCrypt(activity).encrypt(data, outputStream);

				returnStatus = STATUS_OK;
			}
			catch (FileNotFoundException e) {}
			catch (ImageReadException e) {}
			catch (IOException e) {}
			catch (ImageWriteException e) {}
			*/
			return returnStatus;
		}
		
		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(STATUS_CANCEL);
		}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			wl.release();
			
			if (finishListener != null) {
				finishListener.onFinish(result);
			}
		}

	}
	
	public static class ShowImageThumb extends AsyncTask<File, Void, Bitmap> {

		private final ImageView imageView;
		private final MemoryCache cache;
		private OnAsyncTaskFinish finishListener;
		
		
		public ShowImageThumb(ImageView pImageView, MemoryCache pCache){
			this(pImageView, pCache, null);
		}
		
		public ShowImageThumb(ImageView pImageView, MemoryCache pCache, OnAsyncTaskFinish pFinishListener){
			imageView = pImageView;
			cache = pCache;
			finishListener = pFinishListener;
		}
		
		public void setOnFinish(OnAsyncTaskFinish onFinish){
			this.finishListener = onFinish;
		}
		
		@Override
		protected Bitmap doInBackground(File... params) {
			try {
				Bitmap image = Helpers.decodeFile(new FileInputStream(params[0]), Helpers.getThumbSize(StinglePhotosApplication.getAppContext()));
			
				//image = Helpers.getThumbFromBitmap(image, Helpers.getThumbSize(StinglePhotosApplication.getAppContext()));
				
				if(image != null){
					cache.put(params[0].getPath(), image);
					return image;
				}
				
				image = ThumbnailUtils.createVideoThumbnail(params[0].getPath(), Thumbnails.MICRO_KIND);
				
				if(image != null){
					cache.put(params[0].getPath(), image);
					return image;
				}
			}
			catch (FileNotFoundException e) {}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			if(result != null){
				imageView.setImageBitmap(result);
			}
			if (finishListener != null) {
				finishListener.onFinish();
			}
		}

	}
	
	public static class ShowSystemImageThumb extends AsyncTask<File, Void, Bitmap> {

		private final ImageView imageView;
		private final MemoryCache cache;
		private OnAsyncTaskFinish finishListener;
		private final int imgId;
		private final int orientation;
		
		
		public ShowSystemImageThumb(ImageView pImageView, int pImgId, int pOrientation, MemoryCache pCache){
			this(pImageView, pImgId, pOrientation, pCache, null);
		}
		
		public ShowSystemImageThumb(ImageView pImageView, int pImgId, int pOrientation, MemoryCache pCache, OnAsyncTaskFinish pFinishListener){
			imageView = pImageView;
			cache = pCache;
			imgId = pImgId;
			orientation = pOrientation;
			finishListener = pFinishListener;
		}
		
		public void setOnFinish(OnAsyncTaskFinish onFinish){
			this.finishListener = onFinish;
		}
		
		private Bitmap getBitmap(Bitmap image){
			if(orientation != 0){
				Matrix matrix = new Matrix();
				matrix.postRotate(orientation);
				image = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
			}
			return image;
		}
		
		@Override
		protected Bitmap doInBackground(File... params) {
			
			// Get from cache
			Bitmap image = (Bitmap)cache.get(params[0].getPath());
			if(image != null){
				return image;
			}
			
			try{
				// Get from MediaStore
				image = MediaStore.Images.Thumbnails.getThumbnail(StinglePhotosApplication.getAppContext().getContentResolver(), imgId, MediaStore.Images.Thumbnails.MINI_KIND, null);
				if(image != null){
					image = getBitmap(image);
					cache.put(params[0].getPath(), image);
					return image;
				}
				
				// Get from File and Resize it
				image = Helpers.decodeFile(new FileInputStream(params[0]), Helpers.getThumbSize(StinglePhotosApplication.getAppContext()));
				
				if(image != null){
					cache.put(params[0].getPath(), image);
					return image;
				}
				
				// Get from Video
				image = ThumbnailUtils.createVideoThumbnail(params[0].getPath(), Thumbnails.MINI_KIND);
				if(image != null){
					cache.put(params[0].getPath(), image);
					return image;
				}
			}
			catch(OutOfMemoryError e){}
			catch (FileNotFoundException e) {}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			if(result != null){
				imageView.setImageBitmap(result);
			}
			if (finishListener != null) {
				finishListener.onFinish();
			}
		}

	}
}
