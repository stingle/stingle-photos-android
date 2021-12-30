package org.stingle.photos.CameraX.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.camera.core.ImageCapture;
import androidx.core.content.ContextCompat;

import org.stingle.photos.R;

import java.lang.ref.WeakReference;

public class CameraToolsHelper {

    private static final String TIMER_PREF = "timerX";
    private static final String REPEAT_PREF = "repeatX";
    private static final String FLASH_MODE_PREF = "flash_modeX";

    private final WeakReference<Context> contextRef;
    private final Animation animation;
    private final SharedPreferences preferences;

    private int flashMode = ImageCapture.FLASH_MODE_AUTO;
    private int timerValue = 0;
    private int repeatValue = 1;
    private Toast toast;
    private CountDownTimer timer;

    public CameraToolsHelper(Context context, SharedPreferences preferences) {
        animation = AnimationUtils.loadAnimation(context, R.anim.time_animation);
        this.contextRef = new WeakReference<>(context);
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
        timer = new CountDownTimer(timerValue * 1000L, 1000) {
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

    public void cancelTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    public void onRepeatClick(UIUpdateListener listener) {
        String toastMessage = "";
        if (repeatValue == 1) {
            toastMessage = contextRef.get().getString(R.string.repeat_enabled_2);
            repeatValue = 2;
        } else if (repeatValue == 2) {
            toastMessage = contextRef.get().getString(R.string.repeat_enabled_5);
            repeatValue = 5;
        } else if (repeatValue == 5) {
            toastMessage = contextRef.get().getString(R.string.repeat_enabled_10);
            repeatValue = 10;
        } else if (repeatValue == 10) {
            toastMessage = contextRef.get().getString(R.string.repeat_disabled);
            repeatValue = 1;
        }
        showToast(toastMessage);
        preferences.edit().putInt(REPEAT_PREF, repeatValue).apply();
        if (listener != null) {
            listener.onUpdate(updateRepeatButton());
        }
    }

    public void onTimerClick(UIUpdateListener listener) {
        String toastMessage = "";
        if (timerValue == 0) {
            toastMessage = contextRef.get().getString(R.string.timer_enabled_2);
            timerValue = 2;
        } else if (timerValue == 2) {
            toastMessage = contextRef.get().getString(R.string.timer_enabled_5);
            timerValue = 5;
        } else if (timerValue == 5) {
            toastMessage = contextRef.get().getString(R.string.timer_enabled_10);
            timerValue = 10;
        } else if (timerValue == 10) {
            toastMessage = contextRef.get().getString(R.string.timer_disabled);
            timerValue = 0;
        }
        showToast(toastMessage);
        preferences.edit().putInt(TIMER_PREF, timerValue).apply();
        if (listener != null) {
            listener.onUpdate(updateTimeButton());
        }
    }

    public void onFlashClick(boolean isVideoCapture, UIUpdateListener listener) {
        String toastMessage = "";
        if (!isVideoCapture) {
            if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                toastMessage = contextRef.get().getString(R.string.flash_mode_auto);
                setFlashMode(ImageCapture.FLASH_MODE_AUTO);
            } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
                toastMessage = contextRef.get().getString(R.string.flash_mode_on);
                setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
                toastMessage = contextRef.get().getString(R.string.flash_mode_off);
                setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        } else {
            if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                toastMessage = contextRef.get().getString(R.string.torch_on);
                setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
                toastMessage = contextRef.get().getString(R.string.torch_off);
                setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        }
        showToast(toastMessage);
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

    private void showToast(String message) {
        TextView textview = new TextView(contextRef.get());
        textview.setText(message);
        textview.setBackground(ContextCompat.getDrawable(contextRef.get(), R.drawable.chrono_bg));
        textview.setTextColor(Color.WHITE);
        textview.setPadding(30,10,30,10);
        if (toast != null) {
            toast.cancel();
        }
        toast = new Toast(contextRef.get());
        toast.setView(textview);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 200);
        toast.show();
    }

    public interface UIUpdateListener {
        void onUpdate(@DrawableRes int iconId);
    }

    public interface OnTimerListener {
        void onTick();
        void onFinish();
    }
}
