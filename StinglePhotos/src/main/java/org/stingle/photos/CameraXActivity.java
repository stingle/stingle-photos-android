package org.stingle.photos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.VideoCapture;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.GetThumbFromPlainFile;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.ShowLastThumbAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.CameraX.CameraImageSize;
import org.stingle.photos.CameraX.CameraView;
import org.stingle.photos.CameraX.MediaEncryptService;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;

// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.


public class CameraXActivity extends AppCompatActivity {

	private static final String[] CAMERA_PERMISSIONS = {
			Manifest.permission.CAMERA,
			Manifest.permission.RECORD_AUDIO,
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
	};
	public static final String FLASH_MODE_PREF = "flash_modeX";
	private CameraView cameraView;
	private LinearLayout cameraParent;
	private FlashMode flashMode = FlashMode.AUTO;
	private SharedPreferences preferences;
	private SharedPreferences settings;
	private boolean isInVideoMode = false;
	private boolean isRecordingVideo = false;
	private ImageView galleryButton;
	private LocalBroadcastManager lbm;
	private ImageButton modeChangerButton;
	private ImageButton optionsButton;
	private ImageButton flashButton;
	private ImageButton switchCamButton;
	private ImageButton takePhotoButton;
	private Chronometer chronometer;
	private RelativeLayout chronoBar;

	private OrientationEventListener mOrientationEventListener;
	private int mOrientation = -1;

