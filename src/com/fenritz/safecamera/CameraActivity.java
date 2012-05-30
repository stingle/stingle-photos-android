package com.fenritz.safecamera;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.fenritz.safecamera.util.AsyncTasks.DecryptPopulateImage;
import com.fenritz.safecamera.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecamera.util.CameraPreview;
import com.fenritz.safecamera.util.Helpers;

public class CameraActivity extends Activity {

	public static final String FLASH_MODE = "flash_mode";
	public static final String PHOTO_SIZE = "photo_size";

	private CameraPreview mPreview;
	Camera mCamera;
	int numberOfCameras;
	int cameraCurrentlyLocked;
	FrameLayout.LayoutParams origParams;
	private int mOrientation = -1;
	private OrientationEventListener mOrientationEventListener;

	private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
	private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
	private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
	private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;

	private ImageButton takePhotoButton;
	private ImageButton flashButton;
	private ImageView galleryButton;
	
	private File lastFile;
	private Drawable lastFileDrawable;

	// The first rear facing camera
	int defaultCameraId;
	private BroadcastReceiver receiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Hide the window title.
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Create a RelativeLayout container that will hold a SurfaceView,
		// and set it as the content of our activity.

		setContentView(R.layout.camera);
		
		takePhotoButton = (ImageButton) findViewById(R.id.take_photo);
		takePhotoButton.setOnClickListener(takePhoto());

		galleryButton = (ImageView) findViewById(R.id.lastPhoto);
		galleryButton.setOnClickListener(openGallery());
		lastFileDrawable = galleryButton.getDrawable();

		flashButton = (ImageButton) findViewById(R.id.flashButton);
		flashButton.setOnClickListener(toggleFlash());

		mPreview = ((CameraPreview) findViewById(R.id.camera_preview));

