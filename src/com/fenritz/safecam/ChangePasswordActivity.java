package com.fenritz.safecam;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.fenritz.safecam.util.AESCrypt;
import com.fenritz.safecam.util.AESCryptException;
import com.fenritz.safecam.util.Helpers;

public class ChangePasswordActivity extends SherlockActivity {

	private BroadcastReceiver receiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		setContentView(R.layout.change_password);
		
		Helpers.fixBackgroundRepeat(findViewById(R.id.parentLayout));
		
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
		if(receiver != null){
			unregisterReceiver(receiver);
		}
	}
	
	private OnClickListener changeClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				String currentPassword = ((EditText)findViewById(R.id.current_password)).getText().toString();
				
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				String savedHash = preferences.getString(SafeCameraActivity.PASSWORD, "");
				
				try {
					String enteredPasswordHash = AESCrypt.byteToHex(AESCrypt.getHash(AESCrypt.byteToHex(AESCrypt.getHash(currentPassword)) + currentPassword));
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
					
					if(newPassword.length() < Integer.valueOf(getString(R.string.min_pass_length))){
						Helpers.showAlertDialog(ChangePasswordActivity.this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
						return;
					}
						
					new ReEncryptFiles().execute(newPassword);
				}
				catch (AESCryptException e) {
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
		private PowerManager.WakeLock wl;

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
			
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "decrypt");
			wl.acquire();
		}

		private ArrayList<File> getFilesList(String path){
			File dir = new File(path);
			File[] folderFiles = dir.listFiles();

			Arrays.sort(folderFiles);

			ArrayList<File> files = new ArrayList<File>();
			for (File file : folderFiles) {
				if (file.isFile() && file.getName().endsWith(getString(R.string.file_extension))) {
					files.add(file);
					
					/*String thumbPath = Helpers.getThumbsDir(ChangePasswordActivity.this) + "/" + Helpers.getThumbFileName(file);
					File thumb = new File(thumbPath);
					if(thumb.exists() && thumb.isFile()){
						files.add(thumb);
					}*/
				}
				else if(file.isDirectory() && !file.getName().startsWith(".")){
					files.addAll(getFilesList(file.getPath()));
				}
			}
			
			return files;
		}
		
		@Override
		protected Void doInBackground(String... params) {
			String newPassword = params[0];
			try {
				String passwordHash = AESCrypt.byteToHex(AESCrypt.getHash(newPassword));
	
				AESCrypt newCrypt = Helpers.getAESCrypt(passwordHash, ChangePasswordActivity.this);
				
				ArrayList<File> files = getFilesList(Helpers.getHomeDir(ChangePasswordActivity.this));
				
				progressDialog.setMax(files.size());
				
				int counter = 0;
				for(File file : files){
					try {
						FileInputStream inputStream = new FileInputStream(file);
						
						String origFilePath = file.getPath();
						String tmpFilePath = origFilePath + ".tmp";
						
						File tmpFile = new File(tmpFilePath);
						FileOutputStream outputStream = new FileOutputStream(tmpFile);
						
						if(Helpers.getAESCrypt(ChangePasswordActivity.this).reEncrypt(inputStream, outputStream, newCrypt, null, this)){
							String oldFilename = file.getName();
							String decryptedFilename = Helpers.decryptFilename(ChangePasswordActivity.this, oldFilename);
							
							Integer num = null;
							Pattern p = Pattern.compile("^zzSC\\-\\d+\\_.+");
							Matcher m = p.matcher(oldFilename);
							if (m.find()) {
								try{
									num = Integer.parseInt(oldFilename.substring(5, oldFilename.indexOf("_")));
									
								}
								catch(NumberFormatException e){}
							}
							
							String newFilename;
							if(num != null){
								newFilename = "zzSC-" + String.valueOf(num) + "_" + Helpers.encryptFilename(ChangePasswordActivity.this, decryptedFilename, newCrypt);
							}
							else{
								newFilename = decryptedFilename;
							}
							
							String finalFilePath = file.getParent() + "/" + newFilename;
							
							String thumbPath = Helpers.getThumbsDir(ChangePasswordActivity.this) + "/" + Helpers.getThumbFileName(file);
							File thumb = new File(thumbPath);
							if(thumb.exists() && thumb.isFile()){
								FileInputStream thumbInputStream = new FileInputStream(thumb);
								FileOutputStream thumbOutputStream = new FileOutputStream(new File(Helpers.getThumbsDir(ChangePasswordActivity.this), Helpers.getThumbFileName(new File(finalFilePath))));
								
								if(Helpers.getAESCrypt(ChangePasswordActivity.this).reEncrypt(thumbInputStream, thumbOutputStream, newCrypt, null, this)){
									thumb.delete();
								}
							}
							
							file.delete();
							tmpFile.renameTo(new File(finalFilePath));
						}
						else{
							if(tmpFile.isFile()){
								tmpFile.delete();
							}
						}
						
						publishProgress(++counter);
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
				
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				String loginHash = AESCrypt.byteToHex(AESCrypt.getHash(passwordHash + newPassword));
				preferences.edit().putString(SafeCameraActivity.PASSWORD, loginHash).commit();
				Helpers.writeLoginHashToFile(ChangePasswordActivity.this, loginHash);
				
				((SafeCameraApplication) ChangePasswordActivity.this.getApplication()).setKey(passwordHash);
			}
			catch (AESCryptException e1) {
				e1.printStackTrace();
			}
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
			
			wl.release();
			
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