	private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
	private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
	private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
	private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;
	private int mDeviceRotation = 0;
	private int mOverallRotation = 0;
	private int thumbSize;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_camera_x);

		preferences = getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, MODE_PRIVATE);
		settings = PreferenceManager.getDefaultSharedPreferences(this);

		cameraView = findViewById(R.id.view_finder);
		cameraParent = findViewById(R.id.camera_parent);
		flashButton = findViewById(R.id.flashButton);
		takePhotoButton = findViewById(R.id.take_photo);
		switchCamButton = findViewById(R.id.switchCamButton);
		modeChangerButton = findViewById(R.id.modeChanger);
		optionsButton = findViewById(R.id.optionsButton);
		galleryButton = findViewById(R.id.lastPhoto);
		chronometer = findViewById(R.id.chrono);
		chronoBar = findViewById(R.id.chronoBar);

		takePhotoButton.setOnClickListener(getTakePhotoListener());
		switchCamButton.setOnClickListener(getSwitchCamListener());
		modeChangerButton.setOnClickListener(getModeChangerListener());
		flashButton.setOnClickListener(toggleFlash());
		optionsButton.setOnClickListener(openSettings());
		galleryButton.setOnClickListener(openGallery());

		lbm = LocalBroadcastManager.getInstance(this);

		lbm.registerReceiver(onEncFinish, new IntentFilter("MEDIA_ENC_FINISH"));
		thumbSize = (int) Math.round(Helpers.getThumbSize(this) / 1.4);
	}


	@Override
	protected void onResume() {
		super.onResume();
		LoginManager.checkIfLoggedIn(this);

		if (hasAllPermissionsGranted()) {
			initCamera();
		}
		else{
			requestNextPermission();
		}

	}

	@Override
	protected void onPause() {
		super.onPause();
		sendCameraStatusBroadcast(false);
		destroyOrientationListener();
	}

	@SuppressLint("MissingPermission")
	private void initCamera(){
		applyResolution();
		cameraView.bindToLifecycle(this);
		applyLastFlashMode();
		showLastThumb();

		startService(new Intent(this, MediaEncryptService.class));
		sendCameraStatusBroadcast(true);

		initOrientationListener();
	}

	private boolean hasAllPermissionsGranted() {
		for (String permission : CAMERA_PERMISSIONS) {
			if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

	public void requestNextPermission(){
		for (String permission : CAMERA_PERMISSIONS) {
			if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				if(permission.equals(Manifest.permission.CAMERA)){
					requestCameraPermission();
					break;
				}
				else if(permission.equals(Manifest.permission.RECORD_AUDIO)){
					requestAudioPermission();
					break;
				}
				else if(permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE) || permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
					FileManager.requestSDCardPermission(this);
					break;
				}
			}
		}
	}

	public boolean requestCameraPermission() {
		if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

			if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {

				new AlertDialog.Builder(this)
						.setMessage(getString(R.string.camera_perm_explain))
						.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								requestPermissions(new String[]{Manifest.permission.CAMERA}, StinglePhotosApplication.REQUEST_CAMERA_PERMISSION);
							}
						})
						.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										CameraXActivity.this.finish();
									}
								}
						)
						.create()
						.show();

			} else {
				requestPermissions(new String[]{Manifest.permission.CAMERA}, StinglePhotosApplication.REQUEST_CAMERA_PERMISSION);
			}
			return false;
		}
		return true;
	}
	public boolean requestAudioPermission() {
		if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

			if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {

				new AlertDialog.Builder(this)
						.setMessage(getString(R.string.camera_perm_explain))
						.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, StinglePhotosApplication.REQUEST_AUDIO_PERMISSION);
							}
						})
						.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										CameraXActivity.this.finish();
									}
								}
						)
						.create()
						.show();

			} else {
				requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, StinglePhotosApplication.REQUEST_AUDIO_PERMISSION);
			}
			return false;
		}
		return true;
	}

	private View.OnClickListener openGallery() {
		return v -> {
			Intent intent = new Intent();
			intent.setClass(CameraXActivity.this, ViewItemActivity.class);
			startActivityForResult(intent, GalleryActivity.REQUEST_VIEW_PHOTO);
		};
	}

	private BroadcastReceiver onEncFinish = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			showLastThumb();
		}
	};

	private void showLastThumb(){

		(new ShowLastThumbAsyncTask(this, galleryButton))
				.setThumbSize(thumbSize)
				.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private void getThumbIntoMemory(File file, boolean isVideo){
		(new GetThumbFromPlainFile(this, file).setThumbSize(thumbSize).setIsVideo(isVideo).setOnFinish(new OnAsyncTaskFinish() {
			@Override
			public void onFinish(Object object) {
				super.onFinish(object);
				if(object != null) {
					galleryButton.setImageBitmap((Bitmap) object);
				}

			}
		})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private View.OnClickListener openSettings() {
		return v -> {
			Intent intent = new Intent();
			intent.setClass(CameraXActivity.this, CameraSettingsActivity.class);
			startActivity(intent);
		};
	}

	private void applyResolution(){
		CameraCharacteristics characteristics = cameraView.getCameraCharacteristics();
		if(characteristics != null){
			StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

			String sizeIndex = "0";
			if(cameraView.getCameraLensFacing() == CameraX.LensFacing.FRONT){
				if(isInVideoMode) {
					sizeIndex = settings.getString("front_video_res", "0");
				}
				else{
					sizeIndex = settings.getString("front_photo_res", "0");
				}
			}
			else if(cameraView.getCameraLensFacing() == CameraX.LensFacing.BACK){
				if(isInVideoMode) {
					sizeIndex = settings.getString("back_video_res", "0");
				}
				else{
					sizeIndex = settings.getString("back_photo_res", "0");
				}
			}

			CameraImageSize size;
			if(isInVideoMode) {
				size = Helpers.parseVideoOutputs(this, map).get(Integer.parseInt(sizeIndex));
			}
			else{
				size = Helpers.parsePhotoOutputs(this, map).get(Integer.parseInt(sizeIndex));
			}

			cameraView.setCustomImageSize(size);
			if((boolean)characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)){
				flashButton.setVisibility(View.VISIBLE);
			}
			else{
				flashButton.setVisibility(View.INVISIBLE);
			}
		}
	}

	private void applyLastFlashMode() {
		String lastFlashModename = preferences.getString(FLASH_MODE_PREF, FlashMode.AUTO.name());
		if (lastFlashModename.equals(FlashMode.AUTO.name())){
			setFlashMode(FlashMode.AUTO);
		}
		else if (lastFlashModename.equals(FlashMode.ON.name())){
			setFlashMode(FlashMode.ON);
		}
		else if (lastFlashModename.equals(FlashMode.OFF.name())){
			setFlashMode(FlashMode.OFF);
		}
	}

	private void sendNewMediaBroadcast(File file){
		startService(new Intent(this, MediaEncryptService.class));
		Intent broadcastIntent = new Intent("NEW_MEDIA");
		broadcastIntent.putExtra("file", file.getAbsolutePath());
		lbm.sendBroadcast(broadcastIntent);
	}

	private void sendCameraStatusBroadcast(boolean isRunning){
		Intent broadcastIntent = new Intent("CAMERA_STATUS");
		broadcastIntent.putExtra("isRunning", isRunning);
		lbm.sendBroadcast(broadcastIntent);
	}

	private View.OnClickListener getTakePhotoListener() {
		return v -> {
			cameraView.setMediaRotation(getPhotoRotation(cameraView.getCameraCharacteristics()));
			if(!isInVideoMode) {
				String filename = Helpers.getTimestampedFilename(Helpers.IMAGE_FILE_PREFIX, ".jpg");
				cameraView.takePicture(new File(FileManager.getCameraTmpDir(CameraXActivity.this) + filename), AsyncTask.THREAD_POOL_EXECUTOR, new ImageCapture.OnImageSavedListener() {
					@Override
					public void onImageSaved(@NonNull File file) {
						if(!LoginManager.isKeyInMemory()){
							getThumbIntoMemory(file, false);
						}
						sendNewMediaBroadcast(file);
					}

					@Override
					public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError, @NonNull String message, @Nullable Throwable cause) {

					}
				});
				whitenScreen();
			}
			else{
				if(!isRecordingVideo) {
					optionsButton.setVisibility(View.INVISIBLE);
					switchCamButton.setVisibility(View.INVISIBLE);
					modeChangerButton.setVisibility(View.INVISIBLE);
					flashButton.setVisibility(View.INVISIBLE);
					chronoBar.setVisibility(View.VISIBLE);
					chronometer.setBase(SystemClock.elapsedRealtime());
					chronometer.start();
					takePhotoButton.setImageDrawable(getDrawable(R.drawable.button_shutter_video_active));
					Helpers.acquireWakeLock(this);

					String filename = Helpers.getTimestampedFilename(Helpers.VIDEO_FILE_PREFIX, ".mp4");
					cameraView.startRecording(new File(FileManager.getCameraTmpDir(CameraXActivity.this) + filename), AsyncTask.THREAD_POOL_EXECUTOR, new VideoCapture.OnVideoSavedListener() {
						@Override
						public void onVideoSaved(@NonNull File file) {
							if(!LoginManager.isKeyInMemory()){
								getThumbIntoMemory(file, true);
							}
							sendNewMediaBroadcast(file);
							runOnUiThread(() -> {
								whitenScreen();

								chronoBar.setVisibility(View.INVISIBLE);
								chronometer.stop();
								takePhotoButton.setImageDrawable(getDrawable(R.drawable.button_shutter_video));

								optionsButton.setVisibility(View.VISIBLE);
								switchCamButton.setVisibility(View.VISIBLE);
								modeChangerButton.setVisibility(View.VISIBLE);
								flashButton.setVisibility(View.VISIBLE);
								Helpers.releaseWakeLock(CameraXActivity.this);
							});
						}

						@Override
						public void onError(@NonNull VideoCapture.VideoCaptureError videoCaptureError, @NonNull String message, @Nullable Throwable cause) {

						}
					});
					isRecordingVideo = true;
				}
				else{
					cameraView.stopRecording();
					isRecordingVideo = false;
				}
			}
		};
	}

	private View.OnClickListener getSwitchCamListener() {
		return v -> {
			cameraView.toggleCamera();
			applyResolution();
		};
	}

	private View.OnClickListener toggleFlash() {
		return v -> {
			if (flashMode == FlashMode.OFF) {
				setFlashMode(FlashMode.AUTO);
			}
			else if (flashMode == FlashMode.AUTO) {
				setFlashMode(FlashMode.ON);
			}
			else if (flashMode == FlashMode.ON) {
				setFlashMode(FlashMode.OFF);
			}

			//
		};
	}

	@SuppressLint("MissingPermission")
	private View.OnClickListener getModeChangerListener() {
		return v -> {
			isInVideoMode = !isInVideoMode;

			applyResolution();
			if(!isInVideoMode){
				cameraView.setCaptureMode(CameraView.CaptureMode.IMAGE);
				modeChangerButton.setImageResource(R.drawable.ic_photo);
				takePhotoButton.setImageDrawable(getDrawable(R.drawable.button_shutter));
			}
			else{
				cameraView.setCaptureMode(CameraView.CaptureMode.VIDEO);
				modeChangerButton.setImageResource(R.drawable.ic_video);
				takePhotoButton.setImageDrawable(getDrawable(R.drawable.button_shutter_video));
			}
		};
	}

	private void setFlashMode(FlashMode mode){
		flashMode = mode;
		if (flashMode == FlashMode.OFF) {
			flashButton.setImageResource(R.drawable.flash_off);
		}
		else if (flashMode == FlashMode.AUTO) {
			flashButton.setImageResource(R.drawable.flash_auto);
		}
		else if (flashMode == FlashMode.ON) {
			flashButton.setImageResource(R.drawable.flash_on);
		}

		cameraView.setFlash(flashMode);
		preferences.edit().putString(FLASH_MODE_PREF, flashMode.name()).apply();
	}

	private void whitenScreen(){
		cameraParent.postDelayed(new Runnable() {
			@Override
			public void run() {
				cameraParent.setForeground(new ColorDrawable(Color.WHITE));
				cameraParent.postDelayed(new Runnable() {
					@Override
					public void run() {
						cameraParent.setForeground(null);
					}
				}, 50L);
			}
		}, 100L);
	}

	private void initOrientationListener() {
		if (mOrientationEventListener == null) {
			mOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {

				@Override
				public void onOrientationChanged(int orientation) {
					if (orientation == ORIENTATION_UNKNOWN) {
						return;
					}

					orientation = (orientation + 45) / 90 * 90;
					int currentOrientation = orientation % 360;
					int newRotation = 0;

					// determine our orientation based on sensor response
					int lastOrientation = mOrientation;

					if (orientation >= 315 || orientation < 45) {
						if (mOrientation != ORIENTATION_PORTRAIT_NORMAL) {
							mOrientation = ORIENTATION_PORTRAIT_NORMAL;
						}
					} else if (orientation < 315 && orientation >= 225) {
						if (mOrientation != ORIENTATION_LANDSCAPE_NORMAL) {
							mOrientation = ORIENTATION_LANDSCAPE_NORMAL;
						}
					} else if (orientation < 225 && orientation >= 135) {
						if (mOrientation != ORIENTATION_PORTRAIT_INVERTED) {
							mOrientation = ORIENTATION_PORTRAIT_INVERTED;
						}
					} else { // orientation <135 && orientation > 45
						if (mOrientation != ORIENTATION_LANDSCAPE_INVERTED) {
							mOrientation = ORIENTATION_LANDSCAPE_INVERTED;
						}
					}

					if (lastOrientation != mOrientation) {
						rotateUIElements();
					}
				}
			};
		}
		if (mOrientationEventListener.canDetectOrientation()) {
			mOrientationEventListener.enable();
		}
	}

	private void destroyOrientationListener() {
		if (mOrientationEventListener != null) {
			mOrientationEventListener.disable();
			mOrientationEventListener = null;
		}
	}

	private int getCurrentDeviceRotation(){
		switch (mOrientation){
			case ORIENTATION_PORTRAIT_NORMAL:
				return 0;
			case ORIENTATION_PORTRAIT_INVERTED:
				return 180;
			case ORIENTATION_LANDSCAPE_NORMAL:
				return 90;
			case ORIENTATION_LANDSCAPE_INVERTED:
				return 270;
		}
		return -1;
	}

	private int getCurrentDeviceRotationForCameraX(int deg){
		switch (deg){
			case 0:
				return Surface.ROTATION_0;
			case 180:
				return Surface.ROTATION_180;
			case 90:
				return Surface.ROTATION_90;
			case 270:
				return Surface.ROTATION_270;
		}
		return -1;
	}

	private int getPhotoRotation(CameraCharacteristics characteristics){
		Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
		int deviceRotation = getCurrentDeviceRotation();

		sensorOrientation -= 90;
		int result;
		if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
			result = (sensorOrientation + deviceRotation + 180) % 360;
		} else {
			result = (sensorOrientation + deviceRotation) % 360;
		}
		return getCurrentDeviceRotationForCameraX(result);
	}


	private void rotateUIElements(){
		int oldDeviceRotation = mDeviceRotation;
		int oldOverallRotation = mOverallRotation;
		mDeviceRotation = getCurrentDeviceRotation();

		mOverallRotation += getHowMuchRotated(oldDeviceRotation, getCurrentDeviceRotation());

		oldOverallRotation -= 90;
		int overallRotation1 = mOverallRotation - 90;

		rotateElement(galleryButton, oldOverallRotation, overallRotation1);
		rotateElement(flashButton, oldOverallRotation, overallRotation1);
		rotateElement(switchCamButton, oldOverallRotation, overallRotation1);
		rotateElement(modeChangerButton, oldOverallRotation, overallRotation1);
	}

	private void rotateElement(View view, int start, int end){
		RotateAnimation rotateAnimation = new RotateAnimation(start, end, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		rotateAnimation.setInterpolator(new LinearInterpolator());
		rotateAnimation.setDuration(500);
		rotateAnimation.setFillAfter(true);
		rotateAnimation.setFillEnabled(true);

		view.startAnimation(rotateAnimation);
	}

	private int getHowMuchRotated(int oldRotation, int currentRotation){
		int howMuchRotated = 0;

		switch(oldRotation){
			case 0:
				switch(currentRotation){
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
				switch(currentRotation){
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
				switch(currentRotation){
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
				switch(currentRotation){
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

		return howMuchRotated;
	}
}
