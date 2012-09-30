package com.fenritz.safecam;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.fenritz.safecam.util.AsyncTasks.DecryptPopulateImage;
import com.fenritz.safecam.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.util.CameraPreview;
import com.fenritz.safecam.util.Helpers;
import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class CameraActivity extends SherlockActivity {

	public static final String FLASH_MODE = "flash_mode";
	public static final String TIMER_MODE = "timer_mode";
	public static final String PHOTO_SIZE = "photo_size";
	public static final String PHOTO_SIZE_FRONT = "photo_size_front";

	private CameraPreview mPreview;
	Camera mCamera;
	int cameraCurrentlyLocked;
	FrameLayout.LayoutParams origParams;
	private int mOrientation = -1;
	private OrientationEventListener mOrientationEventListener;

	private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
	private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
	private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
	private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;
	
	private static final int DEFAULT_CAMERA = 0;
	private int numberOfCameras = 1; 
	private int currentCamera = 0; 
	
	private boolean isTimerOn = false; 

	private ImageButton takePhotoButton;
	private ImageButton flashButton;
	private ImageButton timerButton;
	private ImageView galleryButton;
	
	private int timerTotalSeconds = 10; 
	private int timerTimePassed = 0;
	private boolean isTimerRunning = false;
	
	private File lastFile;
	private int lastRotation = 0;
	private int overallRotation = 0;
	
	private Timer timer;
	
	private int photoSizeIndex = 0;

	// The first rear facing camera
	int defaultCameraId;
	private BroadcastReceiver receiver;
	private AdView adView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(!checkCameraHardware()){
			finish();
			return;
		}
		
		// Hide the window title.
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.camera);
		
		Helpers.fixBackgroundRepeat(findViewById(R.id.parentLayout));
		
		takePhotoButton = (ImageButton) findViewById(R.id.take_photo);
		takePhotoButton.setOnClickListener(takePhotoClick());

		galleryButton = (ImageView) findViewById(R.id.lastPhoto);
		galleryButton.setOnClickListener(openGallery());

		flashButton = (ImageButton) findViewById(R.id.flashButton);
		flashButton.setOnClickListener(toggleFlash());
		
		timerButton = (ImageButton) findViewById(R.id.timerButton);
		timerButton.setOnClickListener(toggleTimer());
		
		findViewById(R.id.switchCamButton).setOnClickListener(switchCamera());
		findViewById(R.id.photoSizeLabel).setOnClickListener(showPhotoSizes());
		findViewById(R.id.optionsButton).setOnClickListener(showOptions());
		
		mPreview = new CameraPreview(this); 
		((LinearLayout) findViewById(R.id.camera_preview_container)).addView(mPreview);

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
		
		if(Helpers.isDemo(CameraActivity.this)){
			// Create the adView
		    adView = new AdView(this, AdSize.BANNER, getString(R.string.ad_publisher_id));
		    LinearLayout layout = (LinearLayout)findViewById(R.id.adHolder);
		    layout.addView(adView);
		    adView.loadAd(new AdRequest());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		boolean logined = Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);

		if(logined){
			startCamera();
			initOrientationListener();
			showLastPhotoThumb();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		Helpers.checkLoginedState(this);
		Helpers.setLockedTime(this);

		destroyOrientationListener();

		// Because the Camera object is a shared resource, it's very
		// important to release it when the activity is paused.
		releaseCamera();
	}
	
	private void startCamera(){
		startCamera(DEFAULT_CAMERA);
	}
	
	private OnClickListener switchCamera(){
		return new OnClickListener() {
			public void onClick(View v) {
				if(numberOfCameras > 1){
					releaseCamera();
					
					currentCamera++;
					
					if(currentCamera == numberOfCameras){
						currentCamera = 0;
					}
					
					mPreview = null;
					mPreview = new CameraPreview(CameraActivity.this);
					((LinearLayout) findViewById(R.id.camera_preview_container)).removeAllViews();
					((LinearLayout) findViewById(R.id.camera_preview_container)).addView(mPreview);
					startCamera(currentCamera);
					//mPreview.switchCamera(mCamera);
				}
			}
		};
	}
	private OnClickListener showOptions(){
		return new OnClickListener() {
			public void onClick(View v) {
				openOptionsMenu();
			}
		};
	}
	
	private OnClickListener showPhotoSizes(){
		return new OnClickListener() {
			public void onClick(View v) {
				showPhotoSizeChooser();
			}
		};
	}
	
	@SuppressLint("NewApi")
	private void startCamera(int cameraId){
		final SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
		
		// Open the default i.e. the first rear facing camera.
		
		try{
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD){
				numberOfCameras = Camera.getNumberOfCameras();
				if(numberOfCameras > 1){
					findViewById(R.id.switchCamButton).setVisibility(View.VISIBLE);
					mCamera = Camera.open(cameraId);
					currentCamera = cameraId;
				}
				else{
					findViewById(R.id.switchCamButton).setVisibility(View.GONE);
					mCamera = Camera.open(DEFAULT_CAMERA);
					currentCamera = DEFAULT_CAMERA;
				}
			}
			else{
				mCamera = Camera.open();
			}
		}
		catch(RuntimeException e){
			Toast.makeText(CameraActivity.this, getText(R.string.unable_to_connect_camera), Toast.LENGTH_LONG).show();
			showLastPhotoThumb();
			return;
		}

		List<Size> mSupportedPictureSizes = getSupportedImageSizes();
		if(isCurrentCameraFrontFacing()){
			photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE_FRONT, 0);
		}
		else{
			photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE, 0);
		}
		
		if(Helpers.isDemo(CameraActivity.this)){
			for(int i=0;i<mSupportedPictureSizes.size();i++){
				if(mSupportedPictureSizes.get(i).width <= 640){
					photoSizeIndex = i;
					if(isCurrentCameraFrontFacing()){
						preferences.edit().putInt(CameraActivity.PHOTO_SIZE_FRONT, photoSizeIndex).commit();
					}
					else{
						preferences.edit().putInt(CameraActivity.PHOTO_SIZE, photoSizeIndex).commit();
					}
					break;
				}
			}
		}
		Size seletectedSize = mSupportedPictureSizes.get(photoSizeIndex);

		// Set flash mode from preferences and update button accordingly
		
		
		String flashMode = preferences.getString(CameraActivity.FLASH_MODE, Parameters.FLASH_MODE_OFF);

		if (flashMode.equals(Parameters.FLASH_MODE_OFF)) {
			flashButton.setImageResource(R.drawable.flash_off);
		}
		else if (flashMode.equals(Parameters.FLASH_MODE_ON)) {
			flashButton.setImageResource(R.drawable.flash_on);
		}
		
		boolean timerMode = preferences.getBoolean(CameraActivity.TIMER_MODE, false);
		
		if(timerMode){
			isTimerOn = true;
			timerButton.setImageResource(R.drawable.timer);
		}
		else{
			isTimerOn = false;
			timerButton.setImageResource(R.drawable.timer_disabled);
		}

		Camera.Parameters parameters = mCamera.getParameters();
		List<String> flashModes = parameters.getSupportedFlashModes();
		List<String> focusModes = parameters.getSupportedFocusModes();
		
		if(flashModes == null || flashModes.size() == 0){
			flashButton.setVisibility(View.INVISIBLE);
		}
		else{
			flashButton.setVisibility(View.VISIBLE);
		}
		
		if(flashModes != null && flashModes.contains(flashMode)){
			parameters.setFlashMode(flashMode);
		}
		if(focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)){
			parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		}
		parameters.setPictureSize(seletectedSize.width, seletectedSize.height);
		
		setMegapixelLabel(seletectedSize.width, seletectedSize.height);
		
		mCamera.setParameters(parameters);

		cameraCurrentlyLocked = defaultCameraId;
		mPreview.setCamera(mCamera);
	}
	
	private void releaseCamera(){
		if (mCamera != null) {
			mPreview.setCamera(null);
			mCamera.release();
			mCamera = null;
		}
	}
	
	private void initOrientationListener(){
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
	}
	
	private void destroyOrientationListener(){
		if (mOrientationEventListener != null) {
			mOrientationEventListener.disable();
			mOrientationEventListener = null;
		}
	}
	
	private boolean checkCameraHardware() {
	    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) || getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)){
	        // this device has a camera
	        return true;
	    } else {
	        // no camera on this device
	        return false;
	    }
	}

	private void setMegapixelLabel(int width, int height){
		try{
			String megapixel = String.format("%.1f", (double)width * (double)height / 1000000);
			
			((TextView)findViewById(R.id.photoSizeLabel)).setText(megapixel + " MP");
		}
		catch(NumberFormatException e){
			((TextView)findViewById(R.id.photoSizeLabel)).setText("");
		}
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
		
		if(folderFiles != null && folderFiles.length > 0){
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
			final int thumbSize = (int) Math.round(Helpers.getThumbSize(CameraActivity.this) / 1.3);
			
			DecryptPopulateImage task = new DecryptPopulateImage(CameraActivity.this, lastFileThumb.getPath(), galleryButton);
			task.setSize(thumbSize);
			task.setOnFinish(new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					galleryButton.setLayoutParams(new LinearLayout.LayoutParams(thumbSize, thumbSize));
					changeRotation(mOrientation);
					findViewById(R.id.lastPhotoContainer).setVisibility(View.VISIBLE);
				}
			});
			task.execute();
			
		}
		else{
			findViewById(R.id.lastPhotoContainer).setVisibility(View.INVISIBLE);
		}
	}
	
	@Override
	protected void onDestroy() {
		if (adView != null){
			adView.destroy();
		}
		if(receiver != null){
			unregisterReceiver(receiver);
		}
		super.onDestroy();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
			takePhoto();
			return true;
		}
		
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(CameraActivity.this);
		boolean volumeKeysTakePhoto = sharedPrefs.getBoolean("volume_take_photo", true);
		
		if(volumeKeysTakePhoto && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
			takePhoto();
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
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

				int flashButtonImage = R.drawable.flash_off;
				if (flashMode.equals(Parameters.FLASH_MODE_OFF)) {
					flashMode = Parameters.FLASH_MODE_ON;
					flashButtonImage = R.drawable.flash_on;
				}
				else if (flashMode.equals(Parameters.FLASH_MODE_ON)) {
					flashMode = Parameters.FLASH_MODE_OFF;
				}

				Camera.Parameters parameters = mCamera.getParameters();
				List<String> flashModes = parameters.getSupportedFlashModes();
				if(flashModes != null && flashModes.contains(flashMode)){
					parameters.setFlashMode(flashMode);
					mCamera.setParameters(parameters);
					flashButton.setImageResource(flashButtonImage);
					preferences.edit().putString(CameraActivity.FLASH_MODE, flashMode).commit();
				}
				changeRotation(mOrientation);
			}
		};
	}
	
	private OnClickListener toggleTimer() {
		return new OnClickListener() {
			public void onClick(View v) {
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				
				if (isTimerOn) {
					timerButton.setImageResource(R.drawable.timer_disabled);
					isTimerOn = false;
				}
				else{
					timerButton.setImageResource(R.drawable.timer);
					isTimerOn = true;
				}
				
				preferences.edit().putBoolean(CameraActivity.TIMER_MODE, isTimerOn).commit();
				changeRotation(mOrientation);
			}
		};
	}

	private OnClickListener takePhotoClick() {
		return new OnClickListener() {
			public void onClick(View v) {
				takePhoto();
			}
		};
	}
	
	private void takePhoto() {
		if (mCamera != null) {
			Camera.Parameters parameters = mCamera.getParameters();
			parameters.setRotation(changeRotation(mOrientation));
			mCamera.setParameters(parameters);
			
			if(isTimerOn){
				if(!isTimerRunning){
					((TextView)findViewById(R.id.timerLabel)).setText(String.valueOf(timerTotalSeconds));
					((LinearLayout)findViewById(R.id.timerLabelContainer)).setVisibility(View.VISIBLE);
					timerTimePassed = 0;
					
					SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(CameraActivity.this);
					timerTotalSeconds = Integer.valueOf(sharedPrefs.getString("timerDuration", "10"));
					
					isTimerRunning = true;
					timer = new Timer();
					timer.schedule(new TimerTask() {
						@Override
						public void run() {
							runOnUiThread(
									new Runnable() {
										public void run() {
											((TextView)findViewById(R.id.timerLabel)).setText(String.valueOf((timerTotalSeconds-timerTimePassed)+1));
											if(timerTimePassed > timerTotalSeconds){
												((LinearLayout)findViewById(R.id.timerLabelContainer)).setVisibility(View.GONE);
											}
										}
									}
							);
							
							if(timerTimePassed > timerTotalSeconds){
								if(mCamera != null){
									mCamera.takePicture(null, null, getPictureCallback());
								}
								this.cancel();
								timer.cancel();
								isTimerRunning = false;
							}
							
							timerTimePassed++;
						}
					}, 0, 1000);
				}
				else if(timer != null){
					timer.cancel();
					((LinearLayout)findViewById(R.id.timerLabelContainer)).setVisibility(View.GONE);
					isTimerRunning = false;
				}
			}
			else{
				mCamera.takePicture(null, null, getPictureCallback());
			}
		}
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
				/*if(isCurrentCameraFrontFacing()){
					data = FixFrontCamPhoto(data, currentCamera);
				}*/
				
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
	
	public static byte[] FixFrontCamPhoto(byte[] data, int cameraID) {
	    Matrix matrix = new Matrix();
	    
	    // Convert ByteArray to Bitmap
	    Bitmap bitPic = Helpers.decodeBitmap(data, 999999999, true);

	    // Perform matrix rotations/mirrors depending on camera that took the photo
        float[] mirrorY = { -1, 0, 0, 0, 1, 0, 0, 0, 1};
        Matrix matrixMirrorY = new Matrix();
        matrixMirrorY.setValues(mirrorY);

        matrix.postConcat(matrixMirrorY);

	    //matrix.postRotate(90);


	    // Create new Bitmap out of the old one
	    Bitmap bitPicFinal = Bitmap.createBitmap(bitPic, 0, 0, bitPic.getWidth(), bitPic.getHeight(), matrix, true);
	    bitPic.recycle();
	    
	    ByteArrayOutputStream stream = new ByteArrayOutputStream();
	    bitPicFinal.compress(Bitmap.CompressFormat.JPEG, 100, stream);
	    
	    return stream.toByteArray();
	}

	@SuppressLint("NewApi")
	private boolean isCurrentCameraFrontFacing(){
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD){
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(currentCamera, cameraInfo);
			if(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT){
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Performs required action to accommodate new orientation
	 * 
	 * @param orientation
	 * @param lastOrientation
	 */
	private int changeRotation(int orientation) {
		boolean isFrontCamera = isCurrentCameraFrontFacing();
		int rotation = 0;
		int viewsRotation = 0;
		switch (orientation) {
			case ORIENTATION_PORTRAIT_NORMAL:
				rotation = (!isFrontCamera ? 90 : 270);
				viewsRotation = 270;
				break;
			case ORIENTATION_LANDSCAPE_NORMAL:
				rotation = 0;
				viewsRotation = 0;
				break;
			case ORIENTATION_PORTRAIT_INVERTED:
				rotation = (!isFrontCamera ? 270 : 90);
				viewsRotation = 90;
				break;
			case ORIENTATION_LANDSCAPE_INVERTED:
				rotation = 180;
				viewsRotation = 180;
				break;
		}
		
		int howMuchRotated = 0;
		
		switch(lastRotation){
			case 0:
				switch(viewsRotation){
					case 0:
						howMuchRotated = 0;
						break;
					case 90:
						howMuchRotated = 90;
						break;
					case 180:
						howMuchRotated = 180;
						break;
					case 270:
						howMuchRotated = -90;
						break;
				}
				break;
			case 90:
				switch(viewsRotation){
					case 0:
						howMuchRotated = -90;
						break;
					case 90:
						howMuchRotated = 0;
						break;
					case 180:
						howMuchRotated = 90;
						break;
					case 270:
						howMuchRotated = 180;
						break;
				}
				break;
			case 180:
				switch(viewsRotation){
					case 0:
						howMuchRotated = 180;
						break;
					case 90:
						howMuchRotated = -90;
						break;
					case 180:
						howMuchRotated = 0;
						break;
					case 270:
						howMuchRotated = 90;
						break;
				}
				break;
			case 270:
				switch(viewsRotation){
					case 0:
						howMuchRotated = 90;
						break;
					case 90:
						howMuchRotated = 180;
						break;
					case 180:
						howMuchRotated = -90;
						break;
					case 270:
						howMuchRotated = 0;
						break;
				}
				break;
		}

		
		int oldOverallRotation = overallRotation;
		overallRotation += howMuchRotated;
		
		rotateElement(galleryButton, oldOverallRotation, overallRotation);
		rotateElement(flashButton, oldOverallRotation, overallRotation);
		rotateElement(timerButton, oldOverallRotation, overallRotation);
		rotateElement(findViewById(R.id.switchCamButton), oldOverallRotation, overallRotation);
		rotateElement(findViewById(R.id.optionsButton), oldOverallRotation, overallRotation);
		
		lastRotation = viewsRotation;
		
		return rotation;
	}
	
	private void rotateElement(View view, int start, int end){
		RotateAnimation rotateAnimation = new RotateAnimation(start, end, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		rotateAnimation.setInterpolator(new LinearInterpolator());
		rotateAnimation.setDuration(500);
		rotateAnimation.setFillAfter(true);
		rotateAnimation.setFillEnabled(true);
		
		view.startAnimation(rotateAnimation);
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

	private void showPhotoSizeChooser(){
		if (mCamera == null) {
			return;
		}
		
		final List<Size> mSupportedPictureSizes = getSupportedImageSizes();
		
		CharSequence[] listEntries = new CharSequence[mSupportedPictureSizes.size()];
		
		for (int i = 0; i < mSupportedPictureSizes.size(); i++) {
			Camera.Size size = mSupportedPictureSizes.get(i);
			String megapixel = String.format("%.1f", (double)size.width * (double)size.height / 1000000);
			listEntries[i] = megapixel + " MP - " + String.valueOf(size.width) + "x" + String.valueOf(size.height);
		}
		
		final SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
		if(isCurrentCameraFrontFacing()){
			photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE_FRONT, 0);
		}
		else{
			photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE, 0);
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.photo_size_choose));
		builder.setSingleChoiceItems(new PhotoSizeAdapter(CameraActivity.this, android.R.layout.simple_list_item_single_choice, listEntries), 
				photoSizeIndex, 
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
			
				if(Helpers.isDemo(CameraActivity.this) && mSupportedPictureSizes.get(item).width > 640){
					Helpers.warnProVersion(CameraActivity.this);
					dialog.cancel();
					return;
				}
				
				if(isCurrentCameraFrontFacing()){
					preferences.edit().putInt(CameraActivity.PHOTO_SIZE_FRONT, item).commit();
				}
				else{
					preferences.edit().putInt(CameraActivity.PHOTO_SIZE, item).commit();
				}
				
				mPreview.setCamera(null);
				
				Camera.Parameters parameters = mCamera.getParameters();
				parameters.setPictureSize(mSupportedPictureSizes.get(item).width, mSupportedPictureSizes.get(item).height);
				mCamera.setParameters(parameters);
				
				setMegapixelLabel(mSupportedPictureSizes.get(item).width, mSupportedPictureSizes.get(item).height);
				
				mPreview.setCamera(mCamera);
				mPreview.requestLayout();
				
				dialog.dismiss();
			}
		}).show();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.camera_menu, menu);;
        return super.onCreateOptionsMenu(menu);
	}

	private List<Size> getSupportedImageSizes(){
		List<Size> mSupportedPictureSizes = mCamera.getParameters().getSupportedPictureSizes();

		Collections.sort(mSupportedPictureSizes, new Comparator<Size>() {

			public int compare(Size lhs, Size rhs) {
				double megapixel1 = (double)lhs.width * (double)lhs.height / 1000000;
				double megapixel2 = (double)rhs.width * (double)rhs.height / 1000000;
				
				if(megapixel1 == megapixel2){
					return 0;
				}
				else if(megapixel1 < megapixel2){
					return -1;
				}
				else{
					return 1;
				}
			}
			
		});
		
		return mSupportedPictureSizes;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		switch (item.getItemId()) {
			case R.id.settings:
				intent.setClass(CameraActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.gotoGallery:
				intent.setClass(CameraActivity.this, GalleryActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
				startActivity(intent);
				finish();
				return true;
			case R.id.photo_size:
				showPhotoSizeChooser();
				return true;
			case R.id.logout:
				Helpers.logout(CameraActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	private class PhotoSizeAdapter extends ArrayAdapter<CharSequence> {

	    public PhotoSizeAdapter(Context context, int textViewResId, CharSequence[] strings) {
	        super(context, textViewResId, strings);
	    }

	    @Override
        public View getView(int position, View convertView, ViewGroup parent) {
	    	View view = super.getView(position, convertView, parent);
	    	((TextView) view.findViewById(android.R.id.text1)).setTextColor(Color.parseColor("#9a3333"));
            return view;
        }
	}

}
