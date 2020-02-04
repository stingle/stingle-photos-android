package org.stingle.photos;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.ReuploadKeysAsyncTask;
import org.stingle.photos.Auth.BiometricsManagerWrapper;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Auth.PasswordReturnListener;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class SettingsActivity extends AppCompatActivity implements
		PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

	private static final String TITLE_TAG = "settingsActivityTitle";

	private LocalBroadcastManager lbm;
	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LoginManager.redirectToLogin(SettingsActivity.this);
		}
	};

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

		lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));
	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this);
		LoginManager.disableLockTimer(this);
	}

	@Override
	protected void onPause() {
		super.onPause();

		LoginManager.setLockedTime(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		lbm.unregisterReceiver(onLogout);
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
		else{
			finish();
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

	public static class AccountPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.account_preferences, rootKey);

			initKeyBackupSettings();
			initChangePassword();
		}

		private void initKeyBackupSettings(){
			SwitchPreference keyBackupPref = findPreference("is_key_backed_up");
			keyBackupPref.setOnPreferenceChangeListener((preference, newValue) -> {
				final Context context = AccountPreferenceFragment.this.getContext();
				if((Boolean)newValue == false){
					Helpers.showConfirmDialog(context, getString(R.string.key_backup_delete_confirm), (dialogInterface, i) -> {
						(new ReuploadKeysAsyncTask(AccountPreferenceFragment.this.getActivity(), null, true, new OnAsyncTaskFinish() {
							@Override
							public void onFinish() {
								keyBackupPref.setChecked(false);
							}
						})).execute();

					}, null);
				}
				else{
					LoginManager.LoginConfig loginConfig = new LoginManager.LoginConfig() {{
						showCancel = true;
						showLogout = false;
						quitActivityOnCancel = false;
						cancellable = true;
					}};
					LoginManager.getPasswordFromUser(AccountPreferenceFragment.this.getActivity(), loginConfig, new PasswordReturnListener() {
						@Override
						public void passwordReceived(String enteredPassword, AlertDialog dialog) {
							LoginManager.dismissLoginDialog(dialog);
							(new ReuploadKeysAsyncTask(AccountPreferenceFragment.this.getActivity(), enteredPassword, false, new OnAsyncTaskFinish() {
								@Override
								public void onFinish() {
									keyBackupPref.setChecked(true);
								}
							})).execute();
						}

						@Override
						public void passwordReceiveFailed(AlertDialog dialog) {

						}

					});
				}

				return false;
			});
		}

		private void initChangePassword(){
			Preference changePassPref = findPreference("change_password");
			changePassPref.setOnPreferenceClickListener(preference -> {
				Intent intent = new Intent();
				intent.setClass(AccountPreferenceFragment.this.getActivity(), ChangePasswordActivity.class);
				startActivity(intent);
				return false;
			});

		}

	}

	public static class SecurityPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.security_preferences, rootKey);

			initBiometricsSettings();
			initBlockScreenshotsSettings();
		}

		private void initBiometricsSettings(){
			SwitchPreference biometricSetting = findPreference(LoginManager.BIOMETRIC_PREFERENCE);

			BiometricsManagerWrapper biometricsManagerWrapper = new BiometricsManagerWrapper((AppCompatActivity) getActivity());
			if(!biometricsManagerWrapper.isBiometricsAvailable()){
				biometricSetting.setEnabled(false);
			}
			biometricSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean isEnabled = (boolean)newValue;
				if (isEnabled) {
					biometricsManagerWrapper.setupBiometrics(biometricSetting, null);
					return false;
				}
				return true;
			});
		}



		private void initBlockScreenshotsSettings(){
			SwitchPreference blockScreenshotsSetting = findPreference("block_screenshots");
			blockScreenshotsSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				final Context context = SecurityPreferenceFragment.this.getContext();
				Helpers.showConfirmDialog(context, getString(R.string.need_restart), (dialogInterface, i) -> {

					Intent intent = new Intent(context, GalleryActivity.class);
					intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
					//intent.putExtra(KEY_RESTART_INTENT, nextIntent);
					context.startActivity(intent);

					Runtime.getRuntime().exit(0);
				}, null);
				return true;
			});
		}
	}

	public static class SyncPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.sync_preferences, rootKey);

			initBatteryPref();
		}

		private void initBatteryPref(){
			SeekBarPreference pref = findPreference("upload_battery_level");
			pref.setSummary(getString(R.string.upload_battery_summary, String.valueOf(pref.getValue())));
			pref.setOnPreferenceChangeListener((preference, newValue) -> {
				final int progress = Integer.valueOf(String.valueOf(newValue));
				preference.setSummary(getString(R.string.upload_battery_summary, String.valueOf(progress)));
				return true;
			});
		}
	}

	public static class AdvancedPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.advanced_preferences, rootKey);

			initResyncDBButton();
		}

		protected void initResyncDBButton() {
			Preference resyncDBPref = findPreference("resync_db");
			resyncDBPref.setOnPreferenceClickListener(preference -> {
				final ProgressDialog spinner = Helpers.showProgressDialog(AdvancedPreferenceFragment.this.getContext(), getString(R.string.syncing_db), null);
				SyncManager.syncFSToDB(AdvancedPreferenceFragment.this.getContext(), new SyncManager.OnFinish() {
					@Override
					public void onFinish(Boolean needToUpdateUI) {
						spinner.dismiss();
					}
				}, AsyncTask.THREAD_POOL_EXECUTOR);
				return true;
			});
		}
	}
}
