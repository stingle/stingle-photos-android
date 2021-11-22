package org.stingle.photos.camera;

import static androidx.camera.video.QualitySelector.QUALITY_FHD;
import static androidx.camera.video.QualitySelector.QUALITY_HD;
import static androidx.camera.video.QualitySelector.QUALITY_SD;
import static androidx.camera.video.QualitySelector.QUALITY_UHD;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.ActiveRecording;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.common.util.concurrent.ListenableFuture;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.CameraSettingsActivity;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.ViewItemActivity;
import org.stingle.photos.camera.helpers.CameraHelper;
import org.stingle.photos.camera.helpers.CameraSoundHelper;
import org.stingle.photos.camera.helpers.MediaSaveHelper;
import org.stingle.photos.camera.helpers.OrientationHelper;
import org.stingle.photos.camera.helpers.PermissionHelper;
import org.stingle.photos.camera.helpers.SystemHelper;
import org.stingle.photos.camera.helpers.CameraToolsHelper;
import org.stingle.photos.databinding.ActivityCameraNewctivityBinding;
import org.stingle.photos.databinding.CameraUiContainerBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraNewActivity extends AppCompatActivity {

    private static final String TAG = "StingleCamera";
    private static final long IMMERSIVE_FLAG_TIMEOUT = 500L;

    private SharedPreferences preferences;
    private SharedPreferences settings;
    private PermissionHelper permissionHelper;
    private OrientationHelper orientationHelper;
    private MediaSaveHelper mediaSaveHelper;
    private CameraHelper cameraHelper;
    private CameraToolsHelper cameraToolsHelper;
    private CameraSoundHelper cameraSoundHelper;
    private ExecutorService cameraExecutor;

    private CameraImageSize cameraImageSize;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalyzer;
    private VideoCapture<Recorder> videoCapture;
    private ActiveRecording activeRecording;
    private Camera camera;

    private int quality = QUALITY_HD;
    private boolean isVideoCapture = false;
    private boolean isVideoRecording = false;
    private boolean isVideoRecordingStopped = false;
    private long timeWhenStopped = 0;
    private int lensFacing;
    private boolean isCaptureProcess;

    private ActivityCameraNewctivityBinding rootBinding;
    private CameraUiContainerBinding cameraUiContainerBinding;


    private final View.OnClickListener capturePhotoClickListener = v -> {
        if (isVideoCapture) {
            onVideoCapturing();
            return;
        }
        onImageCapturing();
    };

    private final View.OnClickListener cameraSwitchListener = v -> onCameraSwitch();

    private final View.OnClickListener photoViewClickListener = v -> {
        if (isVideoRecording) {
            takePicture();
            return;
        }
        openGallery();
    };

    private final View.OnClickListener modeChangerClickListener = v -> {
        isVideoCapture = !isVideoCapture;
        applyResolution();
        changePhotoVideoMode();
        bindCameraUseCases();
    };

    private final View.OnClickListener optionsButtonClickListener = view -> {
        Intent intent = new Intent();
        intent.setClass(CameraNewActivity.this, CameraSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    };

    private final View.OnClickListener exposureButtonClickListener = view -> {
        if (cameraUiContainerBinding.seekBarContainer.getVisibility() == View.VISIBLE) {
            cameraUiContainerBinding.seekBarContainer.setVisibility(View.GONE);
            return;
        }
        cameraUiContainerBinding.seekBarContainer.setVisibility(View.VISIBLE);
    };

    private final View.OnClickListener flashModeClickListener =
            v -> cameraToolsHelper.onFlashClick(isVideoCapture, iconId -> {
                if (!isVideoCapture) {
                    imageCapture.setFlashMode(cameraToolsHelper.getFlashMode());
                } else {
                    camera.getCameraControl().enableTorch(cameraToolsHelper.isTorchEnabled());
                }
                cameraUiContainerBinding.flashButton.setImageResource(iconId);
            });

    private final View.OnClickListener repeatButtonClickListener =
            view -> cameraToolsHelper.onRepeatClick(iconId -> cameraUiContainerBinding.repeat.setImageResource(iconId));

    private final View.OnClickListener timeButtonClickListener =
            view -> cameraToolsHelper.onTimerClick(iconId -> cameraUiContainerBinding.time.setImageResource(iconId));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Helpers.setLocale(this);
        rootBinding = ActivityCameraNewctivityBinding.inflate(getLayoutInflater());
        preferences = getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, MODE_PRIVATE);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(rootBinding.getRoot());
        setupHelpers();
        setupProperties();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rootBinding.cameraContainer.postDelayed(this::updateSystemUI, IMMERSIVE_FLAG_TIMEOUT);
        if (LoginManager.checkIfLoggedIn(this)) {
            permissionHelper.requirePermissionsResult(this::startCamera);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraToolsHelper.cancelTimer();
        isCaptureProcess = false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        bindCameraUseCases();

        // Enable or disable switching between cameras
        updateCameraSwitchButton();
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean volumeKeysTakePhoto = settings.getBoolean("volume_keys_snap", true);
        if (volumeKeysTakePhoto &&
                (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            cameraUiContainerBinding.cameraCaptureButton.callOnClick();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isCaptureProcess) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    /* Helper Methods */

    private void updateSystemUI() {
        SystemHelper.setBrightness(this);
        SystemHelper.hideNavigationBar(this, rootBinding.cameraContainer);
    }

    private void setupHelpers() {
        permissionHelper = new PermissionHelper(this);
        orientationHelper = new OrientationHelper(this, () -> {
            if (imageCapture != null) {
                imageCapture.setTargetRotation(orientationHelper.getCurrentDeviceRotationForCameraX());
                imageAnalyzer.setTargetRotation(orientationHelper.getCurrentDeviceRotationForCameraX());
            }
        });
        mediaSaveHelper = new MediaSaveHelper(this);
        cameraHelper = new CameraHelper(this);
        cameraToolsHelper = new CameraToolsHelper(this, preferences);
        cameraSoundHelper = new CameraSoundHelper(this);
        getLifecycle().addObserver(orientationHelper);
        getLifecycle().addObserver(mediaSaveHelper);
    }

    private void setupProperties() {
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        // Wait for the views to be properly laid out
        rootBinding.viewFinder.post(() -> {
            updateCameraUi();
            setUpCamera();
        });
    }

    /**
     * Method used to re-draw the camera UI controls, called every time configuration changes.
     */
    private void updateCameraUi() {
        // Remove previous UI if any
        if (cameraUiContainerBinding != null) {
            rootBinding.getRoot().removeView(cameraUiContainerBinding.getRoot());
        }
        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
                LayoutInflater.from(this),
                rootBinding.getRoot(),
                true
        );
        orientationHelper.setUI(cameraUiContainerBinding);
        mediaSaveHelper.setImageViewRef(cameraUiContainerBinding.photoViewButton);

        // Listener for button used to capture photo
        cameraUiContainerBinding.cameraCaptureButton.setOnClickListener(capturePhotoClickListener);
        cameraUiContainerBinding.cameraSwitchButton.setEnabled(false);
        cameraUiContainerBinding.cameraSwitchButton.setOnClickListener(cameraSwitchListener);
        cameraUiContainerBinding.photoViewButton.setOnClickListener(photoViewClickListener);
        cameraUiContainerBinding.cameraModeChanger.setOnClickListener(modeChangerClickListener);
        cameraUiContainerBinding.flashButton.setOnClickListener(flashModeClickListener);
        cameraUiContainerBinding.exposureButton.setOnClickListener(exposureButtonClickListener);
        cameraUiContainerBinding.optionsButton.setOnClickListener(optionsButtonClickListener);
        cameraUiContainerBinding.time.setOnClickListener(timeButtonClickListener);
        cameraUiContainerBinding.repeat.setOnClickListener(repeatButtonClickListener);
        if (isVideoCapture) {
            cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_photo);
            cameraUiContainerBinding.repeat.setVisibility(View.GONE);
            cameraUiContainerBinding.repeat.setAlpha(0f);
            cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.button_shutter_video));
            setVideoRecordingState(false);

            mediaSaveHelper.showLastThumb();
        }
    }

    private void bindCameraUseCases() {
        Size size = orientationHelper.isPortrait() ? cameraImageSize.getRevertedSize() : cameraImageSize.getSize();
        // CameraSelector
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        // Preview
        Preview preview = new Preview.Builder()
                .setTargetResolution(cameraImageSize.getRevertedSize())
                .setTargetRotation(Surface.ROTATION_0)
                .build();

        // ImageCapture
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(size)
                .setTargetRotation(orientationHelper.getCurrentDeviceRotationForCameraX())
                .build();

        // ImageAnalysis
        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetResolution(size)
                .setTargetRotation(orientationHelper.getCurrentDeviceRotationForCameraX())
                .build();

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.of(quality)) // TODO -> settings
                .build();

        videoCapture = VideoCapture.withOutput(recorder);

        // TODO -> imageAnalyzer.setAnalyzer(cameraExecutor, new LuminosityAnalyzer { luma ->

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll();

        try {
            if (!isVideoCapture) {
                camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer);

            } else {
                camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, videoCapture);
                camera.getCameraControl().enableTorch(cameraToolsHelper.isTorchEnabled());
            }

            cameraHelper.setCamera(camera);
            cameraHelper.zoomAndFocus(rootBinding.viewFinder);
            cameraHelper.exposure(cameraUiContainerBinding.exposureSeekbar);
            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(rootBinding.viewFinder.getSurfaceProvider());
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }

    private void setUpCamera() {
        mediaSaveHelper.sendCameraStatusBroadcast(true, false);
        mediaSaveHelper.showLastThumb();
        applyLastFlashMode();
        applyLastTimer();
        applyLastRepeat();
        ListenableFuture<ProcessCameraProvider> processCameraProvider =
                ProcessCameraProvider.getInstance(this);
        processCameraProvider.addListener(() -> {
            try {
                cameraProvider = processCameraProvider.get();
                if (hasBackCamera()) {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                } else if (hasFrontCamera()) {
                    lensFacing = CameraSelector.LENS_FACING_FRONT;

                } else {
                    throw new IllegalArgumentException("Back and front camera are unavailable");
                }
                updateCameraSwitchButton();
                applyResolution();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void onImageCapturing() {
        if (imageCapture != null) {
            isCaptureProcess = true;
            cameraToolsHelper.startCapturing(cameraUiContainerBinding.timeout, new CameraToolsHelper.OnTimerListener() {
                @Override
                public void onTick() {
                    if (settings.getBoolean("sound_enabled", true)) {
                        cameraSoundHelper.playLowSound();
                    }
                }

                @Override
                public void onFinish() {
                    for (int i = 0; i < cameraToolsHelper.getRepeatValue(); i++) {
                        takePicture();
                    }
                    isCaptureProcess = false;
                }
            });
        }
    }

    private void onVideoCapturing() {
        // during video capturing pressing on image capture button case
        if (isVideoRecording) {
            stopVideoRecording();
            return;
        }
        cameraUiContainerBinding.cameraCaptureButton.setEnabled(false);
        isCaptureProcess = true;
        cameraToolsHelper.startCapturing(cameraUiContainerBinding.timeout, new CameraToolsHelper.OnTimerListener() {
            @Override
            public void onTick() {
                if (settings.getBoolean("sound_enabled", true)) {
                    cameraSoundHelper.playLowSound();
                }
            }

            @Override
            public void onFinish() {
                isCaptureProcess = false;
                startVideoRecording();
            }
        });
    }

    private void onCameraSwitch() {
        if (isVideoRecording) {
            if (isVideoRecordingStopped) {
                resumeVideoRecording();
            } else {
                pauseVideoRecording();
            }
            return;
        }
        if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
            lensFacing = CameraSelector.LENS_FACING_BACK;
        } else {
            lensFacing = CameraSelector.LENS_FACING_FRONT;
        }
        applyResolution();
        bindCameraUseCases();
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        String name = Helpers.getTimestampedFilename(Helpers.VIDEO_FILE_PREFIX, ".mp4");
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, name);

        FileOutputOptions outputOptions = new FileOutputOptions.Builder(
                new File(FileManager.getCameraTmpDir(CameraNewActivity.this) + name)).build();

        // configure Recorder and Start recording to the mediaStoreOutput.
        boolean isAudioEnabled = settings.getBoolean("audio_enabled", true);
        if (isAudioEnabled) {
            activeRecording = videoCapture.getOutput().prepareRecording(this, outputOptions)
                    .withEventListener(
                            cameraExecutor,
                            videoRecordEvent -> {
                                if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                    mediaSaveHelper.saveVideo(videoRecordEvent);
                                }
                            }).withAudioEnabled().start();
        } else {
            activeRecording = videoCapture.getOutput().prepareRecording(this, outputOptions)
                    .withEventListener(
                            cameraExecutor,
                            videoRecordEvent -> {
                                if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                    mediaSaveHelper.saveVideo(videoRecordEvent);
                                }
                            }).start();
        }
        Log.i(TAG, "Recording started");
    }

    private void takePicture() {
        imageCapture.setTargetRotation(orientationHelper.getCurrentDeviceRotationForCameraX());
        imageAnalyzer.setTargetRotation(orientationHelper.getCurrentDeviceRotationForCameraX());
        Log.d(TAG, "Photo capture Finished");
        if (settings.getBoolean("sound_enabled", true)) {
            cameraSoundHelper.playSound(MediaActionSound.SHUTTER_CLICK);
        }
        Animation animation = AnimationUtils.loadAnimation(CameraNewActivity.this, R.anim.capture_animation);
        rootBinding.viewFinder.startAnimation(animation);
        String filename = Helpers.getTimestampedFilename(Helpers.IMAGE_FILE_PREFIX, ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                new File(FileManager.getCameraTmpDir(CameraNewActivity.this) + filename))
                .build();
        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                File file = new File(outputFileResults.getSavedUri().getPath());
                if (!LoginManager.isKeyInMemory() && !isVideoRecording) {
                    mediaSaveHelper.getThumbIntoMemory(file, false);
                }
                mediaSaveHelper.sendNewMediaBroadcast(file);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exception);
            }
        });
    }

    private void startVideoRecording() {
        if (settings.getBoolean("sound_enabled", true)) {
            cameraSoundHelper.playSound(MediaActionSound.START_VIDEO_RECORDING);
        }
        Log.d(TAG, "Video recording Started");
        setVideoRecordingState(true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startRecording();
            cameraUiContainerBinding.cameraCaptureButton.setEnabled(true);
        }, 700L);
        cameraUiContainerBinding.chronoBar.setVisibility(View.VISIBLE);
        cameraUiContainerBinding.cameraModeChanger.setVisibility(View.INVISIBLE);
        cameraUiContainerBinding.cameraModeChanger.setAlpha(0f);
        cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter_video_active));
        cameraUiContainerBinding.photoViewButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter));
        cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.ic_pause));
        cameraUiContainerBinding.chrono.setBase(SystemClock.elapsedRealtime());
        cameraUiContainerBinding.chrono.start();
    }

    private void stopVideoRecording() {
        Log.d(TAG, "Video recording Finished");
        activeRecording.stop();
        setVideoRecordingState(false);
        if (settings.getBoolean("sound_enabled", true)) {
            cameraSoundHelper.playSound(MediaActionSound.STOP_VIDEO_RECORDING);
        }
        timeWhenStopped = 0;
        cameraUiContainerBinding.chrono.stop();
        cameraUiContainerBinding.chronoBar.setVisibility(View.INVISIBLE);
        cameraUiContainerBinding.cameraModeChanger.setVisibility(View.VISIBLE);
        cameraUiContainerBinding.cameraModeChanger.setAlpha(1f);
        cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter_video));
        cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.ic_flip_camera));
    }

    private void pauseVideoRecording() {
        cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.ic_start));
        isVideoRecordingStopped = true;
        timeWhenStopped = cameraUiContainerBinding.chrono.getBase() - SystemClock.elapsedRealtime();
        cameraUiContainerBinding.chrono.stop();
        activeRecording.pause();
    }

    private void resumeVideoRecording() {
        cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.ic_pause));
        isVideoRecordingStopped = false;
        activeRecording.resume();
        cameraUiContainerBinding.chrono.setBase(SystemClock.elapsedRealtime() + timeWhenStopped);
        cameraUiContainerBinding.chrono.start();
    }

    /**
     * Returns true if the device has an available back camera. False otherwise
     */
    private boolean hasBackCamera() {
        try {
            return cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns true if the device has an available front camera. False otherwise
     */
    private boolean hasFrontCamera() {
        try {
            return cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
        } catch (CameraInfoUnavailableException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Enabled or disabled a button to switch cameras depending on the available cameras
     */
    private void updateCameraSwitchButton() {
        cameraUiContainerBinding.cameraSwitchButton.setEnabled(hasBackCamera() && hasFrontCamera());
    }

    private void changePhotoVideoMode() {
        if (!isVideoCapture) {
            cameraUiContainerBinding.repeat.setVisibility(View.VISIBLE);
            cameraUiContainerBinding.repeat.setAlpha(1f);
            cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_video);
            cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                    ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter));
        } else {
            cameraUiContainerBinding.repeat.setVisibility(View.GONE);
            cameraUiContainerBinding.repeat.setAlpha(0f);
            cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_photo);
            cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                    ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter_video));
            cameraToolsHelper.setFlashMode(ImageCapture.FLASH_MODE_OFF);
            applyLastFlashMode();
        }
    }

    private void openGallery() {
        Log.d(TAG, "photoViewButton Clicked ");
        Intent intent = new Intent();
        intent.setClass(CameraNewActivity.this, ViewItemActivity.class);
        startActivity(intent);
    }

    private void setVideoRecordingState(boolean isRecording) {
        isVideoRecording = isRecording;
        mediaSaveHelper.setVideoRecording(isVideoRecording);
    }

    private void applyLastTimer() {
        cameraToolsHelper.applyLastTimer(
                iconId -> cameraUiContainerBinding.time.setImageResource(iconId));
    }

    private void applyLastRepeat() {
        cameraToolsHelper.applyLastRepeat(
                iconId -> cameraUiContainerBinding.repeat.setImageResource(iconId));
    }

    private void applyLastFlashMode() {
        cameraToolsHelper.applyLastFlashMode(
                iconId -> cameraUiContainerBinding.flashButton.setImageResource(iconId));
    }

    private void applyResolution() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics != null) {
                    if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != lensFacing) {
                        continue;
                    }
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    String sizeIndex = "0";
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        if (isVideoCapture) {
                            sizeIndex = settings.getString("front_video_res", "0");
                        } else {
                            sizeIndex = settings.getString("front_photo_res", "0");
                        }
                    } else if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        if (isVideoCapture) {
                            sizeIndex = settings.getString("back_video_res", "0");
                        } else {

                            sizeIndex = settings.getString("back_photo_res", "0");
                        }
                    }

                    cameraImageSize = null;
                    if (isVideoCapture) {
                        ArrayList<String> videoOutputs = Helpers.parseVideoOutputs(this, map);
                        if (videoOutputs.size() > 0) {
                            String qualityString = videoOutputs.get(Integer.parseInt(sizeIndex));
                            switch (qualityString) {
                                case "UHD":
                                    quality = QUALITY_UHD;
                                    cameraImageSize = new CameraImageSize(this, 3840, 2160);
                                    break;
                                case "Full HD":
                                    quality = QUALITY_FHD;
                                    cameraImageSize = new CameraImageSize(this, 1920, 1080);
                                    break;
                                case "HD":
                                    quality = QUALITY_HD;
                                    cameraImageSize = new CameraImageSize(this, 1280, 720);
                                    break;
                                case "SD":
                                    quality = QUALITY_SD;
                                    cameraImageSize = new CameraImageSize(this, 720, 480);
                                    break;
                            }
                        }
                    } else {
                        ArrayList<CameraImageSize> photoOutputs = Helpers.parsePhotoOutputs(this, map);
                        if (photoOutputs.size() > 0) {
                            cameraImageSize = photoOutputs.get(Integer.parseInt(sizeIndex));
                        }
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}