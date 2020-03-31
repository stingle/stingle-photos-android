package org.stingle.photos.AsyncTasks;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DecryptFilesAsyncTask extends AsyncTask<List<StingleFile>, Integer, ArrayList<File>> {

	private ProgressDialog progressDialog;
	private final Context context;
	private final File destinationFolder;
	private final OnAsyncTaskFinish onFinishListener;
	private int folder = SyncManager.FOLDER_MAIN;
	private String folderId = null;

	private boolean performMediaScan = false;

	public DecryptFilesAsyncTask(Activity context, File destinationFolder) {
		this(context, destinationFolder, null);
	}

	public DecryptFilesAsyncTask(Context context, File destinationFolder, OnAsyncTaskFinish onFinishListener) {
		this.context = context;
		this.destinationFolder = destinationFolder;
		this.onFinishListener = onFinishListener;
	}

	public void setPerformMediaScan(boolean performMediaScan) {
		this.performMediaScan = performMediaScan;
	}

	public void setFolder(int folder) {
		this.folder = folder;
	}
	public void setFolderId(String folderId) {
		this.folderId = folderId;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		progressDialog = new ProgressDialog(context);
		progressDialog.setCancelable(true);
		progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				DecryptFilesAsyncTask.this.cancel(false);
				Helpers.releaseWakeLock((Activity)context);
			}
		});
		progressDialog.setMessage(context.getString(R.string.decrypting_files));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.show();

		Helpers.acquireWakeLock((Activity)context);
	}

	@Override
	protected ArrayList<File> doInBackground(List<StingleFile>... params) {
		List<StingleFile> filesToDecrypt = params[0];
		ArrayList<File> decryptedFiles = new ArrayList<File>();

		progressDialog.setMax(filesToDecrypt.size());
		destinationFolder.mkdirs();

		for (int i = 0; i < filesToDecrypt.size(); i++) {
			StingleFile dbFile = filesToDecrypt.get(i);

			File file = null;

			if (dbFile.isLocal) {
				file = new File(FileManager.getHomeDir(context) + "/" + dbFile.filename);
			} else {
				HashMap<String, String> postParams = new HashMap<String, String>();

				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("file", dbFile.filename);
				postParams.put("thumb", "0");
				postParams.put("folder", String.valueOf(folder));


				try {
					String finalWritePath = FileManager.findNewFileNameIfNeeded(context, destinationFolder.getPath(), dbFile.filename);
					HttpsClient.downloadFile(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams, finalWritePath);
					file = new File(finalWritePath);
				} catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
					e.printStackTrace();
				}
			}

			if (file != null && file.exists() && file.isFile()) {
				String destFileName = Helpers.decryptFilename(file.getPath());

				try {
					FileInputStream inputStream = new FileInputStream(file);

					String finalWritePath = FileManager.findNewFileNameIfNeeded(context, destinationFolder.getPath(), destFileName);
					FileOutputStream outputStream = new FileOutputStream(new File(finalWritePath));

					CryptoHelpers.decryptDbFile(context, folder, folderId, dbFile.headers, false, inputStream, outputStream, null, this);

					publishProgress(i + 1);
					File decryptedFile = new File(finalWritePath);
					if (performMediaScan) {
						ShareManager.scanFile(context, decryptedFile);
					}
					decryptedFiles.add(decryptedFile);
				} catch (IOException | CryptoException e) {
					e.printStackTrace();
				}
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
		Helpers.releaseWakeLock((Activity)context);

		if (onFinishListener != null) {
			onFinishListener.onFinish(decryptedFiles);
		}
	}
}
