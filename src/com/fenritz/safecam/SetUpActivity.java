package com.fenritz.safecam;

import java.io.File;
import java.io.FileFilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.fenritz.safecam.util.AESCrypt;
import com.fenritz.safecam.util.AESCryptException;
import com.fenritz.safecam.util.Helpers;

public class SetUpActivity  extends Activity{

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.setup);

		File homeFolder = new File(Helpers.getHomeDir(this));
		
		if(homeFolder.exists()){
			File[] files = homeFolder.listFiles(new FileFilter() {
				
				public boolean accept(File file) {
					if(file.isFile() && file.getName().endsWith(getString(R.string.file_extension))) {
						return true;
					}
					return false;
				}
			});
			
			if(files.length > 0){
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.attention));
				builder.setMessage(getString(R.string.same_password_alert));
				builder.setNeutralButton(getString(R.string.understood), null);
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		}
		
		((Button) findViewById(R.id.setupButton)).setOnClickListener(setup());
	}
	
	private OnClickListener setup() {
		return new OnClickListener() {
			public void onClick(View v) {
				String password1 = ((EditText)findViewById(R.id.password1)).getText().toString();
				String password2 = ((EditText)findViewById(R.id.password2)).getText().toString();
				
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
				
				SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
				try {
					String loginHash = AESCrypt.byteToHex(AESCrypt.getHash(AESCrypt.byteToHex(AESCrypt.getHash(password1)) + password1));
					preferences.edit().putString(SafeCameraActivity.PASSWORD, loginHash).commit();
					Helpers.writeLoginHashToFile(SetUpActivity.this, loginHash);
				}
				catch (AESCryptException e) {
					Helpers.showAlertDialog(SetUpActivity.this, String.format(getString(R.string.unexpected_error), "102"));
					e.printStackTrace();
				}
				
				try {
					((SafeCameraApplication) SetUpActivity.this.getApplication()).setKey(AESCrypt.byteToHex(AESCrypt.getHash(password1)));
				}
				catch (AESCryptException e) {
					Helpers.showAlertDialog(SetUpActivity.this, getString(R.string.unexpected_error));
					return;
				}
				
				Intent intent = new Intent();
				intent.setClass(SetUpActivity.this, DashboardActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
				finish();
			}
		};
	}
}
