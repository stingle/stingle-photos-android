package org.stingle.photos.CameraX.helpers;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;

import java.lang.ref.WeakReference;

public class PermissionHelper {

    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private final WeakReference<AppCompatActivity> activityRef;

    public PermissionHelper(AppCompatActivity activity) {
        activityRef = new WeakReference<>(activity);
    }

    public void requirePermissionsResult(OnCameraPermissionsGranted callback) {
       if (hasAllPermissionsGranted()) {
           if (callback != null) {
               callback.onGranted();
           }
       } else {
           requestNextPermission();
       }
    }

    private boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (activityRef.get().checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestNextPermission() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (activityRef.get().checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                if (permission.equals(Manifest.permission.CAMERA)) {
                    requestCameraPermission();
                    break;
                } else if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
                    requestAudioPermission();
                    break;
                }
            }
        }
    }

    private void requestCameraPermission() {
        if (activityRef.get().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (activityRef.get().shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                new MaterialAlertDialogBuilder(activityRef.get())
                        .setMessage(activityRef.get().getString(R.string.camera_perm_explain))
                        .setPositiveButton(activityRef.get().getString(R.string.ok), (dialog, which) ->
                                activityRef.get().requestPermissions(new String[]{Manifest.permission.CAMERA},
                                        StinglePhotosApplication.REQUEST_CAMERA_PERMISSION))
                        .setNegativeButton(activityRef.get().getString(R.string.cancel), (dialog, which) -> activityRef.get().finish())
                        .create()
                        .show();

            } else {
                activityRef.get().requestPermissions(new String[]{Manifest.permission.CAMERA},
                        StinglePhotosApplication.REQUEST_CAMERA_PERMISSION);
            }
        }
    }

    private void requestAudioPermission() {
        if (activityRef.get().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (activityRef.get().shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                new MaterialAlertDialogBuilder(activityRef.get())
                        .setMessage(activityRef.get().getString(R.string.camera_perm_explain))
                        .setPositiveButton(activityRef.get().getString(R.string.ok), (dialog, which) ->
                                activityRef.get().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                                        StinglePhotosApplication.REQUEST_AUDIO_PERMISSION))
                        .setNegativeButton(activityRef.get().getString(R.string.cancel), (dialog, which) -> activityRef.get().finish())
                        .create()
                        .show();

            } else {
                activityRef.get().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                        StinglePhotosApplication.REQUEST_AUDIO_PERMISSION);
            }
        }
    }

    public interface OnCameraPermissionsGranted {
        void onGranted();
    }
}
