package com.fenritz.safecam.Files;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.fenritz.safecam.AsyncTasks.DecryptFiles;
import com.fenritz.safecam.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.GalleryActivity;
import com.fenritz.safecam.R;
import com.fenritz.safecam.Util.Helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ShareManager {

	public static void shareDbFiles(final Context context, List<StingleDbFile> dbFiles, int folder){


		DecryptFiles decFilesJob = new DecryptFiles(context, new File(context.getCacheDir().getPath() + "/"+FileManager.SHARE_CACHE_DIR+"/"), new OnAsyncTaskFinish() {
			@Override
			public void onFinish(ArrayList<File> files) {
				super.onFinish(files);
				shareFiles(context, files);
			}
		});
		decFilesJob.setFolder(folder);
		decFilesJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dbFiles);
	}

	public static void shareFiles(Context context, ArrayList<File> fileToShare) {
		if (fileToShare.size() == 1) {
			Intent share = new Intent(Intent.ACTION_SEND);
			share.setType(getMimeType(fileToShare.get(0).getPath()));

			share.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context.getApplicationContext(), "com.fenritz.safecam.shareprovider", fileToShare.get(0)));
			context.startActivity(Intent.createChooser(share, "Share Image"));
		}
		else if (fileToShare.size() > 1) {
			Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);

			//String[] mimetypes = {"image/*", "video/*"};
			//share.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
			//ClipData clipData = new ClipData();

			String mimeType = null;
			boolean sameMimeType = true;
			ArrayList<Uri> uris = new ArrayList<Uri>();
			for (int i = 0; i < fileToShare.size(); i++) {
				//clipData.addItem(new ClipData.Item());
				String thisMimeType = getMimeType(fileToShare.get(0).getPath());
				if(mimeType != null && !mimeType.equals(thisMimeType)){
					sameMimeType = false;
				}
				mimeType = thisMimeType;

				uris.add(FileProvider.getUriForFile(context.getApplicationContext(), "com.fenritz.safecam.shareprovider", fileToShare.get(i)));
			}

			if(sameMimeType) {
				share.setType(mimeType);
				Log.d("mimeType", mimeType);
			}
			else{
				share.setType("*/*");
			}

			//share.setClipData(clipData);
			share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
			context.startActivity(Intent.createChooser(share, context.getString(R.string.share)));
		}
	}

	public static String getMimeType(String url) {
		String type = null;
		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (extension != null) {
			type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
		}
		return type;
	}

	public static void scanFile(final Context context, File file) {
	    try {
	    	if( file.isFile() ) {
		        MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null, null);
	    	}
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	}
}
