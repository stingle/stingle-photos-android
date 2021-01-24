package org.stingle.photos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.MnemonicUtils;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.util.Objects;

public class BackupKeyActivity extends AppCompatActivity {

	private LocalBroadcastManager lbm;
	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LoginManager.redirectToLogin(BackupKeyActivity.this);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Helpers.setLocale(this);
		setContentView(R.layout.activity_backup_key);

		Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));

		findViewById(R.id.getPhrase).setOnClickListener(getPhraseClickListener());
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
	protected void onDestroy() {
		super.onDestroy();
		lbm.unregisterReceiver(onLogout);
	}

	@Override
	public boolean onSupportNavigateUp() {
		finish();
		return true;
	}

	private View.OnClickListener getPhraseClickListener(){
		return v -> {
			LoginManager.LoginConfig loginConfig = new LoginManager.LoginConfig() {{
				showCancel = true;
				showLogout = false;
				quitActivityOnCancel = false;
				cancellable = true;
				okButtonText = getString(R.string.ok);
				titleText = getString(R.string.please_enter_password);
				subTitleText = getString(R.string.password_for_backup);
			}};

			LoginManager.showEnterPasswordToUnlock(this, loginConfig, new LoginManager.UserLogedinCallback() {
				@Override
				public void onUserAuthSuccess() {
					MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(BackupKeyActivity.this);
					builder.setView(R.layout.dialog_backup_phrase);
					builder.setCancelable(true);
					AlertDialog dialog = builder.create();
					dialog.show();

					Button okButton = dialog.findViewById(R.id.okButton);
					TextView keyText = dialog.findViewById(R.id.keyText);

					okButton.setOnClickListener(v -> dialog.dismiss());

					try {
						byte[] privateKey = StinglePhotosApplication.getKey();

						String mnemonicKey = MnemonicUtils.generateMnemonic(BackupKeyActivity.this, privateKey);

						keyText.setText(mnemonicKey);

						Helpers.storePreference(BackupKeyActivity.this, "is_backup_phrase_seen", true);

					} catch (IllegalArgumentException | IOException ignored) {
					}
				}
			});
		};
	}
}
