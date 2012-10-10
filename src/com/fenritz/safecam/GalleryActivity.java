package com.fenritz.safecam;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import android.annotation.SuppressLint;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.AsyncTasks.EncryptFiles;
import com.fenritz.safecam.util.AsyncTasks.ImportFiles;
import com.fenritz.safecam.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.MemoryCache;
import com.fenritz.safecam.widget.CheckableLayout;

public class GalleryActivity extends SherlockActivity {

	public final MemoryCache memCache = SafeCameraApplication.getCache();

	protected static final int REQUEST_DECRYPT = 0;
	protected static final int REQUEST_ENCRYPT = 1;
	protected static final int REQUEST_IMPORT = 2;
	protected static final int REQUEST_VIEW_PHOTO = 3;

	protected static final int ACTION_DECRYPT = 0;
	protected static final int ACTION_SHARE = 1;
	protected static final int ACTION_MOVE = 2;
	protected static final int ACTION_DELETE = 3;
	
	protected static final int ACTION_DELETE_FOLDER = 0;

	private boolean multiSelectModeActive = false;

	private GridView photosGrid;

	private final ArrayList<File> files = new ArrayList<File>();
	
	private final ArrayList<File> selectedFiles = new ArrayList<File>();
	private final ArrayList<File> toGenerateThumbs = new ArrayList<File>();
	private final ArrayList<File> noThumbs = new ArrayList<File>();
	private final GalleryAdapter galleryAdapter = new GalleryAdapter();

	private GenerateThumbs thumbGenTask;
	//private final FillCache fillCacheTask = new FillCache();

	private BroadcastReceiver receiver;
	
	private boolean isWentToLogin = false;
	
	private String currentPath;
	
	private final ArrayList<Dec> queue = new ArrayList<Dec>();
	
	private int currentVisibleItemCount = 25;
	
	private ActionMode mMode;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		setContentView(R.layout.gallery);

		Bundle bundle = new Bundle();
		bundle.putParcelable("intent", getIntent());
		bundle.putBoolean("wentToLoginToProceed", true);
		if(!Helpers.checkLoginedState(this, bundle)){
			isWentToLogin = true;
			finish();
			return;
		}
		
		currentPath = Helpers.getHomeDir(this);
		fillFilesList();

		photosGrid = (GridView) findViewById(R.id.photosGrid);
		photosGrid.setAdapter(galleryAdapter);
		photosGrid.setColumnWidth(Helpers.getThumbSize(GalleryActivity.this)-10);
		photosGrid.setOnScrollListener(getOnScrollListener());

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
		decryptor.interrupt();
		
		if(receiver != null){
			unregisterReceiver(receiver);
		}
		super.onDestroy();
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

		if(folderFiles != null){
			int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;
			
			Arrays.sort(folderFiles, Collections.reverseOrder());
			/*Arrays.sort(folderFiles, new Comparator<File>() {
				public int compare(File lhs, File rhs) {
					if(rhs.lastModified() > lhs.lastModified()){
						return 1;
					}
					else if(rhs.lastModified() < lhs.lastModified()){
						return -1;
					}
					return 0;
				}
			});*/
			
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
						String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + Helpers.getThumbFileName(file);
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
	}
	
	private void changeDir(String newPath){
		currentPath = newPath;
		selectedFiles.clear();
		
		refreshList();
		
		if(!newPath.equals(Helpers.getHomeDir(this))){
			String[] splittedPath = newPath.split("/");
			getSupportActionBar().setTitle(splittedPath[splittedPath.length - 1]);
		}
		else{
			getSupportActionBar().setTitle(getString(R.string.title_gallery));
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if(currentPath != null && !currentPath.equals(Helpers.getHomeDir(this))){
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
		/*if(fillCacheTask != null){
			fillCacheTask.cancel(true);
			fillCacheTask = null;
		}*/
		
		decryptor.interrupt();
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
				//generateVisibleThumbs();
			}
		}
	}

	private void enterMultiSelect(){
	    mMode = startActionMode(new MultiselectMode());
	    multiSelectModeActive = true;
	}
	
	private void exitMultiSelect(){
	    if (mMode != null) {
            mMode.finish();
        }
	    clearSelection();
	    multiSelectModeActive = false;
	}
	
