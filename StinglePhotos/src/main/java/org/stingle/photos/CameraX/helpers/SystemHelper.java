package org.stingle.photos.CameraX.helpers;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SystemHelper {

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
}
