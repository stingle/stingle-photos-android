package com.fenritz.safecamera;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import javax.crypto.Cipher;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.fenritz.safecamera.util.AESCrypt;
import com.fenritz.safecamera.util.AESCryptException;
import com.fenritz.safecamera.util.CameraPreview;
import com.fenritz.safecamera.util.Helpers;

public class SafeCameraActivity extends Activity {
	/** Called when the activity is first created. */
	AESCrypt crypto;
	Camera mCamera;
	CameraPreview mPreview;
	
	public final static String password = "";

	public final static int REQUEST_FROM_CAMERA = 0;
	private static final String JPEG_FILE_PREFIX = "IMG_";
	protected static final int CAM_STATE_PREVIEW = 0;
	protected static final int CAM_STATE_FROZEN = 1;
	private int mPreviewState = CAM_STATE_PREVIEW;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.take_photo);

		// ((Button)findViewById(R.id.encrypt)).setOnClickListener(encrypt());
		// ((Button)findViewById(R.id.decrypt)).setOnClickListener(decrypt());
		((Button) findViewById(R.id.take_photo)).setOnClickListener(takePhoto());
		((Button) findViewById(R.id.decrypt)).setOnClickListener(decrypt());

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

	private void setupAES() {
		try {
			crypto = new AESCrypt(((EditText) findViewById(R.id.pass)).getText().toString());
		}
		catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (AESCryptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
				new EncryptData().execute(data);
				
				Handler myHandler = new ResumePreview();
				myHandler.sendMessageDelayed(myHandler.obtainMessage(), 500);
			}
		};
	}

	private String getFilename() {
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = JPEG_FILE_PREFIX + timeStamp;

		return imageFileName;
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

	private OnClickListener encrypt() {
		return new OnClickListener() {
			public void onClick(View v) {
				try {
					setupAES();
					FileInputStream in = new FileInputStream(Helpers.getMainDir() + "2.jpg");
					FileOutputStream out = new FileOutputStream(Helpers.getMainDir() + "1.jpg.sc");
					crypto.encrypt(in, out);
				}
				catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
	}

	private OnClickListener decrypt() {
		return new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(SafeCameraActivity.this, Gallery.class);
				startActivity(intent);
				/*try {
					setupAES();
					crypto.decrypt(new FileInputStream(Helpers.getMainDir() + "IMG_20120307_010025.jpg.sc"),
							new FileOutputStream(Helpers.getMainDir() + "IMG_20120307_010025.jpg"));
				}
				catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
			}
		};
	}
	
	private class EncryptData extends AsyncTask<byte[], Void, Void> {
		
		@Override
		protected Void doInBackground(byte[]... params) {
			setupAES();

			try {
				FileOutputStream out = new FileOutputStream(Helpers.getMainDir() + getFilename() + ".jpg.sc");
				crypto.encrypt(params[0], out);
			} 
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			return null;
		}
		
	}

	public static void printMaxKeySizes() {
		try {
			Set<String> algorithms = Security.getAlgorithms("Cipher");
			for (String algorithm : algorithms) {
				int max = Cipher.getMaxAllowedKeyLength(algorithm);
				Log.d("keys", String.format("%s: %dbit", algorithm, max));
			}
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
}