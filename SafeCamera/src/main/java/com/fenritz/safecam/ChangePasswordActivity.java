package com.fenritz.safecam;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.fenritz.safecam.AsyncTasks.ChangePasswordAsyncTask;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.LoginManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChangePasswordActivity extends AppCompatActivity {

	private BroadcastReceiver receiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//getActionBar().setDisplayHomeAsUpEnabled(true);
		//getActionBar().setHomeButtonEnabled(true);

		setContentView(R.layout.change_password);

		Toolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(getString(R.string.change_password));
		setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);

		toolbar.setNavigationOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view){
				finish();
			}
		});

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);


		findViewById(R.id.change).setOnClickListener(changeClick());
		findViewById(R.id.cancel).setOnClickListener(cancelClick());
		

	}

	private OnClickListener changeClick() {
		return new OnClickListener() {
			@Override
			public void onClick(View v) {
				String currentPassword = ((EditText)findViewById(R.id.current_password)).getText().toString();
				try {
					SafeCameraApplication.getCrypto().getPrivateKey(currentPassword);
				} catch (CryptoException e) {
					Helpers.showAlertDialog(ChangePasswordActivity.this, getString(R.string.incorrect_password));
					return;
				}


				String newPassword = ((EditText)findViewById(R.id.new_password)).getText().toString();
				String confirm_password = ((EditText)findViewById(R.id.confirm_password)).getText().toString();

				if(!newPassword.equals(confirm_password)){
					Helpers.showAlertDialog(ChangePasswordActivity.this, getString(R.string.password_not_match));
					return;
				}

				if(newPassword.length() < Integer.valueOf(getString(R.string.min_pass_length))){
					Helpers.showAlertDialog(ChangePasswordActivity.this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
					return;
				}

				(new ChangePasswordAsyncTask(ChangePasswordActivity.this, currentPassword, newPassword)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		};
	}

	@Override
	protected void onPause() {
		super.onPause();

		LoginManager.setLockedTime(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this);
		LoginManager.disableLockTimer(this);
	}
	

	
	private OnClickListener cancelClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				ChangePasswordActivity.this.finish();
			}
		};
	}

}