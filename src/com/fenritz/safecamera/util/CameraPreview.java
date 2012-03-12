package com.fenritz.safecamera.util;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CameraPreview";
	private SurfaceHolder mHolder;
    private Camera mCamera = null;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void release(){
    	mCamera = null;
    	mHolder = null;
    }
    
    public void surfaceCreated(SurfaceHolder holder) {
    	if(mCamera != null){
	        try {
	            mCamera.setPreviewDisplay(holder);
	            mCamera.startPreview();
	        } catch (IOException e) {
	            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
	            e.printStackTrace();
	        }
    	}
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    	if(mCamera != null){
    		mCamera.stopPreview();
    	}
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mHolder == null || mHolder.getSurface() == null){
          return;
        }

        try {
            mCamera.stopPreview();
        } catch (Exception e){ }


        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }
}
