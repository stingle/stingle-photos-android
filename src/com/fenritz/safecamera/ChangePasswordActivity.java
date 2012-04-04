package com.fenritz.safecamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.SecretKey;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.change_password);
		
		findViewById(R.id.change).setOnClickListener(changeClick());
	}
	
	private OnClickListener changeClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				String currentPassword = ((EditText)findViewById(R.id.current_password)).getText().toString();
				String newPassword = ((EditText)findViewById(R.id.new_password)).getText().toString();
				new ReEncryptFiles().execute(newPassword);
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

			SecretKey newKey = Helpers.getAESKey(ChangePasswordActivity.this, newPassword);
			AESCrypt newCrypt = Helpers.getAESCrypt(newKey);
			
			
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
					byte[] decryptedData = Helpers.getAESCrypt().decrypt(inputStream, null, this);

					if(decryptedData != null){
						String origFilePath = file.getPath();
						String tmpFilePath = origFilePath + ".tmp";
						
						File tmpFile = new File(tmpFilePath);
						FileOutputStream outputStream = new FileOutputStream(tmpFile);
						newCrypt.encrypt(decryptedData, outputStream);
						
						file.delete();
						tmpFile.renameTo(new File(origFilePath));
					}
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
			
			Helpers.key = newKey;

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
}
