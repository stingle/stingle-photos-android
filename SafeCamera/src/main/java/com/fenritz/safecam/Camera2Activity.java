/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fenritz.safecam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fenritz.safecam.Camera.CameraImageSize;
import com.fenritz.safecam.Util.AsyncTasks;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.LoginManager;
import com.fenritz.safecam.Widget.AutoFitTextureView;
import com.fenritz.safecam.Widget.FocusCircleView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Camera2Activity extends Activity implements View.OnClickListener {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;
    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
    private static final long PRECAPTURE_TIMEOUT_MS = 5000;
    private static final double ASPECT_RATIO_TOLERANCE = 0.005;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private boolean mFlashSupported = false;
    private boolean mAESupported = false;
    private OrientationEventListener mOrientationListener;

    private AutoFitTextureView mTextureView;
    private HandlerThread mBackgroundThread;
    private final AtomicInteger mRequestCounter = new AtomicInteger();
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private final Object mCameraStateLock = new Object();
    private String mCameraId;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private CameraCharacteristics mCharacteristics;
    private Handler mBackgroundHandler;
    private RefCountedAutoCloseable<ImageReader> mJpegImageReader;
    //private RefCountedAutoCloseable<ImageReader> mRawImageReader;
    private boolean mNoAFRun = false;
    private int mPendingUserCaptures = 0;
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mJpegResultQueue = new TreeMap<>();
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mRawResultQueue = new TreeMap<>();
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private long mCaptureTimer;
    Surface mPreviewSurface;
    private static final String TAG = "Camera2Activity";

    private static final int  STATE_INIT = 0;
    private static final int  STATE_PREVIEW = 1;
    private static final int  STATE_PICTURE_TAKEN = 2;
    private static final int  STATE_WAITING_LOCK = 3;
    private static final int  STATE_WAITING_PRECAPTURE = 4;
    private static final int  STATE_WAITING_NON_PRECAPTURE = 5;
    private static final int  STATE_STARTING_RECORDING = 6;
    private static final int  STATE_STOPING_RECORDING = 7;
    private static final int  STATE_RECORDING = 8;
    private static final int  STATE_CAMERA_CLOSED = 9;
    private static final int  STATE_CAMERA_OPENED = 10;

    private int mState = STATE_INIT;


    public static final String FLASH_MODE_PREF = "flash_mode2";
    public static final int FLASH_MODE_OFF = 0;
    public static final int FLASH_MODE_ON = 1;
    public static final int FLASH_MODE_AUTO = 2;

    public static final String TIMER_MODE = "timer_mode2";
    public static final String PHOTO_SIZE = "photo_size2";
    public static final String PHOTO_SIZE_FRONT = "photo_size_front2";

    private ImageButton flashButton;
	private ImageView galleryButton;
    private ImageButton modeChangerButton;
    private int flashMode = FLASH_MODE_AUTO;
    private SharedPreferences preferences;

    private boolean isFrontCamera = false;
    private OrientationEventListener mOrientationEventListener;
    private int mOrientation = -1;

    private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
    private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
    private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
    private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;

    private boolean isInVideoMode = false;
    private CameraImageSize mVideoSize;
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo = false;
    private Integer mSensorOrientation;

    private static Bitmap mLastThumbBitmap;
	private File lastFile;

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private String lastVideoFilename = "";
    private String lastVideoFilenameEnc = "";
    private SharedPreferences sharedPrefs;
    private Range<Integer> mPreviewFpsRange = null;

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    public long mDownEventAtMS;
    public float mDownEventAtX;
    public float mDownEventAtY;
    public boolean mIsFocusSupported = false;
    public boolean mIsZoomSupported = false;
	public static final int CLICK_MS = 250;
	public static final int CLICK_DIST = 20;

	public boolean isSetFocusPoint = false;
	public float focusPointX = 0;
	public float focusPointY = 0;
	private FocusCircleView mFocusCircleView;
	private final Handler clearFocusHandler = new Handler();
	private Runnable clearFocusRunnable = null;
	private ScaleGestureDetector scaleGestureDetector;

	private int currentZoomValue;
	private ArrayList<Integer> mZoomRatios;
	private float mMaxZoom;
	private boolean mIsTouchIsMulti = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.camera2);

        mFocusCircleView = (FocusCircleView)findViewById(R.id.focusCircle);

        findViewById(R.id.take_photo).setOnClickListener(this);

		scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        LinearLayout textureHolder = (LinearLayout)findViewById(R.id.textureHolder);
        mTextureView = new AutoFitTextureView(this);
		mTextureView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {

				if (scaleGestureDetector != null) {
					scaleGestureDetector.onTouchEvent(event);
				}
				if( event.getPointerCount() > 1 ) {
					//multitouch_time = System.currentTimeMillis();
					mIsTouchIsMulti = true;
					return true;
				}
				mIsTouchIsMulti = false;

				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					mDownEventAtMS = System.currentTimeMillis();
					mDownEventAtX = event.getX();
					mDownEventAtY = event.getY();
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					if (mIsFocusSupported && System.currentTimeMillis() - mDownEventAtMS < CLICK_MS &&
							mCaptureSession != null &&
							Math.abs(event.getX() - mDownEventAtX) < CLICK_DIST &&
							Math.abs(event.getY() - mDownEventAtY) < CLICK_DIST) {
						try {
							//focusArea(event.getX(), event.getY(), true);
							isSetFocusPoint = true;
							focusPointX = event.getX();
							focusPointY = event.getY();
							isPassiveFocused = false;
							focusMovedTime = 0;
							ArrayList<Area> areas = getAreas(event.getX(), event.getY());
							setMeteringArea(areas);
							mFocusCircleView.drawFocusCircle(event.getX(), event.getY());

							/*removeFocusClearCallback();

							clearFocusRunnable = new Runnable() {
								@Override
								public void run() {
									Log.d("focus", "removeFocus");
									clearFocusRunnable = null;
									clearMeteringArea();
								}
							};

							clearFocusHandler.postDelayed(clearFocusRunnable, 10000);*/
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}

				if (mIsZoomSupported && event.getPointerCount() > 1 && mCaptureSession != null) {
					try {
						//handleZoom(event);
					} catch (Exception e) {

					}
				}
				return true;
				//return onPreviewTouch(v, event);
			}
		});
		textureHolder.addView(mTextureView);


        preferences = getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, MODE_PRIVATE);
        flashMode = preferences.getInt(FLASH_MODE_PREF, FLASH_MODE_AUTO);

        flashButton = (ImageButton) findViewById(R.id.flashButton);
        flashButton.setOnClickListener(toggleFlash());

		galleryButton = (ImageView) findViewById(R.id.lastPhoto);
		galleryButton.setOnClickListener(openGallery());

        modeChangerButton = (ImageButton) findViewById(R.id.modeChanger);
        modeChangerButton.setOnClickListener(toggleCameraMode());

        findViewById(R.id.switchCamButton).setOnClickListener(toggleCamera());

        setFlashButtonImage();
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(Camera2Activity.this);
    }



    @Override
    public void onResume() {
        super.onResume();

		mLastThumbBitmap = null;

        initOrientationListener();
        startBackgroundThread();
        openCamera();
		showLastPhotoThumb();

        if (mTextureView.isAvailable()) {
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        if (mOrientationListener != null && mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
    }

    @Override
    public void onPause() {
        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        closeCamera();
        stopBackgroundThread();
        destroyOrientationListener();
        super.onPause();
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.take_photo: {
                takePicture();
                break;
            }
        }
    }

	public void removeFocusClearCallback(){
		if(clearFocusRunnable != null){
			clearFocusHandler.removeCallbacks(clearFocusRunnable);
			clearFocusRunnable = null;
		}
	}

    private View.OnClickListener toggleFlash() {
        return new View.OnClickListener() {
            public void onClick(View v) {

                if (flashMode == FLASH_MODE_OFF) {
                    flashMode = FLASH_MODE_AUTO;
                }
                else if (flashMode == FLASH_MODE_AUTO) {
                    flashMode = FLASH_MODE_ON;
                }
                else if (flashMode == FLASH_MODE_ON) {
                    flashMode = FLASH_MODE_OFF;
                }

                setFlashButtonImage();
                applyFlashMode();

                preferences.edit().putInt(FLASH_MODE_PREF, flashMode).commit();
            }
        };
    }

    private void setFlashButtonImage(){
        switch (flashMode){
            case FLASH_MODE_AUTO:
                flashButton.setImageResource(R.drawable.flash_auto);
                break;
            case FLASH_MODE_ON:
                flashButton.setImageResource(R.drawable.flash_on);
                break;
            case FLASH_MODE_OFF:
                flashButton.setImageResource(R.drawable.flash_off);
                break;
        }
    }

    private void applyFlashMode(){
        Log.d("Func", "applyFlashMode");
        if ((mState == STATE_PREVIEW || mState == STATE_RECORDING) && mFlashSupported) {
            try {
                //mPreviewRequestBuilder = createPreviewRequestBuilder(CameraDevice.TEMPLATE_PREVIEW);
                setupControls(mPreviewRequestBuilder);
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
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
                        //changeRotation(mOrientation);
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


    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here if
            // the TextureView displaying this has been set up.
            synchronized (mCameraStateLock) {
                Log.d("Func", "StateCallback - onOpened");
                mState = STATE_CAMERA_OPENED;
                mCameraOpenCloseLock.release();
                mCameraDevice = cameraDevice;

                // Start the preview session if the TextureView has been set up already.
                if (mPreviewSize != null && mTextureView.isAvailable()) {
                    createCameraPreviewSession();
                }
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            synchronized (mCameraStateLock) {
                Log.d("Func", "StateCallback - onDisconnected");
                mState = STATE_CAMERA_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "Received camera device error: " + error);
            synchronized (mCameraStateLock) {
                Log.d("Func", "StateCallback - onError");
                mState = STATE_CAMERA_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }
            Camera2Activity.this.finish();
        }

    };

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * JPEG image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnJpegImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d("Func", "onImageAvailable - jpeg");
            dequeueAndSaveImage(mJpegResultQueue, mJpegImageReader);
        }

    };


    /*private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener  = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d("Func", "onImageAvailable - raw");
            dequeueAndSaveImage(mRawResultQueue, mRawImageReader);
        }

    };*/

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events for the preview and
     * pre-capture sequence.
     */
    private boolean isPassiveFocused = false;
    private long focusMovedTime = 0;
    private CameraCaptureSession.CaptureCallback mPreCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            //Log.d("AF_STATE", String.valueOf(result.get(CaptureResult.CONTROL_AF_STATE)));
            //Log.d("AE_STATE", String.valueOf(result.get(CaptureResult.CONTROL_AE_STATE)));
            switch (mState) {
                case STATE_PREVIEW: {
                    //Log.d("Func", "PREVIEW");
                    // We have nothing to do when the camera preview is running normally.
					if(isSetFocusPoint){
						int afState = result.get(CaptureResult.CONTROL_AF_STATE);
						//Log.d("afState", String.valueOf(afState));

						boolean isScanning = afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN || afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN;
						boolean isFocused = afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;

						if(!isPassiveFocused && isFocused){
							isPassiveFocused = true;
							Log.d("afState", "Focused - " + String.valueOf(afState));
						}
						else{
							if(isScanning) {
								if (focusMovedTime == 0) {
									focusMovedTime = System.currentTimeMillis();
									Log.d("afState", "Reset start - " + String.valueOf(afState));
								} else if (System.currentTimeMillis() - focusMovedTime > 1000) {
									//removeFocusClearCallback();
									clearMeteringArea();
									isPassiveFocused = false;
									focusMovedTime = 0;
									Log.d("afState", "Reset naxuy - " + String.valueOf(afState));
								}
							}
							else if(isFocused){
								if(focusMovedTime !=0) {
									Log.d("afState", "Reset to focused - " + String.valueOf(afState));
								}
								focusMovedTime = 0;
							}
						}
					}

                    break;
                }
                case STATE_WAITING_LOCK: {
                    Log.d("Func", "WAITING LOCK");
                    boolean readyAF = false;
                    boolean readyAE = false;
                    boolean readyAWB = false;

                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);
                    Integer flashState = result.get(CaptureResult.FLASH_STATE);

                    if (afState == null) {
                        Log.d("REASON", "AF IS NULL");
                        readyAF = true;
                    }
                    else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        readyAF = true;
                        Log.d("REASON", "AF IS OK");
                    }
                    else{
                        Log.d("REASON", "AF NOT READY");
                        Log.d("REASON", String.valueOf(result.get(CaptureResult.CONTROL_AF_STATE)));
                    }


                    if(awbState == null || awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED){
                        readyAWB = true;
                        Log.d("REASON", "AWB IS OK");
                    }
                    else{
                        Log.d("REASON", "AWB NOT READY");
                    }
                    if(flashState == CaptureResult.FLASH_STATE_FIRED){
                        Log.d("REASON", "FLASH FIRED READY");
                    }
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        readyAE = true;
                        Log.d("REASON", "AE IS OK");
                    }
                    else if (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED){
                        readyAE = true;
                        Log.d("REASON", "AE NEED FLASH");
                    }
                    else {
                        Log.d("REASON", "AE NOT READY");
                        Log.d("REASON", String.valueOf(result.get(CaptureResult.CONTROL_AE_STATE)));

                        //runPrecaptureSequence();
                        //mState = STATE_WAITING_PRECAPTURE;
                        //startTimer();
                    }

                    if ((readyAF && readyAE && readyAWB) || hitTimeout()) {
                        takePictureAfterPrecapture();
                        // After this, the camera will go back to the normal state of preview.
                        mState = STATE_PREVIEW;
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    Log.d("Func", "WAITING PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    Log.d("aeState", String.valueOf(aeState));
                    Log.d("awbState", String.valueOf(result.get(CaptureResult.CONTROL_AWB_STATE)));
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                        startTimer();
                    }
                    else if (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED){
                        takePictureAfterPrecapture();
                        Log.d("flash", "qaq");
                    }
                    else if(hitTimeout()){
                        takePictureAfterPrecapture();
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    Log.d("Func", "WAITING NON PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    Log.d("aeState", String.valueOf(aeState));
                    Log.d("awbState", String.valueOf(result.get(CaptureResult.CONTROL_AWB_STATE)));
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE || hitTimeout()) {
                        takePictureAfterPrecapture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            //Log.d("Func", "onCaptureProgressed");
            //process(partialResult);
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            //Log.d("Func", "onCaptureCompleted");
            process(result);
            super.onCaptureCompleted(session, request, result);
        }

    };

    private void takePictureAfterPrecapture(){
        if (mPendingUserCaptures > 0) {
            // Capture once for each user tap of the "Picture" button.
            while (mPendingUserCaptures > 0) {
                captureStillPicture();
                mPendingUserCaptures--;
            }
            // After this, the camera will go back to the normal state of preview.
            //mState = STATE_PREVIEW;
        }
    }

    private void runPrecaptureSequence() {
        try {
            /*CaptureRequest.Builder precaptureBuilder = createPreviewRequestBuilder(CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);

            precaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            mCaptureSession.capture(precaptureBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
            mCaptureSession.setRepeatingRequest(precaptureBuilder.build(), mPreCaptureCallback, mBackgroundHandler);

            // now set precapture
            precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(precaptureBuilder.build(), mPreCaptureCallback, mBackgroundHandler);*/

            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            //mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
            //mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);

            // now set precapture
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);

        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            Log.d("Func", "captureStillPicture");
            if ( null == mCameraDevice) {
                return;
            }

            mState = STATE_PICTURE_TAKEN;
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            captureBuilder.addTarget(mJpegImageReader.get().getSurface());
            //captureBuilder.addTarget(mRawImageReader.get().getSurface());

            // Use the same AE and AF modes as the preview.
            setupControls(captureBuilder);

            // Set orientation.
            mSensorOrientation = mCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int deviceRotation = getCurrentDeviceRotation();
            Log.d("sensorOrientation", String.valueOf(mSensorOrientation));
            Log.d("getCurrentDeviceRotation", String.valueOf(deviceRotation));

            int invert = 0;
            if(!isFrontCamera && (deviceRotation == 90 || deviceRotation == 270)){
                invert = 180;
            }

            int jpegRotation = (mSensorOrientation + deviceRotation + invert) % 360;
            Log.d("jpegRotation", String.valueOf(jpegRotation));

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegRotation);

            // Set request tag to easily track results in callbacks.
            captureBuilder.setTag(mRequestCounter.getAndIncrement());

            CaptureRequest request = captureBuilder.build();

            // Create an ImageSaverBuilder in which to collect results, and add it to the queue
            // of active requests.
            ImageSaver.ImageSaverBuilder jpegBuilder = new ImageSaver.ImageSaverBuilder(Camera2Activity.this).setCharacteristics(mCharacteristics);
            //ImageSaver.ImageSaverBuilder rawBuilder = new ImageSaver.ImageSaverBuilder(Camera2Activity.this).setCharacteristics(mCharacteristics);

            mJpegResultQueue.put((int) request.getTag(), jpegBuilder);
            // mRawResultQueue.put((int) request.getTag(), rawBuilder);

            //mCaptureSession.stopRepeating();
            //mCaptureSession.abortCaptures();
            mCaptureSession.capture(request, mCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles the still JPEG and RAW capture
     * request.
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                     long timestamp, long frameNumber) {
            Log.d("Func", "mCaptureCallback - onCaptureStarted");
            String currentDateTime = generateTimestamp();

            String filename = Helpers.getTimestampedFilename(Helpers.IMAGE_FILE_PREFIX, ".jpg");
            String filenameEnc = Helpers.getNewEncFilename();

            String path = Helpers.getHomeDir(Camera2Activity.this);
            String finalPath = path + "/" + filenameEnc;

            // Look up the ImageSaverBuilder for this request and update it with the file name
            // based on the capture start time.
            ImageSaver.ImageSaverBuilder jpegBuilder;
            try {
				int requestId = (int) request.getTag();
				synchronized (mCameraStateLock) {
					jpegBuilder = mJpegResultQueue.get(requestId);
					//rawBuilder = mRawResultQueue.get(requestId);
				}
				if (jpegBuilder != null) {
					jpegBuilder.setFile(new File(finalPath));
					jpegBuilder.setFileName(filename);
					jpegBuilder.setOnFinish(new AsyncTasks.OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							showLastPhotoThumb();
						}
					});
				}
			}
			catch (NullPointerException | ClassCastException e){}


            //if (rawBuilder != null) rawBuilder.setFile(rawFile);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            Log.d("Func", "mCaptureCallback - onCaptureCompleted");
            try {
				int requestId = (int) request.getTag();
				ImageSaver.ImageSaverBuilder jpegBuilder;
				//ImageSaver.ImageSaverBuilder rawBuilder;

				// Look up the ImageSaverBuilder for this request and update it with the CaptureResult
				synchronized (mCameraStateLock) {
					jpegBuilder = mJpegResultQueue.get(requestId);
					//rawBuilder = mRawResultQueue.get(requestId);

					if (jpegBuilder != null) {
						jpegBuilder.setResult(result);
					}
                /*if (rawBuilder != null) {
                    rawBuilder.setResult(result);
                    if (jpegBuilder != null) sb.append(", ");
                    sb.append("Saving RAW as: ");
                    sb.append(rawBuilder.getSaveLocation());
                }*/

					// If we have all the results necessary, save the image to a file in the background.
					handleCompletion(requestId, jpegBuilder, mJpegResultQueue);
					//handleCompletion(requestId, rawBuilder, mRawResultQueue);

					//finishedCapture();
					unlockFocus();
				}
			}
			catch (NullPointerException | ClassCastException e){

			}
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                    CaptureFailure failure) {
            Log.d("Func", "mCaptureCallback - onCaptureFailed");
            int requestId = (int) request.getTag();
            synchronized (mCameraStateLock) {
                mJpegResultQueue.remove(requestId);
                mRawResultQueue.remove(requestId);
                unlockFocus();
            }
            showToast("Capture failed!");
        }

    };

    /**
     * A {@link Handler} for showing {@link Toast}s on the UI thread.
     */
    private final Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Toast.makeText(Camera2Activity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
        }
    };


    /**
     * Sets up state related to camera that is needed before opening a {@link CameraDevice}.
     */
    private boolean setUpCameraOutputs() {
        Log.d("Func", "setUpCameraOutputs");
        CameraManager manager = (CameraManager) Camera2Activity.this.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            ErrorDialog.buildErrorDialog("This device doesn't support Camera2 API.").show(getFragmentManager(), "dialog");
            return false;
        }
        try {
            // Find a CameraDevice that supports RAW captures, and configure state.
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We only use a camera that supports RAW in this sample.
                /*if (!contains(characteristics.get( CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES), CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    continue;
                }*/

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(isFrontCamera && facing != CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                CameraImageSize largestJpeg = null;
                if(!isInVideoMode) {
                    largestJpeg = getPhotoSize(map, characteristics);
                    mPreviewFpsRange = largestJpeg.fpsRange;
                }
                else {
                    mVideoSize = getVideoSize(map, characteristics);
                    mPreviewFpsRange = mVideoSize.fpsRange;
                }

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                //Size largestRaw = Collections.max( Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)), new CompareSizesByArea());

                synchronized (mCameraStateLock) {
                    // Set up ImageReaders for JPEG and RAW outputs.  Place these in a reference
                    // counted wrapper to ensure they are only closed when all background tasks
                    // using them are finished.
                    if(!isInVideoMode && largestJpeg != null) {
                        if (mJpegImageReader == null || mJpegImageReader.getAndRetain() == null) {
                            mJpegImageReader = new RefCountedAutoCloseable<>(ImageReader.newInstance(largestJpeg.width, largestJpeg.height, ImageFormat.JPEG, /*maxImages*/5));
                        }
                        mJpegImageReader.get().setOnImageAvailableListener(mOnJpegImageAvailableListener, mBackgroundHandler);
                    }
                    else{
                        mMediaRecorder = new MediaRecorder();
                    }




                    /*if (mRawImageReader == null || mRawImageReader.getAndRetain() == null) {
                        mRawImageReader = new RefCountedAutoCloseable<>(ImageReader.newInstance(largestRaw.getWidth(), largestRaw.getHeight(), ImageFormat.RAW_SENSOR, 5));
                    }
                    mRawImageReader.get().setOnImageAvailableListener(mOnRawImageAvailableListener, mBackgroundHandler);*/

                    // Check if the flash is supported.
                    mFlashSupported = (boolean)characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    mAESupported = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) == null ? false : true;
					mIsZoomSupported = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) > 0f;
					mIsFocusSupported = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1;

					// Get zoom params
					mZoomRatios = new ArrayList<>();
					mZoomRatios.add(100);
					double zoom = 1.0;
					mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
					final int steps_per_2x_factor = 20;
					int n_steps =(int)( (steps_per_2x_factor * Math.log(mMaxZoom + 1.0e-11)) / Math.log(2.0));
					final double scale_factor = Math.pow(mMaxZoom, 1.0/(double)n_steps);
					for(int i=0;i<n_steps-1;i++) {
						zoom *= scale_factor;
						mZoomRatios.add((int)(zoom*100));
					}
					mZoomRatios.add((int)(mMaxZoom*100));
					mMaxZoom = mZoomRatios.size()-1;
					////////////////

                    if(!mFlashSupported){
                        Log.d("flash", "FLASH NOT SUPPORTED");
                        flashButton.setVisibility(View.INVISIBLE);
                    }
                    else{
                        Log.d("flash", "FLASH IS SUPPORTED");
                        flashButton.setVisibility(View.VISIBLE);
                    }

                    mCharacteristics = characteristics;
                    mCameraId = cameraId;
                }
                return true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return false;
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera() {
        if (!setUpCameraOutputs()) {
            return;
        }
        if (!hasAllPermissionsGranted()) {
            requestCameraPermission();
            requestAudioPermission();
            return;
        }
        Log.d("Func", "openCamera");
        CameraManager manager = (CameraManager) Camera2Activity.this.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Wait for any previously running session to finish.
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String cameraId;
            Handler backgroundHandler;
            synchronized (mCameraStateLock) {
                cameraId = mCameraId;
                backgroundHandler = mBackgroundHandler;
            }

            // Attempt to open the camera. mStateCallback will be called on the background handler's
            // thread when this succeeds or fails.
            manager.openCamera(cameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    public boolean requestCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {

                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.camera_perm_explain))
                        .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(new String[]{Manifest.permission.CAMERA}, SafeCameraApplication.REQUEST_CAMERA_PERMISSION);
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Camera2Activity.this.finish();
                                    }
                                }
                        )
                        .create()
                        .show();

            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, SafeCameraApplication.REQUEST_CAMERA_PERMISSION);
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
                                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, SafeCameraApplication.REQUEST_AUDIO_PERMISSION);
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Camera2Activity.this.finish();
                                    }
                                }
                        )
                        .create()
                        .show();

            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, SafeCameraApplication.REQUEST_AUDIO_PERMISSION);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case SafeCameraApplication.REQUEST_CAMERA_PERMISSION:
            case SafeCameraApplication.REQUEST_AUDIO_PERMISSION:{
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    closeCamera();
                    openCamera();
                } else {
                    finish();
                }
                return;
            }
        }
    }


    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            Log.d("Func", "closeCamera");
            mCameraOpenCloseLock.acquire();
            synchronized (mCameraStateLock) {


                // Reset state and clean up resources used by the camera.
                // Note: After calling this, the ImageReaders will be closed after any background
                // tasks saving Images from these readers have been completed.
                mPendingUserCaptures = 0;
                mState = STATE_CAMERA_CLOSED;
                if (null != mCaptureSession) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (null != mJpegImageReader) {
                    mJpegImageReader.close();
                    mJpegImageReader = null;
                }
                if (null != mMediaRecorder) {
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                }
                /*if (null != mRawImageReader) {
                    mRawImageReader.close();
                    mRawImageReader = null;
                }*/
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        Log.d("Func", "startBackgroundThread");
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        synchronized (mCameraStateLock) {
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        Log.d("Func", "stopBackgroundThread");
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            synchronized (mCameraStateLock) {
                mBackgroundHandler = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void createCameraPreviewSession() {
        try {
            Log.d("Func", "createCameraPreviewSession");
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            mPreviewSurface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(isInVideoMode ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, isInVideoMode ? CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD : CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);

            List<Surface> surfaces = new ArrayList<>();

            surfaces.add(mPreviewSurface);

            Surface surface;
            if(!isInVideoMode){
                surfaces.add(mJpegImageReader.get().getSurface());
            }

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            synchronized (mCameraStateLock) {
                                Log.d("Func", "createCaptureSession - onConfigured");
                                // The camera is already closed
                                if (null == mCameraDevice) {
                                    return;
                                }

                                try {
                                    setupControls(mPreviewRequestBuilder);
                                    // Finally, we start displaying the camera preview.
                                    cameraCaptureSession.setRepeatingRequest( mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
                                    mState = STATE_PREVIEW;
                                } catch (CameraAccessException | IllegalStateException e) {
                                    e.printStackTrace();
                                    return;
                                }
                                // When the session is ready, we start displaying the preview.
                                mCaptureSession = cameraCaptureSession;
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed to configure camera.");
                        }
                    }, mBackgroundHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CaptureRequest.Builder createPreviewRequestBuilder(int intent){
        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, intent);
            //builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.addTarget(mPreviewSurface);
            setupControls(builder);

            return builder;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Configure the given {@link CaptureRequest.Builder} to use auto-focus, auto-exposure, and
     * auto-white-balance controls if available.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @param builder the builder to configure.
     */
    private void setupControls(CaptureRequest.Builder builder) {
        Log.d("Func", "setupControls");
        // Enable auto-magical 3A run by camera device
        //builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        Float minFocusDist = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

        // If MINIMUM_FOCUS_DISTANCE is 0, lens is fixed-focus and we need to skip the AF run.
        mNoAFRun = (minFocusDist == null || minFocusDist == 0);

        if (!mNoAFRun) {
            // If there is a "continuous picture" mode available, use it, otherwise default to AUTO.
            if (contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            }
        }

        setAEMode(builder);
        setCameraZoom(currentZoomValue, builder, false);

        // If there is an auto-magical white balance control mode available, use it.
        if (contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES), CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            // Allow AWB to run auto-magically if this device supports this
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
    }

    private void setAEMode(CaptureRequest.Builder builder){
        // If there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        if (mFlashSupported && contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES), CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            switch (flashMode){
                case FLASH_MODE_AUTO:
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
                case FLASH_MODE_ON:
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    break;
                case FLASH_MODE_OFF:
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    break;
            }
            //builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
        }
        else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }

        if(mPreviewFpsRange != null) {
            Log.d("prevewFps", String.valueOf(mPreviewFpsRange));
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mPreviewFpsRange);
        }
    }

    /**
     * Configure the necessary {@link android.graphics.Matrix} transformation to `mTextureView`,
     * and start/restart the preview capture session if necessary.
     * <p/>
     * This method should be called after the camera state has been initialized in
     * setUpCameraOutputs.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        synchronized (mCameraStateLock) {
            //Log.d("Func", "configureTransform");
            if (null == mTextureView) {
                return;
            }

            // Find the rotation of the device relative to the native device orientation.
            int deviceRotation = Camera2Activity.this.getWindowManager().getDefaultDisplay().getRotation();
            Point displaySize = new Point();
            Camera2Activity.this.getWindowManager().getDefaultDisplay().getSize(displaySize);

            // Find the rotation of the device relative to the camera sensor's orientation.
            int totalRotation = sensorToDeviceRotation(mCharacteristics, deviceRotation);

            // Swap the view dimensions for calculation as needed if they are rotated relative to
            // the sensor.
            boolean swappedDimensions = totalRotation == 90 || totalRotation == 270;
            int rotatedViewWidth = viewWidth;
            int rotatedViewHeight = viewHeight;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedViewWidth = viewHeight;
                rotatedViewHeight = viewWidth;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            // Preview should not be larger than display size and 1080p.
            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // Find the best preview size for these view dimensions and configured JPEG size.
            Size chosedSize;
            if(!isInVideoMode){
                chosedSize = getPhotoSize(map, mCharacteristics).getSize();
            }
            else{
                //chosedSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
                chosedSize = getVideoSize(map, mCharacteristics).getSize();
            }
            Size previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedViewWidth, rotatedViewHeight, maxPreviewWidth, maxPreviewHeight, chosedSize);

            if (swappedDimensions) {
                mTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            } else {
                mTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            }

            // Find rotation of device in degrees (reverse device orientation for front-facing
            // cameras).
            int rotation = (mCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) ?
                    (360 - ORIENTATIONS.get(deviceRotation)) % 360 :
                    (360 - ORIENTATIONS.get(deviceRotation)) % 360;

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            // Initially, output stream images from the Camera2 API will be rotated to the native
            // device orientation from the sensor's orientation, and the TextureView will default to
            // scaling these buffers to fill it's view bounds.  If the aspect ratios and relative
            // orientations are correct, this is fine.
            //
            // However, if the device orientation has been rotated relative to its native
            // orientation so that the TextureView's dimensions are swapped relative to the
            // native device orientation, we must do the following to ensure the output stream
            // images are not incorrectly scaled by the TextureView:
            //   - Undo the scale-to-fill from the output buffer's dimensions (i.e. its dimensions
            //     in the native device orientation) to the TextureView's dimension.
            //   - Apply a scale-to-fill from the output buffer's rotated dimensions
            //     (i.e. its dimensions in the current device orientation) to the TextureView's
            //     dimensions.
            //   - Apply the rotation from the native device orientation to the current device
            //     rotation.
            if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / previewSize.getHeight(),
                        (float) viewWidth / previewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);

            }
            matrix.postRotate(rotation, centerX, centerY);

            mTextureView.setTransform(matrix);

            // Start or restart the active capture session if the preview was initialized or
            // if its aspect ratio changed significantly.
            if (mPreviewSize == null || !checkAspectsEqual(previewSize, mPreviewSize)) {
                mPreviewSize = previewSize;
                if (mCameraDevice != null && mState != STATE_CAMERA_CLOSED) {
                    Log.d("cameraState", String.valueOf(mState));
                    createCameraPreviewSession();
                }
            }
        }
    }

    public CameraImageSize getPhotoSize(StreamConfigurationMap map, CameraCharacteristics characteristics){
        ArrayList<CameraImageSize> photoSizes = Helpers.parsePhotoOutputs(this, map, characteristics);
        String sizeIndex = "0";
        if(isFrontCamera){
            sizeIndex = sharedPrefs.getString("front_photo_res", "0");
        }
        else{
            sizeIndex = sharedPrefs.getString("back_photo_res", "0");
        }

        return photoSizes.get(Integer.parseInt(sizeIndex));
    }

    public CameraImageSize getVideoSize(StreamConfigurationMap map, CameraCharacteristics characteristics){
        ArrayList<CameraImageSize> videoSizes = Helpers.parseVideoOutputs(this, map, characteristics);
        String sizeIndex = "0";
        if(isFrontCamera){
            sizeIndex = sharedPrefs.getString("front_video_res", "0");
        }
        else{
            sizeIndex = sharedPrefs.getString("back_video_res", "0");
        }

        return videoSizes.get(Integer.parseInt(sizeIndex));
    }

    /**
     * Initiate a still image capture.
     * <p/>
     * This function sends a capture request that initiates a pre-capture sequence in our state
     * machine that waits for auto-focus to finish, ending in a "locked" state where the lens is no
     * longer moving, waits for auto-exposure to choose a good exposure value, and waits for
     * auto-white-balance to converge.
     */
    private void takePicture() {
        synchronized (mCameraStateLock) {
            Log.d("Func", "takePicture");
            mPendingUserCaptures++;

            // If we already triggered a pre-capture sequence, or are in a state where we cannot
            // do this, return immediately.
            if (mState != STATE_PREVIEW) {
                return;
            }

            if(!isInVideoMode) {
                if (!mNoAFRun) {
                    unlockFocus();
                    lockFocus();
                } else {
                    captureStillPicture();
                }

                startTimer();
            }
            else{
                if (!mIsRecordingVideo) {
                    startRecordingVideo();
                } else {
                    stopRecordingVideo();
                }
            }

        }
    }

    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {

            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    try {
                        setupControls(mPreviewRequestBuilder);
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
                        mIsRecordingVideo = true;

                        mMediaRecorder.start();
                        // Start recording
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(Camera2Activity.this, "Failed", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException/* | IOException*/ e) {
            e.printStackTrace();
		} catch (ErrnoException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        mNextVideoAbsolutePath = null;
        createCameraPreviewSession();
        (new EncryptAndSaveVideo(this, lastVideoFilename, lastVideoFilenameEnc)).execute();
    }

    public class EncryptAndSaveVideo extends AsyncTask<Void, Void, Void> {

        private final Context context;
        private final String fileName;
        private final String encFileName;
        private ProgressDialog progressDialog;

        public EncryptAndSaveVideo(Context context, String fileName, String encFileName) {
            super();
            this.context = context;
            this.fileName = fileName;
            this.encFileName = encFileName;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = ProgressDialog.show(Camera2Activity.this, "", getString(R.string.encrypting_video), false, false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                File cacheDir = context.getCacheDir();

                File tmpFile = new File(cacheDir.getAbsolutePath() + "/" + fileName);
                FileInputStream in = new FileInputStream(tmpFile);
                String encFilePath = Helpers.getHomeDir(Camera2Activity.this) + "/" + encFileName;
                FileOutputStream out = new FileOutputStream(encFilePath);

                byte[] fileId = SafeCameraApplication.getCrypto().encryptFile(in, out, lastVideoFilename, Crypto.FILE_TYPE_VIDEO, in.getChannel().size());

                out.close();

                Bitmap thumb = ThumbnailUtils.createVideoThumbnail(tmpFile.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                thumb.compress(Bitmap.CompressFormat.PNG, 0, bos);

                mLastThumbBitmap = Helpers.generateThumbnail(Camera2Activity.this, bos.toByteArray(), encFileName, fileId, Crypto.FILE_TYPE_VIDEO);

                tmpFile.delete();

                //Helpers.getAESCrypt(CameraActivity.this).encrypt(params[0], out);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CryptoException e) {
                e.printStackTrace();
            }
            // new EncryptAndWriteThumb(filename).execute(params[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            progressDialog.dismiss();
            showLastPhotoThumb();
        }

    }

    private void closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    private String mNextVideoAbsolutePath;
    private void setUpMediaRecorder() throws IOException, ErrnoException {
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);

        Log.d("format", String.valueOf(profile.fileFormat));
        Log.d("videoBitRate", String.valueOf(profile.videoBitRate));
        Log.d("audioBitRate", String.valueOf(profile.audioBitRate));
        Log.d("videoFrameRate", String.valueOf(profile.videoFrameRate));
        Log.d("videoCodec", String.valueOf(profile.videoCodec));
        Log.d("audioCodec", String.valueOf(profile.audioCodec));
        Log.d("width", String.valueOf(mVideoSize.width));
        Log.d("height", String.valueOf( mVideoSize.height));
        Log.d("framerate", String.valueOf( mVideoSize.maxFps));

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(profile.fileFormat);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(this);
        }

		mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);

        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
        mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
        mMediaRecorder.setAudioChannels(profile.audioChannels);

        mMediaRecorder.setVideoFrameRate(mVideoSize.maxFps);
        mMediaRecorder.setVideoSize(mVideoSize.width, mVideoSize.height);
        mMediaRecorder.setVideoEncoder(profile.videoCodec);
        mMediaRecorder.setAudioEncoder(profile.audioCodec);


        // Set orientation.
        mSensorOrientation = mCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int deviceRotation = getCurrentDeviceRotation();
        Log.d("sensorOrientation", String.valueOf(mSensorOrientation));
        Log.d("getCurrentDeviceRotation", String.valueOf(deviceRotation));

        int invert = 0;
        if(!isFrontCamera && (deviceRotation == 90 || deviceRotation == 270)){
            invert = 180;
        }

        int videoRotation = (mSensorOrientation + deviceRotation + invert) % 360;
        Log.d("videoRotation", String.valueOf(videoRotation));

        mMediaRecorder.setOrientationHint(videoRotation);


        /*int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }*/
		mMediaRecorder.prepare();

    }


    private String getVideoFilePath(Context context) {
		lastVideoFilename = Helpers.getTimestampedFilename(Helpers.VIDEO_FILE_PREFIX, ".mp4");
        lastVideoFilenameEnc = Helpers.getNewEncFilename();

        File cacheDir = getCacheDir();
        return cacheDir.getAbsolutePath() + "/" + lastVideoFilename;
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        synchronized (mCameraStateLock) {
            Log.d("Func", "lockFocus");
            try {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                mState = STATE_WAITING_LOCK;
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void unlockFocus() {
        synchronized (mCameraStateLock) {
            Log.d("Func", "unLockFocus");

            try {
                //mPreviewRequestBuilder = createPreviewRequestBuilder(CameraDevice.TEMPLATE_PREVIEW);
                if (!mNoAFRun) {

                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
                    mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);

                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                    mState = STATE_PREVIEW;

                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
                }
                else{
                    mState = STATE_PREVIEW;
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            finally {
                mState = STATE_PREVIEW;
            }
        }
    }

    /**
     * Called after a RAW/JPEG capture has completed; resets the AF trigger state for the
     * pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void finishedCapture() {
        try {
            Log.d("Func", "finishedCapture");
            // Reset the auto-focus trigger in case AF didn't run quickly enough.
            if (!mNoAFRun) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

                mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                        mBackgroundHandler);

                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                /*mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                        mBackgroundHandler);*/
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private View.OnClickListener toggleCamera() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                isFrontCamera = !isFrontCamera;
                closeCamera();
                openCamera();
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        };
    }
    private View.OnClickListener toggleCameraMode() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                isInVideoMode = !isInVideoMode;

                if(!isInVideoMode){
                    modeChangerButton.setImageResource(R.drawable.ic_photo);
                }
                else{
                    modeChangerButton.setImageResource(R.drawable.ic_video);
                }
                closeCamera();
                openCamera();
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        };
    }

    /**
     * Retrieve the next {@link Image} from a reference counted {@link ImageReader}, retaining
     * that {@link ImageReader} until that {@link Image} is no longer in use, and set this
     * {@link Image} as the result for the next request in the queue of pending requests.  If
     * all necessary information is available, begin saving the image to a file in a background
     * thread.
     *
     * @param pendingQueue the currently active requests.
     * @param reader       a reference counted wrapper containing an {@link ImageReader} from which
     *                     to acquire an image.
     */
    private void dequeueAndSaveImage(TreeMap<Integer, ImageSaver.ImageSaverBuilder> pendingQueue,
                                     RefCountedAutoCloseable<ImageReader> reader) {
        synchronized (mCameraStateLock) {
            Log.d("Func", "dequeueAndSaveImage");
            Map.Entry<Integer, ImageSaver.ImageSaverBuilder> entry =
                    pendingQueue.firstEntry();
            ImageSaver.ImageSaverBuilder builder = entry.getValue();

            // Increment reference count to prevent ImageReader from being closed while we
            // are saving its Images in a background thread (otherwise their resources may
            // be freed while we are writing to a file).
            if (reader == null || reader.getAndRetain() == null) {
                Log.e(TAG, "Paused the activity before we could save the image," +
                        " ImageReader already closed.");
                pendingQueue.remove(entry.getKey());
                return;
            }

            Image image;
            try {
                image = reader.get().acquireNextImage();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Too many images queued for saving, dropping image for request: " +
                        entry.getKey());
                pendingQueue.remove(entry.getKey());
                return;
            }

            builder.setRefCountedReader(reader).setImage(image);

            handleCompletion(entry.getKey(), builder, pendingQueue);
        }
    }

    /**
     * Runnable that saves an {@link Image} into the specified {@link File}, and updates
     * {@link android.provider.MediaStore} to include the resulting file.
     * <p/>
     * This can be constructed through an {@link ImageSaverBuilder} as the necessary image and
     * result information becomes available.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The image to save.
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;
		private String mFileName;

        /**
         * The CaptureResult for this image capture.
         */
        private final CaptureResult mCaptureResult;

        /**
         * The CameraCharacteristics for this camera device.
         */
        private final CameraCharacteristics mCharacteristics;

        /**
         * The Context to use when updating MediaStore with the saved images.
         */
        private final Context mContext;

		private AsyncTasks.OnAsyncTaskFinish mOnFinish;

        /**
         * A reference counted wrapper for the ImageReader that owns the given image.
         */
        private final RefCountedAutoCloseable<ImageReader> mReader;

        private ImageSaver(Image image, File file, String fileName, CaptureResult result, CameraCharacteristics characteristics, Context context, RefCountedAutoCloseable<ImageReader> reader, AsyncTasks.OnAsyncTaskFinish onFinish) {
            mImage = image;
            mFile = file;
            mFileName = fileName;
            mCaptureResult = result;
            mCharacteristics = characteristics;
            mContext = context;
            mReader = reader;
			mOnFinish = onFinish;
        }

        @Override
        public void run() {
            boolean success = false;
            int format = mImage.getFormat();
            switch (format) {
                case ImageFormat.JPEG: {
                    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile);
                        byte[] fileId = SafeCameraApplication.getCrypto().encryptFile(output, bytes, mFileName, Crypto.FILE_TYPE_PHOTO);
                        mLastThumbBitmap = Helpers.generateThumbnail(mContext, bytes, mFile.getName(), fileId, Crypto.FILE_TYPE_PHOTO);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (CryptoException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }
                /*case ImageFormat.RAW_SENSOR: {
                    DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile);
                        dngCreator.writeImage(output, mImage);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }*/
                default: {
                    Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                    break;
                }
            }

            // Decrement reference count to allow ImageReader to be closed to free up resources.
            mReader.close();

            // If saving the file succeeded, update MediaStore.
            if (success) {
				if (mOnFinish != null) {
					mOnFinish.onFinish();
				}
                MediaScannerConnection.scanFile(mContext, new String[]{mFile.getPath()},
                        /*mimeTypes*/null, new MediaScannerConnection.MediaScannerConnectionClient() {
                            @Override
                            public void onMediaScannerConnected() {
                                // Do nothing
                            }

                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.i(TAG, "Scanned " + path + ":");
                                Log.i(TAG, "-> uri=" + uri);
                            }
                        });
            }
        }

        /**
         * Builder class for constructing {@link ImageSaver}s.
         * <p/>
         * This class is thread safe.
         */
        public static class ImageSaverBuilder {
            private Image mImage;
            private File mFile;
            private String mFileName;
            private CaptureResult mCaptureResult;
            private CameraCharacteristics mCharacteristics;
            private Context mContext;
            private AsyncTasks.OnAsyncTaskFinish mOnFinish;
            private RefCountedAutoCloseable<ImageReader> mReader;

            /**
             * Construct a new ImageSaverBuilder using the given {@link Context}.
             *
             * @param context a {@link Context} to for accessing the
             *                {@link android.provider.MediaStore}.
             */
            public ImageSaverBuilder(final Context context) {
                mContext = context;
            }

            public synchronized ImageSaverBuilder setRefCountedReader(
                    RefCountedAutoCloseable<ImageReader> reader) {
                if (reader == null) throw new NullPointerException();

                mReader = reader;
                return this;
            }

            public synchronized ImageSaverBuilder setImage(final Image image) {
                if (image == null) throw new NullPointerException();
                mImage = image;
                return this;
            }

            public synchronized ImageSaverBuilder setFile(final File file) {
                if (file == null) throw new NullPointerException();
                mFile = file;
                return this;
            }

            public synchronized ImageSaverBuilder setFileName(final String fileName) {
                if (fileName == null) throw new NullPointerException();
                mFileName = fileName;
                return this;
            }

            public synchronized ImageSaverBuilder setResult(final CaptureResult result) {
                if (result == null) throw new NullPointerException();
                mCaptureResult = result;
                return this;
            }
            public synchronized ImageSaverBuilder setOnFinish(final AsyncTasks.OnAsyncTaskFinish onFinish) {
                if (onFinish == null) throw new NullPointerException();
				mOnFinish = onFinish;
                return this;
            }

            public synchronized ImageSaverBuilder setCharacteristics(
                    final CameraCharacteristics characteristics) {
                if (characteristics == null) throw new NullPointerException();
                mCharacteristics = characteristics;
                return this;
            }

            public synchronized ImageSaver buildIfComplete() {
                if (!isComplete()) {
                    return null;
                }
                return new ImageSaver(mImage, mFile, mFileName, mCaptureResult, mCharacteristics, mContext, mReader, mOnFinish);
            }

            public synchronized String getSaveLocation() {
                return (mFile == null) ? "Unknown" : mFile.toString();
            }

            private boolean isComplete() {
                return mImage != null && mFile != null && mCaptureResult != null && mCharacteristics != null;
            }
        }
    }

    // Utility classes and methods:
    // *********************************************************************************************

    /**
     * Comparator based on area of the given {@link Size} objects.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * A dialog fragment for displaying non-recoverable errors; this {@ling Activity} will be
     * finished once the dialog has been acknowledged by the user.
     */
    public static class ErrorDialog extends DialogFragment {

        private String mErrorMessage;

        public ErrorDialog() {
            mErrorMessage = "Unknown error occurred!";
        }

        // Build a dialog with a custom message (Fragments require default constructor).
        public static ErrorDialog buildErrorDialog(String errorMessage) {
            ErrorDialog dialog = new ErrorDialog();
            dialog.mErrorMessage = errorMessage;
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(mErrorMessage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    /**
     * A wrapper for an {@link AutoCloseable} object that implements reference counting to allow
     * for resource management.
     */
    public static class RefCountedAutoCloseable<T extends AutoCloseable> implements AutoCloseable {
        private T mObject;
        private long mRefCount = 0;

        /**
         * Wrap the given object.
         *
         * @param object an object to wrap.
         */
        public RefCountedAutoCloseable(T object) {
            if (object == null) throw new NullPointerException();
            mObject = object;
        }

        /**
         * Increment the reference count and return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T getAndRetain() {
            if (mRefCount < 0) {
                return null;
            }
            mRefCount++;
            return mObject;
        }

        /**
         * Return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T get() {
            return mObject;
        }

        /**
         * Decrement the reference count and release the wrapped object if there are no other
         * users retaining this object.
         */
        @Override
        public synchronized void close() {
            if (mRefCount >= 0) {
                mRefCount--;
                if (mRefCount < 0) {
                    try {
                        mObject.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        mObject = null;
                    }
                }
            }
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Generate a string containing a formatted timestamp with the current date and time.
     *
     * @return a {@link String} representing a time.
     */
    private static String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);
        return sdf.format(new Date());
    }

    /**
     * Cleanup the given {@link OutputStream}.
     *
     * @param outputStream the stream to close.
     */
    private static void closeOutput(OutputStream outputStream) {
        if (null != outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return true if the given array contains the given integer.
     *
     * @param modes array to check.
     * @param mode  integer to get for.
     * @return true if the array contains the given integer, otherwise false.
     */
    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the two given {@link Size}s have the same aspect ratio.
     *
     * @param a first {@link Size} to compare.
     * @param b second {@link Size} to compare.
     * @return true if the sizes have the same aspect ratio, otherwise false.
     */
    private static boolean checkAspectsEqual(Size a, Size b) {
        double aAspect = a.getWidth() / (double) a.getHeight();
        double bAspect = b.getWidth() / (double) b.getHeight();
        return Math.abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE;
    }

    /**
     * Rotation need to transform from the camera sensor orientation to the device's current
     * orientation.
     *
     * @param c                 the {@link CameraCharacteristics} to query for the camera sensor
     *                          orientation.
     * @param deviceOrientation the current device orientation relative to the native device
     *                          orientation.
     * @return the total rotation from the sensor orientation to the current device orientation.
     */
    private static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation - deviceOrientation + 360) % 360;
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show.
     */
    private void showToast(String text) {
        // We show a Toast by sending request message to mMessageHandler. This makes sure that the
        // Toast is shown on the UI thread.
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    /**
     * If the given request has been completed, remove it from the queue of active requests and
     * send an {@link ImageSaver} with the results from this request to a background thread to
     * save a file.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @param requestId the ID of the {@link CaptureRequest} to handle.
     * @param builder   the {@link ImageSaver.ImageSaverBuilder} for this request.
     * @param queue     the queue to remove this request from, if completed.
     */
    private void handleCompletion(int requestId, ImageSaver.ImageSaverBuilder builder,
                                  TreeMap<Integer, ImageSaver.ImageSaverBuilder> queue) {
        if (builder == null) return;
        ImageSaver saver = builder.buildIfComplete();
        if (saver != null) {
            queue.remove(requestId);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(saver);
        }
    }

    /**
     * Check if we are using a device that only supports the LEGACY hardware level.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if this is a legacy device.
     */
    private boolean isLegacy() {
        return mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    /**
     * Start the timer for the pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void startTimer() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    /**
     * Check if the timer for the pre-capture sequence has been hit.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if the timeout occurred.
     */
    private boolean hitTimeout() {
        boolean isHit = (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;
        if(isHit){
            Log.d("timeout", "HIT TIMEOUT");
        }

        return isHit;
    }


    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            synchronized (mCameraStateLock) {
                mPreviewSize = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

	private class ShowLastPhotoThumb extends AsyncTask<Void, Void, File> {

		@SuppressWarnings("unchecked")
		@Override
		protected File doInBackground(Void... params) {

			File dir = new File(Helpers.getHomeDir(Camera2Activity.this));

			File[] folderFiles = dir.listFiles();

			if (folderFiles != null && folderFiles.length > 0) {
				Arrays.sort(folderFiles, new Comparator<File>() {
					public int compare(File f1, File f2) {
						return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
					}
				});

				for (File file : folderFiles) {
					File thumb = new File(Helpers.getThumbsDir(Camera2Activity.this) + "/" + file.getName());
					if (file.getName().endsWith(SafeCameraApplication.FILE_EXTENSION) && thumb.exists() && thumb.isFile()) {
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

			if (lastFile != null) {
				final File lastFileThumb = new File(Helpers.getThumbsDir(Camera2Activity.this) + "/" + lastFile.getName());
				final int thumbSize = (int) Math.round(Helpers.getThumbSize(Camera2Activity.this) / 1.4);

				if (SafeCameraApplication.getKey() != null) {
					AsyncTasks.DecryptPopulateImage task = new AsyncTasks.DecryptPopulateImage(Camera2Activity.this, lastFileThumb.getPath(), galleryButton);
					task.setSize(thumbSize);
					task.setOnFinish(new AsyncTasks.OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							//galleryButton.setLayoutParams(new LinearLayout.LayoutParams(thumbSize, thumbSize));
							//changeRotation(mOrientation);
							findViewById(R.id.lastPhoto).setVisibility(View.VISIBLE);
						}
					});
					task.execute();
				}
				else if(mLastThumbBitmap != null){
					galleryButton.setImageBitmap(mLastThumbBitmap);
					galleryButton.setVisibility(View.VISIBLE);
				}
			} else {
				findViewById(R.id.lastPhoto).setVisibility(View.INVISIBLE);
			}
		}

	}


	private void showLastPhotoThumb() {
		(new Camera2Activity.ShowLastPhotoThumb()).execute();
	}

	private View.OnClickListener openGallery() {
		return new View.OnClickListener() {
			public void onClick(View v) {
				if (lastFile != null && lastFile.isFile()) {
					LoginManager.checkLogin(Camera2Activity.this, new LoginManager.UserLogedinCallback() {
						@Override
						public void onUserLoginSuccess() {
							Log.d("FILE", lastFile.getPath());
							Intent intent = new Intent();
							intent.setClass(Camera2Activity.this, ViewImageActivityOld.class);
							intent.putExtra("EXTRA_IMAGE_PATH", lastFile.getPath());
							intent.putExtra("EXTRA_CURRENT_PATH", Helpers.getHomeDir(Camera2Activity.this));
							startActivityForResult(intent, GalleryActivityOld.REQUEST_VIEW_PHOTO);

						}

						@Override
						public void onUserLoginFail() {

						}
					});
				}
			}
		};
	}

	private ArrayList<Area> getAreas(float x, float y) {
		float [] coords = {x, y};
		calculatePreviewToCameraMatrix();
		mPreviewToCameraMatrix.mapPoints(coords);
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

		ArrayList<Area> areas = new ArrayList<>();
		areas.add(new Area(rect, 1000));
		return areas;
	}

	public Matrix mCameraToPreviewMatrix = new Matrix();
	public Matrix mPreviewToCameraMatrix = new Matrix();

	private void calculateCameraToPreviewMatrix() {
		if( mCaptureSession == null )
			return;
		mCameraToPreviewMatrix.reset();
		// Unfortunately the transformation for Android L API isn't documented, but this seems to work for Nexus 6.
		// This is the equivalent code for android.hardware.Camera.setDisplayOrientation, but we don't actually use setDisplayOrientation()
		// for CameraController2, so instead this is the equivalent code to https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int),
		// except testing on Nexus 6 shows that we shouldn't change "result" for front facing camera.
		boolean mirror = isFrontCamera;
		mCameraToPreviewMatrix.setScale(1, mirror ? -1 : 1);
		int degrees = getDisplayRotationDegrees();
		int result = (mCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) - degrees + 360) % 360;
		mCameraToPreviewMatrix.postRotate(result);
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		mCameraToPreviewMatrix.postScale(mTextureView.getWidth() / 2000f, mTextureView.getHeight() / 2000f);
		mCameraToPreviewMatrix.postTranslate(mTextureView.getWidth() / 2f, mTextureView.getHeight() / 2f);
	}

	private void calculatePreviewToCameraMatrix() {
		if( mCaptureSession == null )
			return;
		calculateCameraToPreviewMatrix();
		if( !mCameraToPreviewMatrix.invert(mPreviewToCameraMatrix) ) {

		}
	}

	public Matrix getCameraToPreviewMatrix() {
		calculateCameraToPreviewMatrix();
		return mCameraToPreviewMatrix;
	}

	private int getDisplayRotationDegrees() {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0: degrees = 0; break;
			case Surface.ROTATION_90: degrees = 90; break;
			case Surface.ROTATION_180: degrees = 180; break;
			case Surface.ROTATION_270: degrees = 270; break;
			default:
				break;
		}
		return degrees;
	}

	public static class Area {
		final Rect rect;
		final int weight;

		public Area(Rect rect, int weight) {
			this.rect = rect;
			this.weight = weight;
		}
	}

	public boolean setMeteringArea(List<Area> areas) {
		Rect sensor_rect = getViewableRect();

		MeteringRectangle[] regions = new MeteringRectangle[areas.size()];
		int i = 0;
		for (Area area : areas) {
			regions[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
		}

		boolean isAny = false;
		if( mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
			isAny = true;
		}
		if( mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
			isAny = true;
		}
		if(isAny){
			try {
				/*mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
				mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);*/

				/*mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
				mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);*/

				/*mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
				mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);*/
				mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			}

			return true;
		}

		return false;
	}

	public boolean clearMeteringArea() {
		Rect sensor_rect = getViewableRect();

		focusPointX = 0;
		focusPointY = 0;
		isSetFocusPoint = false;

		MeteringRectangle[] regions = new MeteringRectangle[1];
		regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);

		boolean isAny = false;
		if( mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {

			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
			isAny = true;
		}
		if( mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
			isAny = true;
		}
		if(isAny){
			try {
				mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
			}
			catch(CameraAccessException e) {
				e.printStackTrace();
			}

			return true;
		}

		return false;
	}

	private Rect getViewableRect() {
		if( mPreviewRequestBuilder != null ) {
			Rect crop_rect = mPreviewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);
			if( crop_rect != null ) {
				return crop_rect;
			}
		}
		Rect sensor_rect = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		sensor_rect.right -= sensor_rect.left;
		sensor_rect.left = 0;
		sensor_rect.bottom -= sensor_rect.top;
		sensor_rect.top = 0;
		return sensor_rect;
	}

	private MeteringRectangle convertAreaToMeteringRectangle(Rect sensor_rect, Area area) {
		Rect camera2_rect = convertRectToCamera2(sensor_rect, area.rect);
		return new MeteringRectangle(camera2_rect, area.weight);
	}

	private Rect convertRectFromCamera2(Rect crop_rect, Rect camera2_rect) {
		// inverse of convertRectToCamera2()
		double left_f = (camera2_rect.left-crop_rect.left)/(double)(crop_rect.width()-1);
		double top_f = (camera2_rect.top-crop_rect.top)/(double)(crop_rect.height()-1);
		double right_f = (camera2_rect.right-crop_rect.left)/(double)(crop_rect.width()-1);
		double bottom_f = (camera2_rect.bottom-crop_rect.top)/(double)(crop_rect.height()-1);
		int left = (int)(left_f * 2000) - 1000;
		int right = (int)(right_f * 2000) - 1000;
		int top = (int)(top_f * 2000) - 1000;
		int bottom = (int)(bottom_f * 2000) - 1000;

		left = Math.max(left, -1000);
		right = Math.max(right, -1000);
		top = Math.max(top, -1000);
		bottom = Math.max(bottom, -1000);
		left = Math.min(left, 1000);
		right = Math.min(right, 1000);
		top = Math.min(top, 1000);
		bottom = Math.min(bottom, 1000);

		return new Rect(left, top, right, bottom);
	}

	private Rect convertRectToCamera2(Rect crop_rect, Rect rect) {
		// CameraController.Area is always [-1000, -1000] to [1000, 1000] for the viewable region
		// but for CameraController2, we must convert to be relative to the crop region
		double left_f = (rect.left+1000)/2000.0;
		double top_f = (rect.top+1000)/2000.0;
		double right_f = (rect.right+1000)/2000.0;
		double bottom_f = (rect.bottom+1000)/2000.0;
		int left = (int)(crop_rect.left + left_f * (crop_rect.width()-1));
		int right = (int)(crop_rect.left + right_f * (crop_rect.width()-1));
		int top = (int)(crop_rect.top + top_f * (crop_rect.height()-1));
		int bottom = (int)(crop_rect.top + bottom_f * (crop_rect.height()-1));
		left = Math.max(left, crop_rect.left);
		right = Math.max(right, crop_rect.left);
		top = Math.max(top, crop_rect.top);
		bottom = Math.max(bottom, crop_rect.top);
		left = Math.min(left, crop_rect.right);
		right = Math.min(right, crop_rect.right);
		top = Math.min(top, crop_rect.bottom);
		bottom = Math.min(bottom, crop_rect.bottom);

		return new Rect(left, top, right, bottom);
	}

	private float scaleZoomFactor = 1;
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			if( mCameraDevice != null && mIsZoomSupported ) {
				scaleZoomFactor = detector.getScaleFactor();
				if (scaleZoomFactor >= 1.025f || scaleZoomFactor <= 0.9756f) {
					scaleZoom(scaleZoomFactor);
					scaleZoomFactor = 1;
				}
			}
			return true;
		}
	}


	public void scaleZoom(float scale_factor) {
		if( mCaptureSession != null && mIsZoomSupported ) {
			int zoom_factor = currentZoomValue;
			float zoom_ratio = mZoomRatios.get(zoom_factor)/100.0f;
			zoom_ratio *= scale_factor;

			int new_zoom_factor = zoom_factor;
			int max_zoom_factor = (int)mMaxZoom;
			if( zoom_ratio <= 1.0f ) {
				new_zoom_factor = 0;
			}
			else if( zoom_ratio >= mZoomRatios.get(max_zoom_factor)/100.0f ) {
				new_zoom_factor = max_zoom_factor;
			}
			else {
				// find the closest zoom level
				if( scale_factor > 1.0f ) {
					// zooming in
					for(int i=zoom_factor;i<mZoomRatios.size();i++) {
						if( mZoomRatios.get(i)/100.0f >= zoom_ratio ) {
							new_zoom_factor = i;
							break;
						}
					}
				}
				else {
					// zooming out
					for(int i=zoom_factor;i>=0;i--) {
						if( mZoomRatios.get(i)/100.0f <= zoom_ratio ) {
							new_zoom_factor = i;
							break;
						}
					}
				}
			}

			/*final int zoomFactor = new_zoom_factor;
			mBackgroundHandler.postAtFrontOfQueue(new Runnable() {
				@Override
				public void run() {
					zoomTo(zoomFactor);
				}
			});*/
			zoomTo(new_zoom_factor);

			// n.b., don't call zoomTo; this should be called indirectly by applicationInterface.multitouchZoom()
			//applicationInterface.multitouchZoom(new_zoom_factor);
		}
	}

	public void zoomTo(int new_zoom_factor) {
		if( new_zoom_factor < 0 ) {
			new_zoom_factor = 0;
		}
		else if( new_zoom_factor > mMaxZoom ) {
			new_zoom_factor = (int) mMaxZoom;
		}
		// problem where we crashed due to calling this function with null camera should be fixed now, but check again just to be safe
		if( mCaptureSession != null ) {
			if( mIsZoomSupported ) {
				// don't cancelAutoFocus() here, otherwise we get sluggish zoom behaviour on Camera2 API
				setCameraZoom(new_zoom_factor, mPreviewRequestBuilder, true);
//				Prefs.setZoomPref(new_zoom_factor);

				// Reset focus points
				clearMeteringArea();
				isPassiveFocused = false;
				focusMovedTime = 0;
			}
		}
	}

	public void setCameraZoom(int value, CaptureRequest.Builder requestBuilder, boolean setRepeatingRequest) {
		if( mZoomRatios == null || mCaptureSession == null ) {
			return;
		}
		if( value < 0 || value > mZoomRatios.size() ) {
			throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
		}
		float zoom = mZoomRatios.get(value)/100.0f;
		Rect sensor_rect = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
		int left = sensor_rect.width()/2;
		int right = left;
		int top = sensor_rect.height()/2;
		int bottom = top;
		int hwidth = (int)(sensor_rect.width() / (2.0*zoom));
		int hheight = (int)(sensor_rect.height() / (2.0*zoom));
		left -= hwidth;
		right += hwidth;
		top -= hheight;
		bottom += hheight;

		Rect scalar_crop_region = new Rect(left, top, right, bottom);
		requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, scalar_crop_region);
		currentZoomValue = value;
		if(setRepeatingRequest) {
			try {
				mCaptureSession.setRepeatingRequest(requestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
			} catch (CameraAccessException e) {
				e.printStackTrace();
			}
		}
	}

}
