package com.fenritz.safecam;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
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
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecam.util.AsyncTasks.DecryptPopulateImage;
import com.fenritz.safecam.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.util.CameraPreview;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.NaturalOrderComparator;
import com.fenritz.safecam.widget.SafeCameraButton;

public class CameraActivity extends Activity {

	public static final String FLASH_MODE = "flash_mode";
	public static final String TIMER_MODE = "timer_mode";
	public static final String PHOTO_SIZE = "photo_size";
	public static final String PHOTO_SIZE_FRONT = "photo_size_front";

	private CameraPreview mPreview = null;
	static Camera mCamera;
	int cameraCurrentlyLocked;
	FrameLayout.LayoutParams origParams;
	private int mOrientation = -1;
	private boolean app_is_paused = true;
	private OrientationEventListener mOrientationEventListener;

	private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
	private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
	private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
	private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;
	
	public static final float DEMO_MAX_RES = 0.31f;
	
	private static final int DEFAULT_CAMERA = 0;
	private int numberOfCameras = 1; 
	private int currentCamera = 0; 
	
	private boolean isTimerOn = false; 

	private SafeCameraButton takePhotoButton;
	private ImageButton flashButton;
	private ImageButton timerButton;
	private ImageView galleryButton;
	
	private int timerTotalSeconds = 10; 
	private int timerTimePassed = 0;
	private boolean isTimerRunning = false;
	
	private File lastFile;
	private int lastRotation = 0;
	private int overallRotation = 0;
	
	private boolean isTakingPhoto = false;
	
	private Timer timer;
	
	private int photoSizeIndex = 0;

	// The first rear facing camera
	int defaultCameraId;
	private BroadcastReceiver receiver;

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
		
		takePhotoButton = (SafeCameraButton) findViewById(R.id.take_photo);
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
		
