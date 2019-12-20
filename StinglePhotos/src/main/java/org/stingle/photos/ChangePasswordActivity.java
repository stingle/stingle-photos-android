package org.stingle.photos;

import android.content.BroadcastReceiver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View.OnClickListener;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.AsyncTasks.ChangePasswordAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Util.Helpers;

public class ChangePasswordActivity extends AppCompatActivity {

	private BroadcastReceiver receiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_change_password);

		setTitle(getString(R.string.change_password));

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);

		Helpers.blockScreenshotsIfEnabled(this);

		findViewById(R.id.change).setOnClickListener(changeClick());
		findViewById(R.id.cancel).setOnClickListener(cancelClick());
	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this);
		LoginManager.disableLockTimer(this);
	}

	@Override
	protected void onPause() {
		super.onPause();

		LoginManager.setLockedTime(this);
	}

	@Override
	public boolean onSupportNavigateUp() {
		finish();
		return true;
	}

	private OnClickListener changeClick() {
		return v -> {
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
		};
	}

	private OnClickListener cancelClick() {
		return v -> ChangePasswordActivity.this.finish();
	}

}