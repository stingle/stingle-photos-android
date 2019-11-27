package org.stingle.photos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.graphics.Matrix;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import android.os.Bundle;

import org.stingle.photos.Camera.CameraImageSize;
import org.stingle.photos.CameraX.CameraView;
import org.stingle.photos.Util.Helpers;


public class CameraXActivity extends AppCompatActivity {

	// This is an arbitrary number we are using to keep track of the permission
	// request. Where an app has multiple context for requesting permission,
	// this can help differentiate the different contexts.
	private static final int REQUEST_CODE_PERMISSIONS = 10;

	// This is an array of all the permission specified in the manifest.
	private static final List<String> REQUIRED_PERMISSIONS = Arrays.asList(Manifest.permission.CAMERA);
	public static final String FLASH_MODE_PREF = "flash_modeX";
	private CameraView cameraView;
	private LinearLayout cameraParent;
	private ImageButton flashButton;
	private FlashMode flashMode = FlashMode.AUTO;
	private SharedPreferences preferences;
	private SharedPreferences settings;
	private boolean isInVideoMode = false;
	private boolean isRecordingVideo = false;

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

		findViewById(R.id.take_photo).setOnClickListener(getTakePhotoListener());
		findViewById(R.id.switchCamButton).setOnClickListener(getSwitchCamListener());
		findViewById(R.id.modeChanger).setOnClickListener(getModeChangerListener());
		findViewById(R.id.flashButton).setOnClickListener(toggleFlash());
		findViewById(R.id.optionsButton).setOnClickListener(openSettings());
	}


	@Override
	protected void onResume() {
		super.onResume();
		if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			// TODO: Consider calling
			//    Activity#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for Activity#requestPermissions for more details.
			return;
		}
		applyResolution();
		cameraView.bindToLifecycle(this);
		applyLastFlashMode();
	}

	private View.OnClickListener openSettings() {
		return new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(CameraXActivity.this, CameraSettingsActivity.class);
				startActivity(intent);
			}
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

	private View.OnClickListener getTakePhotoListener() {
		return v -> {
			if(!isInVideoMode) {
				String filename = Helpers.getTimestampedFilename(Helpers.IMAGE_FILE_PREFIX, ".jpg");
				cameraView.takePicture(new File(getTmpDir() + filename), AsyncTask.THREAD_POOL_EXECUTOR, new ImageCapture.OnImageSavedListener() {
					@Override
					public void onImageSaved(@NonNull File file) {
						Log.e("saved", file.getAbsolutePath());
					}

					@Override
					public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError, @NonNull String message, @Nullable Throwable cause) {

					}
				});
				whitenScreen();
			}
			else{
				if(!isRecordingVideo) {
					String filename = Helpers.getTimestampedFilename(Helpers.VIDEO_FILE_PREFIX, ".mp4");
					cameraView.startRecording(new File(getVideoTmpDir() + filename), AsyncTask.THREAD_POOL_EXECUTOR, new VideoCapture.OnVideoSavedListener() {
						@Override
						public void onVideoSaved(@NonNull File file) {
							Log.e("saved", file.getAbsolutePath());
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
			applyResolution();
			cameraView.toggleCamera();
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
				((ImageButton)findViewById(R.id.take_photo)).setImageDrawable(getDrawable(R.drawable.button_shutter));
			}
			else{
				cameraView.setCaptureMode(CameraView.CaptureMode.VIDEO);
				((ImageButton)findViewById(R.id.take_photo)).setImageDrawable(getDrawable(R.drawable.button_shutter_video));
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

	private String getTmpDir() {
		File cacheDir = getCacheDir();
		File tmpDir = new File(cacheDir.getAbsolutePath() + "/tmp/");
		tmpDir.mkdirs();
		return tmpDir.getAbsolutePath() + "/";
	}
	private String getVideoTmpDir() {
		File cacheDir = getCacheDir();
		File tmpDir = new File(cacheDir.getAbsolutePath() + "/tmpvideo/");
		tmpDir.mkdirs();
		return tmpDir.getAbsolutePath() + "/";
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

}
