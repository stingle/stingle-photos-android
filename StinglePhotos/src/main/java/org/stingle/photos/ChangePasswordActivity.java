package org.stingle.photos;

import android.content.BroadcastReceiver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.stingle.photos.AsyncTasks.ChangePasswordAsyncTask;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Auth.LoginManager;

public class ChangePasswordActivity extends AppCompatActivity {

	private BroadcastReceiver receiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//getActionBar().setDisplayHomeAsUpEnabled(true);
		//getActionBar().setHomeButtonEnabled(true);

		setContentView(R.layout.activity_change_password);

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
					StinglePhotosApplication.getCrypto().getPrivateKey(currentPassword);
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