package com.fenritz.safecam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fenritz.safecam.util.Helpers;
import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class DashboardActivity extends Activity {
	
	private BroadcastReceiver receiver;
	private AdView adView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);
		
		Helpers.fixBackgroundRepeat(findViewById(R.id.parentLayout));
		
		findViewById(R.id.gotoCamera).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, CameraActivity.class);
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
		
		findViewById(R.id.gotoChangePass).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, ChangePasswordActivity.class);
				startActivity(intent);
			}
		});
		
		findViewById(R.id.gotoSettings).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(DashboardActivity.this, SettingsActivity.class);
				startActivity(intent);
			}
		});
		
		findViewById(R.id.logOut).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Helpers.logout(DashboardActivity.this);
			}
		});
		
		findViewById(R.id.goPro).setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse("market://details?id=" + getString(R.string.key_package_name)));
				startActivity(intent);
			}
		});
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
		
		TextView freeVersionText = (TextView)findViewById(R.id.free_version_text);
		
		if(Helpers.isDemo(DashboardActivity.this)){
			// Create the adView
		    adView = new AdView(this, AdSize.BANNER, getString(R.string.ad_publisher_id));
		    LinearLayout layout = (LinearLayout)findViewById(R.id.adHolder);
		    layout.addView(adView);
		    adView.loadAd(new AdRequest());
		    
		    findViewById(R.id.goProContainer).setVisibility(View.VISIBLE);
		    if(freeVersionText != null){
		    	freeVersionText.setVisibility(View.VISIBLE);
		    }
		}
		else{
			findViewById(R.id.goProContainer).setVisibility(View.GONE);
			if(freeVersionText != null){
				freeVersionText.setVisibility(View.GONE);
			}
		}
		
		boolean showPopup = getIntent().getBooleanExtra("showPopup", false);
		if(showPopup){
			showPopup();
		}
	}
	
	private void showPopup(){
		final SharedPreferences preferences = getSharedPreferences(SafeCameraActivity.DEFAULT_PREFS, MODE_PRIVATE);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(DashboardActivity.this);
		builder.setTitle(getString(R.string.popup_title));
		builder.setMessage(getString(R.string.popup_text));
		builder.setPositiveButton(getString(R.string.review), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse("market://details?id=" + getString(R.string.main_package_name)));
				startActivity(intent);
				preferences.edit().putBoolean(SafeCameraActivity.DONT_SHOW_POPUP, true).commit();
			}
		});
		builder.setNegativeButton(getString(R.string.later), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				int latersCount = preferences.getInt(SafeCameraActivity.POPUP_LATERS_COUNT, 0);
				latersCount++;
				
				if(latersCount >= Integer.valueOf(getString(R.string.popup_laters_limit))){
					preferences.edit().putBoolean(SafeCameraActivity.DONT_SHOW_POPUP, true).commit();
				}
				preferences.edit().putInt(SafeCameraActivity.POPUP_LATERS_COUNT, latersCount).commit();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Helpers.setLockedTime(this);
	}
	
	@Override
	protected void onDestroy() {
		if (adView != null){
			adView.destroy();
		}
		unregisterReceiver(receiver);
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.dashborad_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		switch (item.getItemId()) {
			case R.id.settings:
				intent.setClass(DashboardActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.change_password:
				intent.setClass(DashboardActivity.this, ChangePasswordActivity.class);
				startActivity(intent);
				return true;
			case R.id.read_security:
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.security_page_link)));
				startActivity(browserIntent);
				return true;
			case R.id.logout:
				Helpers.logout(DashboardActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
