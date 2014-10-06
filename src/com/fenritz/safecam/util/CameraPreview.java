package com.fenritz.safecam.util;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.fenritz.safecam.CameraActivity;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
 * to the surface. We need to center the SurfaceView because not all devices have cameras that
 * support preview sizes at the same aspect ratio as the device's display.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private final String TAG = "Preview";

    SurfaceHolder mHolder;
    Size mPreviewSize;
    List<Size> mSupportedPreviewSizes;
    Camera mCamera;
    DisplayMetrics screenSize;
    
    public CameraPreview(Context context, AttributeSet attrs) {
    	super(context, attrs);
    	init(context);
    }
    
    public CameraPreview(Context context) {
        super(context);
        init(context);
    }
    
    protected void init(Context context){

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        screenSize = context.getResources().getDisplayMetrics();
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
            
            /*for (Size size : mSupportedPreviewSizes) {
            	Log.d("qaqPreviewSizes", String.valueOf(size.width) + " - " + String.valueOf(size.height));
            }
            for (Size size : mSupportedPictureSizes) {
            	Log.d("qaqPictureSizes", String.valueOf(size.width) + " - " + String.valueOf(size.height));
            }*/
        }
    }

    public void switchCamera(Camera camera) {
       setCamera(camera);
       try {
           camera.setPreviewDisplay(mHolder);
       } catch (IOException exception) {
           Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
       }
       Camera.Parameters parameters = camera.getParameters();
       parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
       requestLayout();

       camera.setParameters(parameters);
    }

    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.

        if (mSupportedPreviewSizes != null && mCamera != null) {
        	Camera.Parameters parameters = mCamera.getParameters();
        	Camera.Size pictureSize = parameters.getPictureSize();
        	
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, screenSize.widthPixels, screenSize.heightPixels, (double)pictureSize.width / pictureSize.height);
            
            setMeasuredDimension(mPreviewSize.width * screenSize.heightPixels / mPreviewSize.height, screenSize.heightPixels);
            
            
            //Log.d("qaqChoosedPreview", String.valueOf(mPreviewSize.width) + " - " + String.valueOf(mPreviewSize.height));
            //Log.d("qaqScreenSize", String.valueOf(screenSize.widthPixels) + " - " + String.valueOf(screenSize.heightPixels));
            //Log.d("qaqPictureSize", String.valueOf(pictureSize.width) + " - " + String.valueOf(pictureSize.height));
            //Log.d("qaqObjectSize", String.valueOf(mPreviewSize.width * screenSize.heightPixels / mPreviewSize.height) + " - " + String.valueOf(screenSize.heightPixels));
         }
        else{
        	final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
            
            setMeasuredDimension(width, height);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed) {

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
            }

            // Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                layout((width - scaledChildWidth) / 2, 0,
                        (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                layout(0, (height - scaledChildHeight) / 2,
                        width, (height + scaledChildHeight) / 2);
            }
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
    	((CameraActivity)this.getContext()).handleOnDraw(canvas);
    	super.onDraw(canvas);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
        setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }


    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h, double targetRatio) {
        final double ASPECT_TOLERANCE = 0.1;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE){
            	continue;
            }
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        
        return optimalSize;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
    	if(mCamera != null){
    		Camera.Parameters parameters = mCamera.getParameters();
    		parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        
    		//requestLayout();

    		mCamera.stopPreview();
    		mCamera.setParameters(parameters);
    		mCamera.startPreview();
    	}
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //scaleGestureDetector.onTouchEvent(event);
		CameraActivity cam_activity = (CameraActivity)this.getContext();
		return cam_activity.handleTouchEvent(event);
    }
}
