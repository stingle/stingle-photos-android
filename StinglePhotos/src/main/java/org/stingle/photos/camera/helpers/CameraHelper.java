package org.stingle.photos.camera.helpers;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.camera.core.Camera;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.view.PreviewView;

import java.util.concurrent.TimeUnit;

public class CameraHelper {

    @SuppressLint("ClickableViewAccessibility")
    public static void zoomAndFocus(PreviewView previewView, Camera camera) {
        previewView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
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

        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(previewView.getContext(),
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
                });
        previewView.getViewTreeObserver()
                .addOnGlobalLayoutListener(() -> previewView.setOnTouchListener((view, event) -> {
                    scaleGestureDetector.onTouchEvent(event);
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            return true;
                        case MotionEvent.ACTION_UP: {
                            MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(
                                    previewView.getWidth(), previewView.getHeight()
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
                }));
    }


}
