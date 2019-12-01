package org.stingle.photos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.
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
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.VideoCapture;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import android.os.Bundle;
import android.widget.RelativeLayout;

import org.stingle.photos.AsyncTasks.ShowLastThumbAsyncTask;
import org.stingle.photos.AsyncTasks.ShowThumbInImageView;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Camera.CameraImageSize;
import org.stingle.photos.CameraX.CameraView;
import org.stingle.photos.CameraX.MediaEncryptService;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.VideoEncryptService;
import org.stingle.photos.Util.Helpers;


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
	}


	@Override
	protected void onResume() {
		super.onResume();

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
	}

	@SuppressLint("MissingPermission")
	private void initCamera(){
		applyResolution();
		cameraView.bindToLifecycle(this);
		applyLastFlashMode();
		showLastThumb(null, false);

		startService(new Intent(this, MediaEncryptService.class));
		sendCameraStatusBroadcast(true);
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
			showLastThumb(null, false);
		}
	};

	private void showLastThumb(File file, boolean isVideo){
		int thumbSize = (int) Math.round(Helpers.getThumbSize(this) / 1.4);
		if(file != null){
			(new ShowThumbInImageView(CameraXActivity.this, file, galleryButton))
					.setThumbSize(thumbSize)
					.setIsVideo(isVideo)
					.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		else{
			(new ShowLastThumbAsyncTask(this, galleryButton))
					.setThumbSize(thumbSize)
					.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
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
			if(!isInVideoMode) {
				String filename = Helpers.getTimestampedFilename(Helpers.IMAGE_FILE_PREFIX, ".jpg");
				cameraView.takePicture(new File(getTmpDir() + filename), AsyncTask.THREAD_POOL_EXECUTOR, new ImageCapture.OnImageSavedListener() {
					@Override
					public void onImageSaved(@NonNull File file) {
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
					cameraView.startRecording(new File(getTmpDir() + filename), AsyncTask.THREAD_POOL_EXECUTOR, new VideoCapture.OnVideoSavedListener() {
						@Override
						public void onVideoSaved(@NonNull File file) {
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

	private String getTmpDir() {
		File cacheDir = getCacheDir();
		File tmpDir = new File(cacheDir.getAbsolutePath() + "/camera_tmp/");
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
