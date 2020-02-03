package org.stingle.photos;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.widget.EditText;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.stingle.photos.AsyncTasks.SignUpAsyncTask;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Widget.Tooltip.SimpleTooltip;

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

		findViewById(R.id.signup).setOnClickListener(signUp());
		findViewById(R.id.loginBtn).setOnClickListener(gotoLogin());
		findViewById(R.id.backup_keys_info).setOnClickListener(backupKeysInfo());
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

	private OnClickListener backupKeysInfo() {
		return v -> {
			new SimpleTooltip.Builder(this)
					.anchorView(v)
					.text(getString(R.string.backup_key_desc))
					.gravity(Gravity.TOP)
					.transparentOverlay(false)
					.overlayWindowBackgroundColor(Color.BLACK)
					.build()
					.show();

		};
	}

	private OnClickListener signUp() {
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

			SwitchCompat isBackup = findViewById(R.id.is_backup_keys);

			(new SignUpAsyncTask(SetUpActivity.this, email, password1, isBackup.isChecked())).execute();
		};
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}
}
