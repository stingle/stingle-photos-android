package com.fenritz.safecamera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

	public static AESCrypt crypto;

	public static final String DEFAULT_PREFS = "default_prefs";
	public static final String PASSWORD = "password";

	private SharedPreferences preferences;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.startup);

		Helpers.createFolders(this);
		
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

	private void doLogin() {
		String savedHash = preferences.getString(PASSWORD, "");
		String enteredPassword = ((EditText) findViewById(R.id.password)).getText().toString();
		try {
			String enteredPasswordHash = AESCrypt.byteToHex(AESCrypt.getHash(enteredPassword));
			if (!enteredPasswordHash.equals(savedHash)) {
				Helpers.showAlertDialog(SafeCameraActivity.this, getString(R.string.incorrect_password));
				return;
			}

			crypto = Helpers.getAESCrypt(SafeCameraActivity.this, enteredPassword);

			Intent intent = new Intent();
			intent.setClass(SafeCameraActivity.this, CameraActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			SafeCameraActivity.this.finish();
		}
		catch (AESCryptException e) {
			Helpers.showAlertDialog(SafeCameraActivity.this, String.format(getString(R.string.unexpected_error), "102"));
			e.printStackTrace();
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