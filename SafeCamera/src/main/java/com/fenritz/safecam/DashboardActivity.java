package com.fenritz.safecam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.LoginManager;

public class DashboardActivity extends Activity {
	
	private BroadcastReceiver receiver;
	public static final int REQUEST_SD_CARD_PERMISSION = 1;
	public static final int REQUEST_CAMERA_PERMISSION = 2;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//requestWindowFeature(Window.FEATURE_NO_TITLE);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.dashboard);
		
		initViews();
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
		
		boolean showPopup = getIntent().getBooleanExtra("showPopup", false);
		if(showPopup){
			showPopup();
		}

		if(Helpers.requestSDCardPermission(this)){
			filesystemInit();
		}
	}

	public void filesystemInit(){
		Helpers.createFolders(this);

		Helpers.deleteTmpDir(this);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case REQUEST_SD_CARD_PERMISSION: {
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					filesystemInit();

				} else {
					finish();
				}
				return;
			}
		}
	}
	
	private void initViews(){
		findViewById(R.id.gotoCamera).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, Camera2ActivityRaw.class);
				startActivity(intent);
			}
		});
		
		findViewById(R.id.gotoGallery).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, GalleryActivity.class);
				startActivity(intent);
			}
		});
	}
	

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
			setContentView(R.layout.dashboard);
		}
		else if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE){
			setContentView(R.layout.dashboard_land);
		}
		initViews();
	}

	private void showPopup(){
		final SharedPreferences preferences = getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, MODE_PRIVATE);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(DashboardActivity.this);
		builder.setTitle(getString(R.string.popup_title));
		builder.setMessage(getString(R.string.popup_text));
		builder.setPositiveButton(getString(R.string.review), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse("market://details?id=" + getString(R.string.main_package_name)));
				startActivity(intent);
				preferences.edit().putBoolean(LoginManager.DONT_SHOW_POPUP, true).commit();
			}
		});
		builder.setNegativeButton(getString(R.string.later), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				int latersCount = preferences.getInt(LoginManager.POPUP_LATERS_COUNT, 0);
				latersCount++;
				
				if(latersCount >= Integer.valueOf(getString(R.string.popup_laters_limit))){
					preferences.edit().putBoolean(LoginManager.DONT_SHOW_POPUP, true).commit();
				}
				preferences.edit().putInt(LoginManager.POPUP_LATERS_COUNT, latersCount).commit();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.disableLockTimer(this);

        Helpers.checkIsMainFolderWritable(this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();

		LoginManager.setLockedTime(this);
	}
	
	@Override
	protected void onDestroy() {
		if(receiver != null){
			unregisterReceiver(receiver);
		}
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.dashborad_menu, menu);
        return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent;
		switch (item.getItemId()) {
			case R.id.settings:
				intent = new Intent();
				intent.setClass(DashboardActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.change_password:
				intent = new Intent();
				intent.setClass(DashboardActivity.this, ChangePasswordActivity.class);
				startActivity(intent);
				return true;
			case R.id.read_security:
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.security_page_link)));
				startActivity(browserIntent);
				return true;
			case R.id.logout:
				LoginManager.logout(DashboardActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
