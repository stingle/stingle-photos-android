package com.fenritz.safecam;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.StorageUtils;
import com.fenritz.safecam.util.StorageUtils.StorageInfo;

public class SettingsActivity extends SherlockPreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);

		/*
		 * Camera mCamera = Camera.open(); List<Size> mSupportedPictureSizes =
		 * mCamera.getParameters().getSupportedPictureSizes();
		 * mCamera.release(); mCamera = null;
		 * 
		 * CharSequence[] entries = new
		 * CharSequence[mSupportedPictureSizes.size()];
		 * 
		 * for(int i=0;i<mSupportedPictureSizes.size();i++){ Camera.Size size =
		 * mSupportedPictureSizes.get(i); entries[i] =
		 * String.valueOf(size.width) + "x" + String.valueOf(size.height); }
		 * 
		 * ListPreference listPref =
		 * (ListPreference)findPreference("photo_size");
		 * listPref.setEntries(entries); getP listPref.setValueIndex(0);
		 * listPref.setEntryValues(entries);
		 */

		ListPreference homeFolderLocPref = (ListPreference)findPreference("home_folder");
		EditTextPreference decFolderPref = (EditTextPreference)findPreference("dec_folder");
		
		List<StorageInfo> storageList = StorageUtils.getStorageList();
		
		if(storageList.size() > 0){
			CharSequence[] storageEntries = new CharSequence[storageList.size()];
			CharSequence[] storageEntriesValues = new CharSequence[storageList.size()];
			
			for(int i=0;i<storageList.size();i++){
				StorageInfo item = storageList.get(i);
				storageEntries[i] = item.getDisplayName();
				storageEntriesValues[i] = item.path;
			}
			
			homeFolderLocPref.setEntries(storageEntries);
			homeFolderLocPref.setEntryValues(storageEntriesValues);
			
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
			
			String homeDirPath = sharedPrefs.getString("home_folder", null);
			if(homeDirPath == null){
				homeFolderLocPref.setValue(Helpers.getDefaultHomeDir());
			}
			
			homeFolderLocPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					Helpers.createFolders(SettingsActivity.this, (String)newValue);
					int result = Helpers.synchronizePasswordHash(SettingsActivity.this, (String)newValue);
					
					if(result == Helpers.HASH_SYNC_UPDATED_PREF){
						Toast.makeText(SettingsActivity.this, getString(R.string.need_to_relogin), Toast.LENGTH_LONG).show();
						Helpers.logout(SettingsActivity.this);
						finish();
					}
					
					return true;
				}
			});
		}
		
		Preference clearCachePref = findPreference("clear_cache");
		clearCachePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			
			public boolean onPreferenceClick(Preference preference) {
				AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
				builder.setMessage(getString(R.string.confirm_delete_cache));
				builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
					@SuppressWarnings("unchecked")
					public void onClick(DialogInterface dialog, int whichButton) {
						ArrayList<File> selectedFiles = new ArrayList<File>();
						File thumbsDir = new File(Helpers.getThumbsDir(SettingsActivity.this));
						File[] folderFiles = thumbsDir.listFiles();
						for(File file : folderFiles){
							selectedFiles.add(file);
						}
						new AsyncTasks.DeleteFiles(SettingsActivity.this).execute(selectedFiles);
					}
				});
				builder.setNegativeButton(getString(R.string.no), null);
				AlertDialog dialog = builder.create();
				dialog.show();
				return true;
			}
		});
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		Helpers.disableLockTimer(this);
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

}
