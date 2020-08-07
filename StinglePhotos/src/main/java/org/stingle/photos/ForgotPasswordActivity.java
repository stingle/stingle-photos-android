package org.stingle.photos;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.stingle.photos.AsyncTasks.CheckRecoveryPhraseAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.SetNewPasswordAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Util.Helpers;

public class ForgotPasswordActivity extends AppCompatActivity {

	private EditText email;
	private EditText phrase;
	private Button checkBtn;
	private Boolean isKeyBackedUp = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Helpers.setLocale(this);
		setContentView(R.layout.activity_forgot_password);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		email = findViewById(R.id.email);
		phrase = findViewById(R.id.backup_phrase);
		checkBtn = findViewById(R.id.checkBtn);

		checkBtn.setOnClickListener(checkInput());
	}

	@Override
	protected void onResume() {
		super.onResume();

		if(LoginManager.isLoggedIn(this)){
			finish();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	private View.OnClickListener checkInput(){
		return v -> {
			(new CheckRecoveryPhraseAsyncTask(ForgotPasswordActivity.this, email.getText().toString(), phrase.getText().toString(), new OnAsyncTaskFinish() {
				@Override
				public void onFinish(Boolean isKeyBackedUpParam) {
					super.onFinish();
					showPasswordChangeDialog();
					isKeyBackedUp = isKeyBackedUpParam;
				}
			})).execute();
		};
	}

	private void showPasswordChangeDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.please_set_new_password);
		builder.setView(R.layout.dialog_new_password);
		builder.setCancelable(true);
		AlertDialog dialog = builder.create();
		dialog.show();

		Button okButton = dialog.findViewById(R.id.okButton);
		Button cancelButton = dialog.findViewById(R.id.cancelButton);
		final EditText passwordField = dialog.findViewById(R.id.password);
		final EditText password2Field = dialog.findViewById(R.id.password2);

		final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

		okButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

			String password1 = passwordField.getText().toString();
			String password2 = password2Field.getText().toString();

			if(password1.equals("")){
				Helpers.showAlertDialog(ForgotPasswordActivity.this, getString(R.string.error), getString(R.string.password_empty));
				return;
			}

			if(!password1.equals(password2)){
				Helpers.showAlertDialog(ForgotPasswordActivity.this, getString(R.string.error), getString(R.string.password_not_match));
				return;
			}

			if(password1.length() < Integer.valueOf(getString(R.string.min_pass_length))){
				Helpers.showAlertDialog(ForgotPasswordActivity.this, getString(R.string.error), String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
				return;
			}

			(new SetNewPasswordAsyncTask(ForgotPasswordActivity.this, email.getText().toString(), phrase.getText().toString(), password1, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					dialog.dismiss();
				}
			})).setUploadPrivateKey(isKeyBackedUp).execute();
		});

		cancelButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			if(dialog != null){
				dialog.dismiss();
			}
		});
	}



	@Override
	public boolean onSupportNavigateUp() {
		finish();
		return super.onSupportNavigateUp();
	}


}
