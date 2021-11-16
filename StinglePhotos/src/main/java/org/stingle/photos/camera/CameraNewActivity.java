package org.stingle.photos.camera;

import static androidx.camera.video.QualitySelector.QUALITY_HD;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.ActiveRecording;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.window.WindowManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.RelativeLayout;

import com.google.common.util.concurrent.ListenableFuture;

import org.stingle.photos.AsyncTasks.GetThumbFromPlainFile;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.ShowLastThumbAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.CameraX.MediaEncryptService;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.ViewItemActivity;
import org.stingle.photos.camera.helpers.PermissionHelper;
import org.stingle.photos.databinding.ActivityCameraNewctivityBinding;
import org.stingle.photos.databinding.CameraUiContainerBinding;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CameraNewActivity extends AppCompatActivity {

    private static final String TAG = "StingleCamera";
    private static final double RATIO_4_3_VALUE = 4.0 / 3.0;
    private static final double RATIO_16_9_VALUE = 16.0 / 9.0;
    private static final long IMMERSIVE_FLAG_TIMEOUT = 500L;

    private PermissionHelper permissionHelper;
    private ExecutorService cameraExecutor;
    private DisplayManager displayManager;
    private WindowManager windowManager;
    private LocalBroadcastManager lbm;

    private File outputDirectory;
    private int displayId = -1;
    private int lensFacing;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalyzer;
    private Camera camera;
    private VideoCapture<Recorder> videoCapture;
    private VideoRecordEvent recordingState;
    private ActiveRecording activeRecording;

    private int thumbSize;
    private boolean isVideoCapture = false;
    private boolean isVideoRecording = false;
    private boolean isVideoRecordingStopped = false;
    private long timeWhenStopped = 0;

    // For UI rotation
    private OrientationEventListener mOrientationEventListener;
	private int mOrientation = -1;

	private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
	private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
	private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
	private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;
	private int mDeviceRotation = 0;
	private int mOverallRotation = 0;

    private ActivityCameraNewctivityBinding rootBinding;
    private CameraUiContainerBinding cameraUiContainerBinding;

    private final View.OnClickListener capturePhotoClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            playSoundEffect();
            if (isVideoCapture) {
                if (isVideoRecording) {
                    Log.d(TAG, "Video recording Finished");
                    activeRecording.stop();
                    isVideoRecording = false;
                    timeWhenStopped = 0;
                    cameraUiContainerBinding.chrono.stop();
                    cameraUiContainerBinding.chronoBar.setVisibility(View.INVISIBLE);
                    cameraUiContainerBinding.cameraModeChanger.setVisibility(View.VISIBLE);
                    cameraUiContainerBinding.cameraModeChanger.setAlpha(1f);
                    cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(getDrawable(R.drawable.button_shutter_video));
                    cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(getDrawable(R.drawable.ic_switch_camera));
                    return;
                }
                isVideoRecording = true;
                cameraUiContainerBinding.chronoBar.setVisibility(View.VISIBLE);
                cameraUiContainerBinding.cameraModeChanger.setVisibility(View.INVISIBLE);
                cameraUiContainerBinding.cameraModeChanger.setAlpha(0f);
                cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(getDrawable(R.drawable.button_shutter_video_active));
                cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(getDrawable(R.drawable.ic_pause));
                Log.d(TAG, "Video recording started");
                startRecording();
                cameraUiContainerBinding.chrono.setBase(SystemClock.elapsedRealtime());
                cameraUiContainerBinding.chrono.start();
                return;
            }
            if (imageCapture != null) {
                takePicture();
            }
        }
    };

    private final View.OnClickListener cameraSwitchListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isVideoRecording) {
                if (isVideoRecordingStopped) {
                    cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(getDrawable(R.drawable.ic_pause));
                    isVideoRecordingStopped = false;
                    activeRecording.resume();
                    cameraUiContainerBinding.chrono.setBase(SystemClock.elapsedRealtime() + timeWhenStopped);
                    cameraUiContainerBinding.chrono.start();
                } else {
                    cameraUiContainerBinding.cameraSwitchButton.setImageDrawable(getDrawable(R.drawable.ic_start));
                    isVideoRecordingStopped = true;
                    timeWhenStopped = cameraUiContainerBinding.chrono.getBase() - SystemClock.elapsedRealtime();
                    cameraUiContainerBinding.chrono.stop();
                    activeRecording.pause();
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

    private final View.OnClickListener photoViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isVideoRecording) {
                takePicture();
                return;
            }
            if (outputDirectory != null && outputDirectory.listFiles().length != 0) {
                Log.d(TAG, "photoViewButton Clicked ");
                Intent intent = new Intent();
                intent.setClass(CameraNewActivity.this, ViewItemActivity.class);
                startActivity(intent);
            }
        }
    };

    private final View.OnClickListener modeChangerClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            isVideoCapture = !isVideoCapture;
            if (!isVideoCapture) {
                cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_video);
                cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(getDrawable(R.drawable.button_shutter));
            } else {
                cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_photo);
                cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(getDrawable(R.drawable.button_shutter_video));
            }
            bindCameraUseCases();
        }
    };

    private final ScaleGestureDetector.SimpleOnScaleGestureListener zoomListener =
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    try {
                        float currentZoomRatio =
                                camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                        float delta = detector.getScaleFactor();
                        camera.getCameraControl().setZoomRatio(currentZoomRatio * delta);
                        return true;
                    } catch (Exception e) {
                        camera.getCameraControl().setZoomRatio(1F);
                        return false;
                    }
                }
            };

    private final BroadcastReceiver onEncFinish = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showLastThumb();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootBinding = ActivityCameraNewctivityBinding.inflate(getLayoutInflater());
        setContentView(rootBinding.getRoot());
        setupHelpers();

        lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(onEncFinish, new IntentFilter("MEDIA_ENC_FINISH"));
        thumbSize = (int) Math.round(Helpers.getScreenWidthByColumns(this) / 1.4);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (LoginManager.checkIfLoggedIn(this)) {
            permissionHelper.requirePermissionsResult(this::startCamera);
        }
        rootBinding.cameraContainer.postDelayed(this::updateSystemUI, IMMERSIVE_FLAG_TIMEOUT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sendCameraStatusBroadcast(false, isVideoRecording);
        destroyOrientationListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lbm.unregisterReceiver(onEncFinish);
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
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

        android.view.WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = 1f;
        getWindow().setAttributes(layout);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), rootBinding.cameraContainer);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void setupHelpers() {
        permissionHelper = new PermissionHelper(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        //Initialize WindowManager to retrieve display metrics
        windowManager = new WindowManager(this);
        // Determine the output directory
        outputDirectory = getOutputDirectory();

        registerDisplayListener();
    }

    private void registerDisplayListener() {
        // Every time the orientation of device changes, update rotation for use cases
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
        rootBinding.viewFinder.post(new Runnable() {
            @Override
            public void run() {
                displayId = rootBinding.viewFinder.getDisplay().getDisplayId();
                updateCameraUi();
                setUpCamera();
            }
        });
    }

    /**
     * Method used to re-draw the camera UI controls, called every time configuration changes.
     */
    private void updateCameraUi() {
        initOrientationListener();
        // Remove previous UI if any
        if (cameraUiContainerBinding != null) {
            rootBinding.getRoot().removeView(cameraUiContainerBinding.getRoot());
        }
        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
                LayoutInflater.from(this),
                rootBinding.getRoot(),
                true
        );

        // In the background, load latest photo taken (if any) for gallery thumbnail
        // TODO - encrypt and show latest photo in tumbnail

        // Listener for button used to capture photo
        cameraUiContainerBinding.cameraCaptureButton.setOnClickListener(capturePhotoClickListener);
        cameraUiContainerBinding.cameraSwitchButton.setEnabled(false);
        cameraUiContainerBinding.cameraSwitchButton.setOnClickListener(cameraSwitchListener);
        cameraUiContainerBinding.photoViewButton.setOnClickListener(photoViewClickListener);
        cameraUiContainerBinding.cameraModeChanger.setOnClickListener(modeChangerClickListener);
        if (isVideoCapture) {
            cameraUiContainerBinding.cameraModeChanger.setImageResource(R.drawable.ic_photo);
            cameraUiContainerBinding.cameraCaptureButton.setImageDrawable(getDrawable(R.drawable.button_shutter_video));
            showLastThumb();
            isVideoRecording = false;
        }
    }

    private void bindCameraUseCases() {
        // Get screen metrics used to setup camera for full screen resolution
        Rect metrics = windowManager.getCurrentWindowMetrics().getBounds();
        int screenAspectRatio = aspectRatio(metrics.width(), metrics.height());
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
            }
            camera.getCameraControl().setExposureCompensationIndex(0);

            zoomAndFocus();

            // Attach the viewfinder's surface provider to preview use case
            preview.setSurfaceProvider(rootBinding.viewFinder.getSurfaceProvider());
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void zoomAndFocus() {
        rootBinding.viewFinder.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            MeteringPoint autoFocusPoint = new SurfaceOrientedMeteringPointFactory(1f, 1f)
                    .createPoint(.5f, .5f);
            try {
                FocusMeteringAction autoFocusAction = new FocusMeteringAction.Builder(
                        autoFocusPoint,
                        FocusMeteringAction.FLAG_AF
                ).setAutoCancelDuration(2, TimeUnit.SECONDS).build();
                camera.getCameraControl().startFocusAndMetering(autoFocusAction);
            } catch (Exception e) {
                Log.d("ERROR", "cannot access camera", e);
            }
        });

        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this, zoomListener);
        rootBinding.viewFinder.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            rootBinding.viewFinder.setOnTouchListener((view, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        return true;
                    case MotionEvent.ACTION_UP: {
                        MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(
                                rootBinding.viewFinder.getWidth(), rootBinding.viewFinder.getHeight()
                        );
                        MeteringPoint autoFocusPoint = factory.createPoint(event.getX(), event.getY());
                        try {
                            camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(
                                    autoFocusPoint,
                                    FocusMeteringAction.FLAG_AF
                            ).disableAutoCancel().build());
                        } catch (Exception e) {
                            Log.d("ERROR", "cannot access camera", e);
                        }
                        return true;
                    }
                }
                return false;
            });
        });
    }

    private void setUpCamera() {
        startService(new Intent(this, MediaEncryptService.class));
        sendCameraStatusBroadcast(true, false);
        showLastThumb();
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
        repositionChronoBar();
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        String name = Helpers.getTimestampedFilename(Helpers.VIDEO_FILE_PREFIX, ".mp4");
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, name);

        FileOutputOptions outputOptions = new FileOutputOptions.Builder(
                new File(FileManager.getCameraTmpDir(CameraNewActivity.this) + name)).build();

        // configure Recorder and Start recording to the mediaStoreOutput.
        activeRecording = videoCapture.getOutput().prepareRecording(this, outputOptions)
                .withEventListener(
                        cameraExecutor,
                        (Consumer<VideoRecordEvent>) videoRecordEvent -> {
                            if (!(videoRecordEvent instanceof VideoRecordEvent.Status)) {
                                recordingState = videoRecordEvent;
                            }
//                            updateUI(videoRecordEvent);

                            if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                saveVideo(videoRecordEvent);
                            }
                        }).start();
        Log.i(TAG, "Recording started");
    }

    private void takePicture() {
        String filename = Helpers.getTimestampedFilename(Helpers.IMAGE_FILE_PREFIX, ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                new File(FileManager.getCameraTmpDir(CameraNewActivity.this) + filename))
                .build();
        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                File file = new File(outputFileResults.getSavedUri().getPath());
                if (!LoginManager.isKeyInMemory()) {
                    getThumbIntoMemory(file, false);
                }
                sendNewMediaBroadcast(file);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exception);
            }
        });
    }


    private void saveVideo(VideoRecordEvent event) {
        File file = new File(((VideoRecordEvent.Finalize) event).getOutputResults().getOutputUri().getPath());
        sendNewMediaBroadcast(file);
        if (!LoginManager.isKeyInMemory()) {
            getThumbIntoMemory(file, true);
        }
        Log.d(TAG, ((VideoRecordEvent.Finalize) event).getOutputResults().getOutputUri().getPath());
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

    private int aspectRatio(int width, int height) {
        double previewRatio = (double) max(width, height) / min(width, height);
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    private File getOutputDirectory() {
        File mediaDir = getExternalMediaDirs()[0];
        if (mediaDir != null) {
            boolean isCreated =
                    new File(mediaDir, getApplicationContext().getString(R.string.app_name)).mkdirs();

        }
        return (mediaDir != null && mediaDir.exists()) ? mediaDir : getApplicationContext().getFilesDir();
    }

    private void playSoundEffect() {
        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mgr.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, 1.0f);
    }

    private void showLastThumb() {
        (new ShowLastThumbAsyncTask(this, cameraUiContainerBinding.photoViewButton))
                .setThumbSize(thumbSize)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        cameraUiContainerBinding.lastPhotoContainer.bringToFront();
    }

    private void sendNewMediaBroadcast(File file) {
        Log.d(TAG, file.getPath());
        startService(new Intent(this, MediaEncryptService.class));
        Intent broadcastIntent = new Intent("NEW_MEDIA");
        broadcastIntent.putExtra("file", file.getAbsolutePath());
        lbm.sendBroadcast(broadcastIntent);
    }

    private void sendCameraStatusBroadcast(boolean isRunning, boolean wasRecoringVideo) {
        Intent broadcastIntent = new Intent("CAMERA_STATUS");
        broadcastIntent.putExtra("isRunning", isRunning);
        broadcastIntent.putExtra("wasRecoringVideo", wasRecoringVideo);
        lbm.sendBroadcast(broadcastIntent);
    }

    private void getThumbIntoMemory(File file, boolean isVideo) {
        (new GetThumbFromPlainFile(this, file).setThumbSize(thumbSize).setIsVideo(isVideo).setOnFinish(new OnAsyncTaskFinish() {
            @Override
            public void onFinish(Object object) {
                super.onFinish(object);
                if (object != null) {
                    cameraUiContainerBinding.photoViewButton.setImageBitmap((Bitmap) object);
                }
            }
        })).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }



    // Rotate UI
	private void rotateUIElements(){
		int oldDeviceRotation = mDeviceRotation;
		int oldOverallRotation = mOverallRotation;
		mDeviceRotation = getCurrentDeviceRotation();

		mOverallRotation += getHowMuchRotated(oldDeviceRotation, getCurrentDeviceRotation());

		//oldOverallRotation -= 90;
		int overallRotation1 = mOverallRotation;

		rotateElement(cameraUiContainerBinding.photoViewButton, oldOverallRotation, overallRotation1);
		rotateElement(cameraUiContainerBinding.flashButton, oldOverallRotation, overallRotation1);
		rotateElement(cameraUiContainerBinding.cameraSwitchButton, oldOverallRotation, overallRotation1);
		rotateElement(cameraUiContainerBinding.cameraModeChanger, oldOverallRotation, overallRotation1);
	}

	private void rotateElement(View view, int start, int end){
		RotateAnimation rotateAnimation = new RotateAnimation(start, end, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		rotateAnimation.setInterpolator(new LinearInterpolator());
		rotateAnimation.setDuration(500);
		rotateAnimation.setFillAfter(true);
		rotateAnimation.setFillEnabled(true);


		view.startAnimation(rotateAnimation);
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


	private void repositionChronoBar(){
		int rotation = getCurrentDeviceRotation();
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
		        cameraUiContainerBinding.chronoBar.getLayoutParams());

		params.addRule(RelativeLayout.BELOW, 0);
		params.addRule(RelativeLayout.ABOVE, 0);
		params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
		params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
		params.addRule(RelativeLayout.ALIGN_PARENT_START, 0);
		params.addRule(RelativeLayout.ALIGN_PARENT_END, 0);

		int marginPx = Helpers.convertDpToPixels(this,10);

		params.topMargin = marginPx;
		params.rightMargin = marginPx;
		params.bottomMargin = marginPx;
		params.leftMargin = marginPx;

		switch (rotation){
			case 0:
				params.addRule(RelativeLayout.BELOW, R.id.topPanel);
				params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
				break;
			case 90:
				params.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE);
				params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
				break;
			case 180:
				params.addRule(RelativeLayout.ABOVE, R.id.bottomPanel);
				params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
				break;
			case 270:
				params.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE);
				params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
				break;
		}
        cameraUiContainerBinding.chronoBar.setLayoutParams(params);
        cameraUiContainerBinding.chronoBar.setRotation(rotation);
	}


}