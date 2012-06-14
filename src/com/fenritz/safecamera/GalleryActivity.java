package com.fenritz.safecamera;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecamera.util.AsyncTasks;
import com.fenritz.safecamera.util.AsyncTasks.EncryptFiles;
import com.fenritz.safecamera.util.AsyncTasks.ImportFiles;
import com.fenritz.safecamera.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecamera.util.Helpers;
import com.fenritz.safecamera.util.MemoryCache;
import com.fenritz.safecamera.widget.CheckableLayout;

public class GalleryActivity extends Activity {

	public final MemoryCache memCache = new MemoryCache();

	private final static int MULTISELECT_OFF = 0;
	private final static int MULTISELECT_ON = 1;

	protected static final int REQUEST_DECRYPT = 0;
	protected static final int REQUEST_ENCRYPT = 1;
	protected static final int REQUEST_IMPORT = 2;
	protected static final int REQUEST_VIEW_PHOTO = 3;

	protected static final int ACTION_DECRYPT = 0;
	protected static final int ACTION_SHARE = 1;
	protected static final int ACTION_MOVE = 2;
	protected static final int ACTION_DELETE = 3;
	
	protected static final int ACTION_DELETE_FOLDER = 0;

	private int multiSelectMode = MULTISELECT_OFF;
	
	private boolean multiSelectModeActive = false;

	private GridView photosGrid;

	private final ArrayList<File> files = new ArrayList<File>();
	
	private final ArrayList<File> selectedFiles = new ArrayList<File>();
	private final ArrayList<File> toGenerateThumbs = new ArrayList<File>();
	private final ArrayList<File> noThumbs = new ArrayList<File>();
	private final GalleryAdapter galleryAdapter = new GalleryAdapter();

	private GenerateThumbs thumbGenTask;
	private FillCache fillCacheTask = new FillCache();

	private BroadcastReceiver receiver;
	
	private boolean isWentToLogin = false;
	
	private String currentPath;
	
