package org.stingle.photos.CameraX.helpers;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.camera.core.Camera;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.view.PreviewView;

import org.stingle.photos.CameraX.models.CameraImageSize;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class CameraHelper {

    private final WeakReference<Context> contextRef;

    private Camera camera;
    private ScaleGestureDetector scaleGestureDetector;
    private int exposureIndex = 0;
    private Bitmap overlay;
    private final Handler handler;

    public CameraHelper(Context context) {
        this.contextRef = new WeakReference<>(context);
        initGestureListener();
        handler = new Handler(Looper.getMainLooper());
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void zoomAndFocus(PreviewView previewView,
                             ImageView focusRect,
                             CameraImageSize cameraImageSize) {
        autoFocus();
        zoomAndTouchToFocus(previewView, focusRect, cameraImageSize);
    }

    public void exposure(SeekBar exposureSeekBar) {
        if (camera.getCameraInfo().getExposureState().isExposureCompensationSupported()) {
            exposureIndex = camera.getCameraInfo().getExposureState().getExposureCompensationIndex() + 24;
            exposureSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
            exposureSeekBar.setMax(48);
            exposureSeekBar.setProgress(exposureIndex);
            exposureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    exposureIndex = seekBar.getProgress() - 24;
                    exposure();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    private void exposure() {
        Range<Integer> range = camera.getCameraInfo().getExposureState().getExposureCompensationRange();
        if (range.contains(exposureIndex)) {
            camera.getCameraControl().setExposureCompensationIndex(exposureIndex);
        }
    }

    private void autoFocus() {
        MeteringPoint autoFocusPoint = new SurfaceOrientedMeteringPointFactory(1f, 1f)
                .createPoint(.5f, .5f);
        try {
            FocusMeteringAction autoFocusAction = new FocusMeteringAction.Builder(
                    autoFocusPoint,
                    FocusMeteringAction.FLAG_AF
            ).setAutoCancelDuration(2, TimeUnit.SECONDS).build();
            if (camera != null) {
                camera.getCameraControl().startFocusAndMetering(autoFocusAction);
            }
        } catch (Exception e) {
            Log.d("ERROR", "cannot access camera", e);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void zoomAndTouchToFocus(PreviewView previewView,
                                     ImageView focusRect,
                                     CameraImageSize cameraImageSize) {
        previewView.setOnTouchListener((view, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_UP: {
                    try {
                        if (camera != null) {
                            MeteringPoint meteringPoint = new DisplayOrientedMeteringPointFactory(previewView.getDisplay(),
                                    camera.getCameraInfo(), previewView.getWidth(), previewView.getHeight())
                                    .createPoint(event.getX(), event.getY());
                            // drawing rect
                            drawRectangle(event.getX(), event.getY(), focusRect, cameraImageSize);

                            // Prepare focus action to be triggered.
                            FocusMeteringAction
                                    action = new FocusMeteringAction.Builder(meteringPoint).build();
                            // Execute focus action
                            camera.getCameraControl().startFocusAndMetering(action);
                        }
                    } catch (Exception e) {
                        Log.d("ERROR", "cannot access camera", e);
                    }
                    return true;
                }
            }
            return false;
        });
    }

    private void initGestureListener() {
        scaleGestureDetector = new ScaleGestureDetector(contextRef.get(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        try {
                            if (camera != null) {
                                float currentZoomRatio =
                                        camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                                float delta = detector.getScaleFactor();
                                camera.getCameraControl().setZoomRatio(currentZoomRatio * delta);
                                return true;
                            }
                            return false;
                        } catch (Exception e) {
                            if (camera != null) {
                                camera.getCameraControl().setZoomRatio(1F);
                            }
                            return false;
                        }
                    }
                });
    }

    private void drawRectangle(float tapX, float tapY, ImageView imageView, CameraImageSize cameraImageSize) {
        try {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((Activity) contextRef.get()).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int height = displayMetrics.heightPixels;
            int width = displayMetrics.widthPixels;
            int previewHeight = width * cameraImageSize.getRevertedSize().getHeight()
                    / cameraImageSize.getRevertedSize().getWidth();

            int y = height / 2 - previewHeight / 2;
            if (tapY > y + 100 && tapY < height - y) {
                overlay = Bitmap.createBitmap(imageView.getWidth(),
                        imageView.getHeight(),
                        Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(overlay);
                Paint paint = new Paint(ANTI_ALIAS_FLAG);
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(5);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(tapX - 100, tapY - 100, tapX + 100, tapY + 100, paint);

                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> imageView.setVisibility(View.GONE), 3000);
                ((Activity) contextRef.get()).runOnUiThread(() -> {
                    try {
                        imageView.setVisibility(View.VISIBLE);
                        imageView.setImageBitmap(overlay);
                    } catch (Exception e) {
                        // ignoring
                    }
                });
            }
        } catch (Exception e) {
            // ignoring rect drawing
        }
    }
}
