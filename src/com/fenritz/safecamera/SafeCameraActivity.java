package com.fenritz.safecamera;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.fenritz.safecamera.util.AESCrypt;
import com.fenritz.safecamera.util.AESCryptException;
import com.fenritz.safecamera.util.Helpers;

public class SafeCameraActivity extends Activity {

	public static AESCrypt crypto;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.startup);

		((Button) findViewById(R.id.login)).setOnClickListener(login());
	}

	private OnClickListener login() {
		return new OnClickListener() {
			public void onClick(View v) {
				try {
					crypto = Helpers.getAESCrypt(((EditText) findViewById(R.id.password)).getText().toString());
					
					Intent intent = new Intent();
					intent.setClass(SafeCameraActivity.this, CameraActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
					finish();
				}
				catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
					Helpers.showAlertDialog(SafeCameraActivity.this, String.format(getString(R.string.unexpected_error),"100"));
				}
				catch (NoSuchProviderException e) {
					e.printStackTrace();
					Helpers.showAlertDialog(SafeCameraActivity.this, String.format(getString(R.string.unexpected_error),"101"));
				}
				catch (AESCryptException e) {
					e.printStackTrace();
					Helpers.showAlertDialog(SafeCameraActivity.this, String.format(getString(R.string.unexpected_error),"102"));
				}
			}
		};
	}
}