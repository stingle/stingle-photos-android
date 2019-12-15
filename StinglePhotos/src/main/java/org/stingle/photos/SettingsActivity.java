package org.stingle.photos;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.stingle.photos.Auth.BiometricsManagerWrapper;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class SettingsActivity extends AppCompatActivity implements
		PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

	private static final String TITLE_TAG = "settingsActivityTitle";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.settings, new HeaderFragment())
					.commit();
		} else {
			setTitle(savedInstanceState.getCharSequence(TITLE_TAG));
		}
		getSupportFragmentManager().addOnBackStackChangedListener(
				() -> {
					if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
						setTitle(R.string.title_activity_settings);
					}
				});
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Save current activity title so we can set it again after a configuration change
		outState.putCharSequence(TITLE_TAG, getTitle());
	}

	@Override
	public boolean onSupportNavigateUp() {
		if (getSupportFragmentManager().popBackStackImmediate()) {
			return true;
		}
		return super.onSupportNavigateUp();
	}

	@Override
	public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
		// Instantiate the new Fragment
		final Bundle args = pref.getExtras();
		final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
				getClassLoader(),
				pref.getFragment());
		fragment.setArguments(args);
		fragment.setTargetFragment(caller, 0);
		// Replace the existing Fragment with the new Fragment
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.settings, fragment)
				.addToBackStack(null)
				.commit();
		setTitle(pref.getTitle());
		return true;
	}

	public static class HeaderFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.header_preferences, rootKey);
		}
	}

	public static class GeneralPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.general_preferences, rootKey);

			initResyncDBButton();

			SwitchPreference blockScreenshotsSetting = (SwitchPreference)findPreference("block_screenshots");
			blockScreenshotsSetting.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					final Context context = GeneralPreferenceFragment.this.getContext();
					Helpers.showConfirmDialog(context, getString(R.string.need_restart), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {

							Intent intent = new Intent(context, GalleryActivity.class);
							intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
							//intent.putExtra(KEY_RESTART_INTENT, nextIntent);
							context.startActivity(intent);

							Runtime.getRuntime().exit(0);
						}
					}, null);
					return true;
				}
			});
		}

		protected void initResyncDBButton() {
			Preference resyncDBPref = findPreference("resync_db");
			resyncDBPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

				public boolean onPreferenceClick(Preference preference) {
					final ProgressDialog spinner = Helpers.showProgressDialog(GeneralPreferenceFragment.this.getContext(), getString(R.string.syncing_db), null);
					SyncManager.syncFSToDB(GeneralPreferenceFragment.this.getContext(), new SyncManager.OnFinish() {
						@Override
						public void onFinish(Boolean needToUpdateUI) {
							spinner.dismiss();
						}
					}, AsyncTask.THREAD_POOL_EXECUTOR);
					return true;
				}
			});
		}
	}

	public static class SecurityPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.security_preferences, rootKey);

			SwitchPreference biometricSetting = findPreference(LoginManager.BIOMETRIC_PREFERENCE);

			BiometricsManagerWrapper biometricsManagerWrapper = new BiometricsManagerWrapper((AppCompatActivity) getActivity());
			BiometricManager biometricManager = BiometricManager.from(getActivity());
			if(biometricManager.canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS){
				biometricSetting.setEnabled(false);
			}
			biometricSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean isEnabled = (boolean)newValue;
				if (isEnabled) {
					biometricsManagerWrapper.setupBiometrics(biometricSetting);
					return false;
				}
				return true;
			});
		}
	}

	public static class SyncPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.sync_preferences, rootKey);
		}
	}
}
