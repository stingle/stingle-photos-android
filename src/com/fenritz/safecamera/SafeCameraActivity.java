package com.fenritz.safecamera;

import javax.crypto.SecretKey;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fenritz.safecamera.util.AESCrypt;
import com.fenritz.safecamera.util.AESCryptException;
import com.fenritz.safecamera.util.Helpers;

public class SafeCameraActivity extends Activity {

	

	public static final String DEFAULT_PREFS = "default_prefs";
	public static final String PASSWORD = "password";
	public static final String ACTION_JUST_LOGIN = "just_login";
	public static final String PARAM_EXTRA_DATA = "extra_data";

	private SharedPreferences preferences;
	
	private boolean justLogin = false;
	private Bundle extraData;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.startup);

		Helpers.createFolders(this);
		
		Helpers.deleteTmpDir(SafeCameraActivity.this);
		
		justLogin = getIntent().getBooleanExtra(ACTION_JUST_LOGIN, false);
		extraData = getIntent().getBundleExtra(PARAM_EXTRA_DATA);
		
		preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
		if (!preferences.contains(SafeCameraActivity.PASSWORD)) {
			Intent intent = new Intent();
			intent.setClass(SafeCameraActivity.this, SetUpActivity.class);
			startActivity(intent);
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
		
		Helpers.disableLockTimer(this);
		
		SecretKey key = ((SafeCameraApplication) this.getApplicationContext()).getKey();
		if(key != null){
			Intent intent = new Intent();
			intent.setClass(SafeCameraActivity.this, CameraActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			finish();
		}
	}
	
	private void doLogin() {
		String savedHash = preferences.getString(PASSWORD, "");
		String enteredPassword = ((EditText) findViewById(R.id.password)).getText().toString();
		try{
			String enteredPasswordHash = AESCrypt.byteToHex(AESCrypt.getHash(enteredPassword));
			if (!enteredPasswordHash.equals(savedHash)) {
				Helpers.showAlertDialog(SafeCameraActivity.this, getString(R.string.incorrect_password));
				return;
			}

			new Login().execute(enteredPassword);
		}
		catch (AESCryptException e) {
			Helpers.showAlertDialog(SafeCameraActivity.this, String.format(getString(R.string.unexpected_error), "102"));
			e.printStackTrace();
		}
	}
	
	private class Login extends AsyncTask<String, Void, Boolean> {
		
		private ProgressDialog progressDialog;
		
		@Override
    	protected void onPreExecute() {
    		super.onPreExecute();
    		progressDialog = ProgressDialog.show(SafeCameraActivity.this, "", getString(R.string.generating_key), false, true, new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					
				}
			});
    	}
		
		@Override
		protected Boolean doInBackground(String... params) {
			SecretKey key = Helpers.getAESKey(SafeCameraActivity.this, params[0]);
			if(key != null){
				((SafeCameraApplication) SafeCameraActivity.this.getApplication()).setKey(key);
				return true;
			}
			return false;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			
			if(result == true){
				if(justLogin && extraData != null){
					Intent intent = extraData.getParcelable("intent");
					startActivity(intent);
				}
				else{
					Intent intent = new Intent();
					intent.setClass(SafeCameraActivity.this, CameraActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
				}
				SafeCameraActivity.this.finish();
			}
			else{
				Helpers.showAlertDialog(SafeCameraActivity.this, getString(R.string.incorrect_password));
			}
			progressDialog.dismiss();
		}
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.settings:
			Intent intent = new Intent();
			intent.setClass(SafeCameraActivity.this, SettingsActivity.class);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}