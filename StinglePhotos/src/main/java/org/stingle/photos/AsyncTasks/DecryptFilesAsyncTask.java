package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Crypto.CryptoProgress;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.ImportedIdsDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DecryptFilesAsyncTask extends AsyncTask<List<StingleDbFile>, Integer, ArrayList<File>> {

	private ProgressDialog progressDialog;
	private final Context context;
	private final OnAsyncTaskFinish onFinishListener;
	private int set = SyncManager.GALLERY;
	private String albumId = null;

	private boolean performMediaScan = false;
	private boolean insertIntoGallery = false;

	public DecryptFilesAsyncTask(Activity context) {
		this(context, null);
	}

	public DecryptFilesAsyncTask(Context context, OnAsyncTaskFinish onFinishListener) {
		this.context = context;
		this.onFinishListener = onFinishListener;
	}

	public void setPerformMediaScan(boolean performMediaScan) {
		this.performMediaScan = performMediaScan;
	}

	public void setInsertIntoGallery(boolean insertIntoGallery){
		this.insertIntoGallery = insertIntoGallery;
	}

	public void setSet(int set) {
		this.set = set;
	}
	public void setAlbumId(String albumId) {
		this.albumId = albumId;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(context);
		progressDialog.setCancelable(true);
		progressDialog.setOnCancelListener(dialog -> {
			DecryptFilesAsyncTask.this.cancel(false);
			Helpers.releaseWakeLock((Activity)context);
		});
		progressDialog.setMessage(context.getString(R.string.decrypting_files));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMax(100);
		progressDialog.show();

		Helpers.acquireWakeLock((Activity)context);
	}

	@Override
	protected ArrayList<File> doInBackground(List<StingleDbFile>... params) {
		List<StingleDbFile> filesToDecrypt = params[0];
		ArrayList<File> decryptedFiles = new ArrayList<File>();

		ImportedIdsDb db = new ImportedIdsDb(context);
		File destinationFolder = new File(context.getCacheDir().getPath() + "/"+FileManager.SHARE_CACHE_DIR+"/");



		destinationFolder.mkdirs();

		for (int i = 0; i < filesToDecrypt.size(); i++) {
			StingleDbFile dbFile = filesToDecrypt.get(i);
			final int currentItemNumber = i;
			if(dbFile == null){
				continue;
			}

			File file = null;

			if (dbFile.isLocal) {
				file = new File(FileManager.getHomeDir(context) + "/" + dbFile.filename);
			}
			else {
				publishProgress(0, currentItemNumber+1, filesToDecrypt.size(), 0);
				HashMap<String, String> postParams = new HashMap<String, String>();

				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("file", dbFile.filename);
				postParams.put("thumb", "0");
				postParams.put("set", String.valueOf(set));


				try {
					String finalWritePath = FileManager.findNewFileNameIfNeeded(context, destinationFolder.getPath(), dbFile.filename);
					HttpsClient.downloadFile(StinglePhotosApplication.getApiUrl() + context.getString(R.string.download_file_path), postParams, finalWritePath, new HttpsClient.OnUpdateProgress() {
						@Override
						public void onUpdate(int progress) {
							publishProgress(0, currentItemNumber+1, filesToDecrypt.size(), progress);
						}
					});
					file = new File(finalWritePath);
				} catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
					e.printStackTrace();
				}
			}

			if (file != null && file.exists() && file.isFile()) {
				try {
					Crypto.Header headers = CryptoHelpers.decryptFileHeaders(context, set, albumId, dbFile.headers, false);
					FileInputStream inputStream = new FileInputStream(file);

					OutputStream outputStream;
					String finalWritePath = null;

					if(insertIntoGallery){
						ContentResolver cr = context.getContentResolver();

						ContentValues values = new ContentValues();

						values.put(MediaStore.MediaColumns.TITLE, headers.filename);
						values.put(MediaStore.MediaColumns.DISPLAY_NAME, headers.filename);
						values.put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis());
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							values.put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis());
						}

						Uri uri = null;

						try {
							if(headers.fileType == Crypto.FILE_TYPE_PHOTO) {
								uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
							}
							else if(headers.fileType == Crypto.FILE_TYPE_VIDEO) {
								uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
							}
							else{
								continue;
							}
							long mediaId = ContentUris.parseId(uri);

							db.insertImportedId(mediaId);
							outputStream = cr.openOutputStream(uri);
						}
						catch (Exception e){
							continue;
						}
					}
					else {
						finalWritePath = FileManager.findNewFileNameIfNeeded(context, destinationFolder.getPath(), headers.filename);
						outputStream = new FileOutputStream(new File(finalWritePath));
					}

					publishProgress(1, currentItemNumber+1, filesToDecrypt.size(), 0);


					CryptoProgress progress = new CryptoProgress(headers.dataSize){
						@Override
						public void setProgress(long pCurrent){
							super.setProgress(pCurrent);
							int progress = (int) (100 * pCurrent / getTotal());
							publishProgress(1, currentItemNumber+1, filesToDecrypt.size(), progress);
						}
					};

					CryptoHelpers.decryptDbFile(context, set, albumId, dbFile.headers, false, inputStream, outputStream, progress, this);

					if(finalWritePath != null) {
						File decryptedFile = new File(finalWritePath);
						if (performMediaScan) {
							ShareManager.scanFile(context, decryptedFile);
						}

						decryptedFiles.add(decryptedFile);
					}

					if (!dbFile.isLocal) {
						file.delete();
					}
				} catch (IOException | CryptoException e) {
					e.printStackTrace();
				}
			}


			if (isCancelled()) {
				break;
			}
		}

		db.close();

		return decryptedFiles;
	}

	/**
	 * 0 - 0=downloading, 1=decrypting
	 * 1 - current file number
	 * 2 - all files count
	 * 3 - operation progress 0-100
	 * @param val
	 */
	@Override
	protected void onProgressUpdate(Integer... val) {
		super.onProgressUpdate(val);
		if(val[0] == 0){
			progressDialog.setMessage(context.getString(R.string.downloading_file, String.valueOf(val[1]), String.valueOf(val[2])));
			progressDialog.setProgress(val[3]);
		}
		if(val[0] == 1){
			progressDialog.setMessage(context.getString(R.string.decrypting_file, String.valueOf(val[1]), String.valueOf(val[2])));
			progressDialog.setProgress(val[3]);
		}
	}

	@Override
	protected void onCancelled() {
		super.onCancelled();

		this.onPostExecute(null);
	}

	@Override
	protected void onPostExecute(ArrayList<File> decryptedFiles) {
		super.onPostExecute(decryptedFiles);

		progressDialog.dismiss();
		Helpers.releaseWakeLock((Activity)context);

		if (onFinishListener != null) {
			onFinishListener.onFinish(decryptedFiles);
		}
	}
}
