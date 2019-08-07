package com.fenritz.safecam;

import android.content.Intent;
import android.os.Bundle;

import com.fenritz.safecam.Auth.LoginManager;
import com.fenritz.safecam.Sync.SyncManager;
import com.fenritz.safecam.Sync.SyncService;
import com.fenritz.safecam.ViewItem.ViewPagerAdapter;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.fenritz.safecam.R;

public class ViewItemActivity extends AppCompatActivity {

	protected int itemPosition = 0;
	protected int folder = SyncManager.FOLDER_MAIN;
	protected int currentPosition = 0;
	protected ViewPager viewPager;
	protected ViewPagerAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_view_item);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setTitle("");

		toolbar.setNavigationOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View view){
				finish();
			}
		});



		Intent intent = getIntent();
		itemPosition = intent.getIntExtra("EXTRA_ITEM_POSITION", 0);
		folder = intent.getIntExtra("EXTRA_ITEM_FOLDER", 0);
		currentPosition = itemPosition;
		viewPager = (ViewPager)findViewById(R.id.viewPager);

		viewPager.addOnPageChangeListener(getOnPageChangeListener());
	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserLoginSuccess() {
				if(adapter != null){
					adapter.releasePlayers();
				}
				adapter = new ViewPagerAdapter(ViewItemActivity.this, folder);
				viewPager.setAdapter(adapter);
				viewPager.setCurrentItem(itemPosition);
				Log.d("initialPos", String.valueOf(itemPosition));
			}

			@Override
			public void onUserLoginFail() {

			}
		});
		LoginManager.disableLockTimer(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		viewPager.setAdapter(null);
		if(adapter != null){
			adapter.releasePlayers();
			adapter = null;
		}
		LoginManager.setLockedTime(this);
	}

	protected ViewPager.OnPageChangeListener getOnPageChangeListener(){
		return new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
			}

			@Override
			public void onPageSelected(int position) {
				Log.d("onSelected", String.valueOf(position));
				if(adapter != null){
					adapter.setCurrentPosition(position);
					adapter.pauseAllPlayers();
					//adapter.startPlaying(position);
					Log.d("Play", String.valueOf(position));
				}
			}

			@Override
			public void onPageScrollStateChanged(int state) {

			}
		};
	}
}
