package com.fenritz.safecam.ViewItem;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Sync.FileManager;
import com.fenritz.safecam.Sync.SyncManager;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Util.MemoryCache;
import com.fenritz.safecam.Video.StingleDataSourceFactory;
import com.fenritz.safecam.Video.StingleHttpDataSource;
import com.fenritz.safecam.Widget.ImageHolderLayout;
import com.fenritz.safecam.Widget.photoview.PhotoViewAttacher;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.FileDataSource;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class GetOriginalRemotePhotoTask extends AsyncTask<Void, Integer, Bitmap> {

	private Context context;
	private ViewItemAsyncTask.ViewItemTaskResult result;
	private ImageView image;
	private PhotoViewAttacher attacher;

	public GetOriginalRemotePhotoTask(Context context, ViewItemAsyncTask.ViewItemTaskResult result, ImageView image, PhotoViewAttacher attacher) {
		super();
		this.context = context;
		this.result = result;
		this.image = image;
		this.attacher = attacher;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", result.filename);
		postParams.put("thumb", "0");
		postParams.put("folder", String.valueOf(result.folder));

		try {
			byte[] encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);
			if(encFile == null || encFile.length == 0){
				return null;
			}

			byte[] fileBeginning = Arrays.copyOfRange(encFile, 0, Crypto.FILE_BEGGINIG_LEN);
			Log.d("beg", new String(fileBeginning, "UTF-8"));
			if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
				return null;
			}

			byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(encFile, this);

			return Helpers.decodeBitmap(decryptedData, ViewItemAsyncTask.getSize(context));
		}
		catch (NoSuchAlgorithmException | KeyManagementException | IOException | CryptoException e) {

		}

		return null;
	}


	@Override
	protected void onPostExecute(Bitmap bitmap) {
		super.onPostExecute(bitmap);

		if(bitmap != null) {
			Log.d("update", " qaq");
			//image.setImageResource(R.drawable.no);
			image.setImageBitmap(bitmap);
			attacher.update();
		}
		else{
			Log.d("noupdate", "shit qaq");
		}
	}


}
