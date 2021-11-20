package org.stingle.photos.camera.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.camera.core.ImageCapture;

import org.stingle.photos.R;

public class CameraToolsHelper {

    private static final String TIMER_PREF = "timerX";
    private static final String REPEAT_PREF = "repeatX";
    private static final String FLASH_MODE_PREF = "flash_modeX";

    private final Animation animation;
    private final SharedPreferences preferences;

    private int flashMode = ImageCapture.FLASH_MODE_AUTO;
    private int timerValue = 0;
    private int repeatValue = 1;

    public CameraToolsHelper(Context context, SharedPreferences preferences) {
        animation = AnimationUtils.loadAnimation(context, R.anim.time_animation);
        this.preferences = preferences;
    }

    public int getRepeatValue() {
        return repeatValue;
    }

    public int getFlashMode() {
        return flashMode;
    }

    public void setFlashMode(int mode) {
        flashMode = mode;
        preferences.edit().putInt(FLASH_MODE_PREF, flashMode).apply();
    }

    public boolean isTorchEnabled() {
        return flashMode == ImageCapture.FLASH_MODE_ON;
    }

    public void startCapturing(TextView timerTextView, OnTimerListener listener) {
        int currentTimerValue = timerValue;
        new CountDownTimer(timerValue * 1000L, 1000) {
            public void onTick(long millisUntilFinished) {
                if (listener != null) {
                    listener.onTick();
                }
                timerTextView.startAnimation(animation);
                timerTextView.setText(String.valueOf(timerValue--));
                timerTextView.setVisibility(View.VISIBLE);

            }

            public void onFinish() {
                if (listener != null) {
                    listener.onFinish();
                }
                timerTextView.clearAnimation();
                timerTextView.setVisibility(View.GONE);
                timerValue = currentTimerValue;
            }
        }.start();
    }

    public void onRepeatClick(UIUpdateListener listener) {
        if (repeatValue == 1) {
            repeatValue = 2;
        } else if (repeatValue == 2) {
            repeatValue = 5;
        } else if (repeatValue == 5) {
            repeatValue = 10;
        } else if (repeatValue == 10) {
            repeatValue = 1;
        }
        preferences.edit().putInt(REPEAT_PREF, repeatValue).apply();
        if (listener != null) {
            listener.onUpdate(updateRepeatButton());
        }
    }

    public void onTimerClick(UIUpdateListener listener) {
        if (timerValue == 0) {
            timerValue = 2;
        } else if (timerValue == 2) {
            timerValue = 5;
        } else if (timerValue == 5) {
            timerValue = 10;
        } else if (timerValue == 10) {
            timerValue = 0;
        }
        preferences.edit().putInt(TIMER_PREF, timerValue).apply();
        if (listener != null) {
            listener.onUpdate(updateTimeButton());
        }
    }

    public void onFlashClick(boolean isVideoCapture, UIUpdateListener listener) {
        if (!isVideoCapture) {
            if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                setFlashMode(ImageCapture.FLASH_MODE_AUTO);
            } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
                setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
                setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        } else {
            if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
                setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        }
        if (listener != null) {
            listener.onUpdate(updateFlashButton());
        }
    }

    public void applyLastTimer(UIUpdateListener listener) {
        timerValue = preferences.getInt(TIMER_PREF, 0);
        if (listener != null) {
            listener.onUpdate(updateTimeButton());
        }
    }

    public void applyLastRepeat(UIUpdateListener listener) {
        repeatValue = preferences.getInt(REPEAT_PREF, 1);
        if (listener != null) {
            listener.onUpdate(updateRepeatButton());
        }
    }

    public void applyLastFlashMode(UIUpdateListener listener) {
        flashMode = preferences.getInt(FLASH_MODE_PREF, ImageCapture.FLASH_MODE_AUTO);
        if (listener != null) {
            listener.onUpdate(updateFlashButton());
        }
    }

    private @DrawableRes int updateRepeatButton() {
        if (repeatValue == 2) {
            return R.drawable.ic_repeat_2;
        } else if (repeatValue == 5) {
            return R.drawable.ic_repeat_5;
        } else if (repeatValue == 10) {
            return R.drawable.ic_repeat_10;
        }
        return R.drawable.ic_repeat_off;
    }

    private @DrawableRes int updateTimeButton() {
        if (timerValue == 2) {
            return R.drawable.ic_timer_2;
        } else if (timerValue == 5) {
            return R.drawable.ic_timer_5;
        } else if (timerValue == 10) {
            return R.drawable.ic_timer_10;
        }
        return R.drawable.ic_timer_off;
    }

    private @DrawableRes int updateFlashButton() {
        if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            return R.drawable.flash_auto;
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            return R.drawable.flash_on;
        }
        return R.drawable.flash_off;
    }

    public interface UIUpdateListener {
        void onUpdate(@DrawableRes int iconId);
    }

    public interface OnTimerListener {
        void onTick();
        void onFinish();
    }
}