	private void importClick() {
		AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
		builder.setTitle(getString(R.string.import_desc));
		
		CharSequence[] items = getResources().getStringArray(R.array.importMenu);
		
		builder.setItems(items, new DialogInterface.OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(getBaseContext(), FileDialog.class);
				switch (which){
					case 0:
						Intent photosIntent = new Intent();
						photosIntent.setClass(GalleryActivity.this, ImportPhotosActivity.class);
						startActivityForResult(photosIntent, REQUEST_ENCRYPT);
						
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

						startActivityForResult(intent, REQUEST_ENCRYPT);
						break;
					case 2:
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
		builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				refreshList();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void refreshList(){
		fillFilesList();
		galleryAdapter.notifyDataSetChanged();
		exitMultiSelect();
		
		//generateVisibleThumbs();
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
		builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				refreshList();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
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
				new AsyncTasks.DecryptFiles(GalleryActivity.this, filePath, new OnAsyncTaskFinish() {
					@Override
					public void onFinish(java.util.ArrayList<File> decryptedFiles) {
						for(File file : decryptedFiles){
							sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
						}
						exitMultiSelect();
					}
				}).execute(selectedFiles);
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
									public void onFinish(ArrayList<File> deletedFiles) {
										for(File file : deletedFiles){
											sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
										}
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
			refreshList();
		}

	}

	private void clearSelection() {
		selectedFiles.clear();
		for (int i = 0; i < photosGrid.getChildCount(); i++) {
			((CheckableLayout) photosGrid.getChildAt(i)).setChecked(false);
		}
		galleryAdapter.notifyDataSetChanged();
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
					if (multiSelectModeActive) {
						layout.toggle();
						if (layout.isChecked()) {
							selectedFiles.add(file);
						}
						else {
							selectedFiles.remove(file);
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
					if (multiSelectModeActive) {
						layout.toggle();
						if (layout.isChecked()) {
							selectedFiles.add(file);
						}
						else {
							selectedFiles.remove(file);
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
		
		@SuppressLint("NewApi")
		public View getView(int position, View convertView, ViewGroup parent) {
			final CheckableLayout layout = new CheckableLayout(GalleryActivity.this);
			int thumbSize = Helpers.getThumbSize(GalleryActivity.this);
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
						String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + Helpers.getThumbFileName(file);
						if (toGenerateThumbs.contains(file)) {
							ProgressBar progress = new ProgressBar(GalleryActivity.this);
							progress.setOnClickListener(onClick);
							progress.setOnLongClickListener(onLongClick);
							progress.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
							layout.addView(progress);
						}
						else{
							ImageView imageView = new ImageView(GalleryActivity.this);
							
							Bitmap image = memCache.get(thumbPath);
							if(image != null){
								imageView.setImageBitmap(image);
							}
							else{
								imageView.setImageResource(R.drawable.file);
								
								boolean found = false;
								for(Dec item : queue){
									if(item.fileName.equals(thumbPath)){
										synchronized (item.images) {
											item.images.add(imageView);
										}
										found = true;
									}
								}
								
								if(!found){
									queue.add(new Dec(thumbPath, imageView));
									if(!decryptor.isAlive()){
										decryptor.start();
									}
								}
							}
							imageView.setOnClickListener(onClick);
							imageView.setOnLongClickListener(onLongClick);
							imageView.setPadding(3, 3, 3, 3);
							imageView.setScaleType(ScaleType.FIT_CENTER);
							layout.addView(imageView);
						}
					}
				}
				else if(file.isDirectory()){
					ImageView folderImage = new ImageView(GalleryActivity.this);
					folderImage.setImageResource(R.drawable.folder);
					folderImage.setPadding(3, 3, 3, 3);
					folderImage.setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							if (multiSelectModeActive) {
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
	
	private class Dec{
		public String fileName;
		public ArrayList<ImageView> images = new ArrayList<ImageView>();
		
		public Dec(String fileName, ImageView image){
			this.fileName = fileName;
			this.images.add(image);
		}
	}
	
	private final Thread decryptor = new Thread(){
	    @Override
	    public void run() {
	    	while(!isInterrupted()){
				try {
					int size = queue.size();
					if(size > 0){
						
						if(size > currentVisibleItemCount + 3){
							for(int i = 0; i < size - (currentVisibleItemCount + 3); i++){
								queue.remove(queue.get(i));
								i--;
								size--;
							}
						}
						
						for(int i = 0; i < size; i++){
							final Dec item = queue.get(i);
							FileInputStream input = new FileInputStream(new File(item.fileName));
							byte[] decryptedData = Helpers.getAESCrypt(GalleryActivity.this).decrypt(input);

							if (decryptedData != null) {
								final Bitmap bitmap = Helpers.decodeBitmap(decryptedData, Helpers.getThumbSize(GalleryActivity.this));
								decryptedData = null;
								if (bitmap != null) {
									if(memCache != null){
										memCache.put(item.fileName, bitmap);
									}
									runOnUiThread(new Runnable() {
										public void run() {
											if(item.images.size() > 0){
												for(ImageView image : item.images){
													image.setImageBitmap(bitmap);
												}
											}
										}
									});
								}
							}
							
							queue.remove(item);
							i--;
							size--;
						}
					}
					sleep(50);
				}
				catch (InterruptedException e) { }
				catch (FileNotFoundException e) { }
			}
	    }
	};
	
	private OnScrollListener getOnScrollListener(){
		return new OnScrollListener() {
			
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				
			}
			
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if(visibleItemCount >= 12){
					currentVisibleItemCount = visibleItemCount;
				}
			}
		};
	}
	
	
	private class GenerateThumbs extends AsyncTask<ArrayList<File>, Integer, Void> {

		@Override
		protected Void doInBackground(ArrayList<File>... params) {

			int i = 0;
			while (toGenerateThumbs.size() > 0) {
				File file = toGenerateThumbs.get(0);

				if (file.exists() && file.isFile()) {
					try {
						FileInputStream inputStream = new FileInputStream(file);
						byte[] decryptedData = Helpers.getAESCrypt(GalleryActivity.this).decrypt(inputStream, null, this);

						String thumbsDir = Helpers.getThumbsDir(GalleryActivity.this) + "/";
						String fileName = Helpers.getThumbFileName(file);
						String key = thumbsDir + fileName;
						if (decryptedData != null) {
							memCache.put(key, Helpers.generateThumbnail(GalleryActivity.this, decryptedData, fileName));
						}
						
						if(memCache.get(key) == null){
							noThumbs.add(file);
						}

						publishProgress(++i);

						if (isCancelled()) {
							break;
						}
					}
					catch (FileNotFoundException e) {
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

    private final class MultiselectMode implements ActionMode.Callback {
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        	getSupportMenuInflater().inflate(R.menu.gallery_menu_multiselect, menu);
        	
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        	if (multiSelectModeActive) {
	        	switch(item.getItemId()){
	        		case R.id.decrypt:
    					decryptSelected();
	        			break;
	        		case R.id.share:
    					Helpers.share(GalleryActivity.this, selectedFiles, new OnAsyncTaskFinish() {
    						@Override
    						public void onFinish() {
    							super.onFinish();
    							refreshList();
    						}
    					});
	        			break;
	        		case R.id.move:
    					moveSelected();
	        			break;
	        		case R.id.delete:
    					deleteSelected();
	        			break;
	        		case R.id.select_all:
	    				selectedFiles.clear();
	    				selectedFiles.addAll(files);
	    				galleryAdapter.notifyDataSetChanged();
	    				return true;
	        		case R.id.deselect_all:
	    				selectedFiles.clear();
	    				galleryAdapter.notifyDataSetChanged();
	    				return true;
	        	}
        	}
            return true;
        }

        public void onDestroyActionMode(ActionMode mode) {
        	multiSelectModeActive = false;
        	clearSelection();
        }
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.gallery_menu, menu);
        return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		switch (item.getItemId()) {
			case android.R.id.home:
				if(currentPath != null && !currentPath.equals(Helpers.getHomeDir(this))){
					changeDir((new File(currentPath)).getParent());
				}
				else{
					intent.setClass(GalleryActivity.this, DashboardActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(intent);
					finish();
				}
				return true;
			case R.id.multi_select:
				enterMultiSelect();
				return true;
			case R.id.goto_camera:
				intent.setClass(GalleryActivity.this, CameraActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
				startActivity(intent);
				finish();
				return true;
			case R.id.add_new_folder:
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
				return true;
			case R.id.importBtn:
				importClick();
				return true;
			case R.id.select_all:
				enterMultiSelect();
				selectedFiles.clear();
				selectedFiles.addAll(files);
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
			case R.id.read_security:
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.security_page_link)));
				startActivity(browserIntent);
				return true;
			case R.id.logout:
				Helpers.logout(GalleryActivity.this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	

}
