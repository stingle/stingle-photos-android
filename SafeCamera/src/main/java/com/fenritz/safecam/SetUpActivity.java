package com.fenritz.safecam;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fenritz.safecam.AsyncTasks.SignUpAsyncTask;
import com.fenritz.safecam.Files.FileManager;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.KeyManagement;

import org.json.JSONObject;

import java.util.HashMap;

public class SetUpActivity  extends Activity{

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.setup);

		((Button) findViewById(R.id.signup)).setOnClickListener(signup());
		((TextView) findViewById(R.id.loginBtn)).setOnClickListener(gotoLogin());
	}

	@Override
	protected void onResume() {
		super.onResume();

		if(FileManager.requestSDCardPermission(this)){
			FileManager.createFolders(this);
		}
	}
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case SafeCameraApplication.REQUEST_SD_CARD_PERMISSION: {
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					FileManager.createFolders(this);

				} else {
					finish();
				}
				return;
			}
		}
	}

	private OnClickListener gotoLogin() {
		return new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(SetUpActivity.this, LoginActivity.class);
				startActivity(intent);
				finish();
			}
		};
	}

	private OnClickListener signup() {
		return new OnClickListener() {
			public void onClick(View v) {
				final String email = ((EditText)findViewById(R.id.email)).getText().toString();
				final String password1 = ((EditText)findViewById(R.id.password1)).getText().toString();
				String password2 = ((EditText)findViewById(R.id.password2)).getText().toString();

				if(!Helpers.isValidEmail(email)){
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.invalid_email));
					return;
				}

				if(password1.equals("")){
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.password_empty));
					return;
				}
				
				if(!password1.equals(password2)){
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.password_not_match));
					return;
				}
				
				if(password1.length() < Integer.valueOf(getString(R.string.min_pass_length))){
					Helpers.showAlertDialog(SetUpActivity.this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
					return;
				}


				(new SignUpAsyncTask(SetUpActivity.this, email, password1)).execute();
			}
		};
	}


}
