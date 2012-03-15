package com.fenritz.safecamera;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.fenritz.safecamera.util.CameraPreview;
import com.fenritz.safecamera.util.Helpers;

public class CameraActivity extends Activity{
	
	Camera mCamera;
	CameraPreview mPreview;
	
	public final static int REQUEST_FROM_CAMERA = 0;
	protected static final int CAM_STATE_PREVIEW = 0;
	protected static final int CAM_STATE_FROZEN = 1;
	private int mPreviewState = CAM_STATE_PREVIEW;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
	    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		setContentView(R.layout.camera);

		((ImageButton) findViewById(R.id.take_photo)).setOnClickListener(takePhoto());
		((ImageButton) findViewById(R.id.decrypt)).setOnClickListener(openGallery());
	}
	
	class ResumePreview extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if (mCamera != null) {
				mCamera.startPreview();
				mPreviewState = CAM_STATE_PREVIEW;
			}
		}
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
	
	private OnClickListener takePhoto() {
		return new OnClickListener() {
			public void onClick(View v) {
				if (mCamera != null && mPreviewState == CAM_STATE_PREVIEW) {

					mCamera.takePicture(null, null, getPictureCallback());
					mPreviewState = CAM_STATE_FROZEN;
				}
			}
		};
	}
	
	public Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		}
		catch (Exception e) {
			Log.e("camera", "Error openning camera!");
			e.printStackTrace();
		}
		return c; // returns null if camera is unavailable
	}

	public void getCameraAndPreview() {
		// releaseCameraAndPreview();
		if (mCamera == null) {
			mCamera = getCameraInstance();
			if (mCamera != null) {
				// Create our Preview view and set it as the content of our
				// activity.
				mPreview = new CameraPreview(this, mCamera);
				FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
				preview.addView(mPreview);

			}
		}
	}

	public void releaseCameraAndPreview() {
		if (mPreview != null) {
			mPreview.release();
		}
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			mPreview = null;
			((FrameLayout) findViewById(R.id.camera_preview)).removeViewAt(0);
		}
	}

	private PictureCallback getPictureCallback() {

		return new PictureCallback() {

			public void onPictureTaken(byte[] data, Camera camera) {
				String filename = Helpers.getFilename(CameraActivity.this, Helpers.JPEG_FILE_PREFIX);
				
				new EncryptAndWriteFile(filename).execute(data);
				//new EncryptAndWriteThumb(filename).execute(data);
				
				Handler myHandler = new ResumePreview();
				myHandler.sendMessageDelayed(myHandler.obtainMessage(), 500);
			}
		};
	}

	@Override
	protected void onResume() {
		getCameraAndPreview();
		super.onResume();
	}

	@Override
	protected void onPause() {
		releaseCameraAndPreview();
		super.onPause();
	}
	
	private class EncryptAndWriteFile extends AsyncTask<byte[], Void, Void> {
		
		private final String filename;
		
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
		protected Void doInBackground(byte[]... params) {
			try {
				FileOutputStream out = new FileOutputStream(Helpers.getHomeDir(CameraActivity.this) + "/" + filename);
				SafeCameraActivity.crypto.encrypt(params[0], out);
			} 
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			new EncryptAndWriteThumb(filename).execute(params[0]);
			return null;
		}
		
	}
	
	private class EncryptAndWriteThumb extends AsyncTask<byte[], Void, Void> {
		
		private final String filename;
		
		public EncryptAndWriteThumb(){
			this(null);
		}
		
		public EncryptAndWriteThumb(String pFilename){
			super();
			if(pFilename == null){
				pFilename = Helpers.getFilename(CameraActivity.this, Helpers.JPEG_FILE_PREFIX);
			}
			
			filename = pFilename;
		}
		
		@Override
		protected Void doInBackground(byte[]... params) {
			try {
				BitmapFactory.Options bitmapOptions=new BitmapFactory.Options();
				bitmapOptions.inSampleSize = 4;
				Bitmap bitmap = getThumbFromBitmap(BitmapFactory.decodeByteArray(params[0], 0, params[0].length, bitmapOptions), Integer.valueOf(getString(R.string.thumb_size)));
				
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
				byte[] imageByteArray = stream.toByteArray();
				
				FileOutputStream out = new FileOutputStream(Helpers.getThumbsDir(CameraActivity.this) + "/" + filename);
				SafeCameraActivity.crypto.encrypt(imageByteArray, out);
			} 
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
	
	private Bitmap getThumbFromBitmap(Bitmap bitmap, int squareSide){
	    int imgWidth = bitmap.getWidth();
	    int imgHeight = bitmap.getHeight();
	    
	    int cropX, cropY, cropWidth, cropHeight;
	    if(imgWidth >= imgHeight){
	    	cropX = imgWidth/2 - imgHeight/2;
	    	cropY = 0;
	    	cropWidth = imgHeight;
	    	cropHeight = imgHeight;
	    }
	    else{
	    	cropX = 0;
	    	cropY = imgHeight/2 - imgWidth/2;
	    	cropWidth = imgWidth;
	    	cropHeight = imgWidth;
	    }
	    
	    Bitmap cropedImg = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight);
	    
	    return Bitmap.createScaledBitmap(cropedImg, squareSide, squareSide, true);
	}
	
}
