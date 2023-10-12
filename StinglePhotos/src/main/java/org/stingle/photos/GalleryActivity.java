package org.stingle.photos;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.GravityCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.DeleteUrisAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.SetAlbumCoverAsyncTask;
import org.stingle.photos.AsyncTasks.GetServerPKAsyncTask;
import org.stingle.photos.AsyncTasks.ImportFilesAsyncTask;
import org.stingle.photos.AsyncTasks.MigrateFilesAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Albums.AlbumsFragment;
import org.stingle.photos.Gallery.Gallery.AutoImportSetup;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.Gallery.Gallery.GalleryFragment;
import org.stingle.photos.Gallery.Gallery.GalleryFragmentParent;
import org.stingle.photos.Gallery.Gallery.SyncBarHandler;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.Sync.JobScheduler.ImportJobSchedulerService;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, GalleryFragmentParent {

	public static final int REQUEST_VIEW_PHOTO = 1;

	public static final int FRAGMENT_GALLERY = 1;
	public static final int FRAGMENT_ALBUMS_LIST = 2;

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

	private boolean sendBackDecryptedFile = false;
	private Intent originalIntent = null;

	public static int INTENT_IMPORT = 1;
	public static int INTENT_DELETE_FILE = 2;

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
	private BroadcastReceiver refreshGallery = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(galleryFragment != null) {
				galleryFragment.updateDataSet();
			}
			if(albumsFragment != null){
				albumsFragment.updateDataSet();
			}
		}
	};
	private BroadcastReceiver refreshGalleryItem = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();

			if(bundle == null){
				return;
			}

			String filename = bundle.getString("filename");
			int set = bundle.getInt("set");
			String albumId = bundle.getString("albumId");

			FilesDb db;
			int sort = StingleDb.SORT_DESC;
			if(set == SyncManager.GALLERY || set == SyncManager.TRASH) {
				db = new GalleryTrashDb(context, set);
			}
			else if(set == SyncManager.ALBUM){
				db = new AlbumFilesDb(context);
				//sort = StingleDb.SORT_ASC;
			}
			else{
				return;
			}

			int position = db.getFilePositionByFilename(filename, albumId, sort);
			db.close();

			Log.d("updateItem number", String.valueOf(position));

			updateGalleryFragmentItem(position, set, albumId);
		}
	};
	private boolean isImporting = false;
	private boolean isSyncBarDisabled = false;
	private SharedPreferences sharedPreferences;
	private boolean isSyncEnabled;
	private SyncBarHandler syncBarHandler;
	private FloatingActionButton fab;
	private BottomNavigationView bottomNavigationView;
	private ActionBarDrawerToggle toggle;
	private DrawerLayout drawer;
	private NavigationView navigationView;
	private SwipeRefreshLayout pullToRefresh;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Helpers.setLocale(this);
		setContentView(R.layout.activity_gallery);

		Helpers.blockScreenshotsIfEnabled(this);

		lbm = LocalBroadcastManager.getInstance(this);

		toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(getString(R.string.title_gallery_for_app));
		setSupportActionBar(toolbar);

		fab = findViewById(R.id.import_fab);
		fab.setOnClickListener(getImportOnClickListener());
		bottomNavigationView = findViewById(R.id.bottom_navigation);

		drawer = findViewById(R.id.drawer_layout);
		navigationView = findViewById(R.id.nav_view);
		showBurgerMenu();

		pullToRefresh = findViewById(R.id.pullToRefresh);
		pullToRefresh.setOnRefreshListener(() -> {
			SyncManager.stopSync(this);
			SyncManager.startSync(this);
			pullToRefresh.setRefreshing(false);
		});


		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));
		lbm.registerReceiver(onEncFinish, new IntentFilter("MEDIA_ENC_FINISH"));
		lbm.registerReceiver(refreshGallery, new IntentFilter("REFRESH_GALLERY"));
		lbm.registerReceiver(refreshGalleryItem, new IntentFilter("REFRESH_GALLERY_ITEM"));

		handleIncomingIntent(getIntent());

		headerView = navigationView.getHeaderView(0);

		setupBottomNavigationView();
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		syncBarHandler = new SyncBarHandler(this);
		syncBarHandler.updateSyncBar();

		if(savedInstanceState != null){
			if(savedInstanceState.containsKey("set")) {
				currentSet = savedInstanceState.getInt("set");
			}
			if(savedInstanceState.containsKey("albumId")) {
				currentAlbumId = savedInstanceState.getString("albumId");
			}
			if(savedInstanceState.containsKey("view")) {
				currentAlbumsView = savedInstanceState.getInt("view");
			}
			if(savedInstanceState.containsKey("fragment")) {
				currentFragment = savedInstanceState.getInt("fragment");
			}
			if(savedInstanceState.containsKey("isSyncBarDisabled")) {
				isSyncBarDisabled = savedInstanceState.getBoolean("isSyncBarDisabled");
			}
			if(savedInstanceState.containsKey("albumsLastScrollPos")) {
				albumsLastScrollPos = savedInstanceState.getInt("albumsLastScrollPos");
			}
			if(savedInstanceState.containsKey("sharingLastScrollPos")) {
				sharingLastScrollPos = savedInstanceState.getInt("sharingLastScrollPos");
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

		setVersionLabel();
		if(Helpers.isServerAddonPresent(this,"addon-api-stingle-org") || !Helpers.isServerAddonsFieldSet(this)){
			navigationView.getMenu().findItem(R.id.nav_storage).setVisible(true);
		}

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

	private static MigrateFilesAsyncTask migrateTask;

	@Override
	protected void onResume() {
		super.onResume();
		Log.d("GalleryActivity", "onResume");
		((TextView)headerView.findViewById(R.id.userEmail)).setText(Helpers.getPreference(this, StinglePhotosApplication.USER_EMAIL, ""));

		File oldHomeDir = new File(FileManager.getOldHomeDir(this));
		if(oldHomeDir.exists()) {
			if(migrateTask == null) {
				migrateTask = new MigrateFilesAsyncTask(this, new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						executeOnResume();
					}
				});
				migrateTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
			}
		}
		else {
			executeOnResume();
		}
	}

	private void executeOnResume(){
		isSyncEnabled = SyncManager.isBackupEnabled(this);
		if (!isSyncEnabled) {
			disableSyncBar();
		} else if (isSyncBarDisabled() && currentSet == SyncManager.GALLERY) {
			enableSyncBar();
		}

		if (!isImporting) {
			SyncManager.startPeriodicWork(this);
			checkLoginAndInit();
		}

		if (SyncManager.isImportEnabled(this)) {
			ImportJobSchedulerService.scheduleJob(this);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();


		LoginManager.setLockedTime(this);
		findViewById(R.id.contentHolder).setVisibility(View.INVISIBLE);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt("set", currentSet);
		outState.putInt("view", currentAlbumsView);
		outState.putInt("fragment", currentFragment);
		outState.putString("albumId", currentAlbumId);
		outState.putBoolean("isSyncBarDisabled", isSyncBarDisabled);
		outState.putInt("albumsLastScrollPos", albumsLastScrollPos);
		outState.putInt("sharingLastScrollPos", sharingLastScrollPos);
	}

	/*private void requestToDisableBatteryOptimization(){
		Intent intent = new Intent();
		String packageName = getPackageName();
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
			MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
			builder.setTitle(getString(R.string.disable_battery_optimization));
			builder.setMessage(getString(R.string.disable_battery_optimization_desc));
			builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
				intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				intent.setData(Uri.parse("package:" + packageName));
				startActivity(intent);
			});
			builder.setCancelable(false);
			builder.setIcon(getDrawable(R.drawable.ic_battery_alert));
			AlertDialog dialog = builder.create();
			dialog.show();
		}
	}*/

	public void showMainGallery(){
		currentFragment = FRAGMENT_GALLERY;
		currentSet = SyncManager.GALLERY;
		currentAlbumId = null;
		initCurrentFragment();
		currentAlbumName = "";
	}
	public void showTrash(){
		currentFragment = FRAGMENT_GALLERY;
		currentSet = SyncManager.TRASH;
		currentAlbumId = null;
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

	private void setVersionLabel(){
		((TextView)findViewById(R.id.version_label)).setText("v" + BuildConfig.VERSION_NAME);
	}


	public void disablePullToRefresh(){
		pullToRefresh.setEnabled(false);
	}

	public void enablePullToRefresh(){
		pullToRefresh.setEnabled(true);
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

		if(currentSet == SyncManager.GALLERY){
			toolbar.setTitle(getString(R.string.title_gallery_for_app));
			enableSyncBar();
			fab.setVisibility(View.VISIBLE);
			showBurgerMenu();
		}
		if(currentSet == SyncManager.TRASH){
			toolbar.setTitle(getString(R.string.title_trash));
			disableSyncBar();
			fab.setVisibility(View.GONE);
			showBurgerMenu();
		}
		else if(currentSet == SyncManager.ALBUM){
			fab.setVisibility(View.VISIBLE);
			Log.d("albumId", currentAlbumId);
			StingleDbAlbum album = GalleryHelpers.getAlbum(this, currentAlbumId);
			String albumName = GalleryHelpers.getAlbumName(this, currentAlbumId);
			if(albumName != null && albumName.length() > 0){
				currentAlbumName = albumName;
				toolbar.setTitle(albumName);
				showBackButton();

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
		showBurgerMenu();

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

	private void showBurgerMenu(){
		getSupportActionBar().setDisplayHomeAsUpEnabled(false);
		toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();
		navigationView.setNavigationItemSelectedListener(this);
		toggle.syncState();
	}

	private void showBackButton(){
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		toolbar.setNavigationOnClickListener(view -> onBackPressed());
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
		//Log.e("finish", currentSet + " - " + set + " ;;;;; " + currentAlbumId + " - " + albumId);
		if(galleryFragment != null && currentSet == set && Helpers.isStringsEqual(currentAlbumId, albumId)) {
			galleryFragment.updateItem(position);
		}
	}

	private void checkLoginAndInit(){
		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserAuthSuccess() {
				LoginManager.disableLockTimer(GalleryActivity.this);
				requestNotificationPermission();
				initGallery();
			}
		});
	}

	private void requestNotificationPermission(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (!Helpers.getPreference(this, "notif_denied", false)) {
				if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
					requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, StinglePhotosApplication.REQUEST_NOTIF_PERMISSION);
				}
			}
		}
	}

	public ActivityResultLauncher<Intent> manageExternalStoragePermissionLauncher =
			registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
					result -> {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
							if (Environment.isExternalStorageManager()) {
								AutoImportSetup.setImportDeleteSwitch(this, true);
							} else {
								AutoImportSetup.setImportDeleteSwitch(this, false);
							}
						}
					});

	public void requestManageExternalStoragePermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if (!Environment.isExternalStorageManager()) {

				new MaterialAlertDialogBuilder(this)
						.setTitle(getString(R.string.permission_required))
						.setMessage(getString(R.string.magage_storage_perm_explain))
						.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
							Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
							intent.setData(Uri.parse("package:" + getPackageName()));
							manageExternalStoragePermissionLauncher.launch(intent);
						})
						.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
							AutoImportSetup.setImportDeleteSwitch(this, false);
							dialog.dismiss();

						})
						.create().show();
			}
			else {
				AutoImportSetup.setImportDeleteSwitch(this,true);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		switch (requestCode) {
			case StinglePhotosApplication.REQUEST_SD_CARD_PERMISSION:
			case StinglePhotosApplication.REQUEST_MEDIA_PERMISSION: {
				if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
					if(SyncManager.isImportEnabled(this)) {
						sharedPreferences.edit().putBoolean(SyncManager.PREF_IMPORT_ENABLED, false).apply();
						Toast.makeText(this, getString(R.string.disabling_autoimport), Toast.LENGTH_LONG).show();
					}
					if(sharedPreferences.getString(SyncManager.PREF_IMPORT_DELETE_APP, "ask").equals("always")){
						sharedPreferences.edit().putString(SyncManager.PREF_IMPORT_DELETE_APP, "ask").apply();
					}
				}
				else{
					SyncManager.startSync(this);
				}
				return;
			}
			case StinglePhotosApplication.REQUEST_NOTIF_PERMISSION: {
				if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
					Helpers.storePreference(this, "notif_denied", true);
				}
				return;
			}
		}
	}

	private void initGallery(){

		initCurrentFragment();
		if(galleryFragment != null) {
			galleryFragment.updateAutoFit();
			if(galleryFragment.isSelectionModeActive()){
				actionMode = startSupportActionMode(getActionModeCallback());
			}
		}
		if(albumsFragment != null) {
			albumsFragment.updateAutoFit();
		}
		findViewById(R.id.contentHolder).setVisibility(View.VISIBLE);
		findViewById(R.id.bottom_navigation).setVisibility(View.VISIBLE);
		if(currentFragment != FRAGMENT_ALBUMS_LIST && !(currentFragment == FRAGMENT_GALLERY && currentSet == SyncManager.TRASH)) {
			findViewById(R.id.import_fab).setVisibility(View.VISIBLE);
		}
		if(!dontStartSyncYet){
			if(FileManager.requestOldMediaPermissions(GalleryActivity.this)) {
				SyncManager.startSync(this);
			}
		}
		updateQuotaInfo();

		byte[] serverPK = StinglePhotosApplication.getCrypto().getServerPublicKey();
		if(serverPK == null){
			(new GetServerPKAsyncTask(this)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

		AutoImportSetup.showAutoImportSetup(this);
		showBackupPhraseReminder();

		// Check if delete after import is enabled and we are not storage manager, request for permission
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if(SyncManager.isImportEnabled(this)){
				FileManager.requestReadMediaPermissions(this);
			}
			if(SyncManager.isImportDeleteEnabled(this) && !Environment.isExternalStorageManager()){
				requestManageExternalStoragePermission();
			}
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
			if(galleryFragment == null || (galleryFragment != null && galleryFragment.getFirstVisibleItemNumber() == 0)) {
				syncBarHandler.showSyncBar();
				syncBarHandler.showSyncBarAnimated();
			}
			syncBarHandler.updateSyncBar();
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
			dontStartSyncYet = true;
			(new ImportFilesAsyncTask(this, urisToImport, SyncManager.GALLERY, null, new FileManager.OnFinish() {
				@Override
				public void onFinish(Long date) {
					if(galleryFragment != null) {
						galleryFragment.updateDataSet();
						if(currentSet == SyncManager.GALLERY) {
							galleryFragment.scrollToDate(date);
						}
					}
					dontStartSyncYet = false;
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
		else if (galleryFragment != null && galleryFragment.isSelectionModeActive()) {
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
								menu.findItem(R.id.action_set_blank_cover).setVisible(false);
								menu.findItem(R.id.action_reset_cover).setVisible(false);
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
							menu.findItem(R.id.action_album_info).setVisible(false);
							menu.findItem(R.id.action_leave_album).setVisible(false);
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
		else if (id == R.id.action_set_blank_cover) {
			GalleryActions.setAsAlbumCover(GalleryActivity.this, currentAlbumId, SetAlbumCoverAsyncTask.ALBUM_COVER_BLANK, null);
		}
		else if (id == R.id.action_reset_cover) {
			GalleryActions.setAsAlbumCover(GalleryActivity.this, currentAlbumId, SetAlbumCoverAsyncTask.ALBUM_COVER_DEFAULT, null);
		}

		return super.onOptionsItemSelected(item);
	}

	private void setupBottomNavigationView() {
		// Set action to perform when any menu-item is selected.
		bottomNavigationView.setOnItemSelectedListener(
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
		else if (id == R.id.nav_free_up_space) {
			GalleryActions.freeUpSpace(this);
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
			ShareManager.sendBackSelection(this, originalIntent, selectedFiles, currentSet, currentAlbumId);
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
			if(currentSet == SyncManager.ALBUM){
				StingleDbAlbum album = GalleryHelpers.getCurrentAlbum(GalleryActivity.this);
				if(album != null && album.isOwner){
					if(galleryFragment != null && galleryFragment.getSelectedFiles().size() == 1){
						actionMode.getMenu().findItem(R.id.set_as_album_cover).setVisible(true);
					}
					else{
						actionMode.getMenu().findItem(R.id.set_as_album_cover).setVisible(false);
					}
				}

			}
			actionMode.setTitle(String.valueOf(count));
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
						GalleryActions.addToAlbumSelected(GalleryActivity.this, galleryFragment.getSelectedFiles(), currentAlbumId,currentSet == SyncManager.ALBUM);
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
						ShareManager.sendBackSelection(GalleryActivity.this, originalIntent, galleryFragment.getSelectedFiles(), currentSet, currentAlbumId);
						break;
					case R.id.set_as_album_cover :
						GalleryActions.setAsAlbumCover(GalleryActivity.this,currentAlbumId, SetAlbumCoverAsyncTask.ALBUM_COVER_FILE, galleryFragment.getSelectedFiles().get(0).filename);
						break;
					case R.id.download :
						GalleryActions.downloadSelected(GalleryActivity.this, galleryFragment.getSelectedFiles(), getCurrentSet(), getCurrentAlbumId());
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

		if (resultCode != Activity.RESULT_OK) {
			return;
		}
		if(requestCode == INTENT_IMPORT && data != null){
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
				public void onFinish(Long date) {
					if(galleryFragment != null) {
						galleryFragment.updateDataSet();
						if(currentSet == SyncManager.GALLERY) {
							galleryFragment.scrollToDate(date);
						}
					}
					dontStartSyncYet = false;
					SyncManager.startSync(GalleryActivity.this);
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		else if(requestCode == INTENT_DELETE_FILE){
			Uri uri = DeleteUrisAsyncTask.urisToDelete.pollFirst();
			if(uri != null){
				Uri contentUri = DeleteUrisAsyncTask.getContentUri(this, uri);
				if(contentUri != null) {
					try {
						getContentResolver().delete(contentUri, null, null);
					} catch (Exception e) {
						Helpers.showAlertDialog(
								this,
								getString(R.string.delete_failed),
								getString(R.string.delete_failed_desc)
						);
					}
				}
			}
		}
	}

	private boolean isShownBackupPhraseReminder = false;
	private void showBackupPhraseReminder(){
		int appStartCount = Helpers.getPreference(this, StinglePhotosApplication.PREF_APP_START_COUNT, 1);
		boolean isBackedUpPhrase = Helpers.getPreference(this, SyncManager.PREF_IS_BACKUP_PHRASE_SEEN, false);
		if(!isShownBackupPhraseReminder && !isBackedUpPhrase && (appStartCount % 5 == 0 || appStartCount == 2)) {
			MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
			builder.setCancelable(true);
			builder.setTitle(R.string.backup_phrase_not_backed);
			builder.setMessage(R.string.backup_phrase_not_backed_desc);
			builder.setIcon(R.drawable.ic_attention);
			builder.setPositiveButton(R.string.go_to_backup_phrase, (dialog, which) -> {
				Intent intent = new Intent();
				intent.setClass(GalleryActivity.this, BackupKeyActivity.class);
				startActivity(intent);
			});

			if(appStartCount != 2){
				builder.setNeutralButton(R.string.dont_show_again, (dialog, which) -> Helpers.storePreference(GalleryActivity.this, SyncManager.PREF_IS_BACKUP_PHRASE_SEEN, true));
			}

			builder.setNegativeButton(R.string.cancel, null);
			AlertDialog dialog = builder.create();
			dialog.show();
			isShownBackupPhraseReminder = true;
		}
	}
}
