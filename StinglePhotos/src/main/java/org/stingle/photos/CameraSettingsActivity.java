
package org.stingle.photos;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import org.stingle.photos.Util.Helpers;
import org.stingle.photos.camera.CameraImageSize;
import org.stingle.photos.camera.CameraNewActivity;

import java.util.ArrayList;

public class CameraSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Helpers.setLocale(this);
        setContentView(R.layout.activity_camera_settings);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.camera_preferences, rootKey);

            initCameraResolution();
        }

		public void initCameraResolution(){
			try {
				CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
				if (manager != null) {
					for (String cameraId : manager.getCameraIdList()) {
						CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
						Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
						StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

						ArrayList<CameraImageSize> imageSizes = Helpers.parsePhotoOutputs(getContext(), map);
						ArrayList<String> videoSizes = Helpers.parseVideoOutputs(getContext(), map);
						ListPreference photoRes;
						ListPreference videoRes;
						if(facing == CameraCharacteristics.LENS_FACING_FRONT) {
							photoRes = (ListPreference) findPreference("front_photo_res");
							videoRes = (ListPreference) findPreference("front_video_res");
						}
						else {
							photoRes = (ListPreference) findPreference("back_photo_res");
							videoRes = (ListPreference) findPreference("back_video_res");
						}

						CharSequence[] imageEntries = new CharSequence[imageSizes.size()];
						CharSequence[] imageEntriesValues = new CharSequence[imageSizes.size()];
						CharSequence[] videoEntries = new CharSequence[videoSizes.size()];
						CharSequence[] videoEntriesValues = new CharSequence[videoSizes.size()];

						for(int i=0;i<imageSizes.size();i++){
							imageEntries[i] = imageSizes.get(i).getPhotoString();
							imageEntriesValues[i] = String.valueOf(i);
						}
						for(int i=0;i<videoSizes.size();i++){
							videoEntries[i] = videoSizes.get(i);
							videoEntriesValues[i] = String.valueOf(i);
						}
						photoRes.setEntries(imageEntries);
						photoRes.setEntryValues(imageEntriesValues);
						if(photoRes.getValue() == null) {
							photoRes.setValueIndex(0);
						}

						videoRes.setEntries(videoEntries);
						videoRes.setEntryValues(videoEntriesValues);
						if(videoRes.getValue() == null) {
							videoRes.setValueIndex(0);
						}

					}
				}
			}
			catch (Exception e){}
		}
	}

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.setClass(CameraSettingsActivity.this, CameraNewActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }
}