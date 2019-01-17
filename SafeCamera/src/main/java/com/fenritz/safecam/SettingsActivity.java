package com.fenritz.safecam;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;

import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.FingerprintManagerWrapper;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.LoginManager;
import com.fenritz.safecam.util.StorageUtils;

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

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
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
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
            bindPreferenceSummaryToValue(findPreference("home_folder_name"));
            bindPreferenceSummaryToValue(findPreference("dec_folder"));

            initHomeFolderPref();
            initClearCacheButton();
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

        protected void initClearCacheButton(){
            Preference clearCachePref = findPreference("clear_cache");
            clearCachePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(GeneralPreferenceFragment.this.getContext());
                    builder.setMessage(getString(R.string.confirm_delete_cache));
                    builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @SuppressWarnings("unchecked")
                        public void onClick(DialogInterface dialog, int whichButton) {
                            ArrayList<File> selectedFiles = new ArrayList<File>();
                            File thumbsDir = new File(Helpers.getThumbsDir(GeneralPreferenceFragment.this.getContext()));
                            File[] folderFiles = thumbsDir.listFiles();
                            for(File file : folderFiles){
                                selectedFiles.add(file);
                            }
                            new AsyncTasks.DeleteFiles(GeneralPreferenceFragment.this.getContext()).execute(selectedFiles);
                        }
                    });
                    builder.setNegativeButton(getString(R.string.no), null);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return true;
                }
            });
        }

        protected void initHomeFolderPref(){
            final ListPreference homeFolderLocPref = (ListPreference)findPreference("home_folder");
            EditTextPreference decFolderPref = (EditTextPreference)findPreference("dec_folder");

            List<StorageUtils.StorageInfo> storageList = StorageUtils.getStorageList();

            final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(GeneralPreferenceFragment.this.getContext());
            CharSequence[] storageEntries = new CharSequence[storageList.size()+1];
            final CharSequence[] storageEntriesValues = new CharSequence[storageList.size()+1];

            final String homeDirCustomPath = sharedPrefs.getString("home_folder_location", null);
            String homeDirPath = sharedPrefs.getString("home_folder", null);

            if(storageList.size() > 0){

                for(int i=0;i<storageList.size();i++){
                    StorageUtils.StorageInfo item = storageList.get(i);
                    storageEntries[i] = item.getDisplayName();
                    storageEntriesValues[i] = item.path;
                }

            }

            if(homeDirCustomPath != null){
                storageEntries[storageEntries.length-1] = getString(R.string.custom_folder) + "("+homeDirCustomPath+")";
            }
            else{
                storageEntries[storageEntries.length-1] = getString(R.string.custom_folder);
            }
            storageEntriesValues[storageEntriesValues.length-1] = CUSTOM_HOME_VALUE;

            homeFolderLocPref.setEntries(storageEntries);
            homeFolderLocPref.setEntryValues(storageEntriesValues);

            final String homeFolderCurrentValue;

            if(homeDirPath != null && homeDirPath.equals(CUSTOM_HOME_VALUE) && homeDirCustomPath != null){
                homeFolderCurrentValue = String.valueOf(storageEntriesValues[storageEntriesValues.length-1]);
            }
            else if(homeDirPath == null){
                homeFolderCurrentValue = Helpers.getDefaultHomeDir();
            }
            else{
                homeFolderCurrentValue = homeFolderLocPref.getValue();
            }

            homeFolderLocPref.setValue(homeFolderCurrentValue);

            homeFolderLocPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(final Preference preference, Object newValue) {
                    String select = (String)newValue;

                    if(select.equals(CUSTOM_HOME_VALUE)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(GeneralPreferenceFragment.this.getContext());
                        builder.setMessage(getString(R.string.choose_folder));

                        final EditText locationText = new EditText(GeneralPreferenceFragment.this.getContext());

                        if(homeDirCustomPath != null){
                            locationText.setText(homeDirCustomPath);
                        }
                        else{
                            locationText.setText(Helpers.getDefaultHomeDir());
                        }
                        builder.setView(locationText);

                        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                File newLoc = new File(locationText.getText().toString());

                                if(newLoc.exists() && newLoc.isDirectory() && newLoc.canWrite()){
                                    sharedPrefs.edit().putString("home_folder_location", locationText.getText().toString()).commit();
                                    initFolders(locationText.getText().toString());
                                    initHomeFolderPref();
                                }
                                else{
                                    String message = String.format(getString(R.string.no_such_folder),locationText.getText().toString());

                                    new AlertDialog.Builder(GeneralPreferenceFragment.this.getContext()).setMessage(message).setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener(){
                                        public void onClick(DialogInterface dialog, int which) {
                                            if(Helpers.getDefaultHomeDir().equals(locationText.getText().toString())){
                                                sharedPrefs.edit().putString("home_folder_location", null).commit();
                                            }
                                            homeFolderLocPref.setValue(storageEntriesValues[0].toString());
                                            initHomeFolderPref();
                                        };
                                    }).setCancelable(false)
                                            .show();
                                }
                            }
                        });
                        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if(Helpers.getDefaultHomeDir().equals(locationText.getText().toString())){
                                    sharedPrefs.edit().putString("home_folder_location", null).commit();
                                }
                                homeFolderLocPref.setValue(storageEntriesValues[0].toString());
                                initHomeFolderPref();
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                    else{
                        initFolders(select);
                    }

                    return true;
                }
            });
        }
        protected void initFolders(String homePath){
            Helpers.createFolders(GeneralPreferenceFragment.this.getContext(), homePath);
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
            final FingerprintManagerWrapper fingerprintManager = new FingerprintManagerWrapper(this.getContext());

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

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("lock_time"));
            bindPreferenceSummaryToValue(findPreference("timerDuration"));
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
}
