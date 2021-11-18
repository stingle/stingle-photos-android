package org.stingle.photos.camera;

import static androidx.camera.video.QualitySelector.QUALITY_HD;

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
import androidx.window.WindowManager;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;

import com.google.common.util.concurrent.ListenableFuture;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.ViewItemActivity;
import org.stingle.photos.camera.helpers.CameraHelper;
import org.stingle.photos.camera.helpers.MediaSaveHelper;
import org.stingle.photos.camera.helpers.OrientationHelper;
import org.stingle.photos.camera.helpers.PermissionHelper;
import org.stingle.photos.camera.helpers.SystemHelper;
import org.stingle.photos.databinding.ActivityCameraNewctivityBinding;
import org.stingle.photos.databinding.CameraUiContainerBinding;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraNewActivity extends AppCompatActivity {

    private static final String TAG = "StingleCamera";
    private static final String FLASH_MODE_PREF = "flash_modeX";
    private static final String AUDIO_PREF = "audio_enableX";
    private static final long IMMERSIVE_FLAG_TIMEOUT = 500L;

    private SharedPreferences preferences;
    private PermissionHelper permissionHelper;
    private OrientationHelper orientationHelper;
    private MediaSaveHelper mediaSaveHelper;
    private ExecutorService cameraExecutor;
    private WindowManager windowManager;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalyzer;
    private VideoCapture<Recorder> videoCapture;
    private ActiveRecording activeRecording;
    private Camera camera;

    private int flashMode = ImageCapture.FLASH_MODE_AUTO;
    private boolean isVideoCapture = false;
    private boolean isVideoRecording = false;
    private boolean isVideoRecordingStopped = false;
    private boolean isAudioEnabled = false;
    private long timeWhenStopped = 0;
    private int displayId = -1;
    private int lensFacing;

    private ActivityCameraNewctivityBinding rootBinding;
    private CameraUiContainerBinding cameraUiContainerBinding;

    private final View.OnClickListener capturePhotoClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            SystemHelper.playSoundEffect(CameraNewActivity.this);
            if (isVideoCapture) {
                if (isVideoRecording) {
                    stopVideoRecording();
                    return;
                }
                startVideoRecording();
                return;
            }
            if (imageCapture != null) {
                takePicture();
            }
        }
    };

    private final View.OnClickListener flashModeClickListener = v -> {
        if (!isVideoCapture) {
            if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                setFlashMode(ImageCapture.FLASH_MODE_AUTO);
            } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
                setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
                setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        } else {
            if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
                setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        }
    };

    private final View.OnClickListener cameraSwitchListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
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
            bindCameraUseCases();
        }
    };

    private final View.OnClickListener photoViewClickListener = v -> {
        if (isVideoRecording) {
            takePicture();
            return;
        }

        openGallery();
    };

    private final View.OnClickListener modeChangerClickListener = v -> {
        isVideoCapture = !isVideoCapture;
        changePhotoVideoMode();
        bindCameraUseCases();
    };

    private final CompoundButton.OnCheckedChangeListener audioCheckBoxListener = (compoundButton, b) -> {
        isAudioEnabled = b;
        preferences.edit().putBoolean(AUDIO_PREF, isAudioEnabled).apply();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootBinding = ActivityCameraNewctivityBinding.inflate(getLayoutInflater());
        preferences = getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, MODE_PRIVATE);
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
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            cameraUiContainerBinding.cameraCaptureButton.callOnClick();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /* Helper Methods */

    private void updateSystemUI() {
        SystemHelper.setBrightness(this);
        SystemHelper.hideNavigationBar(this, rootBinding.cameraContainer);
    }

    private void setupHelpers() {
        permissionHelper = new PermissionHelper(this);
        orientationHelper = new OrientationHelper(this);
        mediaSaveHelper = new MediaSaveHelper(this);
        getLifecycle().addObserver(orientationHelper);
        getLifecycle().addObserver(mediaSaveHelper);
    }

    private void setupProperties() {
        cameraExecutor = Executors.newSingleThreadExecutor();
        windowManager = new WindowManager(this);
        registerDisplayListener();
    }

    private void registerDisplayListener() {
        // Every time the orientation of device changes, update rotation for use cases
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {

            }

            @Override
            public void onDisplayRemoved(int displayId) {

            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == CameraNewActivity.this.displayId) {
                    Display display = ((android.view.WindowManager) getSystemService(WINDOW_SERVICE))
                            .getDefaultDisplay();
                    imageCapture.setTargetRotation(display.getRotation());
                    imageAnalyzer.setTargetRotation(display.getRotation());

                }
            }
        }, null);
    }

    private void startCamera() {
        // Wait for the views to be properly laid out
        rootBinding.viewFinder.post(() -> {
            displayId = rootBinding.viewFinder.getDisplay().getDisplayId();
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
        cameraUiContainerBinding.audioCheckBox.setOnCheckedChangeListener(audioCheckBoxListener);
        cameraUiContainerBinding.photoViewButton.setOnClickListener(photoViewClickListener);
        cameraUiContainerBinding.cameraModeChanger.setOnClickListener(modeChangerClickListener);
        cameraUiContainerBinding.flashButton.setOnClickListener(flashModeClickListener);
        if (isVideoCapture) {
            cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_photo);
            cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.button_shutter_video));
            cameraUiContainerBinding.audioCheckBoxContainer.setVisibility(View.VISIBLE);
            setVideoRecordingState(false);

            mediaSaveHelper.showLastThumb();
        }
    }

    private void bindCameraUseCases() {
        // Get screen metrics used to setup camera for full screen resolution
        Rect metrics = windowManager.getCurrentWindowMetrics().getBounds();
        int screenAspectRatio = SystemHelper.aspectRatio(metrics.width(), metrics.height());
        int rotation = rootBinding.viewFinder.getDisplay().getRotation();

        // CameraSelector
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        // Preview
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();

        // ImageCapture
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();

        // ImageAnalysis
        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build();

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.of(QUALITY_HD)) // TODO -> settings
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
                camera.getCameraControl().enableTorch(flashMode == ImageCapture.FLASH_MODE_ON);
            }

            CameraHelper.zoomAndFocus(rootBinding.viewFinder, camera);

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
        applyLastAudioEnabled();
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
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
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
        Log.d(TAG, "Photo capture Finished");
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

    private void stopVideoRecording() {
        Log.d(TAG, "Video recording Finished");
        activeRecording.stop();
        setVideoRecordingState(false);
        timeWhenStopped = 0;
        cameraUiContainerBinding.chrono.stop();
        cameraUiContainerBinding.audioCheckBoxContainer.setVisibility(View.VISIBLE);
        cameraUiContainerBinding.chronoBar.setVisibility(View.INVISIBLE);
        cameraUiContainerBinding.cameraModeChanger.setVisibility(View.VISIBLE);
        cameraUiContainerBinding.cameraModeChanger.setAlpha(1f);
        cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter_video));
        cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(
                ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.ic_flip_camera));
    }

    private void startVideoRecording() {
        Log.d(TAG, "Video recording Started");
        setVideoRecordingState(true);
        startRecording();
        cameraUiContainerBinding.audioCheckBoxContainer.setVisibility(View.GONE);
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
            cameraUiContainerBinding.audioCheckBoxContainer.setVisibility(View.GONE);
            cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_video);
            cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                    ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter));
        } else {
            cameraUiContainerBinding.audioCheckBoxContainer.setVisibility(View.VISIBLE);
            cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_photo);
            cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(
                    ContextCompat.getDrawable(CameraNewActivity.this, R.drawable.button_shutter_video));
            setFlashMode(ImageCapture.FLASH_MODE_OFF);
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

    private void applyLastAudioEnabled() {
        isAudioEnabled = preferences.getBoolean(AUDIO_PREF, false);
        cameraUiContainerBinding.audioCheckBox.setChecked(isAudioEnabled);
    }

    private void setFlashMode(int mode) {
        flashMode = mode;
        updateFlashButton();
        preferences.edit().putInt(FLASH_MODE_PREF, flashMode).apply();
        if (!isVideoCapture) {
            imageCapture.setFlashMode(flashMode);
        } else {
            camera.getCameraControl().enableTorch(flashMode == ImageCapture.FLASH_MODE_ON);
        }
    }

    private void applyLastFlashMode() {
        flashMode = preferences.getInt(FLASH_MODE_PREF, ImageCapture.FLASH_MODE_AUTO);
        updateFlashButton();
    }

    private void updateFlashButton() {
        if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            cameraUiContainerBinding.flashButton.setImageResource(R.drawable.flash_off);
        } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            cameraUiContainerBinding.flashButton.setImageResource(R.drawable.flash_auto);
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            cameraUiContainerBinding.flashButton.setImageResource(R.drawable.flash_on);
        }
    }

}