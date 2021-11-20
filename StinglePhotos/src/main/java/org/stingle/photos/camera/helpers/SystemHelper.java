package org.stingle.photos.camera.helpers;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.media.SoundPool;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.stingle.photos.R;

public class SystemHelper {

    private static final double RATIO_4_3_VALUE = 4.0 / 3.0;
    private static final double RATIO_16_9_VALUE = 16.0 / 9.0;
    private static SoundPool pool;
    private static int soundId;
    private static MediaActionSound media_action_sound;

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
    public static void initSounds(AppCompatActivity activity) {
        if (activity == null) {
            return;
        }
        media_action_sound = new MediaActionSound();
        media_action_sound.load(MediaActionSound.START_VIDEO_RECORDING);
        media_action_sound.load(MediaActionSound.STOP_VIDEO_RECORDING);
        media_action_sound.load(MediaActionSound.SHUTTER_CLICK);


        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        pool = new SoundPool.Builder().setAudioAttributes(audioAttributes)
                .setMaxStreams(5)
                .build();
        soundId = pool.load(activity, R.raw.timer_animation, 1);
    }


    public static void playSound(AppCompatActivity activity, int sound) {
        AudioManager audioManager = (AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
        if( audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL ) {
            media_action_sound.play(sound);
        }
    }

    public static void playLowSound() {
        pool.play(soundId, 1f, 1f, 1, 0, 1f);
    }
}
