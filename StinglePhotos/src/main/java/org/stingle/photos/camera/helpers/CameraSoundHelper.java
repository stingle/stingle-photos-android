package org.stingle.photos.camera.helpers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.media.SoundPool;

import org.stingle.photos.R;

import java.lang.ref.WeakReference;

public class CameraSoundHelper {

    private final WeakReference<Context> contextRef;
    private final AudioManager audioManager;

    private static SoundPool pool;
    private static int soundId;
    private static MediaActionSound media_action_sound;

    public CameraSoundHelper(Context context) {
        this.contextRef = new WeakReference<>(context);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        initSounds();
    }

    public void playSound(int sound) {
        if (audioManager != null && audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            media_action_sound.play(sound);
        }
    }

    public void playLowSound() {
        pool.play(soundId, 1f, 1f, 1, 0, 1f);
    }

    private void initSounds() {
        if (contextRef.get() == null) {
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
        soundId = pool.load(contextRef.get(), R.raw.timer_animation, 1);
    }
}
