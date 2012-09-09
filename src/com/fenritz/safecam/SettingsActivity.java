package com.fenritz.safecam;

import java.io.File;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.Helpers;

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

		Preference homeFolderPref = findPreference("home_folder");
		homeFolderPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			
			public boolean onPreferenceClick(Preference preference) {
				((EditTextPreference) preference).getEditText().setText(Helpers.getHomeDir(SettingsActivity.this));
				return true;
			}
		});
		
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
		// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
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
