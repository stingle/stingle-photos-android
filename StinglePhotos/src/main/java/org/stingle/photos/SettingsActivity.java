package org.stingle.photos;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.view.MenuItem;

import org.stingle.photos.Camera.CameraImageSize;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Util.AsyncTasks;
import org.stingle.photos.Auth.FingerprintManagerWrapper;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Auth.LoginManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity {


    public final static String CUSTOM_HOME_VALUE = "-custom-";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    @Override
    protected void onResume() {
        super.onResume();

        LoginManager.disableLockTimer(this);
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            }
            else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }



    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || SecurityPreferenceFragment.class.getName().equals(fragmentName)
                || CameraPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.

            initClearCacheButton();

            bindPreferenceSummaryToValue(findPreference("lock_time"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        protected void initClearCacheButton() {
            Preference clearCachePref = findPreference("clear_cache");
            clearCachePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(GeneralPreferenceFragment.this.getContext());
                    builder.setMessage(getString(R.string.confirm_delete_cache));
                    builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @SuppressWarnings("unchecked")
                        public void onClick(DialogInterface dialog, int whichButton) {
                            ArrayList<File> selectedFiles = new ArrayList<File>();
                            File thumbsDir = new File(FileManager.getThumbsDir(GeneralPreferenceFragment.this.getContext()));
                            File[] folderFiles = thumbsDir.listFiles();
                            for (File file : folderFiles) {
                                selectedFiles.add(file);
                            }
                            new AsyncTasks.DeleteFiles(GeneralPreferenceFragment.this.getContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, selectedFiles);
                        }
                    });
                    builder.setNegativeButton(getString(R.string.no), null);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return true;
                }
            });
        }
    }


    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    public static class SecurityPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_security);
            setHasOptionsMenu(true);

            SwitchPreference fingerprintSetting = (SwitchPreference)findPreference("fingerprint");
            final FingerprintManagerWrapper fingerprintManager = new FingerprintManagerWrapper(getActivity());

            if(!fingerprintManager.isFingerprintAvailable()){
                fingerprintSetting.setEnabled(false);
            }
            fingerprintSetting.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean isEnabled = (boolean)newValue;
                    if (isEnabled) {
                        fingerprintManager.setupFingerprint(preference);

                        return false;
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    public static class CameraPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_camera);
            setHasOptionsMenu(true);

            initCameraResolution();
            bindPreferenceSummaryToValue(findPreference("timerDuration"));
            bindPreferenceSummaryToValue(findPreference("front_photo_res"));
            bindPreferenceSummaryToValue(findPreference("front_video_res"));
            bindPreferenceSummaryToValue(findPreference("back_photo_res"));
            bindPreferenceSummaryToValue(findPreference("back_video_res"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        public void initCameraResolution(){
            try {
                CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
                if (manager != null) {
                    for (String cameraId : manager.getCameraIdList()) {
                        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                        ArrayList<CameraImageSize> imageSizes = Helpers.parsePhotoOutputs(getContext(), map, characteristics);
                        ArrayList<CameraImageSize> videoSizes = Helpers.parseVideoOutputs(getContext(), map, characteristics);

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
                            videoEntries[i] = videoSizes.get(i).getVideoString();
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
}
