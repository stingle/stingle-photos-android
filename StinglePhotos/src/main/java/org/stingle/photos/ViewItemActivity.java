package org.stingle.photos;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.Gallery.FileMoveAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.FilesTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.ViewItem.ViewPagerAdapter;

import java.util.ArrayList;
import java.util.Objects;

public class ViewItemActivity extends AppCompatActivity {

	protected int itemPosition = 0;
	protected int folder = SyncManager.FOLDER_MAIN;
	protected String folderId = null;
	protected int currentPosition = 0;
	protected ViewPager viewPager;
	protected ViewPagerAdapter adapter;
	protected GestureDetector gestureDetector;
	protected View.OnTouchListener gestureTouchListener;
	private LocalBroadcastManager lbm;
	private boolean waitingForCameraToClose = false;

	private static final int SWIPE_MIN_DISTANCE = 100;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

		Helpers.blockScreenshotsIfEnabled(this);

		lbm = LocalBroadcastManager.getInstance(this);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_view_item);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
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
		folderId = intent.getStringExtra("EXTRA_ITEM_FOLDER_ID");
		currentPosition = itemPosition;
		viewPager = (ViewPager)findViewById(R.id.viewPager);

		viewPager.addOnPageChangeListener(getOnPageChangeListener());
		gestureDetector = new GestureDetector(this, new SwipeGestureDetector());
		gestureTouchListener = new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return gestureDetector.onTouchEvent(event);
			}
		};

		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));
		lbm.registerReceiver(onEncFinish, new IntentFilter("MEDIA_ENC_FINISH"));
		lbm.registerReceiver(onCameraClosed, new IntentFilter("CAMERA_CLOSED"));
	}

	@Override
	protected void onResume() {
		super.onResume();
		Intent incommingIntent = getIntent();
		if(!incommingIntent.hasExtra("WAIT_FOR_CAMERA_TO_CLOSE")) {
			init();
		}
		else{
			waitingForCameraToClose = true;
		}
		LoginManager.disableLockTimer(this);
	}

	private void init(){
		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				if (adapter != null) {
					adapter.releasePlayers();
				}
				adapter = new ViewPagerAdapter(ViewItemActivity.this, folder, folderId, gestureTouchListener);
				viewPager.setAdapter(adapter);
				viewPager.setCurrentItem(itemPosition);
			}
		});
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
		lbm.unregisterReceiver(onLogout);
		lbm.unregisterReceiver(onEncFinish);
		lbm.unregisterReceiver(onCameraClosed);
	}

	private BroadcastReceiver onEncFinish = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(adapter != null) {
				adapter.notifyDataSetChanged();
				viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
			}
		}
	};
	private BroadcastReceiver onCameraClosed = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(waitingForCameraToClose){
				init();;
			}
		}
	};
	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			finish();
		}
	};

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
		FilesDb db;
		int sort = StingleDb.SORT_DESC;
		if(folder == SyncManager.FOLDER_MAIN || folder == SyncManager.FOLDER_TRASH) {
			db = new FilesTrashDb(this, (folder == SyncManager.FOLDER_TRASH ? StingleDbContract.Columns.TABLE_NAME_TRASH : StingleDbContract.Columns.TABLE_NAME_FILES));
		}
		else if (folder == SyncManager.FOLDER_ALBUM){
			db = new AlbumFilesDb(this);
			sort = StingleDb.SORT_ASC;
		}
		else{
			return false;
		}

		if (id == R.id.share) {
			ArrayList<StingleDbFile> files = new ArrayList<>();
			files.add(db.getFileAtPosition(adapter.getCurrentPosition(), folderId, sort));

			ShareManager.shareDbFiles(this, files, folder, folderId);

		}
		else if (id == R.id.trash) {
			final ProgressDialog spinner = Helpers.showProgressDialog(this, getString(R.string.trashing_files), null);
			ArrayList<StingleDbFile> files = new ArrayList<>();
			files.add(db.getFileAtPosition(adapter.getCurrentPosition(), folderId, sort));

			FileMoveAsyncTask moveTask = new FileMoveAsyncTask(this, files, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					if(adapter != null) {
						adapter.notifyDataSetChanged();
					}
					spinner.dismiss();
					Snackbar mySnackbar = Snackbar.make(findViewById(R.id.view_item_layout), getString(R.string.moved_to_trash), Snackbar.LENGTH_SHORT);
					mySnackbar.show();
				}

				@Override
				public void onFail() {
					super.onFail();
					if(adapter != null) {
						adapter.notifyDataSetChanged();
					}
					spinner.dismiss();
					Snackbar mySnackbar = Snackbar.make(findViewById(R.id.view_item_layout), getString(R.string.default_error_msg), Snackbar.LENGTH_SHORT);
					mySnackbar.show();
				}
			});
			moveTask.setFromFolder(folder);
			moveTask.setFromFolderId(folderId);
			moveTask.setToFolder(SyncManager.FOLDER_TRASH);
			moveTask.setIsMoving(true);
			moveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}


		return super.onOptionsItemSelected(item);
	}
}
