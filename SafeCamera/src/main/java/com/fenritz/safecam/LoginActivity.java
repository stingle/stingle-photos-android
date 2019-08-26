package com.fenritz.safecam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fenritz.safecam.AsyncTasks.LoginAsyncTask;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.LoginManager;

public class LoginActivity extends Activity {



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.login);

		((Button) findViewById(R.id.login)).setOnClickListener(login());
		((TextView) findViewById(R.id.forgot_password)).setOnClickListener(forgotPassword());
		((TextView) findViewById(R.id.register)).setOnClickListener(gotoSignUp());
		((EditText) findViewById(R.id.password)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_GO) {
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
					doLogin();
					return true;
				}
				return false;
			}
		});

		displayVersionName();
	}

	private OnClickListener login() {
		return new OnClickListener() {
			public void onClick(View v) {
				doLogin();
			}
		};
	}

	private OnClickListener gotoSignUp() {
		return new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(LoginActivity.this, SetUpActivity.class);
				startActivity(intent);
				finish();
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		LoginManager.disableLockTimer(this);
	}
	
	private void doLogin() {
		String email = ((EditText) findViewById(R.id.email)).getText().toString();
		String password = ((EditText) findViewById(R.id.password)).getText().toString();

		if(!Helpers.isValidEmail(email)){
			Helpers.showAlertDialog(this, getString(R.string.invalid_email));
			return;
		}

		if(password.length() < Integer.valueOf(getString(R.string.min_pass_length))){
			Helpers.showAlertDialog(this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
			return;
		}

		(new LoginAsyncTask(this, email, password)).execute();

	}

	protected OnClickListener forgotPassword(){
        return new OnClickListener() {
            public void onClick(View v) {
                /*AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
                builder.setTitle(getString(R.string.forgot_password));
                builder.setMessage(getString(R.string.reset_password));
                builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences preferences = getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, MODE_PRIVATE);
                        preferences.edit().remove(SafeCameraApplication.PASSWORD).commit();
						SafeCameraApplication.setKey(null);

                        Intent intent = new Intent();
                        intent.setClass(LoginActivity.this, SetUpActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
                builder.setNegativeButton(getString(R.string.no), null);
                AlertDialog dialog = builder.create();
                dialog.show();*/
            }
        };
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
}