package com.fenritz.safecamera;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
		
		/*Camera mCamera = Camera.open();
		List<Size> mSupportedPictureSizes = mCamera.getParameters().getSupportedPictureSizes();
		mCamera.release();
		mCamera = null;
		
		CharSequence[] entries = new CharSequence[mSupportedPictureSizes.size()];
		
		for(int i=0;i<mSupportedPictureSizes.size();i++){
			Camera.Size size = mSupportedPictureSizes.get(i);
			entries[i] = String.valueOf(size.width) + "x" + String.valueOf(size.height);
		}
		
		ListPreference listPref = (ListPreference)findPreference("photo_size");
		listPref.setEntries(entries);
		getP
		listPref.setValueIndex(0);
	    listPref.setEntryValues(entries);*/
		
	}

}
