package org.stingle.photos.camera.helpers;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SystemHelper {

    private static final double RATIO_4_3_VALUE = 4.0 / 3.0;
    private static final double RATIO_16_9_VALUE = 16.0 / 9.0;

    public static void setBrightness(AppCompatActivity activity) {
        if (activity == null) {
            return;
        }
        android.view.WindowManager.LayoutParams layout = activity.getWindow().getAttributes();
        layout.screenBrightness = 1f;
        activity.getWindow().setAttributes(layout);
    }

    public static void hideNavigationBar(AppCompatActivity activity, View rootArea) {
        if (activity == null) {
            return;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(activity.getWindow(), rootArea);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public static int aspectRatio(int width, int height) {
        double previewRatio = (double) max(width, height) / min(width, height);
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }
}