	private int pageNumber = 1;
	private final int itemsPerPage = 50;
	private final int loadThreshold = 5;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.gallery);

		Bundle bundle = new Bundle();
		bundle.putParcelable("intent", getIntent());
		if(!Helpers.checkLoginedState(this, bundle)){
			isWentToLogin = true;
			return;
		}

		currentPath = Helpers.getHomeDir(this);
		fillFilesList();
		int[] params = {0, itemsPerPage};
		fillCacheTask.execute(params);

		photosGrid = (GridView) findViewById(R.id.photosGrid);
		photosGrid.setAdapter(galleryAdapter);
		photosGrid.setOnScrollListener(getOnScrollListener());

		findViewById(R.id.multi_select).setOnClickListener(multiSelectClick());
		findViewById(R.id.deleteSelected).setOnClickListener(deleteSelectedClick());
		findViewById(R.id.decryptSelected).setOnClickListener(decryptSelectedClick());
		findViewById(R.id.importFiles).setOnClickListener(importClick());
		findViewById(R.id.newFolder).setOnClickListener(newFolderClick());
		findViewById(R.id.moveSelected).setOnClickListener(moveSelectedClick());
		findViewById(R.id.share).setOnClickListener(shareClick());
		findViewById(R.id.gotoHome).setOnClickListener(gotoHome());
		findViewById(R.id.gotoCamera).setOnClickListener(gotoCamera());

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);

		handleIntentFilters(getIntent());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
	}
	
	private OnClickListener gotoHome() {
		return new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(GalleryActivity.this, DashboardActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				finish();
			}
		};
	}
	
	private OnClickListener gotoCamera() {
		return new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(GalleryActivity.this, CameraActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
				startActivity(intent);
				finish();
			}
		};
	}

	void handleIntentFilters(Intent intent){
		// Handle Intent filters
		String action = intent.getAction();
		String type = intent.getType();

		if (!"text/plain".equals(type)) {
			if (Intent.ACTION_SEND.equals(action) && type != null) {
				handleSendSingle(intent);
			}
			else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
				handleSendMulti(intent);
			}
			else {
				// Handle other intents, such as being started from the home
				// screen
			}
		}
	}
	
	void handleSendSingle(Intent intent) {
		Uri fileUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
		if (fileUri != null) {
			String filePath;
			if(new File(fileUri.getPath()).exists()){
				filePath = fileUri.getPath();
			}
			else{
				filePath = Helpers.getRealPathFromURI(this, fileUri);
			}
			
			String[] filePaths = {filePath};
			new EncryptFiles(GalleryActivity.this, currentPath, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					
					refreshList();
				}
			}).execute(filePaths);
		}
	}
	
	void handleSendMulti(Intent intent) {
		ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
		if (fileUris != null) {
			String[] filePaths = new String[fileUris.size()];
			int counter = 0;
			for(Uri fileUri : fileUris){
				String filePath;
				if(new File(fileUri.getPath()).exists()){
					filePath = fileUri.getPath();
				}
				else{
					filePath = Helpers.getRealPathFromURI(this, fileUri);
				}
				
				filePaths[counter++] = filePath;
			}
			new EncryptFiles(GalleryActivity.this, currentPath, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					
					refreshList();
				}
			}).execute(filePaths);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void fillFilesList() {
		if (thumbGenTask != null) {
			thumbGenTask.cancel(true);
			thumbGenTask = null;
		}

		ArrayList<File> folders = new ArrayList<File>();
		ArrayList<File> files = new ArrayList<File>();
		
		File dir = new File(currentPath);
		File[] folderFiles = dir.listFiles();

		int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;
		
		Arrays.sort(folderFiles, new Comparator<File>() {
			public int compare(File lhs, File rhs) {
				if(rhs.lastModified() > lhs.lastModified()){
					return 1;
				}
				else if(rhs.lastModified() < lhs.lastModified()){
					return -1;
				}
				return 0;
			}
		});
		
		this.files.clear();
		toGenerateThumbs.clear();
		noThumbs.clear();
		
		for (File file : folderFiles) {
			if(file.isDirectory() && !file.getName().startsWith(".")){
				folders.add(file);
			}
			else if(file.isFile() && file.getName().endsWith(getString(R.string.file_extension))) {
				files.add(file);
				
				if(file.length() >= maxFileSize){
					noThumbs.add(file);
				}
				else{
					String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + file.getName();
					File thumb = new File(thumbPath);
					if (!thumb.exists()) {
						toGenerateThumbs.add(file);
					}
				}
			}
		}

		Collections.sort(folders);
		
		this.files.addAll(folders);
		this.files.addAll(files);
		
		if(this.files.size() == 0){
			findViewById(R.id.no_files).setVisibility(View.VISIBLE);
		}
		else{
			findViewById(R.id.no_files).setVisibility(View.GONE);
		}
		
		thumbGenTask = new GenerateThumbs();
		thumbGenTask.execute(toGenerateThumbs);
	}
	
	private void changeDir(String newPath){
		currentPath = newPath;
		selectedFiles.clear();
		pageNumber = 1;
		
		refreshList();
		
		if(fillCacheTask != null){
			fillCacheTask.cancel(true);
			fillCacheTask = null;
		}
		int[] params = {0, itemsPerPage};
		fillCacheTask = new FillCache();
		fillCacheTask.execute(params);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if(!currentPath.equals(Helpers.getHomeDir(this))){
				changeDir((new File(currentPath)).getParent());
				return true;
			}
		} 
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onPause() {
		super.onPause();

		Helpers.setLockedTime(this);

		if (thumbGenTask != null) {
			thumbGenTask.cancel(true);
			thumbGenTask = null;
		}
		if(fillCacheTask != null){
			fillCacheTask.cancel(true);
			fillCacheTask = null;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onResume() {
		super.onResume();

		if(!isWentToLogin){
			boolean logined = Helpers.checkLoginedState(this);
			Helpers.disableLockTimer(this);
	
			if (logined){
				if(thumbGenTask == null) {
					thumbGenTask = new GenerateThumbs();
					thumbGenTask.execute(toGenerateThumbs);
				}
				if(fillCacheTask == null){
					int[] params = {(pageNumber-1) * itemsPerPage, itemsPerPage};
					fillCacheTask = new FillCache();
					fillCacheTask.execute(params);
				}
			}
		}
	}

	private OnClickListener multiSelectClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_OFF) {
					((ImageButton) v).setImageResource(R.drawable.checkbox_checked);
					multiSelectMode = MULTISELECT_ON;
				}
				else {
					((ImageButton) v).setImageResource(R.drawable.checkbox_unchecked);
					multiSelectMode = MULTISELECT_OFF;
					clearMutliSelect();
				}
			}
		};
	}
	
	private void enterMultiSelect(){
		findViewById(R.id.bottomPanel).startAnimation(AnimationUtils.loadAnimation(GalleryActivity.this, android.R.anim.fade_out));
		findViewById(R.id.bottomPanel).setVisibility(View.GONE);
		
	    findViewById(R.id.bottomPanelMultiSelect).startAnimation(AnimationUtils.loadAnimation(GalleryActivity.this, android.R.anim.fade_in));
	    findViewById(R.id.bottomPanelMultiSelect).setVisibility(View.VISIBLE);
	}
	
	private void exitMultiSelect(){
		findViewById(R.id.bottomPanelMultiSelect).startAnimation(AnimationUtils.loadAnimation(GalleryActivity.this, android.R.anim.fade_out));
	    findViewById(R.id.bottomPanelMultiSelect).setVisibility(View.GONE);
	    
	    findViewById(R.id.bottomPanel).startAnimation(AnimationUtils.loadAnimation(GalleryActivity.this, android.R.anim.fade_in));
	    findViewById(R.id.bottomPanel).setVisibility(View.VISIBLE);
	}
	
	private OnClickListener importClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
				builder.setTitle(getString(R.string.import_desc));
				
				CharSequence[] items = {getString(R.string.import_unencrypted), getString(R.string.import_encrypted)};
				
				builder.setItems(items, new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(getBaseContext(), FileDialog.class);
						switch (which){
							case 0:
								
								intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

								// can user select directories or not
								intent.putExtra(FileDialog.CAN_SELECT_FILE, true);
								intent.putExtra(FileDialog.CAN_SELECT_DIR, false);
								intent.putExtra(FileDialog.SELECTION_MODE, FileDialog.MODE_OPEN);
								intent.putExtra(FileDialog.FILE_SELECTION_MODE, FileDialog.MODE_MULTIPLE);

								// alternatively you can set file filter
								// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
								// "png" });

								startActivityForResult(intent, REQUEST_ENCRYPT);
								break;
							case 1:
								intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

								// can user select directories or not
								intent.putExtra(FileDialog.CAN_SELECT_FILE, true);
								intent.putExtra(FileDialog.CAN_SELECT_DIR, false);
								intent.putExtra(FileDialog.SELECTION_MODE, FileDialog.MODE_OPEN);
								intent.putExtra(FileDialog.FILE_SELECTION_MODE, FileDialog.MODE_MULTIPLE);

								// alternatively you can set file filter
								// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
								// "png" });

								startActivityForResult(intent, REQUEST_IMPORT);
						}
					}
				});
				
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		};
	}
	
	private OnClickListener newFolderClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
				builder.setTitle(getString(R.string.new_folder));
				builder.setMessage(getString(R.string.enter_new_folder_name));
				final EditText input = new EditText(GalleryActivity.this);
				builder.setView(input);
				builder.setPositiveButton(getString(R.string.create), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						File newFolder = new File(currentPath + "/" + input.getText());
						if(newFolder.mkdir()){
							Toast.makeText(GalleryActivity.this, getString(R.string.success_created), Toast.LENGTH_LONG).show();
						}
						else{
							Toast.makeText(GalleryActivity.this, getString(R.string.failed_create_dir), Toast.LENGTH_LONG).show();
						}
						refreshList();
					}
				});
				builder.setNegativeButton(getString(R.string.cancel), null);
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		};
	}
	
	private OnClickListener moveSelectedClick() {
		return new OnClickListener() {
			
			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_ON) {
					moveSelected();
				}
			}
		};
	}
	
	private void moveSelected() {
		AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
		builder.setMessage(getString(R.string.choose_folder));
		
		final Spinner spinner = new Spinner(GalleryActivity.this);
		
		File homeFolder = new File(Helpers.getHomeDir(GalleryActivity.this));
		File[] dirs = homeFolder.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				if(pathname.isDirectory() && !pathname.getName().startsWith(".")){
					return true;
				}
				return false;
			}
		});
		
		Arrays.sort(dirs, new Comparator<File>() {
			public int compare(File lhs, File rhs) {
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});
		
		String[] folderNames;
		int count;
		final boolean isInHome;
		if(!currentPath.equals(Helpers.getHomeDir(GalleryActivity.this))){
			folderNames = new String[dirs.length+2];
			folderNames[0] = "--------";
			folderNames[1] = getString(R.string.home_dir);
			count = 2;
			isInHome = false;
		}
		else{
			folderNames = new String[dirs.length+1];
			folderNames[0] = "--------";
			count = 1;
			isInHome = true;
		}
		
		for(File dir : dirs){
			folderNames[count++] = dir.getName();
		}
		spinner.setAdapter(new ArrayAdapter<String>(GalleryActivity.this, android.R.layout.simple_spinner_dropdown_item, folderNames));
		builder.setView(spinner);
		
		builder.setPositiveButton(getString(R.string.move), new DialogInterface.OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(DialogInterface dialog, int whichButton) {
				File destDir;
				if(!isInHome && spinner.getSelectedItemPosition() == 1){
					destDir = new File(Helpers.getHomeDir(GalleryActivity.this));
				}
				else{
					destDir = new File(Helpers.getHomeDir(GalleryActivity.this), ((String)spinner.getSelectedItem()));
				}
				if(destDir.exists() && destDir.isDirectory()){
					new AsyncTasks.MoveFiles(GalleryActivity.this, destDir, new AsyncTasks.OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							refreshList();
						}
					}).execute(selectedFiles);
				}
				else{
					Toast.makeText(GalleryActivity.this, getString(R.string.failed_move_files), Toast.LENGTH_LONG).show();
				}
			}
		});
		builder.setNegativeButton(getString(R.string.cancel), null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private OnClickListener shareClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_ON) {
					Helpers.share(GalleryActivity.this, selectedFiles, new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							refreshList();
						}
					});
				}
			}
		};
	}

	private OnClickListener deleteSelectedClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_ON) {
					deleteSelected();
				}
			}
		};
	}

	private void refreshList(){
		fillFilesList();
		galleryAdapter.notifyDataSetChanged();
		clearMutliSelect();
		
		if(fillCacheTask == null){
			int[] params = {(pageNumber-1) * itemsPerPage, itemsPerPage};
			fillCacheTask = new FillCache();
			fillCacheTask.execute(params);
		}
	}
	
	private void deleteSelected() {
		AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
		builder.setMessage(String.format(getString(R.string.confirm_delete_files), String.valueOf(selectedFiles.size())));
		builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(DialogInterface dialog, int whichButton) {
				new AsyncTasks.DeleteFiles(GalleryActivity.this, new AsyncTasks.OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						refreshList();
					}
				}).execute(selectedFiles);
			}
		});
		builder.setNegativeButton(getString(R.string.no), null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private OnClickListener decryptSelectedClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_ON) {
					decryptSelected();
				}
			}
		};
	}

	private void decryptSelected() {
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
		intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

		// can user select directories or not
		intent.putExtra(FileDialog.CAN_SELECT_FILE, false);
		intent.putExtra(FileDialog.CAN_SELECT_DIR, true);

		// alternatively you can set file filter
		// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
		// "png" });

		startActivityForResult(intent, REQUEST_DECRYPT);
	}

	@SuppressWarnings("unchecked")
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == REQUEST_DECRYPT) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				File destinationFolder = new File(filePath);
				destinationFolder.mkdirs();
				new AsyncTasks.DecryptFiles(GalleryActivity.this, filePath).execute(selectedFiles);
			}
			else if (requestCode == REQUEST_ENCRYPT) {
				final String[] filePaths = data.getStringArrayExtra(FileDialog.RESULT_PATH);
				new EncryptFiles(GalleryActivity.this, currentPath, new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						refreshList();
						
						AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
						builder.setTitle(getString(R.string.delete_original_files));
						builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								ArrayList<File> filesToDelete = new ArrayList<File>();
								for(String filePath : filePaths){
									File file = new File(filePath);
									if(file.exists() && file.isFile()){
										filesToDelete.add(file);
									}
								}
								new AsyncTasks.DeleteFiles(GalleryActivity.this, new AsyncTasks.OnAsyncTaskFinish() {
									@Override
									public void onFinish() {
										Toast.makeText(GalleryActivity.this, getString(R.string.success_delete_originals), Toast.LENGTH_LONG).show();
									}
								}).execute(filesToDelete);
							}
						});
						builder.setNegativeButton(getString(R.string.no), null);
						AlertDialog dialog = builder.create();
						dialog.show();
					}
				}).execute(filePaths);
			}
			else if (requestCode == REQUEST_IMPORT) {
				final String[] filePaths = data.getStringArrayExtra(FileDialog.RESULT_PATH);

				AlertDialog.Builder dialog = new AlertDialog.Builder(this);

				LayoutInflater layoutInflater = LayoutInflater.from(this);
				final View enterPasswordView = layoutInflater.inflate(R.layout.dialog_import_password, null);

				dialog.setPositiveButton(getString(R.string.import_btn), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String importPassword = ((EditText) enterPasswordView.findViewById(R.id.password)).getText().toString();
						Boolean deleteAfterImport = ((CheckBox) enterPasswordView.findViewById(R.id.deleteAfterImport)).isChecked();

						HashMap<String, Object> params = new HashMap<String, Object>();

						params.put("filePaths", filePaths);
						params.put("password", importPassword);
						params.put("deleteAfterImport", deleteAfterImport);

						new ImportFiles(GalleryActivity.this, currentPath, new OnAsyncTaskFinish() {
							@Override
							public void onFinish(Integer result) {
								super.onFinish();
								refreshList();

								switch (result) {
									case AsyncTasks.ImportFiles.STATUS_OK:
										Toast.makeText(GalleryActivity.this, getString(R.string.success_import), Toast.LENGTH_LONG).show();
										break;
									case AsyncTasks.ImportFiles.STATUS_FAIL:
										Toast.makeText(GalleryActivity.this, getString(R.string.import_fialed), Toast.LENGTH_LONG).show();
										break;
								}
							}
						}).execute(params);
					}
				});

				dialog.setNegativeButton(getString(R.string.cancel), null);

				dialog.setView(enterPasswordView);
				dialog.setTitle(getString(R.string.enter_import_password));

				dialog.show();
			}
			else if (requestCode == REQUEST_VIEW_PHOTO) {
				if(data.hasExtra("needToRefresh") && data.getBooleanExtra("needToRefresh", false) == true){
					refreshList();
				}
			}
		}
		else if (resultCode == Activity.RESULT_CANCELED) {
			// Logger.getLogger().log(Level.WARNING, "file not selected");
		}

	}

	private void clearMutliSelect() {
		((ImageButton) findViewById(R.id.multi_select)).setImageResource(R.drawable.checkbox_unchecked);
		multiSelectMode = MULTISELECT_OFF;
		selectedFiles.clear();
		for (int i = 0; i < photosGrid.getChildCount(); i++) {
			((CheckableLayout) photosGrid.getChildAt(i)).setChecked(false);
		}

		if(multiSelectModeActive){
			exitMultiSelect();
			multiSelectModeActive = false;
		}
	}

	public class GalleryAdapter extends BaseAdapter {

		public int getCount() {
			return files.size();
		}

		public Object getItem(int position) {
			return files.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		private OnClickListener getOnClickListener(final CheckableLayout layout, final File file){
			 return new View.OnClickListener() {
				public void onClick(View v) {
					if (multiSelectMode == MULTISELECT_ON) {
						layout.toggle();
						if (layout.isChecked()) {
							selectedFiles.add(file);
						}
						else {
							selectedFiles.remove(file);
						}
						
						if(selectedFiles.size() > 0 && !multiSelectModeActive){
							enterMultiSelect();
							multiSelectModeActive = true;
						}
						else if(selectedFiles.size() == 0 && multiSelectModeActive){
							exitMultiSelect();
							multiSelectModeActive = false;
						}
					}
					else {
						Intent intent = new Intent();
						intent.setClass(GalleryActivity.this, ViewImageActivity.class);
						intent.putExtra("EXTRA_IMAGE_PATH", file.getPath());
						intent.putExtra("EXTRA_CURRENT_PATH", currentPath);
						startActivityForResult(intent, REQUEST_VIEW_PHOTO);
					}
				}
			};
		}
		private OnClickListener getNoThumbClickListener(final CheckableLayout layout, final File file){
			return new View.OnClickListener() {
				public void onClick(View v) {
					if (multiSelectMode == MULTISELECT_ON) {
						layout.toggle();
						if (layout.isChecked()) {
							selectedFiles.add(file);
						}
						else {
							selectedFiles.remove(file);
						}
						
						if(selectedFiles.size() > 0 && !multiSelectModeActive){
							enterMultiSelect();
							multiSelectModeActive = true;
						}
						else if(selectedFiles.size() == 0 && multiSelectModeActive){
							exitMultiSelect();
							multiSelectModeActive = false;
						}
					}
					else {
						doLongClick(file, v);
					}
				}
			};
		}
		
		private OnLongClickListener getOnLongClickListener(final File file){
			return new View.OnLongClickListener() {
				public boolean onLongClick(View v) {
					return doLongClick(file, v);
				}
			};
		}
		
		private OnClickListener getOnClickListenerLongAction(final File file){
			 return new View.OnClickListener() {
				public void onClick(View v) {
					doLongClick(file, v);
				}
			};
		}
		
		private boolean doLongClick(final File file, View v) {
			CharSequence[] listEntries = getResources().getStringArray(R.array.galleryItemActions);

			AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
			builder.setTitle(file.getName());
			builder.setItems(listEntries, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					selectedFiles.clear();
					selectedFiles.add(file);

					switch (item) {
						case ACTION_DECRYPT:
							decryptSelected();
							break;
						case ACTION_SHARE:
							Helpers.share(GalleryActivity.this, selectedFiles, new OnAsyncTaskFinish() {
								@Override
								public void onFinish() {
									super.onFinish();
									selectedFiles.clear();
									refreshList();
								}
							});
							break;
						case ACTION_MOVE:
							moveSelected();
							break;
						case ACTION_DELETE:
							deleteSelected();
							break;
					}
					
					dialog.dismiss();
				}
			}).show();

			return true;
		}
		
		private TextView getLabel(File file){
			TextView label = new TextView(GalleryActivity.this);
			label.setText(file.getName());
			label.setEllipsize(TruncateAt.END);
			label.setMaxLines(2);
			
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
			params.gravity = Gravity.CENTER_HORIZONTAL;
			params.topMargin = -15;
			label.setLayoutParams(params);
			
			return label;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			final CheckableLayout layout = new CheckableLayout(GalleryActivity.this);
			int thumbSize = Integer.valueOf(getString(R.string.thumb_size));
			layout.setGravity(Gravity.CENTER);
			layout.setLayoutParams(new GridView.LayoutParams(thumbSize, thumbSize));
			layout.setOrientation(LinearLayout.VERTICAL);
			
			final File file = files.get(position);
			
			if(file != null){
				if(file.isFile()){
					if(selectedFiles.contains(file)){
						layout.setChecked(true);
					}
		
					OnClickListener onClick = getOnClickListener(layout, file);
					OnLongClickListener onLongClick = getOnLongClickListener(file);
		
					if(noThumbs.contains(file)){
						ImageView fileImage = new ImageView(GalleryActivity.this);
						fileImage.setImageResource(R.drawable.file);
						fileImage.setPadding(3, 3, 3, 3);
						fileImage.setOnClickListener(getNoThumbClickListener(layout, file));
						fileImage.setOnLongClickListener(onLongClick);
						layout.addView(fileImage);
						layout.addView(getLabel(file));
					}
					else{
						String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + file.getName();
						Bitmap image = memCache.get(thumbPath);
						if (image != null) {
							ImageView imageView = new ImageView(GalleryActivity.this);
							imageView.setImageBitmap(image);
							imageView.setOnClickListener(onClick);
							imageView.setOnLongClickListener(onLongClick);
							imageView.setPadding(3, 3, 3, 3);
							imageView.setScaleType(ScaleType.FIT_CENTER);
							layout.addView(imageView);
						}
						else {
							if (toGenerateThumbs.contains(file)) {
								ProgressBar progress = new ProgressBar(GalleryActivity.this);
								progress.setOnClickListener(onClick);
								progress.setOnLongClickListener(onLongClick);
								progress.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
								layout.addView(progress);
							}
							else {
								if(position>= photosGrid.getFirstVisiblePosition() && position <= photosGrid.getLastVisiblePosition()){
									if(fillCacheTask == null){
										int[] params = {(pageNumber-1) * itemsPerPage, itemsPerPage};
										fillCacheTask = new FillCache();
										fillCacheTask.execute(params);
									}
								}
								ImageView fileImage = new ImageView(GalleryActivity.this);
								fileImage.setImageResource(R.drawable.file);
								fileImage.setPadding(3, 3, 3, 3);
								fileImage.setOnClickListener(onClick);
								fileImage.setOnLongClickListener(onLongClick);
								layout.addView(fileImage);
							}
					}
					}
				}
				else if(file.isDirectory()){
					ImageView folderImage = new ImageView(GalleryActivity.this);
					folderImage.setImageResource(R.drawable.folder);
					folderImage.setPadding(3, 3, 3, 3);
					folderImage.setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							if (multiSelectMode == MULTISELECT_ON) {
								layout.toggle();
								if (layout.isChecked()) {
									selectedFiles.add(file);
								}
								else {
									selectedFiles.remove(file);
								}
							}
							else {
								changeDir(file.getPath());
							}
						}
					});
					folderImage.setOnLongClickListener(new OnLongClickListener() {
						
						public boolean onLongClick(View v) {
							CharSequence[] listEntries = getResources().getStringArray(R.array.galleryFolderActions);

							AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
							builder.setTitle(file.getName());
							builder.setItems(listEntries, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int item) {
									selectedFiles.clear();
									selectedFiles.add(file);
									
									switch (item) {
										case ACTION_DELETE_FOLDER:
											deleteSelected();
											break;
									}
									dialog.dismiss();
								}
							}).show();

							return true;
						}
					});
					
					layout.addView(folderImage);
					layout.addView(getLabel(file));
				}
			}
			return layout;
		}
	}

	private OnScrollListener getOnScrollListener(){
		return new OnScrollListener() {
			
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				
			}
			
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				
				int lastVisiblePosition = firstVisibleItem + visibleItemCount;
				
				int pagedPosition = pageNumber * itemsPerPage;
				
				if(pagedPosition - lastVisiblePosition < loadThreshold){
					if(fillCacheTask == null){
						int[] params = {pageNumber * itemsPerPage, itemsPerPage};
						fillCacheTask = new FillCache();
						fillCacheTask.execute(params);
						pageNumber++;
					}
				}
				
				
				pagedPosition = (pageNumber-1) * itemsPerPage;
				if(pageNumber > 1 && lastVisiblePosition - pagedPosition + loadThreshold * 2  < loadThreshold){
					if(fillCacheTask == null){
						pageNumber--;
						int[] params = {(pageNumber - 1) * itemsPerPage, itemsPerPage, 1};
						fillCacheTask = new FillCache();
						fillCacheTask.execute(params);
					}
				}
				
			}
		};
	}
	
	private class FillCache extends AsyncTask<int[], Void, Void> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			((ProgressBar)findViewById(R.id.progressBar)).setVisibility(View.VISIBLE);
		}
		
		
		@Override
		protected Void doInBackground(int[]... params) {
			
			if(files.size() > 0 && params[0].length >= 2){
				int offset = params[0][0];
				int length = params[0][1];
				
				boolean reverse = false;
				if(params[0].length == 3 && params[0][2] == 1){
					reverse = true;
				}
				
				Context appContext = getApplicationContext(); 
				if(offset+length > files.size()){
					length = files.size() - offset - 1;
				}
				
				String thumbsDir = Helpers.getThumbsDir(GalleryActivity.this) + "/";
				
				/*if(reverse){
					Collections.sort(slicedArray);
				}*/
				
				int start;
				int end;
				if(!reverse){
					start = offset;
					end = offset+length;
				}
				else{
					start = offset+length;
					end = offset;
				}
				int i = start;
				while(true){
					if(files.size() > i){
						File file = files.get(i);
						if(file != null){
							try {
								String thumbPath = thumbsDir + file.getName();
								if(memCache.get(thumbPath) == null){
									FileInputStream input = new FileInputStream(thumbPath);
									memCache.put(thumbPath, Helpers.decodeBitmap(Helpers.getAESCrypt(appContext).decrypt(input, this), 300));
								}
								if(isCancelled()){
									break;
								}
								publishProgress();
							}
							catch (FileNotFoundException e) { }
						}
						if(i==end){
							break;
						}
						if(reverse){
							i--;
						}
						else{
							i++;
						}
					}
				}
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);

			galleryAdapter.notifyDataSetChanged();
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			galleryAdapter.notifyDataSetChanged();
			fillCacheTask = null;
			((ProgressBar)findViewById(R.id.progressBar)).setVisibility(View.GONE);
		}

	}
	
	private class GenerateThumbs extends AsyncTask<ArrayList<File>, Integer, Void> {

		@Override
		protected Void doInBackground(ArrayList<File>... params) {

			int i = 0;
			while (toGenerateThumbs.size() > 0) {
				File file = toGenerateThumbs.get(0);

				if (file.exists() && file.isFile()) {
					try {
						if(file.getPath().equals("/mnt/sdcard/SafeCamera/IMG_20120607_203926.jpg.sc")){
							Log.d("qaq", file.getPath());
						}
						FileInputStream inputStream = new FileInputStream(file);
						byte[] decryptedData = Helpers.getAESCrypt(GalleryActivity.this).decrypt(inputStream, null, this);

						if (decryptedData != null) {
							String thumbsDir = Helpers.getThumbsDir(GalleryActivity.this) + "/";
							memCache.put(thumbsDir + file.getName(), Helpers.generateThumbnail(GalleryActivity.this, decryptedData, file.getName()));
						}

						publishProgress(++i);

						if (isCancelled()) {
							break;
						}
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					toGenerateThumbs.remove(file);
				}

			}

			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			galleryAdapter.notifyDataSetChanged();
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			galleryAdapter.notifyDataSetChanged();
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.gallery_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		switch (item.getItemId()) {
			case R.id.select_all:
				if (multiSelectMode == MULTISELECT_OFF) {
					((ImageButton) findViewById(R.id.multi_select)).setImageResource(R.drawable.checkbox_checked);
					multiSelectMode = MULTISELECT_ON;
				}
				enterMultiSelect();
				selectedFiles.clear();
				selectedFiles.addAll(files);
				galleryAdapter.notifyDataSetChanged();
				return true;
			case R.id.deselect_all:
				if (multiSelectMode == MULTISELECT_ON) {
					((ImageButton) findViewById(R.id.multi_select)).setImageResource(R.drawable.checkbox_unchecked);
					multiSelectMode = MULTISELECT_OFF;
					clearMutliSelect();
				}
				exitMultiSelect();
				selectedFiles.clear();
				galleryAdapter.notifyDataSetChanged();
				
				return true;
			case R.id.change_password:
				intent.setClass(GalleryActivity.this, ChangePasswordActivity.class);
				startActivity(intent);
				return true;
			case R.id.settings:
				intent.setClass(GalleryActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case R.id.logout:
				Helpers.logout(GalleryActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	

}
