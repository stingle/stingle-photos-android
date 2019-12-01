package org.stingle.photos;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import org.stingle.photos.AsyncTasks.DecryptFilesAsyncTask;
import org.stingle.photos.AsyncTasks.ImportFilesAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.ShowEncThumbInImageView;
import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Db.StingleDbFile;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.AutoFitGridLayoutManager;
import org.stingle.photos.Gallery.GalleryAdapterPisasso;
import org.stingle.photos.Gallery.HidingScrollListener;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.SyncService;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Gallery.DragSelectRecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;

import android.view.MenuItem;

import com.google.android.material.navigation.NavigationView;

import androidx.core.widget.ContentLoadingProgressBar;
import androidx.drawerlayout.widget.DrawerLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.view.Menu;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GalleryActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, GalleryAdapterPisasso.Listener {

	public static final int REQUEST_VIEW_PHOTO = 1;
	private LocalBroadcastManager lbm;
	protected DragSelectRecyclerView recyclerView;
	protected GalleryAdapterPisasso adapter;
	protected AutoFitGridLayoutManager layoutManager;
	protected ActionMode actionMode;
	protected Toolbar toolbar;
	protected int currentFolder = SyncManager.FOLDER_MAIN;
	protected ViewGroup syncBar;
	protected ProgressBar syncProgress;
	protected ProgressBar refreshCProgress;
	protected ImageView syncPhoto;
	protected TextView syncText;
	protected Messenger mService = null;
	protected boolean isBound = false;
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	private boolean sendBackDecryptedFile = false;
	private Intent originalIntent = null;

	protected int lastScrollPosition = 0;

	protected int INTENT_IMPORT = 1;

	private boolean dontStartSyncYet = false;
	private View headerView;

	private BroadcastReceiver onLogout = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LoginManager.redirectToLogin(GalleryActivity.this);
		}
	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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

		recyclerView = findViewById(R.id.gallery_recycler_view);
		syncBar = findViewById(R.id.syncBar);
		syncProgress = findViewById(R.id.syncProgress);
		refreshCProgress = findViewById(R.id.refreshCProgress);
		syncPhoto = findViewById(R.id.syncPhoto);
		syncText = findViewById(R.id.syncText);

		final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
		pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				sendMessageToSyncService(SyncService.MSG_START_SYNC);
				pullToRefresh.setRefreshing(false);
			}
		});

		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);
		layoutManager = new AutoFitGridLayoutManager(GalleryActivity.this, Helpers.getThumbSize(GalleryActivity.this));
		layoutManager.setSpanSizeLookup(new AutoFitGridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				if (adapter.getItemViewType(position) == GalleryAdapterPisasso.TYPE_DATE) {
					return layoutManager.getCurrentCalcSpanCount();
				}
				return 1;
			}
		});
		recyclerView.setLayoutManager(layoutManager);
		adapter = new GalleryAdapterPisasso(GalleryActivity.this, GalleryActivity.this, layoutManager, SyncManager.FOLDER_MAIN);
		recyclerView.addOnScrollListener(new HidingScrollListener() {
			@Override
			public void onHide() {
				syncBar.animate().translationY(-syncBar.getHeight()-Helpers.convertDpToPixels(GalleryActivity.this, 20)).setInterpolator(new AccelerateInterpolator(4));
			}


			@Override
			public void onShow() {
				syncBar.animate().translationY(0).setInterpolator(new DecelerateInterpolator(4));
			}
		});

		if(savedInstanceState != null && savedInstanceState.containsKey("scroll")){
			lastScrollPosition = savedInstanceState.getInt("scroll");
		}

		lbm.registerReceiver(onLogout, new IntentFilter("ACTION_LOGOUT"));

		handleIncomingIntent(getIntent());

		headerView = ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0);
		((TextView)headerView.findViewById(R.id.userEmail)).setText(Helpers.getPreference(this, StinglePhotosApplication.USER_EMAIL, ""));
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
		LoginManager.disableLockTimer(GalleryActivity.this);
	}

	private void initGallery(){
		/*String homedir = FileManager.getHomeDir(this);
		if(homedir == null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N){
			//File sdCard = new File(Environment.getExternalStorageDirectory().getPath());
			//StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
			//StorageVolume storageVolume = storageManager.getStorageVolume(sdCard);
			final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			//intent.putExtra(EXTRA_STORAGE_VOLUME, this);
			intent.putExtra("EXTRA_INITIAL_URI", "file://" + getString(R.string.default_home_folder_name));
			startActivityForResult(intent, 7);
		}
		else {*/
			Log.e("InitGallery", "qaq");
			findViewById(R.id.topBar).setVisibility(View.VISIBLE);
			recyclerView.setAdapter(adapter);
			Intent serviceIntent = new Intent(GalleryActivity.this, SyncService.class);
			try {
				startService(serviceIntent);
				bindService(serviceIntent, mConnection, BIND_AUTO_CREATE);
			} catch (IllegalStateException ignored) {
			}
			layoutManager.scrollToPosition(lastScrollPosition);

			if (adapter != null) {
				adapter.updateDataSet();
			}
			updateQuotaInfo();
		//}
	}

	@Override
	protected void onPause() {
		super.onPause();
		lastScrollPosition = layoutManager.findFirstVisibleItemPosition();

		recyclerView.setAdapter(null);
		LoginManager.setLockedTime(this);

		if (isBound) {
			unbindService(mConnection);
			isBound = false;
		}
		findViewById(R.id.topBar).setVisibility(View.INVISIBLE);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
		super.onSaveInstanceState(outState, outPersistentState);

		outState.putInt("scroll", layoutManager.findFirstVisibleItemPosition());
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case StinglePhotosApplication.REQUEST_SD_CARD_PERMISSION: {
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					initGallery();
				} else {
					finish();
				}
				return;
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
			(new ImportFilesAsyncTask(this, urisToImport, new FileManager.OnFinish() {
				@Override
				public void onFinish() {
					adapter.updateDataSet();
					sendMessageToSyncService(SyncService.MSG_START_SYNC);
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

	}

	private void updateQuotaInfo(){
		int spaceUsed = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_USED, 0);
		int spaceQuota = Helpers.getPreference(this, SyncManager.PREF_LAST_SPACE_QUOTA, 1);
		int percent = Math.round(spaceUsed * 100 / spaceQuota);


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

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == SyncService.MSG_RESP_SYNC_STATUS) {
				Bundle bundle = msg.getData();
				int syncStatus = bundle.getInt("syncStatus");
				int totalItemsNumber = bundle.getInt("totalItemsNumber");
				int uploadedFilesCount = bundle.getInt("uploadedFilesCount");
				String currentFile = bundle.getString("currentFile");

				if (syncStatus == SyncService.STATUS_UPLOADING) {
					syncProgress.setMax(totalItemsNumber);
					syncProgress.setProgress(uploadedFilesCount);
					syncText.setText(getString(R.string.uploading_file, String.valueOf(uploadedFilesCount), String.valueOf(totalItemsNumber)));
					(new ShowEncThumbInImageView(GalleryActivity.this, currentFile, syncPhoto)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					setSyncStatus(SyncService.STATUS_UPLOADING);
				} else if (syncStatus == SyncService.STATUS_REFRESHING) {
					setSyncStatus(SyncService.STATUS_REFRESHING);
				} else if (syncStatus == SyncService.STATUS_IDLE) {
					setSyncStatus(SyncService.STATUS_IDLE);
				}

			}
			else if(msg.what == SyncService.MSG_SYNC_CURRENT_FILE) {
				Bundle bundle = msg.getData();
				String currentFile = bundle.getString("currentFile");
				(new ShowEncThumbInImageView(GalleryActivity.this, currentFile, syncPhoto)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

				setSyncStatus(SyncService.STATUS_UPLOADING);
			}
			else if(msg.what == SyncService.MSG_SYNC_UPLOAD_PROGRESS) {
				Bundle bundle = msg.getData();
				int totalItemsNumber = bundle.getInt("totalItemsNumber");
				int uploadedFilesCount = bundle.getInt("uploadedFilesCount");

				syncProgress.setMax(totalItemsNumber);
				syncProgress.setProgress(uploadedFilesCount);
				syncText.setText(getString(R.string.uploading_file, String.valueOf(uploadedFilesCount), String.valueOf(totalItemsNumber)));

				setSyncStatus(SyncService.STATUS_UPLOADING);
			}
			else if(msg.what == SyncService.MSG_SYNC_STATUS_CHANGE) {
				Bundle bundle = msg.getData();
				int newStatus = bundle.getInt("newStatus");
				setSyncStatus(newStatus);
			}
			else if(msg.what == SyncService.MSG_REFRESH_GALLERY) {
				int lastScrollPos = recyclerView.getScrollY();
				adapter.updateDataSet();
				recyclerView.setScrollY(lastScrollPos);
			}
			else if(msg.what == SyncService.MSG_REFRESH_GALLERY_ITEM) {
				Bundle bundle = msg.getData();
				int position = bundle.getInt("position");

				adapter.updateItem(position);
			}
			else{
				super.handleMessage(msg);
			}
		}
	}

	private void setSyncStatus(int syncStatus){
		if (syncStatus == SyncService.STATUS_UPLOADING) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.VISIBLE);
			syncProgress.setVisibility(View.VISIBLE);
		} else if (syncStatus == SyncService.STATUS_REFRESHING) {
			refreshCProgress.setVisibility(View.VISIBLE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.refreshing));
		} else if (syncStatus == SyncService.STATUS_NO_SPACE_LEFT) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.no_space_left));
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_DISABLED) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.sync_disabled));
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_NOT_WIFI) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.sync_not_on_wifi));
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_BATTERY_LOW) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(getString(R.string.sync_battery_low));
			updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_IDLE) {
			syncText.setText(getString(R.string.backup_complete));
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			refreshCProgress.setVisibility(View.GONE);
			updateQuotaInfo();
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
		layoutManager.updateAutoFit();
	}

	@Override
	public void onBackPressed() {
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START);
		}
		else if (adapter.isSelectionModeActive()) {
			exitActionMode();
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
			startActivity(intent);
		}
		else if (id == R.id.action_empty_trash) {
			emptyTrash();
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_gallery) {
			adapter.setFolder(SyncManager.FOLDER_MAIN);
			currentFolder = SyncManager.FOLDER_MAIN;
			recyclerView.scrollToPosition(0);
			toolbar.setTitle(getString(R.string.title_gallery_for_app));
			invalidateOptionsMenu();
		}
		else if (id == R.id.nav_trash) {
			adapter.setFolder(SyncManager.FOLDER_TRASH);
			currentFolder = SyncManager.FOLDER_TRASH;
			recyclerView.scrollToPosition(0);
			toolbar.setTitle(getString(R.string.title_trash));
			invalidateOptionsMenu();
		}
		else if (id == R.id.nav_account) {
			Intent intent = new Intent();
			intent.setClass(this, AccountActivity.class);
			startActivity(intent);
		}
		else if (id == R.id.nav_change_pass) {
			Intent intent = new Intent();
			intent.setClass(this, ChangePasswordActivity.class);
			startActivity(intent);
		}
		else if (id == R.id.nav_settings) {
			Intent intent = new Intent();
			intent.setClass(this, SettingsActivity.class);
			startActivity(intent);
		}
		else if (id == R.id.nav_telegram) {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/stingle_photos_group"));
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
	public void onClick(int index) {
		if (adapter.isSelectionModeActive()){
			adapter.toggleSelected(index);
		}
		else {
			if(sendBackDecryptedFile){
				ArrayList<StingleDbFile> selectedFiles = new ArrayList<>();
				selectedFiles.add(adapter.getStingleFileAtPosition(index));
				ShareManager.sendBackSelection(this, originalIntent, selectedFiles, currentFolder);
			}
			else {
				Intent intent = new Intent();
				intent.setClass(this, ViewItemActivity.class);
				intent.putExtra("EXTRA_ITEM_POSITION", adapter.getDbPositionFromRaw(index));
				intent.putExtra("EXTRA_ITEM_FOLDER", currentFolder);
				startActivity(intent);
			}
		}
	}

	@Override
	public void onLongClick(int index) {
		recyclerView.setDragSelectActive(true, index);
		if(!adapter.isSelectionModeActive()){
			actionMode = startSupportActionMode(getActionModeCallback());
			onSelectionChanged(1);
		}
		adapter.setSelectionModeActive(true);
	}


	@Override
	public void onSelectionChanged(int count) {
		if(actionMode != null) {
			if(count == 0){
				actionMode.setTitle("");
			}
			else {
				actionMode.setTitle(String.valueOf(count));
			}
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
						shareSelected();
						break;
					case R.id.decrypt:
						decryptSelected();
						break;
					case R.id.trash :
						trashSelected();
						break;
					case R.id.restore :
						restoreSelected();
						break;
					case R.id.delete :
						deleteSelected();
						break;
					case R.id.send_back :
						sendBackSelected();
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

	private void shareSelected() {
		List<Integer> indices = adapter.getSelectedIndices();
		ArrayList<StingleDbFile> files = new ArrayList<>();
		for(Integer index : indices){
			files.add(adapter.getStingleFileAtPosition(index));
		}

		ShareManager.shareDbFiles(this, files, currentFolder);
	}

	private void decryptSelected() {
		final List<Integer> indices = adapter.getSelectedIndices();
		Helpers.showConfirmDialog(GalleryActivity.this, String.format(getString(R.string.confirm_decrypt_files)), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(GalleryActivity.this, getString(R.string.decrypting_files), null);
						ArrayList<StingleDbFile> files = new ArrayList<>();
						for(Integer index : indices){
							files.add(adapter.getStingleFileAtPosition(index));
						}

						File decryptDir = new File(Environment.getExternalStorageDirectory().getPath() + "/" + FileManager.DECRYPT_DIR);
						DecryptFilesAsyncTask decFilesJob = new DecryptFilesAsyncTask(GalleryActivity.this, decryptDir, new OnAsyncTaskFinish() {
							@Override
							public void onFinish(ArrayList<File> files) {
								super.onFinish(files);
								exitActionMode();
								spinner.dismiss();
							}
						});
						decFilesJob.setFolder(currentFolder);
						decFilesJob.setPerformMediaScan(true);
						decFilesJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, files);
					}
				},
				null);
	}

	private void sendBackSelected() {
		List<Integer> indices = adapter.getSelectedIndices();
		ArrayList<StingleDbFile> files = new ArrayList<>();
		for(Integer index : indices){
			files.add(adapter.getStingleFileAtPosition(index));
		}

		ShareManager.sendBackSelection(this, originalIntent, files, currentFolder);
	}


	protected void exitActionMode(){
		adapter.clearSelected();
		recyclerView.setDragSelectActive(false, 0);
		if(actionMode != null){
			actionMode.finish();
		}
	}

	protected void trashSelected(){
		final List<Integer> indices = adapter.getSelectedIndices();
		Helpers.showConfirmDialog(GalleryActivity.this, String.format(getString(R.string.confirm_trash_files), String.valueOf(indices.size())), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				final ProgressDialog spinner = Helpers.showProgressDialog(GalleryActivity.this, getString(R.string.trashing_files), null);
				ArrayList<String> filenames = new ArrayList<>();
				for(Integer index : indices){
					StingleDbFile file = adapter.getStingleFileAtPosition(index);
					filenames.add(file.filename);
				}

				new SyncManager.MoveToTrashAsyncTask(GalleryActivity.this, filenames, new SyncManager.OnFinish(){
					@Override
					public void onFinish(Boolean needToUpdateUI) {
						adapter.updateDataSet();
						exitActionMode();
						spinner.dismiss();
					}
				}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		},
		null);
	}

	protected void restoreSelected(){
		final List<Integer> indices = adapter.getSelectedIndices();

		final ProgressDialog spinner = Helpers.showProgressDialog(GalleryActivity.this, getString(R.string.restoring_files), null);
		ArrayList<String> filenames = new ArrayList<>();
		for(Integer index : indices){
			StingleDbFile file = adapter.getStingleFileAtPosition(index);
			filenames.add(file.filename);
		}

		new SyncManager.RestoreFromTrashAsyncTask(GalleryActivity.this, filenames, new SyncManager.OnFinish(){
			@Override
			public void onFinish(Boolean needToUpdateUI) {
				adapter.updateDataSet();
				exitActionMode();
				spinner.dismiss();
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

	}

	protected void deleteSelected(){
		final List<Integer> indices = adapter.getSelectedIndices();
		Helpers.showConfirmDialog(GalleryActivity.this, String.format(getString(R.string.confirm_delete_files), String.valueOf(indices.size())), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(GalleryActivity.this, getString(R.string.deleting_files), null);
						ArrayList<String> filenames = new ArrayList<>();
						for(Integer index : indices){
							StingleDbFile file = adapter.getStingleFileAtPosition(index);
							filenames.add(file.filename);
						}

						new SyncManager.DeleteFilesAsyncTask(GalleryActivity.this, filenames, new SyncManager.OnFinish(){
							@Override
							public void onFinish(Boolean needToUpdateUI) {
								adapter.updateDataSet();
								exitActionMode();
								spinner.dismiss();
							}
						}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				},
				null);
	}

	protected void emptyTrash(){
		Helpers.showConfirmDialog(GalleryActivity.this, String.format(getString(R.string.confirm_empty_trash)), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(GalleryActivity.this, getString(R.string.emptying_trash), null);

						new SyncManager.EmptyTrashAsyncTask(GalleryActivity.this, new SyncManager.OnFinish(){
							@Override
							public void onFinish(Boolean needToUpdateUI) {
								adapter.updateDataSet();
								spinner.dismiss();
							}
						}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				},
				null);
	}

	protected View.OnClickListener getImportOnClickListener(){
		return new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				String[] mimetypes = {"image/*", "video/*"};
				intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
				//intent.addCategory(Intent.CATEGORY_DEFAULT);
				intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				// Note: This is not documented, but works: Show the Internal Storage menu item in the drawer!
				//intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
				startActivityForResult(Intent.createChooser(intent, "Select photos and videos"), INTENT_IMPORT);
			}
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

			lastScrollPosition = 0;
			layoutManager.scrollToPosition(lastScrollPosition);
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
					adapter.updateDataSet();
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
