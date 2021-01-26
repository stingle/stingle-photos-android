package org.stingle.photos;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import org.stingle.photos.AsyncTasks.DeleteAccountAsyncTask;
import org.stingle.photos.AsyncTasks.GenericAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.ReuploadKeysAsyncTask;
import org.stingle.photos.AsyncTasks.Sync.FsSyncAsyncTask;
import org.stingle.photos.Auth.BiometricsManagerWrapper;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Auth.PasswordReturnListener;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;

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
		Helpers.setLocale(this);
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
		} else {
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

			initEmail();
			initKeyBackupSettings();
			initChangePassword();
			initDeleteAccount();
		}

		private void initEmail() {
			Preference email = findPreference("email");
			email.setSummary(Helpers.getPreference(AccountPreferenceFragment.this.getActivity(), StinglePhotosApplication.USER_EMAIL, ""));
		}

		private void initKeyBackupSettings() {
			SwitchPreference keyBackupPref = findPreference("is_key_backed_up");
			keyBackupPref.setOnPreferenceChangeListener((preference, newValue) -> {
				final Context context = getContext();
				if ((Boolean) newValue == false) {
					Helpers.showConfirmDialog(
							context,
							getString(R.string.delete_key_backup_question),
							getString(R.string.key_backup_delete_confirm),
							R.drawable.ic_action_delete,
							(dialogInterface, i) -> {
						(new ReuploadKeysAsyncTask(AccountPreferenceFragment.this.getActivity(), null, true, new OnAsyncTaskFinish() {
							@Override
							public void onFinish() {
								keyBackupPref.setChecked(false);
							}
						})).execute();

					}, null);
				} else {
					LoginManager.LoginConfig loginConfig = new LoginManager.LoginConfig() {{
						showCancel = true;
						showLogout = false;
						quitActivityOnCancel = false;
						cancellable = true;
						okButtonText = getString(R.string.ok);
						titleText = getString(R.string.please_enter_password);
						subTitleText = getString(R.string.password_for_key_backup);
						showLockIcon = false;
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

		private void initChangePassword() {
			Preference changePassPref = findPreference("change_password");
			changePassPref.setOnPreferenceClickListener(preference -> {
				Intent intent = new Intent();
				intent.setClass(AccountPreferenceFragment.this.getActivity(), ChangePasswordActivity.class);
				startActivity(intent);
				return false;
			});

		}

		private void initDeleteAccount() {
			Preference deleteAccountPref = findPreference("delete_account");
			deleteAccountPref.setOnPreferenceClickListener(preference -> {


				Helpers.showConfirmDialog(
						getContext(),
						getString(R.string.delete_account_question),
						getString(R.string.delete_account_question_desc),
						R.drawable.ic_action_delete,
						(dialog, which) -> {
							LoginManager.LoginConfig loginConfig = new LoginManager.LoginConfig() {{
								showCancel = true;
								showLogout = false;
								quitActivityOnCancel = false;
								cancellable = true;
								okButtonText = getContext().getString(R.string.ok);
								titleText = getContext().getString(R.string.please_enter_password);
								subTitleText = getContext().getString(R.string.password_for_account_delete);
								showLockIcon = false;
							}};
							LoginManager.getPasswordFromUser(getActivity(), loginConfig, new PasswordReturnListener() {
								@Override
								public void passwordReceived(String enteredPassword, AlertDialog dialog) {
									try{
										StinglePhotosApplication.getCrypto().getPrivateKey(enteredPassword);
										LoginManager.dismissLoginDialog(dialog);
										Helpers.showConfirmDialog(
												getContext(),
												getString(R.string.delete_account_question2),
												getString(R.string.delete_account_question_desc2),
												R.drawable.ic_action_delete,
												(dialog2, which) -> {
													(new DeleteAccountAsyncTask(getActivity(), enteredPassword)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
												},
												null);
									}
									catch (CryptoException e) {
										Helpers.showAlertDialog(dialog.getContext(), dialog.getContext().getString(R.string.error), dialog.getContext().getString(R.string.incorrect_password));
									}
								}

								@Override
								public void passwordReceiveFailed(AlertDialog dialog) {
									LoginManager.dismissLoginDialog(dialog);
								}
							});

						},
						null);


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

		private void initBiometricsSettings() {
			SwitchPreference biometricSetting = findPreference(LoginManager.BIOMETRIC_PREFERENCE);

			BiometricsManagerWrapper biometricsManagerWrapper = new BiometricsManagerWrapper((AppCompatActivity) getActivity());
			if (!biometricsManagerWrapper.isBiometricsAvailable()) {
				biometricSetting.setEnabled(false);
			}
			biometricSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean isEnabled = (boolean) newValue;
				if (isEnabled) {
					biometricsManagerWrapper.setupBiometrics(biometricSetting, null);
					return false;
				}
				return true;
			});
		}


		private void initBlockScreenshotsSettings() {
			SwitchPreference blockScreenshotsSetting = findPreference("block_screenshots");
			blockScreenshotsSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				restartApp(getContext());
				return true;
			});
		}
	}

	public static class SyncPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.sync_preferences, rootKey);

			initBatteryPref();
			initDisableBackup();
		}

		private void initBatteryPref() {
			SeekBarPreference pref = findPreference("upload_battery_level");
			pref.setSummary(getString(R.string.upload_battery_summary, String.valueOf(pref.getValue())));
			pref.setOnPreferenceChangeListener((preference, newValue) -> {
				final int progress = Integer.valueOf(String.valueOf(newValue));
				preference.setSummary(getString(R.string.upload_battery_summary, String.valueOf(progress)));
				return true;
			});
		}

		private void initDisableBackup() {
			SwitchPreference enableImportSetting = findPreference("enable_backup");

			enableImportSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean isEnabled = (boolean) newValue;
				if (!isEnabled) {
					SyncManager.stopSync(getContext());
				}
				return true;
			});
		}
	}

	public static class ImportPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.import_preferences, rootKey);
			initEnableAutoImport();
			initAutoImportSource();
		}

		private void initEnableAutoImport() {
			SwitchPreference enableImportSetting = findPreference(SyncManager.PREF_IMPORT_ENABLED);

			enableImportSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean isEnabled = (boolean) newValue;
				if (isEnabled) {
					Helpers.storePreference(getContext(), SyncManager.LAST_IMPORTED_FILE_DATE, System.currentTimeMillis() / 1000);
				}
				SyncManager.stopSync(getContext());
				return true;
			});
		}

		private void initAutoImportSource() {
			ListPreference importFromSetting = findPreference(SyncManager.PREF_IMPORT_FROM);

			importFromSetting.setOnPreferenceChangeListener((preference, newValue) -> {
				SyncManager.stopSync(getContext());
				return true;
			});
		}
	}

	public static class AppearancePreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.appearance_preferences, rootKey);

			initThemePref();
			initLanguagePref();
		}

		private void initThemePref() {
			ListPreference pref = findPreference("theme");
			pref.setOnPreferenceChangeListener((preference, newValue) -> {
				Helpers.applyTheme((String) newValue);
				return true;
			});
		}

		private void initLanguagePref() {
			ListPreference pref = findPreference("locale");
			pref.setOnPreferenceChangeListener((preference, newValue) -> {
				restartApp(getContext());
				return true;
			});
		}
	}

	public static class AdvancedPreferenceFragment extends PreferenceFragmentCompat {

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.advanced_preferences, rootKey);

			initResyncDBButton();
			initDeleteCacheButton();
		}

		private void initResyncDBButton() {
			Preference resyncDBPref = findPreference("resync_db");
			assert resyncDBPref != null;
			resyncDBPref.setOnPreferenceClickListener(preference -> {
				final ProgressDialog spinner = Helpers.showProgressDialog(getContext(), getString(R.string.syncing_db), null);

				(new FsSyncAsyncTask(getContext(), new SyncManager.OnFinish() {
					@Override
					public void onFinish(Boolean needToUpdateUI) {
						spinner.dismiss();
					}
				})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

				Helpers.deletePreference(getContext(), SyncManager.PREF_LAST_SEEN_TIME);
				Helpers.deletePreference(getContext(), SyncManager.PREF_TRASH_LAST_SEEN_TIME);
				Helpers.deletePreference(getContext(), SyncManager.PREF_ALBUMS_LAST_SEEN_TIME);
				Helpers.deletePreference(getContext(), SyncManager.PREF_ALBUM_FILES_LAST_SEEN_TIME);
				Helpers.deletePreference(getContext(), SyncManager.PREF_LAST_DEL_SEEN_TIME);
				Helpers.deletePreference(getContext(), SyncManager.PREF_LAST_CONTACTS_SEEN_TIME);

				return true;
			});
		}

		private void initDeleteCacheButton() {
			Preference deleteCachePref = findPreference("delete_cache");
			assert deleteCachePref != null;
			deleteCachePref.setOnPreferenceClickListener(preference -> {
				Helpers.showConfirmDialog(
						getContext(),
						getString(R.string.delete_question),
						getString(R.string.confirm_delete_cache),
						R.drawable.ic_action_delete,
						(dialog, which) -> {

							GenericAsyncTask task = new GenericAsyncTask(getContext());
							task.setWork(new GenericAsyncTask.GenericTaskWork() {
								@Override
								public Object execute(Context context) {
									File thumbCacheDir = new File(context.getCacheDir().getPath() + "/" + FileManager.THUMB_CACHE_DIR);
									File filesCacheDir = new File(context.getCacheDir().getPath() + "/" + FileManager.FILE_CACHE_DIR);
									Helpers.deleteFolderRecursive(thumbCacheDir);
									Helpers.deleteFolderRecursive(filesCacheDir);
									return true;
								}
							});

							task.setOnFinish(new OnAsyncTaskFinish() {
								@Override
								public void onFinish() {
									super.onFinish();
									Toast.makeText(getContext(), R.string.success_deleted, Toast.LENGTH_LONG).show();
								}

							});

							task.showSpinner(getString(R.string.deleting_cache));
							task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
						},
						null);

				return true;
			});
		}
	}

	private static void restartApp(Context context){
		Helpers.showConfirmDialog(
				context,
				context.getString(R.string.app_restart_question),
				context.getString(R.string.need_restart),
				null,
				(dialogInterface, i) -> {

					Intent intent = new Intent(context, GalleryActivity.class);
					intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
					//intent.putExtra(KEY_RESTART_INTENT, nextIntent);
					context.startActivity(intent);

					Runtime.getRuntime().exit(0);
				}, null);
	}
}
