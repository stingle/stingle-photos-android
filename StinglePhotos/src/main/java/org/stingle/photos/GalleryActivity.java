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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.stingle.photos.AsyncTasks.GetServerPKAsyncTask;
import org.stingle.photos.AsyncTasks.ImportFilesAsyncTask;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Objects.StingleFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Albums.AlbumsFragment;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.Gallery.Gallery.GalleryFragment;
import org.stingle.photos.Gallery.Gallery.GalleryFragmentParent;
import org.stingle.photos.Gallery.Gallery.SyncBarHandler;
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
	protected ActionMode actionMode;
	protected Toolbar toolbar;
	protected int currentFolder = SyncManager.FOLDER_MAIN;

	protected Messenger mService = null;
	protected boolean isBound = false;
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	private boolean sendBackDecryptedFile = false;
	private Intent originalIntent = null;

	protected int INTENT_IMPORT = 1;

	private boolean dontStartSyncYet = false;
	private View headerView;

	private int currentFragment = FRAGMENT_GALLERY;
	private String currentFolderId = null;

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


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTheme(R.style.AppTheme);
		setContentView(R.layout.activity_gallery);

		Helpers.blockScreenshotsIfEnabled(this);

		lbm = LocalBroadcastManager.getInstance(this);

		toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(getString(R.string.title_gallery_for_app));
		setSupportActionBar(toolbar);
		FloatingActionButton fab = findViewById(R.id.import_fab);
		fab.setOnClickListener(getImportOnClickListener());
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
	}

	@Override
	protected void onStart() {
		super.onStart();

	}

	@Override
	protected void onStop() {
		super.onStop();

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
		else if (isSyncBarDisabled() && currentFolder == SyncManager.FOLDER_MAIN){
			enableSyncBar();
		}

		if(!isImporting){
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

	public void showMainGallery(){
		currentFragment = FRAGMENT_GALLERY;
		currentFolder = SyncManager.FOLDER_MAIN;
		currentFolderId = null;
		toolbar.setTitle(getString(R.string.title_gallery_for_app));
		enableSyncBar();
		initCurrentFragment();
	}
	public void showTrash(){
		currentFragment = FRAGMENT_GALLERY;
		currentFolder = SyncManager.FOLDER_TRASH;
		currentFolderId = null;
		toolbar.setTitle(getString(R.string.title_trash));
		disableSyncBar();
		initCurrentFragment();
	}
	public void showAlbumsList(){
		currentFragment = FRAGMENT_ALBUMS_LIST;
		currentFolder = -1;
		currentFolderId = null;
		toolbar.setTitle(getString(R.string.albums));
		disableSyncBar();
		initCurrentFragment();
	}
	public void showAlbum(String albumId, String albumName){
		currentFragment = FRAGMENT_GALLERY;
		currentFolder = SyncManager.FOLDER_ALBUM;
		currentFolderId = albumId;
		if(albumName != null && albumName.length() > 0){
			toolbar.setTitle(albumName);
		}
		else{
			toolbar.setTitle(getString(R.string.album));
		}
		disableSyncBar();
		initCurrentFragment();
	}

	private void initCurrentFragment(){
		switch (currentFragment){
			case FRAGMENT_GALLERY:
				initGalleryFragment(currentFolder, currentFolderId, true);
				break;
			case FRAGMENT_ALBUMS_LIST:
				initAlbumsFragment();
				break;
		}
	}

	private void initGalleryFragment(int folderType, String folderId, boolean initNow){
		currentFragment = FRAGMENT_GALLERY;
		currentFolder = folderType;
		currentFolderId = folderId;
		FragmentManager fm = getSupportFragmentManager();

		Bundle bundle = new Bundle();
		bundle.putInt("currentFolder", folderType);
		bundle.putString("folderId", folderId);
		bundle.putBoolean("initNow", initNow);

		galleryFragment = new GalleryFragment();
		galleryFragment.setArguments(bundle);

		Fragment fragment = fm.findFragmentById(R.id.galleryContainer);
		FragmentTransaction ft = fm.beginTransaction();
		if (fragment==null) {

			ft.add(R.id.galleryContainer, galleryFragment);
		}
		else {
			ft.replace(R.id.galleryContainer, galleryFragment);
		}
		ft.commit();
	}

	private void initAlbumsFragment(){
		FragmentManager fm = getSupportFragmentManager();

		Bundle bundle = new Bundle();

		AlbumsFragment albumsFragment = new AlbumsFragment();
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
	}

	public void updateGalleryFragmentData(){
		galleryFragment.updateDataSet();
	}
	public void updateGalleryFragmentItem(int position){
		galleryFragment.updateItem(position);
	}

	private void checkLoginAndInit(){
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
		LoginManager.disableLockTimer(GalleryActivity.this);
	}

	private void initGallery(){

		initCurrentFragment();


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

	private void startSync(){
		Intent serviceIntent = new Intent(this, SyncService.class);
		serviceIntent.putExtra("START_SYNC", true);
		startService(serviceIntent);
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
			(new ImportFilesAsyncTask(this, urisToImport, new FileManager.OnFinish() {
				@Override
				public void onFinish() {
					galleryFragment.updateDataSet();
					startSync();
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
		else if(currentFolder == SyncManager.FOLDER_ALBUM){
			showAlbumsList();
		}
		else if(currentFolder == SyncManager.FOLDER_TRASH){
			showMainGallery();
		}
		else {
			super.onBackPressed();
		}
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if(!sendBackDecryptedFile) {
			switch (currentFolder) {
				case SyncManager.FOLDER_MAIN:
					getMenuInflater().inflate(R.menu.gallery, menu);
					break;
				case SyncManager.FOLDER_TRASH:
					getMenuInflater().inflate(R.menu.gallery_trash, menu);
					break;
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
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
		else if (id == R.id.action_empty_trash) {
			GalleryActions.emptyTrash(this);
		}

		return super.onOptionsItemSelected(item);
	}

	private void setupBottomNavigationView() {
		BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
		if (bottomNavigationView != null) {

			// Set action to perform when any menu-item is selected.
			bottomNavigationView.setOnNavigationItemSelectedListener(
					item -> {
						item.setChecked(true);

						switch (item.getItemId()) {
							case R.id.action_gallery:
								showMainGallery();
								break;
							case R.id.action_albums:
								showAlbumsList();
								break;
							case R.id.action_sharing:
								break;
						}
						return false;
					});
		}
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_gallery) {
			showMainGallery();
			invalidateOptionsMenu();
		}
		else if (id == R.id.nav_trash) {
			showTrash();
			invalidateOptionsMenu();
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
	public boolean onClick(StingleFile file) {
		if (!galleryFragment.isSelectionModeActive() && sendBackDecryptedFile){
			ArrayList<StingleFile> selectedFiles = new ArrayList<>();
			selectedFiles.add(file);
			ShareManager.sendBackSelection(this, originalIntent, selectedFiles, currentFolder);
			return false;
		}

		return true;
	}

	@Override
	public boolean onLongClick(int index) {
		if(!galleryFragment.isSelectionModeActive()){
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
					switch (currentFolder) {
						case SyncManager.FOLDER_MAIN:
							mode.getMenuInflater().inflate(R.menu.gallery_action_mode, menu);
							break;
						case SyncManager.FOLDER_TRASH:
							mode.getMenuInflater().inflate(R.menu.gallery_trash_action_mode, menu);
							break;
						case SyncManager.FOLDER_ALBUM:
							mode.getMenuInflater().inflate(R.menu.gallery_album_action_mode, menu);
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
					case R.id.share:
						GalleryActions.shareSelected(GalleryActivity.this, galleryFragment.getSelectedFiles());
						break;
					case R.id.add_to_album:
						GalleryActions.addToAlbumSelected(GalleryActivity.this, galleryFragment.getSelectedFiles());
						break;
					case R.id.decrypt:
						GalleryActions.decryptSelected(GalleryActivity.this, galleryFragment.getSelectedFiles());
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
						ShareManager.sendBackSelection(GalleryActivity.this, originalIntent, galleryFragment.getSelectedFiles(), currentFolder);
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



	public int getCurrentFolder(){
		return currentFolder;
	}
	public String getCurrentFolderId(){
		return currentFolderId;
	}

	public void exitActionMode(){
		galleryFragment.clearSelected();
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
			//intent.addCategory(Intent.CATEGORY_DEFAULT);
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			// Note: This is not documented, but works: Show the Internal Storage menu item in the drawer!
			//intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
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

			galleryFragment.scrollToTop();
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

			(new ImportFilesAsyncTask(this, urisToImport, new FileManager.OnFinish() {
				@Override
				public void onFinish() {
					galleryFragment.updateDataSet();
					dontStartSyncYet = false;
					sendMessageToSyncService(SyncService.MSG_START_SYNC);
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		/*else if(requestCode == 7){
			Log.e("result", data.toString());
			Uri uri = data.getData();

			grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
					Intent.FLAG_GRANT_READ_URI_PERMISSION);

			final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
					Intent.FLAG_GRANT_READ_URI_PERMISSION);

			getContentResolver().takePersistableUriPermission(uri, takeFlags);
		}*/
	}

}
