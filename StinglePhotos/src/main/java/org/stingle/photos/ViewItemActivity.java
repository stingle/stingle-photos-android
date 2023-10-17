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
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.Gallery.MoveFileAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.SetAlbumCoverAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.ViewItem.ViewItemAsyncTask;
import org.stingle.photos.ViewItem.ViewPagerAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class ViewItemActivity extends AppCompatActivity {

	protected int itemPosition = 0;
	protected int set = SyncManager.GALLERY;
	protected String albumId = null;
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
	private View.OnClickListener onSingleClickListener;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

		Helpers.blockScreenshotsIfEnabled(this);

		lbm = LocalBroadcastManager.getInstance(this);

		super.onCreate(savedInstanceState);
		Helpers.setLocale(this);
		setContentView(R.layout.activity_view_item);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setTitle("");

		toolbar.setNavigationOnClickListener(view -> finish());

		Intent intent = getIntent();
		itemPosition = intent.getIntExtra("EXTRA_ITEM_POSITION", 0);
		set = intent.getIntExtra("EXTRA_ITEM_SET", 0);
		albumId = intent.getStringExtra("EXTRA_ITEM_ALBUM_ID");
		currentPosition = itemPosition;
		viewPager = (ViewPager)findViewById(R.id.viewPager);

		viewPager.addOnPageChangeListener(getOnPageChangeListener());
		gestureDetector = new GestureDetector(this, new SwipeGestureDetector());
		gestureTouchListener = (v, event) -> gestureDetector.onTouchEvent(event);
		onSingleClickListener = v -> toggleActionBar();

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
				adapter = new ViewPagerAdapter(ViewItemActivity.this, set, albumId, onSingleClickListener, gestureTouchListener);
				viewPager.setAdapter(adapter);
				viewPager.setCurrentItem(itemPosition);

				if(set == SyncManager.ALBUM) {
					AlbumsDb albumsDb = new AlbumsDb(ViewItemActivity.this);
					StingleDbAlbum album = albumsDb.getAlbumById(albumId);
					albumsDb.close();

					if (album != null && album.permissionsObj != null && !album.isOwner) {
						if (!album.permissionsObj.allowCopy) {
							ViewItemActivity.this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
						}
					}
				}
			}
		});
	}

	public void updateCurrentItem(){
		if(adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		viewPager.setAdapter(null);
		ViewItemAsyncTask.removeRemainingGetOriginalTasks();
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

	private void toggleActionBar(){
		ActionBar actionBar = getSupportActionBar();
		if(actionBar.isShowing()){
			actionBar.hide();
		}
		else{
			actionBar.show();
		}
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


		if(set == SyncManager.ALBUM && albumId != null) {
			StingleDbAlbum album = GalleryHelpers.getAlbum(this, albumId);
			if(album != null) {
				if(album.isOwner){
					menu.findItem(R.id.set_as_album_cover).setVisible(true);
				}
				else if(album.permissionsObj != null && album.isShared && !album.isOwner){
					if (!album.permissionsObj.allowShare) {
						menu.findItem(R.id.share).setVisible(false);
					}
					if (!album.permissionsObj.allowCopy) {
						menu.findItem(R.id.decrypt).setVisible(false);
					}

					menu.findItem(R.id.trash).setVisible(false);
				}
			}
		}
		else if (set == SyncManager.TRASH){
			menu.findItem(R.id.share).setVisible(false);
			menu.findItem(R.id.decrypt).setVisible(false);
			menu.findItem(R.id.add_to_album).setVisible(false);
			menu.findItem(R.id.trash).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int id = item.getItemId();
		FilesDb db;
		int sort = StingleDb.SORT_DESC;
		if(set == SyncManager.GALLERY || set == SyncManager.TRASH) {
			db = new GalleryTrashDb(this, set);
		}
		else if (set == SyncManager.ALBUM){
			db = new AlbumFilesDb(this);
			//sort = StingleDb.SORT_ASC;
		}
		else{
			return false;
		}

		if(adapter == null){
			return false;
		}

		ArrayList<StingleDbFile> files = new ArrayList<>();
		StingleDbFile currentFile = db.getFileAtPosition(adapter.getCurrentPosition(), albumId, sort);
		files.add(currentFile);

		if (id == R.id.share_stingle) {
			GalleryActions.shareStingle(this, set, albumId, null, files, false, null);
		}
		else if (id == R.id.share_to_apps) {
			ShareManager.shareDbFiles(this, files, set, albumId);
		}
		else if(id == R.id.add_to_album) {
			GalleryActions.addToAlbumSelected(this, files, albumId,set == SyncManager.ALBUM);
		}
		else if (id == R.id.decrypt) {
			GalleryActions.decryptSelected(this, files, set, albumId, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.success_decrypted), Snackbar.LENGTH_SHORT).show();
				}
			});
		}
		else if (id == R.id.trash) {
			final ProgressDialog spinner = Helpers.showProgressDialog(this, getString(R.string.trashing_files), null);
			SyncManager.stopSync(this);
			MoveFileAsyncTask moveTask = new MoveFileAsyncTask(this, files, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					if(adapter != null) {
						adapter.notifyDataSetChanged();
					}
					spinner.dismiss();
					Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.moved_to_trash), Snackbar.LENGTH_SHORT).show();
					SyncManager.startSync(ViewItemActivity.this);
				}

				@Override
				public void onFail() {
					super.onFail();
					if(adapter != null) {
						adapter.notifyDataSetChanged();
					}
					spinner.dismiss();
					Snackbar.make(findViewById(R.id.drawer_layout), getString(androidx.biometric.R.string.default_error_msg), Snackbar.LENGTH_SHORT).show();
					SyncManager.startSync(ViewItemActivity.this);
				}
			});
			moveTask.setFromSet(set);
			moveTask.setFromAlbumId(albumId);
			moveTask.setToSet(SyncManager.TRASH);
			moveTask.setIsMoving(true);
			moveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		else if (id == R.id.set_as_album_cover) {
			if(albumId != null) {
				GalleryActions.setAsAlbumCover(this, albumId, SetAlbumCoverAsyncTask.ALBUM_COVER_FILE, currentFile.filename);
			}
		}
		else if (id == R.id.download) {
			GalleryActions.downloadSelected(this, files, set, albumId);
		}
		else if (id == R.id.file_info) {
			showFileInfoDialog();
		}


		return super.onOptionsItemSelected(item);
	}

	private void showFileInfoDialog(){
		FilesDb db;
		int sort = StingleDb.SORT_DESC;
		if(set == SyncManager.GALLERY || set == SyncManager.TRASH) {
			db = new GalleryTrashDb(this, set);
		}
		else if (set == SyncManager.ALBUM){
			db = new AlbumFilesDb(this);
			//sort = StingleDb.SORT_ASC;
		}
		else{
			return;
		}

		StingleDbFile currentFile = db.getFileAtPosition(adapter.getCurrentPosition(), albumId, sort);
		if(currentFile == null){
			return;
		}
		Crypto.Header fileHeader;
		try {
			fileHeader = CryptoHelpers.decryptFileHeaders(this, set, albumId, currentFile.headers, false);
		} catch (IOException | CryptoException e) {
			return;
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
		builder.setView(R.layout.dialog_file_info);
		builder.setCancelable(true);
		AlertDialog infoDialog = builder.create();
		infoDialog.show();

		Button okButton = infoDialog.findViewById(R.id.okButton);
		Objects.requireNonNull(okButton).setOnClickListener(v -> {
			infoDialog.dismiss();
		});

		((TextView)infoDialog.findViewById(R.id.info_enc_name)).setText(currentFile.filename);
		((TextView)infoDialog.findViewById(R.id.info_name)).setText(fileHeader.filename);


		double mbSize = Math.round((fileHeader.dataSize / 1024.0 / 1024.0) * 100.0)/100.0;
		((TextView)infoDialog.findViewById(R.id.info_size)).setText(Helpers.formatSpaceUnits(mbSize));


		((TextView)infoDialog.findViewById(R.id.info_create)).setText(Helpers.getDateFromTimestamp(currentFile.dateCreated));
		((TextView)infoDialog.findViewById(R.id.info_mod)).setText(Helpers.getDateFromTimestamp(currentFile.dateModified));
	}


}
