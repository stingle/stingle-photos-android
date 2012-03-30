package com.fenritz.safecamera;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.fenritz.safecamera.util.CameraPreview;
import com.fenritz.safecamera.util.Helpers;

public class CameraActivity extends Activity {
	
	public static final String FLASH_MODE = "flash_mode";
	
    private CameraPreview mPreview;
    Camera mCamera;
    int numberOfCameras;
    int cameraCurrentlyLocked;
    FrameLayout.LayoutParams origParams;

    // The first rear facing camera
    int defaultCameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Create a RelativeLayout container that will hold a SurfaceView,
        // and set it as the content of our activity.
        
		setContentView(R.layout.camera);

		((ImageButton) findViewById(R.id.take_photo)).setOnClickListener(takePhoto());
		((ImageButton) findViewById(R.id.decrypt)).setOnClickListener(openGallery());
		((ImageButton) findViewById(R.id.flashButton)).setOnClickListener(toggleFlash());
		
		mPreview = ((CameraPreview)findViewById(R.id.camera_preview));
    }
    
    private OnClickListener openGallery() {
		return new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(CameraActivity.this, GalleryActivity.class);
				startActivity(intent);
			}
		};
	}
    
    private OnClickListener toggleFlash() {
		return new OnClickListener() {
			public void onClick(View v) {
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				String flashMode = preferences.getString(CameraActivity.FLASH_MODE, Parameters.FLASH_MODE_OFF);
				
				if(flashMode.equals(Parameters.FLASH_MODE_OFF)){
					flashMode = Parameters.FLASH_MODE_ON;
					((ImageButton) findViewById(R.id.flashButton)).setImageResource(R.drawable.flash_on);
				}
				else if(flashMode.equals(Parameters.FLASH_MODE_ON)){
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
					/*FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)mPreview.getLayoutParams();
					origParams = params;
					params.width = 1024;
					params.height = 768;
					
					mPreview.setLayoutParams(params);*/
				}
			}
		};
	}
	
	class ResumePreview extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if (mCamera != null) {
				//mPreview.setLayoutParams(origParams);
				mCamera.startPreview();
			}
		}
	}
	
	private PictureCallback getPictureCallback() {

		return new PictureCallback() {

			public void onPictureTaken(byte[] data, Camera camera) {
				String filename = Helpers.getFilename(CameraActivity.this, Helpers.JPEG_FILE_PREFIX);
				
				new EncryptAndWriteFile(filename).execute(data);
				new EncryptAndWriteThumb(CameraActivity.this, filename).execute(data);
				
				Handler myHandler = new ResumePreview();
				myHandler.sendMessageDelayed(myHandler.obtainMessage(), 500);
			}
		};
	}
    
    @Override
    protected void onResume() {
        super.onResume();

        // Open the default i.e. the first rear facing camera.
        mCamera = Camera.open();
        cameraCurrentlyLocked = defaultCameraId;
        mPreview.setCamera(mCamera);
        
        // Set flash mode from preferences and update button accordingly
        SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
		String flashMode = preferences.getString(CameraActivity.FLASH_MODE, Parameters.FLASH_MODE_OFF);
		
		if(flashMode.equals(Parameters.FLASH_MODE_OFF)){
			((ImageButton) findViewById(R.id.flashButton)).setImageResource(R.drawable.flash_off);
		}
		else if(flashMode.equals(Parameters.FLASH_MODE_ON)){
			((ImageButton) findViewById(R.id.flashButton)).setImageResource(R.drawable.flash_on);
		}

		Camera.Parameters parameters = mCamera.getParameters();
    	parameters.setFlashMode(flashMode);
    	mCamera.setParameters(parameters);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (mCamera != null) {
            mPreview.setCamera(null);
            mCamera.release();
            mCamera = null;
        }
    }
    
    
    public class EncryptAndWriteFile extends AsyncTask<byte[], Void, Void> {
		
		private final String filename;
		private ProgressDialog progressDialog;
		
		public EncryptAndWriteFile(){
			this(null);
		}

		public EncryptAndWriteFile(String pFilename){
			super();
			if(pFilename == null){
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
				Helpers.getAESCrypt().encrypt(params[0], out);
			} 
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			//new EncryptAndWriteThumb(filename).execute(params[0]);
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
		
		public EncryptAndWriteThumb(Context pContext){
			this(pContext, null);
		}
		
		public EncryptAndWriteThumb(Context pContext, String pFilename){
			super();
			context = pContext;
			if(pFilename == null){
				pFilename = Helpers.getFilename(context, Helpers.JPEG_FILE_PREFIX);
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
	}

}
