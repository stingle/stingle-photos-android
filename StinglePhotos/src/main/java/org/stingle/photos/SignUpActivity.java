package org.stingle.photos;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.stingle.photos.AsyncTasks.SignUpAsyncTask;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Widget.Tooltip.SimpleTooltip;

public class SignUpActivity extends AppCompatActivity {

	private boolean isAdvancedOpen = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Helpers.blockScreenshotsIfEnabled(this);

		setContentView(R.layout.activity_sign_up);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
		actionBar.setTitle(getString(R.string.sign_up));

		findViewById(R.id.signup).setOnClickListener(signUp());
		findViewById(R.id.loginBtn).setOnClickListener(gotoLogin());
		findViewById(R.id.backup_keys_info).setOnClickListener(backupKeysInfo());
		findViewById(R.id.advanced_opener).setOnClickListener(toggleAdvanced());
	}

	@Override
	protected void onResume() {
		super.onResume();
	}


	private OnClickListener gotoLogin() {
		return v -> {
			Intent intent = new Intent();
			intent.setClass(SignUpActivity.this, LoginActivity.class);
			startActivity(intent);
			finish();
		};
	}

	private OnClickListener toggleAdvanced() {
		return v -> {
			if(isAdvancedOpen){
				slideUp(this, findViewById(R.id.advanced_container));
				((ImageView)findViewById(R.id.advanced_arrow)).setImageResource(R.drawable.ic_arrow_drop_down_black);
				isAdvancedOpen = false;
			}
			else{
				slideDown(this, findViewById(R.id.advanced_container));
				((ImageView)findViewById(R.id.advanced_arrow)).setImageResource(R.drawable.ic_arrow_drop_up_black);
				isAdvancedOpen = true;
			}
		};
	}

	public static void slideDown(Context ctx, View v){
		v.setVisibility(View.VISIBLE);
		Animation a = AnimationUtils.loadAnimation(ctx, R.anim.slide_down);
		if(a != null){
			a.reset();
			if(v != null){
				v.clearAnimation();
				v.startAnimation(a);
			}
		}
	}

	public static void slideUp(Context ctx, View v){

		Animation a = AnimationUtils.loadAnimation(ctx, R.anim.slide_up);
		a.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {

			}

			@Override
			public void onAnimationEnd(Animation animation) {
				v.setVisibility(View.GONE);
			}

			@Override
			public void onAnimationRepeat(Animation animation) {

			}
		});
		if(a != null){
			a.reset();
			if(v != null){
				v.clearAnimation();
				v.startAnimation(a);
			}
		}
	}

	private OnClickListener backupKeysInfo() {
		return v -> {
			int bgColor = 0;
			TypedValue typedValue = new TypedValue();
			if (getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true)){
				bgColor= typedValue.data;
			}

			int txtColor = getResources().getColor (R.color.textColor, getTheme());


			Log.d("bgColor", String.valueOf(bgColor));
			Log.d("txtColor", String.valueOf(txtColor));
			new SimpleTooltip.Builder(this)
					.anchorView(v)
					.text(getString(R.string.backup_key_desc))
					.gravity(Gravity.TOP)
					.transparentOverlay(false)
					.overlayWindowBackgroundColor(Color.BLACK)
					.backgroundColor(bgColor)
					.textColor(txtColor)
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
				Helpers.showAlertDialog(SignUpActivity.this, getString(R.string.invalid_email));
				return;
			}

			if(password1.equals("")){
				Helpers.showAlertDialog(SignUpActivity.this, getString(R.string.password_empty));
				return;
			}

			if(!password1.equals(password2)){
				Helpers.showAlertDialog(SignUpActivity.this, getString(R.string.password_not_match));
				return;
			}

			if(password1.length() < Integer.valueOf(getString(R.string.min_pass_length))){
				Helpers.showAlertDialog(SignUpActivity.this, String.format(getString(R.string.password_short), getString(R.string.min_pass_length)));
				return;
			}

			SwitchCompat isBackup = findViewById(R.id.is_backup_keys);

			(new SignUpAsyncTask(SignUpActivity.this, email, password1, isBackup.isChecked())).execute();
		};
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}
}
