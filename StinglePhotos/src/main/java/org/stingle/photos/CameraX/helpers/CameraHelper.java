package org.stingle.photos.CameraX.helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.util.Range;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.SeekBar;

import androidx.camera.core.Camera;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.view.PreviewView;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class CameraHelper {

    private final WeakReference<Context> contextRef;

    private Camera camera;
    private ScaleGestureDetector scaleGestureDetector;
    private int exposureIndex = 0;

    public CameraHelper(Context context) {
        this.contextRef = new WeakReference<>(context);
        initGestureListener();
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void zoomAndFocus(PreviewView previewView) {
        autoFocus();
        zoomAndTouchToFocus(previewView);
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
    private void zoomAndTouchToFocus(PreviewView previewView) {
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

}
