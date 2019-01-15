package com.fenritz.safecam;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;

import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.StorageUtils;
import com.fenritz.safecam.util.StorageUtils.StorageInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends PreferenceActivity {

	public static String CUSTOM_HOME_VALUE = "-custom-";
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			getActionBar().setDisplayHomeAsUpEnabled(true);
			getActionBar().setHomeButtonEnabled(true);
		}

        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

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

		initHomeFolderPref();
		
		// Cache
		
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

        /*((Preference)findPreference("home_folder")).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {


            public boolean onPreferenceChange(Preference preference, Object newValue) {

            }
        });*/
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		Helpers.disableLockTimer(this);
	}
	
	protected void initHomeFolderPref(){
		final ListPreference homeFolderLocPref = (ListPreference)findPreference("home_folder");
		EditTextPreference decFolderPref = (EditTextPreference)findPreference("dec_folder");
		
		List<StorageInfo> storageList = StorageUtils.getStorageList();
		
		final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		CharSequence[] storageEntries = new CharSequence[storageList.size()+1];
		final CharSequence[] storageEntriesValues = new CharSequence[storageList.size()+1];
		
		final String homeDirCustomPath = sharedPrefs.getString("home_folder_location", null);
		String homeDirPath = sharedPrefs.getString("home_folder", null);
		
		if(storageList.size() > 0){
			
			for(int i=0;i<storageList.size();i++){
				StorageInfo item = storageList.get(i);
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
			
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String select = (String)newValue;
				
				if(select.equals(CUSTOM_HOME_VALUE)){
					AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
					builder.setMessage(getString(R.string.choose_folder));
					
					final EditText locationText = new EditText(SettingsActivity.this);
					
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
								
								new AlertDialog.Builder(SettingsActivity.this).setMessage(message).setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener(){
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
		Helpers.createFolders(SettingsActivity.this, homePath);
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
