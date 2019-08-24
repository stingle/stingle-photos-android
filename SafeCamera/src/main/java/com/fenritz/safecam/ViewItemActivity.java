package com.fenritz.safecam;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.fenritz.safecam.Auth.LoginManager;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Files.ShareManager;
import com.fenritz.safecam.Sync.SyncManager;
import com.fenritz.safecam.Sync.SyncService;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.ViewItem.ViewPagerAdapter;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.fenritz.safecam.R;

import java.util.ArrayList;
import java.util.List;

public class ViewItemActivity extends AppCompatActivity {

	protected int itemPosition = 0;
	protected int folder = SyncManager.FOLDER_MAIN;
	protected int currentPosition = 0;
	protected ViewPager viewPager;
	protected ViewPagerAdapter adapter;
	protected GestureDetector gestureDetector;
	protected View.OnTouchListener gestureTouchListener;
	private BroadcastReceiver receiver;

	private static final int SWIPE_MIN_DISTANCE = 100;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;

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
		gestureDetector = new GestureDetector(this, new SwipeGestureDetector());
		gestureTouchListener = new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return gestureDetector.onTouchEvent(event);
			}
		};

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
	}

	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				if(adapter != null){
					adapter.releasePlayers();
				}
				adapter = new ViewPagerAdapter(ViewItemActivity.this, folder, gestureTouchListener);
				viewPager.setAdapter(adapter);
				viewPager.setCurrentItem(itemPosition);
			}

			@Override
			public void onUserAuthFail() {

			}

			@Override
			public void onNotLoggedIn() {

			}

			@Override
			public void onLoggedIn() {

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

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(receiver != null){
			unregisterReceiver(receiver);
		}
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


	class SwipeGestureDetector extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			try {
				if (Math.abs(e1.getX() - e2.getX()) > SWIPE_MAX_OFF_PATH){
					return false;
				}

				if(e1.getY() - e2.getY() > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {

				}
				else if (e2.getY() - e1.getY() > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
					ViewItemActivity.this.finish();
				}
			} catch (Exception e) { }
			return false;
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.view_item_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int id = item.getItemId();
		StingleDbHelper db = new StingleDbHelper(this, (folder == SyncManager.FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));

		if (id == R.id.share) {
			ArrayList<StingleDbFile> files = new ArrayList<StingleDbFile>();
			files.add(db.getFileAtPosition(adapter.getCurrentPosition()));

			ShareManager.shareDbFiles(this, files, folder);

		}
		else if (id == R.id.trash) {
			final ProgressDialog spinner = Helpers.showProgressDialog(this, getString(R.string.trashing_files), null);
			ArrayList<String> filenames = new ArrayList<String>();
			filenames.add(db.getFileAtPosition(adapter.getCurrentPosition()).filename);


			new SyncManager.MoveToTrashAsyncTask(this, filenames, new SyncManager.OnFinish(){
				@Override
				public void onFinish(Boolean needToUpdateUI) {
					if(adapter != null) {
						adapter.notifyDataSetChanged();
					}
					spinner.dismiss();
					Snackbar mySnackbar = Snackbar.make(findViewById(R.id.view_item_layout), getString(R.string.moved_to_trash), Snackbar.LENGTH_SHORT);
					mySnackbar.show();
				}
			}).execute();
		}


		return super.onOptionsItemSelected(item);
	}
}
