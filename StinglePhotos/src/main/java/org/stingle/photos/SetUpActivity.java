package org.stingle.photos;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.AsyncTasks.SignUpAsyncTask;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Util.Helpers;

public class SetUpActivity  extends AppCompatActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Helpers.blockScreenshotsIfEnabled(this);

		setContentView(R.layout.setup);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
		actionBar.setTitle(getString(R.string.sign_up));

		findViewById(R.id.signup).setOnClickListener(signup());
		findViewById(R.id.loginBtn).setOnClickListener(gotoLogin());
	}

	@Override
	protected void onResume() {
		super.onResume();
	}


	private OnClickListener gotoLogin() {
		return v -> {
			Intent intent = new Intent();
			intent.setClass(SetUpActivity.this, LoginActivity.class);
			startActivity(intent);
			finish();
		};
	}

	private OnClickListener signup() {
		return v -> {
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
		};
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}
}
