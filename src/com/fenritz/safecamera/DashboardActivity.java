package com.fenritz.safecamera;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

import com.fenritz.safecamera.util.Helpers;
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
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
		
		if(Helpers.isDemo(DashboardActivity.this)){
			// Create the adView
		    adView = new AdView(this, AdSize.BANNER, getString(R.string.ad_publisher_id));
		    LinearLayout layout = (LinearLayout)findViewById(R.id.adHolder);
		    layout.addView(adView);
		    adView.loadAd(new AdRequest());
		}
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
			case R.id.logout:
				Helpers.logout(DashboardActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