		showLastPhotoThumb();
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
	}

	private void showLastPhotoThumb(){
		File dir = new File(Helpers.getHomeDir(CameraActivity.this));
		final int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;
		File[] folderFiles = dir.listFiles(new FileFilter() {
			
			public boolean accept(File file) {
				File thumb = new File(Helpers.getThumbsDir(CameraActivity.this) + "/" + file.getName());
				if(file.length() < maxFileSize && file.getName().endsWith(getString(R.string.file_extension)) && thumb.exists() && thumb.isFile()){
					return true;
				}
				return false;
			}
		});
		
		Arrays.sort(folderFiles, new Comparator<File>() {
			public int compare(File lhs, File rhs) {
				if(rhs.lastModified() > lhs.lastModified()){
					return 1;
				}
				else if(rhs.lastModified() < lhs.lastModified()){
					return -1;
				}
				return 0;
			}
		});
		
		lastFile = folderFiles[0];
		final File lastFileThumb = new File(Helpers.getThumbsDir(CameraActivity.this) + "/" + lastFile.getName());
		
		DecryptPopulateImage task = new DecryptPopulateImage(CameraActivity.this, lastFileThumb.getPath(), galleryButton);
		task.setRatio(50);
		task.setOnFinish(new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				lastFileDrawable = galleryButton.getDrawable();
				changeRotation(mOrientation);
			}
		});
		task.execute();
		
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
	}
	
	private OnClickListener openGallery() {
		return new OnClickListener() {
			public void onClick(View v) {
				if(lastFile != null && lastFile.isFile()){
					Intent intent = new Intent();
					intent.setClass(CameraActivity.this, ViewImageActivity.class);
					intent.putExtra("EXTRA_IMAGE_PATH", lastFile.getPath());
					intent.putExtra("EXTRA_CURRENT_PATH", Helpers.getHomeDir(CameraActivity.this));
					startActivityForResult(intent, GalleryActivity.REQUEST_VIEW_PHOTO);
				}
			}
		};
	}

	private OnClickListener toggleFlash() {
		return new OnClickListener() {
			public void onClick(View v) {
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				String flashMode = preferences.getString(CameraActivity.FLASH_MODE, Parameters.FLASH_MODE_OFF);

				if (flashMode.equals(Parameters.FLASH_MODE_OFF)) {
					flashMode = Parameters.FLASH_MODE_ON;
					((ImageButton) findViewById(R.id.flashButton)).setImageResource(R.drawable.flash_on);
				}
				else if (flashMode.equals(Parameters.FLASH_MODE_ON)) {
					flashMode = Parameters.FLASH_MODE_OFF;
					((ImageButton) findViewById(R.id.flashButton)).setImageResource(R.drawable.flash_off);
				}

				Camera.Parameters parameters = mCamera.getParameters();
				parameters.setFlashMode(flashMode);
				mCamera.setParameters(parameters);

				preferences.edit().putString(CameraActivity.FLASH_MODE, flashMode).commit();
			}
		};
	}

	private OnClickListener takePhoto() {
		return new OnClickListener() {
			public void onClick(View v) {
				if (mCamera != null) {
					mCamera.takePicture(null, null, getPictureCallback());
					/*
					 * FrameLayout.LayoutParams params =
					 * (FrameLayout.LayoutParams)mPreview.getLayoutParams();
					 * origParams = params; params.width = 1024; params.height =
					 * 768;
					 * 
					 * mPreview.setLayoutParams(params);
					 */
				}
			}
		};
	}

	class ResumePreview extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if (mCamera != null) {
				// mPreview.setLayoutParams(origParams);
				mCamera.startPreview();
			}
		}
	}

	private PictureCallback getPictureCallback() {

		return new PictureCallback() {

			public void onPictureTaken(byte[] data, Camera camera) {
				String filename = Helpers.getFilename(CameraActivity.this, Helpers.JPEG_FILE_PREFIX);

				new EncryptAndWriteFile(filename).execute(data);
				new EncryptAndWriteThumb(CameraActivity.this, filename, new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						showLastPhotoThumb();
					}
				}).execute(data);

				Handler myHandler = new ResumePreview();
				myHandler.sendMessageDelayed(myHandler.obtainMessage(), 500);
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();

		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);

		final SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);

		// Open the default i.e. the first rear facing camera.
		mCamera = Camera.open();

		List<Size> mSupportedPictureSizes = mCamera.getParameters().getSupportedPictureSizes();
		int photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE, 0);
		Size seletectedSize = mSupportedPictureSizes.get(photoSizeIndex);

		// Set flash mode from preferences and update button accordingly
		String flashMode = preferences.getString(CameraActivity.FLASH_MODE, Parameters.FLASH_MODE_OFF);

		if (flashMode.equals(Parameters.FLASH_MODE_OFF)) {
			((ImageButton) findViewById(R.id.flashButton)).setImageResource(R.drawable.flash_off);
		}
		else if (flashMode.equals(Parameters.FLASH_MODE_ON)) {
			((ImageButton) findViewById(R.id.flashButton)).setImageResource(R.drawable.flash_on);
		}

		Camera.Parameters parameters = mCamera.getParameters();
		parameters.setFlashMode(flashMode);
		parameters.setPictureSize(seletectedSize.width, seletectedSize.height);

		if (mOrientationEventListener == null) {
			mOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {

				@Override
				public void onOrientationChanged(int orientation) {
					if (orientation == ORIENTATION_UNKNOWN){
						return;
					}
					
					// determine our orientation based on sensor response
					int lastOrientation = mOrientation;

					if (orientation >= 315 || orientation < 45) {
						if (mOrientation != ORIENTATION_PORTRAIT_NORMAL) {
							mOrientation = ORIENTATION_PORTRAIT_NORMAL;
						}
					}
					else if (orientation < 315 && orientation >= 225) {
						if (mOrientation != ORIENTATION_LANDSCAPE_NORMAL) {
							mOrientation = ORIENTATION_LANDSCAPE_NORMAL;
						}
					}
					else if (orientation < 225 && orientation >= 135) {
						if (mOrientation != ORIENTATION_PORTRAIT_INVERTED) {
							mOrientation = ORIENTATION_PORTRAIT_INVERTED;
						}
					}
					else { // orientation <135 && orientation > 45
						if (mOrientation != ORIENTATION_LANDSCAPE_INVERTED) {
							mOrientation = ORIENTATION_LANDSCAPE_INVERTED;
						}
					}

					if (lastOrientation != mOrientation) {
						Camera.Parameters parameters = mCamera.getParameters();
						parameters.setRotation(changeRotation(mOrientation));
						mCamera.setParameters(parameters);
					}
				}
			};
		}
		if (mOrientationEventListener.canDetectOrientation()) {
			mOrientationEventListener.enable();
		}
		mCamera.setParameters(parameters);

		cameraCurrentlyLocked = defaultCameraId;
		mPreview.setCamera(mCamera);
	}

	@Override
	protected void onPause() {
		super.onPause();

		Helpers.checkLoginedState(this);
		Helpers.setLockedTime(this);

		if (mOrientationEventListener != null) {
			mOrientationEventListener.disable();
			mOrientationEventListener = null;
		}

		// Because the Camera object is a shared resource, it's very
		// important to release it when the activity is paused.
		if (mCamera != null) {
			mPreview.setCamera(null);
			mCamera.release();
			mCamera = null;
		}
	}

	/**
	 * Performs required action to accommodate new orientation
	 * 
	 * @param orientation
	 * @param lastOrientation
	 */
	private int changeRotation(int orientation) {
		int rotation = 0;
		switch (orientation) {
			case ORIENTATION_PORTRAIT_NORMAL:
				galleryButton.setImageDrawable(getRotatedImage(lastFileDrawable, 270));
				flashButton.setImageDrawable(getRotatedImage(R.drawable.flash_off, 270));
				rotation = 90;
				break;
			case ORIENTATION_LANDSCAPE_NORMAL:
				galleryButton.setImageDrawable(getRotatedImage(lastFileDrawable, 0));
				flashButton.setImageResource(R.drawable.flash_off);
				rotation = 0;
				break;
			case ORIENTATION_PORTRAIT_INVERTED:
				galleryButton.setImageDrawable(getRotatedImage(lastFileDrawable, 90));
				flashButton.setImageDrawable(getRotatedImage(R.drawable.flash_off, 90));
				rotation = 270;
				break;
			case ORIENTATION_LANDSCAPE_INVERTED:
				galleryButton.setImageDrawable(getRotatedImage(lastFileDrawable, 180));
				flashButton.setImageDrawable(getRotatedImage(R.drawable.flash_off, 180));
				rotation = 180;
				break;
		}

		return rotation;
	}

	/**
	 * Rotates given Drawable
	 * 
	 * @param drawableId
	 *            Drawable Id to rotate
	 * @param degrees
	 *            Rotate drawable by Degrees
	 * @return Rotated Drawable
	 */
	private Drawable getRotatedImage(int drawableId, int degrees) {
		Bitmap original = BitmapFactory.decodeResource(getResources(), drawableId);
		Matrix matrix = new Matrix();
		matrix.postRotate(degrees);

		Bitmap rotated = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
		return new BitmapDrawable(rotated);
	}
	
	private Drawable getRotatedImage(Drawable drawable, int degrees) {
		Bitmap original = ((BitmapDrawable)drawable).getBitmap();
		Matrix matrix = new Matrix();
		matrix.postRotate(degrees);

		Bitmap rotated = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
		return new BitmapDrawable(rotated);
	}

	public class EncryptAndWriteFile extends AsyncTask<byte[], Void, Void> {

		private final String filename;
		private ProgressDialog progressDialog;

		public EncryptAndWriteFile() {
			this(null);
		}

		public EncryptAndWriteFile(String pFilename) {
			super();
			if (pFilename == null) {
				pFilename = Helpers.getFilename(CameraActivity.this, Helpers.JPEG_FILE_PREFIX);
			}

			filename = pFilename;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = ProgressDialog.show(CameraActivity.this, "", getString(R.string.encrypting_photo), false, false);
		}

		@Override
		protected Void doInBackground(byte[]... params) {
			try {
				FileOutputStream out = new FileOutputStream(Helpers.getHomeDir(CameraActivity.this) + "/" + filename);
				Helpers.getAESCrypt(CameraActivity.this).encrypt(params[0], out);
			}
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			// new EncryptAndWriteThumb(filename).execute(params[0]);
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			progressDialog.dismiss();
		}

	}

	public class EncryptAndWriteThumb extends AsyncTask<byte[], Void, Void> {

		private final String fileName;
		private final Context context;
		private OnAsyncTaskFinish onFinish;

		public EncryptAndWriteThumb(Context pContext) {
			this(pContext, null, null);
		}

		public EncryptAndWriteThumb(Context pContext, String pFilename) {
			this(pContext, pFilename, null);
		}
		
		public EncryptAndWriteThumb(Context pContext, String pFilename, OnAsyncTaskFinish onFinish) {
			super();
			context = pContext;
			if (pFilename == null) {
				pFilename = Helpers.getFilename(context, Helpers.JPEG_FILE_PREFIX);
			}
			if(onFinish != null){
				this.onFinish = onFinish;
			}

			fileName = pFilename;
		}

		@Override
		protected Void doInBackground(byte[]... params) {
			try {
				Helpers.generateThumbnail(context, params[0], fileName);
			}
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			
			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.camera_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case R.id.settings:
				Intent intent = new Intent();
				intent.setClass(CameraActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.photo_size:
				if (mCamera == null) {
					return false;
				}

				final List<Size> mSupportedPictureSizes = mCamera.getParameters().getSupportedPictureSizes();

				CharSequence[] listEntries = new CharSequence[mSupportedPictureSizes.size()];

				for (int i = 0; i < mSupportedPictureSizes.size(); i++) {
					Camera.Size size = mSupportedPictureSizes.get(i);
					listEntries[i] = String.valueOf(size.width) + "x" + String.valueOf(size.height);
				}

				final SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				int photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE, 0);

				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.photo_size_choose));
				builder.setSingleChoiceItems(listEntries, photoSizeIndex, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						preferences.edit().putInt(CameraActivity.PHOTO_SIZE, item).commit();

						mPreview.setCamera(null);

						Camera.Parameters parameters = mCamera.getParameters();
						parameters.setPictureSize(mSupportedPictureSizes.get(item).width, mSupportedPictureSizes.get(item).height);
						mCamera.setParameters(parameters);

						dialog.dismiss();
					}
				}).show();

				return true;
			case R.id.logout:
				Helpers.logout(CameraActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

}
