package com.fenritz.safecam.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.apache.sanselan.formats.tiff.write.TiffOutputDirectory;
import org.apache.sanselan.formats.tiff.write.TiffOutputField;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;

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

import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;

public class AsyncTasks {
	public static abstract class OnAsyncTaskFinish {
		public void onFinish(){}
		public void onFinish(ArrayList<File> files){
			onFinish();
		}
		public void onFinish(Integer result){
			onFinish();
		}
	}
	
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
					byte[] decryptedData = Helpers.getAESCrypt(context).decrypt(input, null, this);

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

		
		private final Activity activity;
		private final OnAsyncTaskFinish finishListener;
		private PowerManager.WakeLock wl;
		private boolean secureDelete = false;

		public DeleteFiles(Activity activity){
			this(activity, null, false);
		}
		
		public DeleteFiles(Activity activity, OnAsyncTaskFinish finishListener){
			this(activity, finishListener, false);
		}
		
		public DeleteFiles(Activity activity, OnAsyncTaskFinish finishListener, boolean secureDelete){
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
			ArrayList<File> filesToDelete = params[0];
			progressBarMain.setMax(filesToDelete.size());
			progressMainCount.setText("0/"+ String.valueOf(filesToDelete.size()));
			
			for (int i = 0; i < filesToDelete.size(); i++) {
				File file = filesToDelete.get(i);
				Bundle fileProgress = new Bundle();
				fileProgress.putString("currentFileName", file.getName());
				publishProgress(fileProgress);
				if (file.exists()){
					if(file.isFile()) {
						if(secureDelete){
							secureDelete(file);
						}
						else{
							file.delete();
						}
						
						broadcastDeletedFile(file);
						
						File thumb = new File(Helpers.getThumbsDir(activity) + "/" + Helpers.getThumbFileName(file));

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
					else if(file.isDirectory()){
						deleteFileFolder(file);
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

			return filesToDelete;
		}
		
		private void broadcastDeletedFile(File file){
			Helpers.rescanDeletedFile(activity, file);
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
				Log.d(SafeCameraApplication.TAG, "Unable to secure wipe "+file.getAbsolutePath());
			}
			
			return false;
		}
		
		private void deleteFileFolder(File fileOrDirectory) {
		    if (fileOrDirectory.isDirectory()){
		        for (File child : fileOrDirectory.listFiles()){
		        	deleteFileFolder(child);
		        }
		    }

		    if(secureDelete && fileOrDirectory.isFile()){
		    	secureDelete(fileOrDirectory);
		    }
		    else{
		    	fileOrDirectory.delete();
		    }
		    
		    broadcastDeletedFile(fileOrDirectory);
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
	
	public static class MoveFiles extends AsyncTask<ArrayList<File>, Integer, Void> {

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final File destination;
		private final OnAsyncTaskFinish finishListener;
		private PowerManager.WakeLock wl;

		public MoveFiles(Activity activity, File destination){
			this(activity, destination, null);
		}
		
		public MoveFiles(Activity activity, File destination, OnAsyncTaskFinish finishListener){
			this.activity = activity;
			this.finishListener = finishListener;
			this.destination = destination;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					MoveFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.moving_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
			
			PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "move");
			wl.acquire();
		}

		@Override
		protected Void doInBackground(ArrayList<File>... params) {
			ArrayList<File> filesToMove = params[0];
			progressDialog.setMax(filesToMove.size());
			for (int i = 0; i < filesToMove.size(); i++) {
				File file = filesToMove.get(i);
				if (file.exists() && file.isFile()) {
					File thumb = new File(Helpers.getThumbsDir(activity) + "/" + Helpers.getThumbFileName(file));
					
					File newPath = new File(destination, file.getName());
					file.renameTo(new File(destination, file.getName()));
					
					if (thumb.exists() && thumb.isFile()) {
						thumb.renameTo(new File(Helpers.getThumbsDir(activity), Helpers.getThumbFileName(newPath)));
					}
				}

				publishProgress(i + 1);

				if (isCancelled()) {
					break;
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
	
	public static class DecryptFiles extends AsyncTask<ArrayList<File>, Integer, ArrayList<File>> {

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final String destinationFolder;
		private final OnAsyncTaskFinish finishListener;
		private PowerManager.WakeLock wl;

		public DecryptFiles(Activity activity, String pDestinationFolder) {
			this(activity, pDestinationFolder, null);
		}

		public DecryptFiles(Activity activity, String pDestinationFolder, OnAsyncTaskFinish pFinishListener) {
			this.activity = activity;
			destinationFolder = pDestinationFolder;
			finishListener = pFinishListener;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					DecryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.decrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
			
			PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "decrypt");
			wl.acquire();
		}

		@Override
		protected ArrayList<File> doInBackground(ArrayList<File>... params) {
			ArrayList<File> filesToDecrypt = params[0];
			ArrayList<File> decryptedFiles = new ArrayList<File>();

			progressDialog.setMax(filesToDecrypt.size());

			for (int i = 0; i < filesToDecrypt.size(); i++) {
				File file = filesToDecrypt.get(i);
				if (file.exists() && file.isFile()) {
					String destFileName = Helpers.decryptFilename(activity, file.getName());
					
					try {
						FileInputStream inputStream = new FileInputStream(file);
						
						String finalWritePath = Helpers.findNewFileNameIfNeeded(activity, destinationFolder, destFileName);
						FileOutputStream outputStream = new FileOutputStream(new File(finalWritePath));

						Helpers.getAESCrypt(activity).decrypt(inputStream, outputStream, null, this);

						publishProgress(i+1);
						File decryptedFile = new File(finalWritePath);
						Helpers.scanFile(activity, decryptedFile);
						decryptedFiles.add(decryptedFile);
					}
					catch (FileNotFoundException e) { }
				}

				if (isCancelled()) {
					break;
				}
			}

			return decryptedFiles;
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
		protected void onPostExecute(ArrayList<File> decryptedFiles) {
			super.onPostExecute(decryptedFiles);

			progressDialog.dismiss();
			wl.release();
			
			if (finishListener != null) {
				finishListener.onFinish(decryptedFiles);
			}
		}
	}
	
	public static class ReEncryptFiles extends AsyncTask<HashMap<String, Object>, Integer, ArrayList<File>> {

		private ProgressDialog progressDialog;
		private final OnAsyncTaskFinish finishListener;
		private final Activity activity;
		private PowerManager.WakeLock wl;

		public ReEncryptFiles(Activity activity, OnAsyncTaskFinish pFinishListener) {
			this.activity = activity;
			finishListener = pFinishListener;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					ReEncryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.reencrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
			
			PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "reencrypt");
			wl.acquire();
		}

		@SuppressWarnings("unchecked")
		@Override
		protected ArrayList<File> doInBackground(HashMap<String, Object>... params) {
			ArrayList<File> reencryptedFiles = new ArrayList<File>();
			String newPassword = params[0].get("newPassword").toString();
			ArrayList<File> files = (ArrayList<File>) params[0].get("files");

			try {
				newPassword = AESCrypt.byteToHex(AESCrypt.getHash(newPassword));
			}
			catch (AESCryptException e1) {
				return null;
			}
			
			AESCrypt newCrypt = Helpers.getAESCrypt(newPassword, activity);

			progressDialog.setMax(files.size());


			int counter = 0;
			for (File file : files) {
				try {
					FileInputStream inputStream = new FileInputStream(file);
					String tmpFilename = Helpers.decryptFilename(activity, file.getName());
					String tmpFilePath = Helpers.getNewDestinationPath(activity, Helpers.getHomeDir(activity) + "/.tmp/", tmpFilename, newCrypt);
					
					File tmpFile = new File(tmpFilePath);
					FileOutputStream outputStream = new FileOutputStream(tmpFile);

					if(Helpers.getAESCrypt(activity).reEncrypt(inputStream, outputStream, newCrypt, null, this)){
						reencryptedFiles.add(tmpFile);
					}
					else{
						if(tmpFile.isFile()){
							tmpFile.delete();
						}
					}
					publishProgress(++counter);
				}
				catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}

			return reencryptedFiles;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<File> reencryptedFiles) {
			super.onPostExecute(reencryptedFiles);

			progressDialog.dismiss();
			wl.release();
			
			if (finishListener != null) {
				finishListener.onFinish(reencryptedFiles);
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

						//String destFilePath = Helpers.findNewFileNameIfNeeded(activity, destinationFolder, origFile.getName())  + activity.getString(R.string.file_extension);
						String destFilePath = Helpers.getNewDestinationPath(activity, destinationFolder, origFile.getName());
						// String destFilePath =
						// findNewFileNameIfNeeded(Helpers.getHomeDir(GalleryActivity.this),
						// origFile.getName());

						FileOutputStream outputStream = new FileOutputStream(destFilePath);

						Helpers.getAESCrypt(activity).encrypt(inputStream, outputStream, null, this);
						
						File destFile = new File(destFilePath);
						String fileName = Helpers.getThumbFileName(destFile);
						
						FileInputStream in = new FileInputStream(origFile);
						ByteArrayOutputStream bytes = new ByteArrayOutputStream();
						int numRead = 0;
						byte[] buf = new byte[1024];
						while ((numRead = in.read(buf)) >= 0) {
							bytes.write(buf, 0, numRead);
						}
						in.close();
						
						System.gc();
						
						Helpers.generateThumbnail(activity, bytes.toByteArray(), fileName);
						
						publishProgress(i+1);
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					catch (IOException e) {
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
	
	public static class ImportFiles extends AsyncTask<HashMap<String, Object>, Integer, Integer> {

		public static final int STATUS_OK = 0;
		public static final int STATUS_FAIL = 1;
		public static final int STATUS_CANCEL = 2;

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final OnAsyncTaskFinish finishListener;
		private final String destinationFolder;
		private PowerManager.WakeLock wl;
		
		public ImportFiles(Activity activity, String destinationFolder){
			this(activity, destinationFolder, null);
		}
		
		public ImportFiles(Activity activity, String destinationFolder, OnAsyncTaskFinish finishListener){
			this.activity = activity;
			this.finishListener = finishListener;
			this.destinationFolder = destinationFolder;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					ImportFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.importing_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
			
			PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "import");
			wl.acquire();
		}

		@Override
		protected Integer doInBackground(HashMap<String, Object>... rparams) {
			HashMap<String, Object> params = rparams[0];
			int returnStatus = STATUS_OK;

			String[] filePaths = (String[]) params.get("filePaths");
			String password = (String) params.get("password");
			Boolean deleteAfterImport = (Boolean) params.get("deleteAfterImport");

			try {
				password = AESCrypt.byteToHex(AESCrypt.getHash(password));
			}
			catch (AESCryptException e1) {
				returnStatus = STATUS_FAIL;
				return returnStatus;
			}
			
			AESCrypt newCrypt = Helpers.getAESCrypt(password, activity);

			progressDialog.setMax(filePaths.length);
			for (int i = 0; i < filePaths.length; i++) {

				File origFile = new File(filePaths[i]);

				if (origFile.exists() && origFile.isFile()) {
					try {
						FileInputStream inputStream = new FileInputStream(origFile);

						String dstFilename = Helpers.decryptFilename(activity, origFile.getName(), newCrypt);
						String destFilePath = Helpers.getNewDestinationPath(activity, destinationFolder, dstFilename);

						FileOutputStream outputStream = new FileOutputStream(destFilePath);
						if(Helpers.getAESCrypt(activity).reEncrypt(inputStream, outputStream, newCrypt, null, this, true)){
							if (deleteAfterImport) {
								origFile.delete();
							}
						}
						else{
							File dstFile = new File(destFilePath);
							if(dstFile.isFile()){
								dstFile.delete();
							}
							returnStatus = STATUS_FAIL;
						}
						publishProgress(i+1);
					}
					catch (FileNotFoundException e) {
						returnStatus = STATUS_FAIL;
					}
				}
			}

			return returnStatus;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(STATUS_CANCEL);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
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

			try {
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

				Helpers.generateThumbnail(activity, data, Helpers.getThumbFileName(filePath));
				
				//new File(Helpers.getThumbsDir(ViewImageActivity.this) + "/" + files.get(currentPosition).getName()).delete();
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
				*/
				returnStatus = STATUS_OK;
			}
			catch (FileNotFoundException e) {}
			catch (ImageReadException e) {}
			catch (IOException e) {}
			catch (ImageWriteException e) {}

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
				Bitmap image = Helpers.decodeFile(new FileInputStream(params[0]), Helpers.getThumbSize(SafeCameraApplication.getAppContext()));
			
				//image = Helpers.getThumbFromBitmap(image, Helpers.getThumbSize(SafeCameraApplication.getAppContext()));
				
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
			Bitmap image = cache.get(params[0].getPath());
			if(image != null){
				return image;
			}
			
			try{
				// Get from MediaStore
				image = MediaStore.Images.Thumbnails.getThumbnail(SafeCameraApplication.getAppContext().getContentResolver(), imgId, MediaStore.Images.Thumbnails.MINI_KIND, null);
				if(image != null){
					image = getBitmap(image);
					cache.put(params[0].getPath(), image);
					return image;
				}
				
				// Get from File and Resize it
				image = Helpers.decodeFile(new FileInputStream(params[0]), Helpers.getThumbSize(SafeCameraApplication.getAppContext()));
				
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
