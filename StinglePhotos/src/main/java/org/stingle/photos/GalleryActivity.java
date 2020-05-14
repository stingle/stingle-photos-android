package org.stingle.photos;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.GravityCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.GetServerPKAsyncTask;
import org.stingle.photos.AsyncTasks.ImportFilesAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Albums.AlbumsFragment;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.Gallery.Gallery.GalleryFragment;
import org.stingle.photos.Gallery.Gallery.GalleryFragmentParent;
import org.stingle.photos.Gallery.Gallery.SyncBarHandler;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.SyncService;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, GalleryFragmentParent {

	public static final int REQUEST_VIEW_PHOTO = 1;

	public static final int FRAGMENT_GALLERY = 1;
	public static final int FRAGMENT_ALBUMS_LIST = 2;
	public static final int FRAGMENT_ALBUM = 3;
	public static final int FRAGMENT_SHARES = 4;
	public static final int FRAGMENT_SHARED_ALBUM = 5;

	private LocalBroadcastManager lbm;
	protected GalleryFragment galleryFragment;
	protected AlbumsFragment albumsFragment;
	protected ActionMode actionMode;
	protected MaterialToolbar toolbar;
	protected int currentSet = SyncManager.GALLERY;
	private int currentFragment = FRAGMENT_GALLERY;
	private Integer currentAlbumsView = AlbumsFragment.VIEW_ALBUMS;
	private String currentAlbumId = null;
	private String currentAlbumName = "";

	protected Messenger mService = null;
	protected boolean isBound = false;
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	private boolean sendBackDecryptedFile = false;
	private Intent originalIntent = null;

	protected int INTENT_IMPORT = 1;

	private boolean dontStartSyncYet = false;
	private View headerView;

	public int albumsLastScrollPos = 0;
	public int sharingLastScrollPos = 0;


	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LoginManager.redirectToLogin(GalleryActivity.this);
		}
	};
	private BroadcastReceiver onEncFinish = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(galleryFragment != null) {
				galleryFragment.updateDataSet();
			}
		}
	};
	private boolean isImporting = false;
	private boolean isSyncBarDisabled = false;
	private SharedPreferences sharedPreferences;
	private boolean isSyncEnabled;
	private SyncBarHandler syncBarHandler;
	private FloatingActionButton fab;
	private BottomNavigationView bottomNavigationView;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_gallery);

		Helpers.blockScreenshotsIfEnabled(this);

		lbm = LocalBroadcastManager.getInstance(this);

		toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(getString(R.string.title_gallery_for_app));
		setSupportActionBar(toolbar);
		fab = findViewById(R.id.import_fab);
		fab.setOnClickListener(getImportOnClickListener());
		bottomNavigationView = findViewById(R.id.bottom_navigation);
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		NavigationView navigationView = findViewById(R.id.nav_view);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();
		navigationView.setNavigationItemSelectedListener(this);

		final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
		pullToRefresh.setOnRefreshListener(() -> {
			sendMessageToSyncService(SyncService.MSG_START_SYNC);
			pullToRefresh.setRefreshing(false);
		});


		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));
		lbm.registerReceiver(onEncFinish, new IntentFilter("MEDIA_ENC_FINISH"));

		handleIncomingIntent(getIntent());

		headerView = navigationView.getHeaderView(0);
		((TextView)headerView.findViewById(R.id.userEmail)).setText(Helpers.getPreference(this, StinglePhotosApplication.USER_EMAIL, ""));

		setupBottomNavigationView();
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		syncBarHandler = new SyncBarHandler(this);

		if(savedInstanceState != null){
			if(savedInstanceState.containsKey("set")) {
				currentSet = savedInstanceState.getInt("set");
			}
			if(savedInstanceState.containsKey("albumId")) {
				currentAlbumId = savedInstanceState.getString("albumId");
			}
		}

		Intent intent = getIntent();
		if(intent.hasExtra("set")){
			currentSet = intent.getIntExtra("set", currentSet);
		}
		if(intent.hasExtra("albumId")){
			currentAlbumId = intent.getStringExtra("albumId");
		}
		if(intent.hasExtra("view")){
			currentAlbumsView = intent.getIntExtra("view", currentAlbumsView);
		}

		updateBottomNavigationMenu();
	}

	@Override
	protected void onStart() {
		super.onStart();

	}

	@Override
	protected void onStop() {
		super.onStop();
		galleryFragment = null;
		albumsFragment = null;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		FileManager.deleteTempFiles(this);
		lbm.unregisterReceiver(onLogout);
	}

	@Override
	protected void onResume() {
		super.onResume();

		isSyncEnabled = sharedPreferences.getBoolean(SyncManager.PREF_BACKUP_ENABLED, true);
		if(!isSyncEnabled){
			disableSyncBar();
		}
		else if (isSyncBarDisabled() && currentSet == SyncManager.GALLERY){
			enableSyncBar();
		}

		if(!isImporting){
			SyncManager.startPeriodicWork(this);
			checkLoginAndInit();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();


		LoginManager.setLockedTime(this);

		if (isBound) {
			unbindService(mConnection);
			isBound = false;
		}
		findViewById(R.id.contentHolder).setVisibility(View.INVISIBLE);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt("set", currentSet);
		outState.putString("albumId", currentAlbumId);
	}

	public void showMainGallery(){
		currentFragment = FRAGMENT_GALLERY;
		currentSet = SyncManager.GALLERY;
		currentAlbumId = null;
		toolbar.setTitle(getString(R.string.title_gallery_for_app));
		enableSyncBar();
		initCurrentFragment();
		currentAlbumName = "";
	}
	public void showTrash(){
		currentFragment = FRAGMENT_GALLERY;
		currentSet = SyncManager.TRASH;
		currentAlbumId = null;
		toolbar.setTitle(getString(R.string.title_trash));
		disableSyncBar();
		currentAlbumName = "";
		initCurrentFragment();
	}

	public void showAlbumsList(){
		showAlbumsList(currentAlbumsView);
	}

	public void showAlbumsList(Integer view){
		currentFragment = FRAGMENT_ALBUMS_LIST;
		currentSet = -1;
		currentAlbumId = null;
		currentAlbumsView = view;
		currentAlbumName = "";
		initCurrentFragment();
	}
	public void showAlbum(String albumId){
		currentFragment = FRAGMENT_GALLERY;
		currentSet = SyncManager.ALBUM;
		currentAlbumId = albumId;
		initCurrentFragment();
	}

	public void initCurrentFragment(){
		switch (currentFragment){
			case FRAGMENT_GALLERY:
				initGalleryFragment(currentSet, currentAlbumId, true);
				break;
			case FRAGMENT_ALBUMS_LIST:
				initAlbumsFragment();
				break;
		}
	}

	private void initGalleryFragment(int set, String albumId, boolean initNow){
		exitActionMode();
		currentFragment = FRAGMENT_GALLERY;
		currentSet = set;
		currentAlbumId = albumId;
		FragmentManager fm = getSupportFragmentManager();

		Bundle bundle = new Bundle();
		bundle.putInt("set", set);
		bundle.putString("albumId", albumId);
		bundle.putBoolean("initNow", initNow);

		GalleryFragment newGalleryFragment = new GalleryFragment();
		newGalleryFragment.setArguments(bundle);


		Fragment fragment = fm.findFragmentById(R.id.galleryContainer);
		FragmentTransaction ft = fm.beginTransaction();
		if (fragment==null) {
			galleryFragment = newGalleryFragment;
			ft.add(R.id.galleryContainer, galleryFragment);
			ft.commit();
		}
		else {
			int fragmentCurrentSet = fragment.getArguments().getInt("set");
			String fragmentAlbumId = fragment.getArguments().getString("albumId");
			boolean albumIdsMatch = false;
			if(fragmentAlbumId == null){
				if(fragmentAlbumId == albumId) {
					albumIdsMatch = true;
				}
			}
			else if (fragmentAlbumId.equals(albumId)){
				albumIdsMatch = true;
			}

			if(galleryFragment != null && fragment.getClass() != galleryFragment.getClass() ||
					fragmentCurrentSet != set ||
					!albumIdsMatch ||
					!(fragment instanceof GalleryFragment)
			) {
				galleryFragment = newGalleryFragment;
				ft.replace(R.id.galleryContainer, galleryFragment);
				ft.commit();
			}
			else{
				galleryFragment = (GalleryFragment)fragment;
				((GalleryFragment)fragment).init();
			}
			invalidateOptionsMenu();
		}
		fab.setVisibility(View.VISIBLE);

		if(currentSet == SyncManager.ALBUM){
			Log.d("albumId", currentAlbumId);
			StingleDbAlbum album = GalleryHelpers.getAlbum(this, currentAlbumId);
			String albumName = GalleryHelpers.getAlbumName(this, currentAlbumId);
			if(albumName != null && albumName.length() > 0){
				currentAlbumName = albumName;
				toolbar.setTitle(albumName);
				if(album.isOwner) {
					toolbar.setOnClickListener(v -> {
						GalleryActions.renameAlbum(this, currentAlbumId, currentAlbumName, new OnAsyncTaskFinish() {
							@Override
							public void onFinish() {
								super.onFinish();
								initCurrentFragment();
							}
						});
					});
				}
			}
			else{
				toolbar.setTitle(getString(R.string.album));
				currentAlbumName = "";
			}
			disableSyncBar();
		}
	}

	private void initAlbumsFragment(){
		exitActionMode();
		FragmentManager fm = getSupportFragmentManager();

		Bundle bundle = new Bundle();
		bundle.putInt("view", currentAlbumsView);

		albumsFragment = new AlbumsFragment();
		albumsFragment.setArguments(bundle);

		Fragment fragment = fm.findFragmentById(R.id.galleryContainer);
		FragmentTransaction ft = fm.beginTransaction();
		if (fragment==null) {

			ft.add(R.id.galleryContainer, albumsFragment);
		}
		else {
			ft.replace(R.id.galleryContainer, albumsFragment);
		}
		ft.commit();
		fab.setVisibility(View.GONE);
		invalidateOptionsMenu();

		if(currentAlbumsView == AlbumsFragment.VIEW_ALBUMS) {
			toolbar.setTitle(getString(R.string.albums));
			bottomNavigationView.getMenu().getItem(1).setChecked(true);
		}
		else if(currentAlbumsView == AlbumsFragment.VIEW_SHARES) {
			toolbar.setTitle(getString(R.string.sharing));
			bottomNavigationView.getMenu().getItem(2).setChecked(true);
		}
		disableSyncBar();
	}

	private void updateBottomNavigationMenu(){
		if(currentSet == SyncManager.GALLERY || currentSet == SyncManager.TRASH){
			bottomNavigationView.getMenu().getItem(0).setChecked(true);
		}
		else if(currentSet == SyncManager.ALBUM) {
			if (currentAlbumsView == AlbumsFragment.VIEW_ALBUMS) {
				bottomNavigationView.getMenu().getItem(1).setChecked(true);
			} else if (currentAlbumsView == AlbumsFragment.VIEW_SHARES) {
				bottomNavigationView.getMenu().getItem(2).setChecked(true);
			}
		}
	}

	public void updateGalleryFragmentData(){
		if(galleryFragment != null) {
			galleryFragment.updateDataSet();
		}
		if(albumsFragment != null) {
			albumsFragment.updateDataSet();
		}
	}
	public void updateGalleryFragmentItem(int position, int set, String albumId){
		if(albumId != null && albumId.length() == 0){
			albumId = null;
		}
		Log.e("finish", currentSet + " - " + set + " ;;;;; " + currentAlbumId + " - " + albumId);
		if(galleryFragment != null && currentSet == set && Helpers.isStringsEqual(currentAlbumId, albumId)) {
			galleryFragment.updateItem(position);
		}
	}

	private void checkLoginAndInit(){
		LoginManager.disableLockTimer(GalleryActivity.this);

		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
					initGallery();
				}
				else{
					FileManager.requestSDCardPermission(GalleryActivity.this);
				}
			}
		});
	}

	private void initGallery(){

		initCurrentFragment();
		if(galleryFragment != null) {
			galleryFragment.updateAutoFit();
		}
		if(albumsFragment != null) {
			albumsFragment.updateAutoFit();
		}
		findViewById(R.id.contentHolder).setVisibility(View.VISIBLE);
		startAndBindService();
		updateQuotaInfo();

		byte[] serverPK = StinglePhotosApplication.getCrypto().getServerPublicKey();
		if(serverPK == null){
			(new GetServerPKAsyncTask(this)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}

	public boolean isSyncBarDisabled(){
		return isSyncBarDisabled;
	}

	public void disableSyncBar(){
		isSyncBarDisabled = true;
		syncBarHandler.hideSyncBar();
	}
	public void enableSyncBar(){
		if(isSyncEnabled) {
			isSyncBarDisabled = false;
			syncBarHandler.showSyncBar();
			syncBarHandler.showSyncBarAnimated();
		}
	}

	private void startAndBindService(){
		Intent serviceIntent = new Intent(GalleryActivity.this, SyncService.class);
		try {
			startService(serviceIntent);
			bindService(serviceIntent, mConnection, BIND_AUTO_CREATE);
		} catch (IllegalStateException ignored) { }
	}

	public class IncomingHandler extends Handler {
		@Override
		public void handleMessage(@NonNull Message msg) {
			syncBarHandler.handleMessage(msg);
		}
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case StinglePhotosApplication.REQUEST_SD_CARD_PERMISSION: {
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					initGallery();
				} else {
					finish();
				}
			}
		}
	}

	private void handleIncomingIntent(Intent intent) {
		String action = intent.getAction();
		String type = intent.getType();

		ArrayList<Uri> urisToImport = new ArrayList<>();

		if (Intent.ACTION_SEND.equals(action) && type != null) {
			Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
			if(fileUri != null){
				urisToImport.add(fileUri);
			}

		} else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
			ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
			if (fileUris != null) {
				urisToImport.addAll(fileUris);
			}
		}
		else if ((Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) && type != null) {
			originalIntent = intent;
			sendBackDecryptedFile = true;
			invalidateOptionsMenu();
		}

		if(urisToImport.size() > 0){
			isImporting = true;
			(new ImportFilesAsyncTask(this, urisToImport, SyncManager.GALLERY, null, new FileManager.OnFinish() {
				@Override
				public void onFinish() {
					if(galleryFragment != null) {
						galleryFragment.updateDataSet();
					}
					SyncManager.startSync(GalleryActivity.this);
					isImporting = false;
					checkLoginAndInit();
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

	}

	public void updateQuotaInfo(){
		int spaceUsed = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_USED, 0);
		int spaceQuota = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_QUOTA, 1);
		int percent = Math.round((spaceUsed * 100) / spaceQuota);


		String quotaText = getString(R.string.quota_text, Helpers.formatSpaceUnits(spaceUsed), Helpers.formatSpaceUnits(spaceQuota), String.valueOf(percent) + "%");
		((TextView)headerView.findViewById(R.id.quotaInfo)).setText(quotaText);
		ContentLoadingProgressBar progressBar = headerView.findViewById(R.id.quotaBar);

		progressBar.setMax(100);
		progressBar.setProgress(percent);

	}

	private void sendMessageToSyncService(int type) {
		if (isBound) {
			if (mService != null) {
				try {
					Message msg = Message.obtain(null, type);
					msg.replyTo = mMessenger;
					mService.send(msg);
				}
				catch (RemoteException ignored) { }
			}
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			isBound = true;
			mService = new Messenger(service);
			sendMessageToSyncService(SyncService.MSG_REGISTER_CLIENT);
			sendMessageToSyncService(SyncService.MSG_GET_SYNC_STATUS);
			if(!dontStartSyncYet){
				sendMessageToSyncService(SyncService.MSG_START_SYNC);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected - process crashed.
			mService = null;
			isBound = false;
		}
	};

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if(galleryFragment != null) {
			galleryFragment.updateAutoFit();
		}
		if(albumsFragment != null){
			albumsFragment.updateAutoFit();
		}
	}

	@Override
	public void onBackPressed() {
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START);
		}
		else if (galleryFragment.isSelectionModeActive()) {
			exitActionMode();
		}
		else if(currentSet == SyncManager.ALBUM){
			showAlbumsList(currentAlbumsView);
		}
		else if(currentSet == SyncManager.TRASH){
			showMainGallery();
		}
		else {
			super.onBackPressed();
		}
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if(!sendBackDecryptedFile) {
			switch (currentSet) {
				case SyncManager.GALLERY:
					getMenuInflater().inflate(R.menu.gallery, menu);
					break;
				case SyncManager.TRASH:
					getMenuInflater().inflate(R.menu.gallery_trash, menu);
					break;
				case SyncManager.ALBUM:
					getMenuInflater().inflate(R.menu.album, menu);
					StingleDbAlbum album = GalleryHelpers.getCurrentAlbum(this);
					if(album != null) {
						if(album.isShared) {
							if(album.isOwner){
								// Owner of shared album
								menu.findItem(R.id.action_album_info).setVisible(false);
								menu.findItem(R.id.action_leave_album).setVisible(false);
							}
							else{
								// Not owner of shared album
								menu.findItem(R.id.action_album_settings).setVisible(false);
								menu.findItem(R.id.action_rename_album).setVisible(false);
								menu.findItem(R.id.action_album_settings).setVisible(false);
								menu.findItem(R.id.action_delete_album).setVisible(false);

								if(album.permissionsObj != null) {
									if (!album.permissionsObj.allowShare) {
										menu.findItem(R.id.share_album).setVisible(false);
									}
									if (!album.permissionsObj.allowAdd) {
										fab.setVisibility(View.GONE);
									}
									else {
										fab.setVisibility(View.VISIBLE);
									}
								}
							}
							menu.findItem(R.id.share_album).setVisible(false);
						}
						else{
							// Album is not shared
							menu.findItem(R.id.action_album_settings).setVisible(false);
						}
					}
					break;
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_lock) {
			LoginManager.lock(this);
			Intent intent = new Intent();
			intent.setClass(this, GalleryActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			finish();
		}
		else if (id == R.id.action_camera) {
			Intent intent = new Intent();
			intent.setClass(this, CameraXActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
		else if (id == R.id.share_album) {
			GalleryActions.shareStingle(this, currentSet, currentAlbumId, currentAlbumName, null, false, null);
		}
		else if (id == R.id.action_album_settings) {
			GalleryActions.albumSettings(this);
		}
		else if (id == R.id.action_album_info) {
			GalleryActions.albumInfo(this);
		}
		else if (id == R.id.action_rename_album) {
			GalleryActions.renameAlbum(this, currentAlbumId, currentAlbumName, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					initCurrentFragment();
					Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.save_success), Snackbar.LENGTH_LONG).show();
				}

				@Override
				public void onFail() {
					super.onFail();
					Snackbar.make(findViewById(R.id.drawer_layout), getString(R.string.something_went_wrong), Snackbar.LENGTH_LONG).show();
				}
			});
		}
		else if (id == R.id.action_empty_trash) {
			GalleryActions.emptyTrash(this);
		}
		else if (id == R.id.action_leave_album) {
			GalleryActions.leaveAlbum(this);
		}
		else if (id == R.id.action_delete_album) {
			GalleryActions.deleteAlbum(this);
		}

		return super.onOptionsItemSelected(item);
	}

	private void setupBottomNavigationView() {
		// Set action to perform when any menu-item is selected.
		bottomNavigationView.setOnNavigationItemSelectedListener(
				item -> {
					item.setChecked(true);

					switch (item.getItemId()) {
						case R.id.action_gallery:
							showMainGallery();
							break;
						case R.id.action_albums:
							showAlbumsList(AlbumsFragment.VIEW_ALBUMS);
							break;
						case R.id.action_sharing:
							showAlbumsList(AlbumsFragment.VIEW_SHARES);
							break;
					}
					return false;
				});
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_gallery) {
			showMainGallery();
			invalidateOptionsMenu();
			bottomNavigationView.getMenu().getItem(0).setChecked(true);
		}
		else if (id == R.id.nav_trash) {
			showTrash();
			invalidateOptionsMenu();
			bottomNavigationView.getMenu().getItem(0).setChecked(true);
		}
		else if (id == R.id.nav_storage) {
			Intent intent = new Intent();
			intent.setClass(this, StorageActivity.class);
			startActivity(intent);
		}
		else if (id == R.id.nav_backup) {
			Intent intent = new Intent();
			intent.setClass(this, BackupKeyActivity.class);
			startActivity(intent);
		}
		else if (id == R.id.nav_settings) {
			Intent intent = new Intent();
			intent.setClass(this, SettingsActivity.class);
			startActivity(intent);
		}
		else if (id == R.id.nav_website) {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://stingle.org"));
			startActivity(intent);
		}
		else if (id == R.id.nav_logout) {
			LoginManager.logout(this);
		}


		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	public boolean onClick(StingleDbFile file) {
		if (!galleryFragment.isSelectionModeActive() && sendBackDecryptedFile){
			ArrayList<StingleDbFile> selectedFiles = new ArrayList<>();
			selectedFiles.add(file);
			ShareManager.sendBackSelection(this, originalIntent, selectedFiles, currentSet);
			return false;
		}

		return true;
	}

	@Override
	public boolean onLongClick(int index) {
		if(galleryFragment != null && !galleryFragment.isSelectionModeActive()){
			actionMode = startSupportActionMode(getActionModeCallback());
		}
		return true;
	}


	@Override
	public boolean onSelectionChanged(int count) {
		if(actionMode != null) {
			if(count == 0){
				actionMode.setTitle("");
			}
			else {
				actionMode.setTitle(String.valueOf(count));
			}
		}
		return true;
	}

	@Override
	public void scrolledDown() {
		if(!isSyncBarDisabled) {
			syncBarHandler.hideSyncBarAnimated();
		}
	}

	@Override
	public void scrolledUp() {
		if(!isSyncBarDisabled) {
			syncBarHandler.showSyncBarAnimated();
		}
	}


	protected ActionMode.Callback getActionModeCallback(){
		return new ActionMode.Callback() {
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				if(!sendBackDecryptedFile) {
					switch (currentSet) {
						case SyncManager.GALLERY:
							mode.getMenuInflater().inflate(R.menu.gallery_action_mode, menu);
							break;
						case SyncManager.TRASH:
							mode.getMenuInflater().inflate(R.menu.gallery_trash_action_mode, menu);
							break;
						case SyncManager.ALBUM:
							mode.getMenuInflater().inflate(R.menu.gallery_album_action_mode, menu);

							StingleDbAlbum album = GalleryHelpers.getCurrentAlbum(GalleryActivity.this);
							if(album != null && album.permissionsObj != null && !album.isOwner && album.isShared) {
								if (!album.permissionsObj.allowShare) {
									menu.findItem(R.id.share).setVisible(false);
								}
								if (!album.permissionsObj.allowCopy) {
									menu.findItem(R.id.decrypt).setVisible(false);
									menu.findItem(R.id.add_to_album).setVisible(false);
								}

								menu.findItem(R.id.trash).setVisible(false);
							}
							break;
					}
				}
				else{
					mode.getMenuInflater().inflate(R.menu.gallery_send_back, menu);
				}

				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return true;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				switch(item.getItemId()){
					case R.id.share_stingle:
						GalleryActions.shareStingle(GalleryActivity.this, currentSet, currentAlbumId, currentAlbumName, galleryFragment.getSelectedFiles(), false, null);
						break;
					case R.id.share_to_apps:
						GalleryActions.shareSelected(GalleryActivity.this, galleryFragment.getSelectedFiles());
						break;
					case R.id.add_to_album:
						GalleryActions.addToAlbumSelected(GalleryActivity.this, galleryFragment.getSelectedFiles(), currentSet == SyncManager.ALBUM);
						break;
					case R.id.decrypt:
						GalleryActions.decryptSelected(GalleryActivity.this, galleryFragment.getSelectedFiles(), currentSet, currentAlbumId, null);
						break;
					case R.id.trash :
						GalleryActions.trashSelected(GalleryActivity.this, galleryFragment.getSelectedFiles());
						break;
					case R.id.restore :
						GalleryActions.restoreSelected(GalleryActivity.this, galleryFragment.getSelectedFiles());
						break;
					case R.id.delete :
						GalleryActions.deleteSelected(GalleryActivity.this, galleryFragment.getSelectedFiles());
						break;
					case R.id.send_back :
						ShareManager.sendBackSelection(GalleryActivity.this, originalIntent, galleryFragment.getSelectedFiles(), currentSet);
						break;
				}
				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				exitActionMode();
				actionMode = null;
			}
		};
	}



	public int getCurrentSet(){
		return currentSet;
	}
	public String getCurrentAlbumId(){
		return currentAlbumId;
	}
	public String getCurrentAlbumName(){
		return currentAlbumName;
	}

	public void exitActionMode(){
		if(galleryFragment != null){
			galleryFragment.clearSelected();
		}
		if(actionMode != null){
			actionMode.finish();
		}
	}

	protected View.OnClickListener getImportOnClickListener(){
		return v -> {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			String[] mimetypes = {"image/*", "video/*"};
			intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			startActivityForResult(Intent.createChooser(intent, "Select photos and videos"), INTENT_IMPORT);
		};
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != Activity.RESULT_OK || data == null) {
			return;
		}
		if(requestCode == INTENT_IMPORT){
			dontStartSyncYet = true;

			if(galleryFragment != null) {
				galleryFragment.scrollToTop();
			}
			ArrayList<Uri> urisToImport = new ArrayList<>();
			ClipData clipData = data.getClipData();
			if (clipData != null && clipData.getItemCount() > 0) {
				for (int i = 0; i < clipData.getItemCount(); i++) {
					urisToImport.add(clipData.getItemAt(i).getUri());
				}
			}
			else {
				Uri uri = data.getData();
				if (uri != null) {
					urisToImport.add(uri);
				}
			}

			(new ImportFilesAsyncTask(this, urisToImport, currentSet, currentAlbumId, new FileManager.OnFinish() {
				@Override
				public void onFinish() {
					if(galleryFragment != null) {
						galleryFragment.updateDataSet();
					}
					dontStartSyncYet = false;
					sendMessageToSyncService(SyncService.MSG_START_SYNC);
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}
}
