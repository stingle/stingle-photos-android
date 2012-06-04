package com.fenritz.safecamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

import com.fenritz.safecamera.util.AESCrypt;
import com.fenritz.safecamera.util.AESCryptException;
import com.fenritz.safecamera.util.Helpers;

public class ChangePasswordActivity extends Activity {

	private BroadcastReceiver receiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.change_password);
		
		findViewById(R.id.change).setOnClickListener(changeClick());
		findViewById(R.id.cancel).setOnClickListener(cancelClick());
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
	}
	
	private OnClickListener changeClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				String currentPassword = ((EditText)findViewById(R.id.current_password)).getText().toString();
				
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				String savedHash = preferences.getString(SafeCameraActivity.PASSWORD, "");
				
				try {
					String enteredPasswordHash = AESCrypt.byteToHex(AESCrypt.getHash(currentPassword));
					if (!enteredPasswordHash.equals(savedHash)) {
						Helpers.showAlertDialog(ChangePasswordActivity.this, getString(R.string.incorrect_password));
						return;
					}
					
					String newPassword = ((EditText)findViewById(R.id.new_password)).getText().toString();
					String confirm_password = ((EditText)findViewById(R.id.confirm_password)).getText().toString();
					
					if(!newPassword.equals(confirm_password)){
						Helpers.showAlertDialog(ChangePasswordActivity.this, getString(R.string.password_not_match));
						return;
					}
						
					new ReEncryptFiles().execute(newPassword);
				}
				catch (AESCryptException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
	}
	
	private OnClickListener cancelClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				ChangePasswordActivity.this.finish();
			}
		};
	}
	
	private class ReEncryptFiles extends AsyncTask<String, Integer, Void> {

		private ProgressDialog progressDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(ChangePasswordActivity.this);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					ReEncryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(getString(R.string.changing_password));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Void doInBackground(String... params) {
			String newPassword = params[0];

			AESCrypt newCrypt = Helpers.getAESCrypt(newPassword, ChangePasswordActivity.this);
			
			
			File dir = new File(Helpers.getHomeDir(ChangePasswordActivity.this));
			File[] folderFiles = dir.listFiles();

			Arrays.sort(folderFiles);

			ArrayList<File> files = new ArrayList<File>();
			for (File file : folderFiles) {
				if (file.getName().endsWith(getString(R.string.file_extension))) {
					files.add(file);
					
					String thumbPath = Helpers.getThumbsDir(ChangePasswordActivity.this) + "/" + file.getName();
					File thumb = new File(thumbPath);
					if(thumb.exists() && thumb.isFile()){
						files.add(thumb);
					}
				}
			}
			
			progressDialog.setMax(files.size());
			
			int counter = 0;
			for(File file : files){
				try {
					FileInputStream inputStream = new FileInputStream(file);
					
					String origFilePath = file.getPath();
					String tmpFilePath = origFilePath + ".tmp";
					
					File tmpFile = new File(tmpFilePath);
					FileOutputStream outputStream = new FileOutputStream(tmpFile);
					
					Helpers.getAESCrypt(ChangePasswordActivity.this).reEncrypt(inputStream, outputStream, newCrypt, null, this);
					
					file.delete();
					tmpFile.renameTo(new File(origFilePath));
					
					publishProgress(++counter);
				}
				catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
			
			SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
			try {
				preferences.edit().putString(SafeCameraActivity.PASSWORD, AESCrypt.byteToHex(AESCrypt.getHash(newPassword))).commit();
			}
			catch (AESCryptException e) {
				e.printStackTrace();
			}
			
			((SafeCameraApplication) ChangePasswordActivity.this.getApplication()).setKey(newPassword);

			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			Toast.makeText(ChangePasswordActivity.this, getString(R.string.success_change_pass), Toast.LENGTH_LONG).show();
			
			ChangePasswordActivity.this.finish();
		}

	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Helpers.setLockedTime(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);
	}
}