		showLastPhotoThumb();
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
	}

	@Override
	protected void onResume() {
		super.onResume();

		isTakingPhoto = false;
		this.app_is_paused = false;
		boolean logined = Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);

		if(logined){
			int lastCamId = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, Context.MODE_PRIVATE).getInt(SafeCameraActivity.LAST_CAM_ID, DEFAULT_CAMERA);
			startCamera(lastCamId);
			initOrientationListener();
			showLastPhotoThumb();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		this.app_is_paused = true;
		
		Helpers.checkLoginedState(this);
		Helpers.setLockedTime(this);

		destroyOrientationListener();

		// Because the Camera object is a shared resource, it's very
		// important to release it when the activity is paused.
		releaseCamera();
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
	
	private int getMaxAvailableSizeIndex(List<Camera.Size> mSupportedPictureSizes){
		if(Helpers.isDemo(CameraActivity.this)){
			for(int i=mSupportedPictureSizes.size()-1; i>=0; i--){
				Camera.Size sz = mSupportedPictureSizes.get(i);
				if((sz.width * sz.height)/1000000f <= DEMO_MAX_RES){
					return i;
				}
			}
			return 0;
		}
		else{
			return mSupportedPictureSizes.size()-1;
		}
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
		int bestAvailableSizeIndex = getMaxAvailableSizeIndex(mSupportedPictureSizes);
		
		if(isCurrentCameraFrontFacing()){
			photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE_FRONT, bestAvailableSizeIndex);
		}
		else{
			photoSizeIndex = preferences.getInt(CameraActivity.PHOTO_SIZE, bestAvailableSizeIndex);
		}
		
		Size selectedSize;
		if(photoSizeIndex < mSupportedPictureSizes.size()){
			selectedSize = mSupportedPictureSizes.get(photoSizeIndex);
		}
		else{
			selectedSize = mSupportedPictureSizes.get(bestAvailableSizeIndex);
		}
		if(selectedSize != null){
			if(Helpers.isDemo(CameraActivity.this)){
				if((selectedSize.width * selectedSize.height)/1000000f > DEMO_MAX_RES){
					selectedSize = mSupportedPictureSizes.get(bestAvailableSizeIndex);
					photoSizeIndex = bestAvailableSizeIndex;
				}
			}
		}
		else{
			selectedSize = mSupportedPictureSizes.get(bestAvailableSizeIndex);
		}
		
		// Set flash mode from preferences and update button accordingly
		String flashMode = preferences.getString(CameraActivity.FLASH_MODE, Parameters.FLASH_MODE_AUTO);

		if (flashMode.equals(Parameters.FLASH_MODE_OFF)) {
			flashButton.setImageResource(R.drawable.flash_off);
		}
		else if (flashMode.equals(Parameters.FLASH_MODE_ON)) {
			flashButton.setImageResource(R.drawable.flash_on);
		}
		else if (flashMode.equals(Parameters.FLASH_MODE_AUTO)) {
			flashButton.setImageResource(R.drawable.flash_auto);
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
		parameters.setPictureSize(selectedSize.width, selectedSize.height);
		
		setMegapixelLabel(selectedSize.width, selectedSize.height);
		
		mCamera.setParameters(parameters);

		cameraCurrentlyLocked = defaultCameraId;
		if(mPreview != null){
			((LinearLayout) findViewById(R.id.camera_preview_container)).removeView(mPreview);
		}
		mPreview = null;
		mPreview = new CameraPreview(CameraActivity.this);
		((LinearLayout) findViewById(R.id.camera_preview_container)).addView(mPreview);
		mPreview.setCamera(mCamera);
		
		getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putInt(SafeCameraActivity.LAST_CAM_ID, cameraId).commit();
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

					if (lastOrientation != mOrientation && mCamera != null) {
						Camera.Parameters parameters = mCamera.getParameters();
						if(parameters != null){
							parameters.setRotation(changeRotation(mOrientation));
							mCamera.setParameters(parameters);
						}
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
	
	private class ShowLastPhotoThumb extends AsyncTask<Void, Void, File> {

		@SuppressWarnings("unchecked")
		@Override
		protected File doInBackground(Void... params) {
			
			File dir = new File(Helpers.getHomeDir(CameraActivity.this));
			
			final int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;
			
			File[] folderFiles = dir.listFiles();
			
			if(folderFiles != null && folderFiles.length > 0){
				Arrays.sort(folderFiles, (new NaturalOrderComparator(){
					@Override
					public int compare(Object o1, Object o2){
						return -super.compare(o1, o2);
					}
				}));
				
				for (File file : folderFiles){
					File thumb = new File(Helpers.getThumbsDir(CameraActivity.this) + "/" + Helpers.getThumbFileName(file));
					if(file.length() < maxFileSize && file.getName().endsWith(getString(R.string.file_extension)) && thumb.exists() && thumb.isFile()){
						return file;
					}
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(File file) {
			super.onPostExecute(file);

			lastFile = file;
			
			if(lastFile != null){
				final File lastFileThumb = new File(Helpers.getThumbsDir(CameraActivity.this) + "/" + Helpers.getThumbFileName(lastFile));
				final int thumbSize = (int) Math.round(Helpers.getThumbSize(CameraActivity.this) / 1.4);
				
				DecryptPopulateImage task = new DecryptPopulateImage(CameraActivity.this, lastFileThumb.getPath(), galleryButton);
				task.setSize(thumbSize);
				task.setOnFinish(new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						//galleryButton.setLayoutParams(new LinearLayout.LayoutParams(thumbSize, thumbSize));
						changeRotation(mOrientation);
						findViewById(R.id.lastPhoto).setVisibility(View.VISIBLE);
					}
				});
				task.execute();
			}
			else{
				findViewById(R.id.lastPhoto).setVisibility(View.INVISIBLE);
			}
		}

	}
	
	
	private void showLastPhotoThumb(){
		(new ShowLastPhotoThumb()).execute();
	}
	
	@Override
	protected void onDestroy() {
		if(receiver != null){
			unregisterReceiver(receiver);
		}
		super.onDestroy();
	}
	
	/*@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int keyCode = event.getKeyCode();
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_DOWN:
				takePhoto();
				return true;
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(CameraActivity.this);
				
				if(sharedPrefs.getBoolean("volume_take_photo", true)){
					takePhoto();
					return true;
				}
			default:
				return super.dispatchKeyEvent(event);
		}
	}*/

	
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
	
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
			return true;
		}
		
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(CameraActivity.this);
		boolean volumeKeysTakePhoto = sharedPrefs.getBoolean("volume_take_photo", true);
		
		if(volumeKeysTakePhoto && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
			return true;
		}
		
		return super.onKeyUp(keyCode, event);
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

				int flashButtonImage = R.drawable.flash_auto;
				if (flashMode.equals(Parameters.FLASH_MODE_AUTO)) {
					flashMode = Parameters.FLASH_MODE_OFF;
					flashButtonImage = R.drawable.flash_off;
				}
				else if (flashMode.equals(Parameters.FLASH_MODE_OFF)) {
					flashMode = Parameters.FLASH_MODE_ON;
					flashButtonImage = R.drawable.flash_on;
				}
				else if (flashMode.equals(Parameters.FLASH_MODE_ON)) {
					flashMode = Parameters.FLASH_MODE_AUTO;
					flashButtonImage = R.drawable.flash_auto;
				}

				if (mCamera != null){
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
		if (mCamera != null && !isTakingPhoto) {
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
									isTakingPhoto = true;
									handleFocusAndTakePicture();
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
				isTakingPhoto = true;
				handleFocusAndTakePicture();
			}
		}
	}
	
	private void takePicture(){
		successfully_focused = false;
		mCamera.takePicture(null, null, getPictureCallback());
	}
	
	private void handleFocusAndTakePicture(){
		List<String> supportedFocusModes = mCamera.getParameters().getSupportedFocusModes();
		boolean hasAutoFocus = supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO);

		if( hasAutoFocus && (!this.successfully_focused || System.currentTimeMillis() > this.successfully_focused_time + 5000 )) {
	        Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
				public void onAutoFocus(boolean success, Camera camera) {
					takePicture();
				}
	        };
    		try {
    	    	mCamera.autoFocus(autoFocusCallback);
    			count_cameraAutoFocus++;
    		}
    		catch(RuntimeException e) {
    			autoFocusCallback.onAutoFocus(false, mCamera);
    			e.printStackTrace();
    		}
		}
		else {
			takePicture();
		}
	}

	static class ResumePreview extends Handler {
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
				isTakingPhoto = false;
				/*if(isCurrentCameraFrontFacing()){
					data = FixFrontCamPhoto(data, currentCamera);
				}*/
				
				String filename = Helpers.getFilename(CameraActivity.this, Helpers.JPEG_FILE_PREFIX);
				
				String path = Helpers.getHomeDir(CameraActivity.this);
				String newFileName = Helpers.getNextAvailableFilePrefix(path) + Helpers.encryptFilename(CameraActivity.this, filename);
				String finalPath = path + "/" + newFileName;
				
				new EncryptAndWriteFile(finalPath).execute(data);
				new EncryptAndWriteThumb(CameraActivity.this, Helpers.getThumbFileName(finalPath), new OnAsyncTaskFinish() {
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
			try{
				Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
				Camera.getCameraInfo(currentCamera, cameraInfo);
				if(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT){
					return true;
				}
			}
			catch(RuntimeException e){ }
		}
		return false;
	}
	
	private int getRotationFromOrientation(int orientation){
		int rotation = 0;
		boolean isFrontCamera = isCurrentCameraFrontFacing();
		switch (orientation) {
			case ORIENTATION_PORTRAIT_NORMAL:
				rotation = (!isFrontCamera ? 90 : 270);
				break;
			case ORIENTATION_LANDSCAPE_NORMAL:
				rotation = 0;
				break;
			case ORIENTATION_PORTRAIT_INVERTED:
				rotation = (!isFrontCamera ? 270 : 90);
				break;
			case ORIENTATION_LANDSCAPE_INVERTED:
				rotation = 180;
				break;
		}
		
		return rotation;
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
		
		//rotateElement(takePhotoButton, oldOverallRotation, overallRotation);
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
				FileOutputStream out = new FileOutputStream(filename);
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
			takePhotoButton.resetAnimation();
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
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.photo_size_choose));
		builder.setSingleChoiceItems(listEntries, 
				photoSizeIndex, 
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
			
				double megapixel = mSupportedPictureSizes.get(item).width * mSupportedPictureSizes.get(item).height;
				megapixel = megapixel / 1000000;
				megapixel = (double)Math.round(megapixel * 10) / 10;
				
				if(Helpers.isDemo(CameraActivity.this) && megapixel > 0.3){
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
				
				photoSizeIndex = item;
				
				dialog.dismiss();
			}
		}).show();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.camera_menu, menu);;
        return super.onCreateOptionsMenu(menu);
	}

	private List<Size> getSupportedImageSizes(){
		List<Size> mSupportedPictureSizes = mCamera.getParameters().getSupportedPictureSizes();

		if(mSupportedPictureSizes.size() > 0){
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
		}
		
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
	
	private boolean touch_was_multitouch = false;
    private boolean has_focus_area = false;
	private int focus_screen_x = 0;
	private int focus_screen_y = 0;
	private long focus_complete_time = -1;
	private int focus_success = FOCUS_DONE;
	private static final int FOCUS_WAITING = 0;
	private static final int FOCUS_SUCCESS = 1;
	private static final int FOCUS_FAILED = 2;
	private static final int FOCUS_DONE = 3;
	private String set_flash_after_autofocus = "";
	private boolean successfully_focused = false;
	private long successfully_focused_time = -1;
	public int count_cameraAutoFocus = 0;
	private final Paint p = new Paint();
	
	private final Matrix camera_to_preview_matrix = new Matrix();
    private final Matrix preview_to_camera_matrix = new Matrix();
    
	
	
    @SuppressLint("NewApi")
	public boolean handleTouchEvent(MotionEvent event) {
    	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH){
	        //scaleGestureDetector.onTouchEvent(event);
			if( event.getPointerCount() != 1 ) {
				touch_was_multitouch = true;
				return true;
			}
			if( event.getAction() != MotionEvent.ACTION_UP ) {
				if( event.getAction() == MotionEvent.ACTION_DOWN && event.getPointerCount() == 1 ) {
					touch_was_multitouch = false;
				}
				return true;
			}
			if( touch_was_multitouch ) {
				return true;
			}
			/*if( this.isTakingPhotoOrOnTimer() ) {
				return true;
			}*/
	
			// note, we always try to force start the preview (in case is_preview_paused has become false)
	        //startCameraPreview();
	        cancelAutoFocus();
	
	        if( mCamera != null ) {
	            Camera.Parameters parameters = mCamera.getParameters();
				String focus_mode = parameters.getFocusMode();
	    		this.has_focus_area = false;
	            if( parameters.getMaxNumFocusAreas() != 0 && ( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ) ) {
					this.has_focus_area = true;
					this.focus_screen_x = (int)event.getX();
					this.focus_screen_y = (int)event.getY();
	
					ArrayList<Camera.Area> areas = getAreas(event.getX(), event.getY());
				    parameters.setFocusAreas(areas);
	
				    // also set metering areas
				    if( parameters.getMaxNumMeteringAreas() > 0 ) {
				    	parameters.setMeteringAreas(areas);
				    }
	
				    try {
				    	mCamera.setParameters(parameters);
				    }
				    catch(RuntimeException e) {
				    	// just in case something has gone wrong
		        		e.printStackTrace();
				    }
	            }
	            else if( parameters.getMaxNumMeteringAreas() != 0 ) {
	        		// don't set has_focus_area in this mode
					ArrayList<Camera.Area> areas = getAreas(event.getX(), event.getY());
			    	parameters.setMeteringAreas(areas);
	
				    try {
				    	mCamera.setParameters(parameters);
				    }
				    catch(RuntimeException e) {
				    	// just in case something has gone wrong
		        		e.printStackTrace();
				    }
	            }
	        }
    	}
    	mPreview.invalidate();
		tryAutoFocus(false, true);
		return true;
    }

    
    private void tryAutoFocus(final boolean startup, final boolean manual) {
    	// manual: whether user has requested autofocus (by touching screen)
		if( mCamera == null ) {
			Log.d(SafeCameraApplication.TAG, "no camera");
		}
		/*else if( !mPreview.has_surface ) {
		}
		else if( !this.is_preview_started ) {
		}*/
		//else if( is_taking_photo ) {
		else if( isTakingPhoto || isTimerRunning ) {
			Log.d(SafeCameraApplication.TAG, "currently taking a photo");
		}
		else {
			// it's only worth doing autofocus when autofocus has an effect (i.e., auto or macro mode)
            Camera.Parameters parameters = mCamera.getParameters();
			String focus_mode = parameters.getFocusMode();
			// getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play from the below line (v1.7),
			// on Galaxy Tab 10.1 (GT-P7500), Android 4.0.3 - 4.0.4; HTC EVO 3D X515m (shooteru), Android 4.0.3 - 4.0.4
	        if( focus_mode != null && ( focus_mode.equals(Camera.Parameters.FOCUS_MODE_AUTO) || focus_mode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ) ) {
    			String old_flash = parameters.getFlashMode();
    			set_flash_after_autofocus = "";
    			// getFlashMode() may return null if flash not supported!
    			if( startup && old_flash != null && old_flash != Camera.Parameters.FLASH_MODE_OFF ) {
        			set_flash_after_autofocus = old_flash;
    				parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    				mCamera.setParameters(parameters);
    			}
		        Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
					public void onAutoFocus(boolean success, Camera camera) {
						autoFocusCompleted(manual, success, false);
					}
		        };
	
				this.focus_success = FOCUS_WAITING;
	    		this.focus_complete_time = -1;
	    		this.successfully_focused = false;
	    		mPreview.invalidate();
	    		try {
	    			mCamera.autoFocus(autoFocusCallback);
	    			count_cameraAutoFocus++;
	    		}
	    		catch(RuntimeException e) {
	    			// just in case? We got a RuntimeException report here from 1 user on Google Play
	    			autoFocusCallback.onAutoFocus(false, mCamera);
	    			e.printStackTrace();
	    		}
	        }
	        else if( has_focus_area ) {
	        	// do this so we get the focus box, for focus modes that support focus area, but don't support autofocus
				focus_success = FOCUS_SUCCESS;
				focus_complete_time = System.currentTimeMillis();
	        }
		}
    }
    
    private void cancelAutoFocus() {
        if( mCamera != null ) {
			try {
				mCamera.cancelAutoFocus();
			}
			catch(RuntimeException e) {
	    		e.printStackTrace();
			}
    		autoFocusCompleted(false, false, true);
        }
    }
    
    private void autoFocusCompleted(boolean manual, boolean success, boolean cancelled) {
		if( cancelled ) {
			focus_success = FOCUS_DONE;
		}
		else {
			focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
			focus_complete_time = System.currentTimeMillis();
		}
		if( manual && !cancelled && success ) {
			successfully_focused = true;
			successfully_focused_time = focus_complete_time;
		}
		if( set_flash_after_autofocus.length() > 0 && mCamera != null ) {
			
			Camera.Parameters parameters = mCamera.getParameters();
			parameters.setFlashMode(set_flash_after_autofocus);
			set_flash_after_autofocus = "";
			mCamera.setParameters(parameters);
		}
		/*if( this.using_face_detection ) {
			// On some devices such as mtk6589, face detection dooes not resume as written in documentation so we have
			// to cancelfocus when focus is finished
			if( camera != null ) {
				try {
					camera.cancelAutoFocus();
				}
				catch(RuntimeException e) {
					if( MyDebug.LOG )
					e.printStackTrace();
				}
			}
		}*/
		mPreview.invalidate();
    }
    
    @SuppressLint("NewApi")
	private ArrayList<Camera.Area> getAreas(float x, float y) {
		float [] coords = {x, y};
		calculatePreviewToCameraMatrix();
		preview_to_camera_matrix.mapPoints(coords);
		float focus_x = coords[0];
		float focus_y = coords[1];
		
		int focus_size = 50;
		Rect rect = new Rect();
		rect.left = (int)focus_x - focus_size;
		rect.right = (int)focus_x + focus_size;
		rect.top = (int)focus_y - focus_size;
		rect.bottom = (int)focus_y + focus_size;
		if( rect.left < -1000 ) {
			rect.left = -1000;
			rect.right = rect.left + 2*focus_size;
		}
		else if( rect.right > 1000 ) {
			rect.right = 1000;
			rect.left = rect.right - 2*focus_size;
		}
		if( rect.top < -1000 ) {
			rect.top = -1000;
			rect.bottom = rect.top + 2*focus_size;
		}
		else if( rect.bottom > 1000 ) {
			rect.bottom = 1000;
			rect.top = rect.bottom - 2*focus_size;
		}

	    ArrayList<Camera.Area> areas = new ArrayList<Camera.Area>();
	    if (android.os.Build.VERSION.SDK_INT >= 14){
	    	areas.add(new Camera.Area(rect, 1000));
	    }
	    return areas;
	}
    
    @SuppressLint("NewApi")
	private void calculateCameraToPreviewMatrix() {
    	int facing = 0;
    	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD){
			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(currentCamera, cameraInfo);
			facing = cameraInfo.facing;
    	}
    	
		camera_to_preview_matrix.reset();
		
		// Need mirror for front camera.
		boolean mirror = (facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
		camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
		// This is the value for android.hardware.Camera.setDisplayOrientation.
		camera_to_preview_matrix.postRotate(getRotationFromOrientation(mOrientation));
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		camera_to_preview_matrix.postScale(mPreview.getWidth() / 2000f, mPreview.getHeight() / 2000f);
		camera_to_preview_matrix.postTranslate(mPreview.getWidth() / 2f, mPreview.getHeight() / 2f);
	}

	private void calculatePreviewToCameraMatrix() {
		calculateCameraToPreviewMatrix();
		if( !camera_to_preview_matrix.invert(preview_to_camera_matrix) ) {
		}
	}
	
	public void handleOnDraw(Canvas canvas) {

		if( this.app_is_paused ) {
			return;
		}

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		final float scale = getResources().getDisplayMetrics().density;
		if( mCamera != null && sharedPreferences.getString("preference_grid", "preference_grid_none").equals("preference_grid_3x3") ) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth()/3.0f, 0.0f, canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(2.0f*canvas.getWidth()/3.0f, 0.0f, 2.0f*canvas.getWidth()/3.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, canvas.getHeight()/3.0f, p);
			canvas.drawLine(0.0f, 2.0f*canvas.getHeight()/3.0f, canvas.getWidth()-1.0f, 2.0f*canvas.getHeight()/3.0f, p);
		}
		if( mCamera != null && sharedPreferences.getString("preference_grid", "preference_grid_none").equals("preference_grid_4x2") ) {
			p.setColor(Color.GRAY);
			canvas.drawLine(canvas.getWidth()/4.0f, 0.0f, canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(canvas.getWidth()/2.0f, 0.0f, canvas.getWidth()/2.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(3.0f*canvas.getWidth()/4.0f, 0.0f, 3.0f*canvas.getWidth()/4.0f, canvas.getHeight()-1.0f, p);
			canvas.drawLine(0.0f, canvas.getHeight()/2.0f, canvas.getWidth()-1.0f, canvas.getHeight()/2.0f, p);
			p.setColor(Color.WHITE);
			int crosshairs_radius = (int) (20 * scale + 0.5f); // convert dps to pixels
			canvas.drawLine(canvas.getWidth()/2.0f, canvas.getHeight()/2.0f - crosshairs_radius, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f + crosshairs_radius, p);
			canvas.drawLine(canvas.getWidth()/2.0f - crosshairs_radius, canvas.getHeight()/2.0f, canvas.getWidth()/2.0f + crosshairs_radius, canvas.getHeight()/2.0f, p);
		}
		
		//canvas.save();
		

		
		/*if( this.has_zoom && camera != null && sharedPreferences.getBoolean("preference_show_zoom", true) ) {
			float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
			// only show when actually zoomed in
			if( zoom_ratio > 1.0f + 1.0e-5f ) {
				// Convert the dps to pixels, based on density scale
				int pixels_offset_y = 2*text_y;
				p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				p.setTextAlign(Paint.Align.CENTER);
				drawTextWithBackground(canvas, p, getResources().getString(R.string.zoom) + ": " + zoom_ratio +"x", Color.WHITE, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
			}
		}*/
		
		
		if( this.focus_success != FOCUS_DONE ) {
			int size = (int) (50 * scale + 0.5f); // convert dps to pixels
			if( this.focus_success == FOCUS_SUCCESS )
				p.setColor(Color.GREEN);
			else if( this.focus_success == FOCUS_FAILED )
				p.setColor(Color.RED);
			else
				p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int pos_x = 0;
			int pos_y = 0;
			if( has_focus_area ) {
				pos_x = focus_screen_x;
				pos_y = focus_screen_y;
			}
			else {
				pos_x = canvas.getWidth() / 2;
				pos_y = canvas.getHeight() / 2;
			}
			canvas.drawRect(pos_x - size, pos_y - size, pos_x + size, pos_y + size, p);
			if( focus_complete_time != -1 && System.currentTimeMillis() > focus_complete_time + 1000 ) {
				focus_success = FOCUS_DONE;
			}
			
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				public void run() {
					mPreview.invalidate();
				}
			}, 1020);
			p.setStyle(Paint.Style.FILL); // reset
		}
		
	}

}
