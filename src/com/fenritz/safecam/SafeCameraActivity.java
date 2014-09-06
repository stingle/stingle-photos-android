package com.fenritz.safecam;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecam.util.AESCrypt;
import com.fenritz.safecam.util.AESCryptException;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.PRNGFixes;

public class SafeCameraActivity extends Activity {

	public static final String DEFAULT_PREFS = "default_prefs";
	public static final String PASSWORD = "password";
	public static final String LAST_CAM_ID = "last_cam_id";
	public static final String LAST_LOCK_TIME = "lock_time";
	public static final String ACTION_JUST_LOGIN = "just_login";
	public static final String PARAM_EXTRA_DATA = "extra_data";
	public static final String LOGINS_COUNT_FOR_POPUP = "logins_count_fp";
	public static final String DONT_SHOW_POPUP = "dont_show_popup";
	public static final String POPUP_LATERS_COUNT = "laters_count";

	private SharedPreferences preferences;
	
	private boolean justLogin = false;
	private Bundle extraData;

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.startup);

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			getActionBar().hide();
		}
		
		Helpers.createFolders(this);
		
		Helpers.deleteTmpDir(SafeCameraActivity.this);
		
		Helpers.synchronizePasswordHash(this);
		
		PRNGFixes.apply();
		
		justLogin = getIntent().getBooleanExtra(ACTION_JUST_LOGIN, false);
		extraData = getIntent().getBundleExtra(PARAM_EXTRA_DATA);
		
		preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
		if (!preferences.contains(SafeCameraActivity.PASSWORD)) {
			Intent intent = new Intent();
			intent.setClass(SafeCameraActivity.this, SetUpActivity.class);
			startActivity(intent);
			finish();
		}

		((Button) findViewById(R.id.login)).setOnClickListener(login());
		((EditText) findViewById(R.id.password)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_GO) {
					InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
					doLogin();
					return true;
				}
				return false;
			}
		});
		
		displayVersionName();
		
		if(extraData != null && extraData.getBoolean("wentToLoginToProceed", false)){
			Toast.makeText(this, getString(R.string.login_to_proceed), Toast.LENGTH_LONG).show();
		}
	}

	private void displayVersionName() {
	    String versionName = "";
	    PackageInfo packageInfo;
	    try {
	        packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
	    	versionName = "v " + packageInfo.versionName;
	    } catch (NameNotFoundException e) {
	        e.printStackTrace();
	    }
	    ((TextView) findViewById(R.id.versionText)).setText(versionName);
	}

	
	private OnClickListener login() {
		return new OnClickListener() {
			public void onClick(View v) {
				doLogin();
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		String key = ((SafeCameraApplication) this.getApplicationContext()).getKey();
		if(key != null){
			Intent intent = new Intent();
			intent.setClass(SafeCameraActivity.this, DashboardActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			finish();
			return;
		}
		
		Helpers.disableLockTimer(this);
	}
	
	private void doLogin() {
		String savedHash = preferences.getString(PASSWORD, "");
		String enteredPassword = ((EditText) findViewById(R.id.password)).getText().toString();
		try{
			String enteredPasswordHash = AESCrypt.byteToHex(AESCrypt.getHash(enteredPassword));
			String loginHash = AESCrypt.byteToHex(AESCrypt.getHash(AESCrypt.byteToHex(AESCrypt.getHash(enteredPassword)) + enteredPassword));
			
			if (!loginHash.equals(savedHash)) {
				Helpers.showAlertDialog(SafeCameraActivity.this, getString(R.string.incorrect_password));
				return;
			}

			((SafeCameraApplication) getApplication()).setKey(enteredPasswordHash);
		}
		catch (AESCryptException e) {
			Helpers.showAlertDialog(SafeCameraActivity.this, String.format(getString(R.string.unexpected_error), "102"));
			e.printStackTrace();
		}
		
		if(justLogin && extraData != null){
			//Intent intent = extraData.getParcelable("intent");
			//startActivity(intent);
			getIntent().putExtra("login_ok", true);
			setResult(RESULT_OK, getIntent());
		}
		else{
			Intent intent = new Intent();
			intent.setClass(SafeCameraActivity.this, DashboardActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			
			boolean dontShowPopup = preferences.getBoolean(DONT_SHOW_POPUP, false);
			
			if(!dontShowPopup){
				int loginsCount = preferences.getInt(LOGINS_COUNT_FOR_POPUP, 0);
				loginsCount++;
				if(loginsCount >= Integer.valueOf(getString(R.string.popup_logins_limit))){
					intent.putExtra("showPopup", true);
					loginsCount = 0;
				}
				preferences.edit().putInt(SafeCameraActivity.LOGINS_COUNT_FOR_POPUP, loginsCount).commit();
			}
			startActivity(intent);
		}
		finish();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);;
        return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.forgot_password:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.forgot_password));
			builder.setMessage(getString(R.string.reset_password));
			builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
					preferences.edit().remove(SafeCameraActivity.PASSWORD).commit();
					Helpers.removeLoginHashFile(SafeCameraActivity.this);
					((SafeCameraApplication) getApplication()).setKey(null);
					
					Intent intent = new Intent();
					intent.setClass(SafeCameraActivity.this, SetUpActivity.class);
					startActivity(intent);
					finish();
				}
			});
			builder.setNegativeButton(getString(R.string.no), null);
			AlertDialog dialog = builder.create();
			dialog.show();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}