package org.stingle.photos.camera.helpers;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.RelativeLayout;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.databinding.CameraUiContainerBinding;

import java.lang.ref.WeakReference;

public class OrientationHelper implements LifecycleObserver {

    private static final int ORIENTATION_PORTRAIT_NORMAL = 1;
    private static final int ORIENTATION_PORTRAIT_INVERTED = 2;
    private static final int ORIENTATION_LANDSCAPE_NORMAL = 3;
    private static final int ORIENTATION_LANDSCAPE_INVERTED = 4;

    private WeakReference<CameraUiContainerBinding> cameraUiContainerBindingRef;
    private final WeakReference<Context> contextRef;

    private OrientationEventListener orientationEventListener;
    private int orientation = -1;
    private int deviceRotation = 0;
    private int overallRotation = 0;

    public OrientationHelper(Context context) {
        contextRef = new WeakReference<>(context);
    }

    public void setUI(CameraUiContainerBinding binding) {
        cameraUiContainerBindingRef = new WeakReference<>(binding);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private void onResume() {
        initOrientationListener();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private void onPause() {
        destroyOrientationListener();
    }

    private void initOrientationListener() {
        if (orientationEventListener == null) {
            orientationEventListener = new OrientationEventListener(contextRef.get(),
                    SensorManager.SENSOR_DELAY_NORMAL) {

                @Override
                public void onOrientationChanged(int orientation) {
                    if (orientation == ORIENTATION_UNKNOWN) {
                        return;
                    }

                    orientation = (orientation + 45) / 90 * 90;

                    // determine our orientation based on sensor response
                    int lastOrientation = OrientationHelper.this.orientation;

                    if (orientation >= 315 || orientation < 45) {
                        if (OrientationHelper.this.orientation != ORIENTATION_PORTRAIT_NORMAL) {
                            OrientationHelper.this.orientation = ORIENTATION_PORTRAIT_NORMAL;
                        }
                    } else if (orientation >= 225) {
                        if (OrientationHelper.this.orientation != ORIENTATION_LANDSCAPE_NORMAL) {
                            OrientationHelper.this.orientation = ORIENTATION_LANDSCAPE_NORMAL;
                        }
                    } else if (orientation >= 135) {
                        if (OrientationHelper.this.orientation != ORIENTATION_PORTRAIT_INVERTED) {
                            OrientationHelper.this.orientation = ORIENTATION_PORTRAIT_INVERTED;
                        }
                    } else { // orientation <135 && orientation > 45
                        if (OrientationHelper.this.orientation != ORIENTATION_LANDSCAPE_INVERTED) {
                            OrientationHelper.this.orientation = ORIENTATION_LANDSCAPE_INVERTED;
                        }
                    }

                    if (lastOrientation != OrientationHelper.this.orientation) {
                        rotateUIElements();
                    }
                }
            };
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    private void destroyOrientationListener() {
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }

    // Rotate UI
    private void rotateUIElements() {
        if (cameraUiContainerBindingRef == null) {
            return;
        }
        int oldDeviceRotation = deviceRotation;
        int oldOverallRotation = overallRotation;
        deviceRotation = getCurrentDeviceRotation();

        overallRotation += getHowMuchRotated(oldDeviceRotation, getCurrentDeviceRotation());

        //oldOverallRotation -= 90;
        int overallRotation1 = overallRotation;

        rotateElement(cameraUiContainerBindingRef.get().photoViewButton, oldOverallRotation, overallRotation1);
        rotateElement(cameraUiContainerBindingRef.get().flashButton, oldOverallRotation, overallRotation1);
        rotateElement(cameraUiContainerBindingRef.get().cameraSwitchButton, oldOverallRotation, overallRotation1);
        rotateElement(cameraUiContainerBindingRef.get().cameraModeChanger, oldOverallRotation, overallRotation1);
        rotateElement(cameraUiContainerBindingRef.get().audioCheckBoxContainer, oldOverallRotation, overallRotation1);
        rotateElement(cameraUiContainerBindingRef.get().exposureButton, oldOverallRotation, overallRotation1);
        rotateElement(cameraUiContainerBindingRef.get().time, oldOverallRotation, overallRotation1);
        rotateElement(cameraUiContainerBindingRef.get().repeat, oldOverallRotation, overallRotation1);
        repositionChronoBar();
        repositionSeekBar();
    }

    private void rotateElement(View view, int start, int end) {
        RotateAnimation rotateAnimation = new RotateAnimation(start, end, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(500);
        rotateAnimation.setFillAfter(true);
        rotateAnimation.setFillEnabled(true);

        view.startAnimation(rotateAnimation);
    }

    private int getCurrentDeviceRotation() {
        switch (orientation) {
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

    private void repositionChronoBar() {
        if (cameraUiContainerBindingRef == null) {
            return;
        }
        int rotation = getCurrentDeviceRotation();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                cameraUiContainerBindingRef.get().chronoBar.getLayoutParams());

        params.addRule(RelativeLayout.BELOW, 0);
        params.addRule(RelativeLayout.ABOVE, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_START, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_END, 0);

        int marginPx = Helpers.convertDpToPixels(contextRef.get(), 10);

        params.topMargin = marginPx;
        params.rightMargin = marginPx;
        params.bottomMargin = marginPx;
        params.leftMargin = marginPx;

        switch (rotation) {
            case 0:
                params.addRule(RelativeLayout.BELOW, R.id.topPanel);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                break;
            case 90:
                params.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                break;
            case 180:
                params.addRule(RelativeLayout.ABOVE, R.id.seek_bar_container);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                break;
            case 270:
                params.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                break;
        }
        cameraUiContainerBindingRef.get().chronoBar.setLayoutParams(params);
        cameraUiContainerBindingRef.get().chronoBar.setRotation(rotation);
    }

    private void repositionSeekBar() {
        if (cameraUiContainerBindingRef == null) {
            return;
        }
        int rotation = getCurrentDeviceRotation();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                cameraUiContainerBindingRef.get().seekBarContainer.getLayoutParams());

        params.addRule(RelativeLayout.BELOW, 0);
        params.addRule(RelativeLayout.ABOVE, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_START, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_END, 0);

        int marginPx = Helpers.convertDpToPixels(contextRef.get(), 5);

        params.topMargin = marginPx;
        params.rightMargin = marginPx;
        params.bottomMargin = marginPx;
        params.leftMargin = marginPx;

        switch (rotation) {
            case 0:
                params.addRule(RelativeLayout.BELOW, R.id.chronoBar);
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
        cameraUiContainerBindingRef.get().seekBarContainer.setLayoutParams(params);
        cameraUiContainerBindingRef.get().seekBarContainer.setRotation(rotation);
    }

    private int getHowMuchRotated(int oldRotation, int currentRotation) {
        int howMuchRotated = 0;
        switch (oldRotation) {
            case 0:
                switch (currentRotation) {
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
                switch (currentRotation) {
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
                switch (currentRotation) {
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
                switch (currentRotation) {
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
}
