package org.stingle.photos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.SetSKHashAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.MnemonicUtils;

import java.io.IOException;

public class BackupKeyActivity extends AppCompatActivity {

	private LocalBroadcastManager lbm;
	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LoginManager.redirectToLogin(BackupKeyActivity.this);
		}
	};

	private TextView keyText;
	private TextView backupDesc;
	private SharedPreferences preferences;

	public static final String IS_SET_SK_HASH = "isSKHashSet";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_backup_key);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setDisplayShowHomeEnabled(true);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));

		keyText = findViewById(R.id.keyText);
		backupDesc = findViewById(R.id.backupDesc);

		preferences = getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, MODE_PRIVATE);
	}

	@Override
	protected void onResume() {
		super.onResume();

		keyText.setText("");
		backupDesc.setText("");

		LoginManager.checkLogin(this);
		LoginManager.disableLockTimer(this);

		LoginManager.showEnterPasswordToUnlock(this, new LoginManager.LoginConfig(){{showLogout=false;showCancel=true;}}, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				showMnemonicKey();
			}
		});

		if(!preferences.contains(IS_SET_SK_HASH)){
			(new SetSKHashAsyncTask(this, new OnAsyncTaskFinish() {
				@Override
				public void onFinish(Boolean result) {
					super.onFinish(result);

					if(result) {
						preferences.edit().putBoolean(IS_SET_SK_HASH, true).apply();
					}
					else{
						BackupKeyActivity.this.finish();
					}
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
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

	private void showMnemonicKey(){

		backupDesc.setText(getString(R.string.backup_key_desc));

		try {
			byte[] privateKey = StinglePhotosApplication.getKey();

			String mnenmonicKey = MnemonicUtils.generateMnemonic(this, privateKey);

			Log.e("privateKey", Crypto.byteArrayToBase64(privateKey));
			Log.e("mnenmonicKey", mnenmonicKey);
			keyText.setText(mnenmonicKey);

			/*byte[] privateKey2 = MnemonicUtils.generateKey(this, mnenmonicKey);
			boolean valid = MnemonicUtils.validateMnemonic(this, mnenmonicKey);
			Log.e("isValid", (valid ? "yes" : "no"));
			Log.e("privateKey2", Crypto.byteArrayToBase64(privateKey2));*/

		} catch (IllegalArgumentException | IOException ignored){ }

	}
}
