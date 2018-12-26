package com.fenritz.safecam;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils.TruncateAt;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
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

import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.AsyncTasks.EncryptFiles;
import com.fenritz.safecam.util.AsyncTasks.ImportFiles;
import com.fenritz.safecam.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.MemoryCache;
import com.fenritz.safecam.widget.CheckableLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import android.util.LruCache;

public class GalleryActivity extends Activity {

	public final MemoryCache memCache = SafeCameraApplication.getCache();
    protected final LruCache<String, String> labelCache = new LruCache<String, String>(100);

	public static final int REQUEST_DECRYPT = 0;
	public static final int REQUEST_ENCRYPT = 1;
	public static final int REQUEST_IMPORT = 2;
	public static final int REQUEST_VIEW_PHOTO = 3;
	public static final int REQUEST_LOGIN = 4;

	protected static final int ACTION_DECRYPT = 0;
	protected static final int ACTION_SHARE = 1;
	protected static final int ACTION_MOVE = 2;
	protected static final int ACTION_DELETE = 3;
	
	protected static final int ACTION_DELETE_FOLDER = 0;

    private File[] currentFolderFiles;

	private boolean multiSelectModeActive = false;

	private GridView photosGrid;

	private static ArrayList<File> files = new ArrayList<File>();
	
	private final ArrayList<File> selectedFiles = new ArrayList<File>();
	private final ArrayList<File> toGenerateThumbs = new ArrayList<File>();
	private final ArrayList<File> noThumbs = new ArrayList<File>();
	private final HashMap<String, String> foldersMap = new HashMap<String, String>();
	private final GalleryAdapter galleryAdapter = new GalleryAdapter();

	private GenerateThumbs thumbGenTask;

	private BroadcastReceiver receiver;
	
	private boolean isWentToLogin = false;
	private boolean sendBackDecryptedFile = false;
	private Intent originalIntent = null;
	
	private String currentPath;

	private String thumbsDir;
	
	private final CopyOnWriteArrayList<Dec> queue = new CopyOnWriteArrayList<Dec>();
	
	private int currentVisibleItemCount = 25;
	
	private ActionMode mMode;

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
            try {
                getActionBar().setDisplayHomeAsUpEnabled(true);
                getActionBar().setHomeButtonEnabled(true);
            }
            catch(NullPointerException e){ }
		}

        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

		setContentView(R.layout.gallery);
		
		setTitle(getString(R.string.title_gallery_for_app));

		this.thumbsDir = Helpers.getThumbsDir(this);

		Bundle bundle = new Bundle();
		bundle.putParcelable("intent", getIntent());
		bundle.putBoolean("wentToLoginToProceed", true);
		if(!Helpers.checkLoginedState(this, bundle)){
			isWentToLogin = true;
		}
		else{
			startupActions();
		}
	}
	
	protected void startupActions(){
		currentPath = Helpers.getHomeDir(this);
        resetListVars();
		fillFilesList();

		photosGrid = (GridView) findViewById(R.id.photosGrid);
		photosGrid.setAdapter(galleryAdapter);
		photosGrid.setColumnWidth(Helpers.getThumbSize(GalleryActivity.this)-10);
		//photosGrid.setOnScrollListener(getOnScrollListener());

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
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
	
	@SuppressLint("NewApi")
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
			else if ((Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) && type != null) {
				originalIntent = intent;
				sendBackDecryptedFile = true;
				if (Build.VERSION.SDK_INT >= 11) {
					invalidateOptionsMenu();
				}
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
			
			final String[] filePaths = {filePath};
			new EncryptFiles(GalleryActivity.this, currentPath, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					
					deleteOriginalsDialog(filePaths);
					
					refreshList();
				}
			}).execute(filePaths);
		}
	}
	
	private void decryptSelected(){
		final AsyncTasks.OnAsyncTaskFinish finishTask = new AsyncTasks.OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				
				exitMultiSelect();
			}
		};
		
		
		AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
		builder.setMessage(String.format(getString(R.string.confirm_decrypt_files), String.valueOf(selectedFiles.size())));
		builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(DialogInterface dialog, int whichButton) {
				Helpers.decryptSelected(GalleryActivity.this, selectedFiles, finishTask);
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
			
			final String[] finalFilePaths = filePaths;
			
			new EncryptFiles(GalleryActivity.this, currentPath, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					
					deleteOriginalsDialog(finalFilePaths);
					
					refreshList();
				}
			}).execute(filePaths);
		}
	}
	
	private class FillFilesList extends AsyncTask<Void, Void, ArrayList<File>> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			findViewById(R.id.fillFilesProgress).setVisibility(View.VISIBLE);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected ArrayList<File> doInBackground(Void... params) {

			ArrayList<File> filesToReturn = new ArrayList<File>();
			
			ArrayList<File> folders = new ArrayList<File>();
			ArrayList<File> files = new ArrayList<File>();

			boolean isDirCacheFound = false;
			foldersMap.clear();
			File dircacheFile = new File(currentPath + "/" + ".dirnamecache");
			if(dircacheFile.exists()){
				try {
					byte[] dirCacheBytes = Helpers.getAESCrypt(GalleryActivity.this).decrypt(new FileInputStream(dircacheFile));
					if(dirCacheBytes != null){
							ObjectInputStream objIn = new ObjectInputStream(new ByteArrayInputStream(dirCacheBytes));
							foldersMap.putAll((HashMap<String, String>) objIn.readObject());
							isDirCacheFound = true;
					}
				}
				catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}

            if(currentFolderFiles == null) {
                File dir = new File(currentPath);
                currentFolderFiles = dir.listFiles();

                if(currentFolderFiles != null) {
					// Sort all files by last modified time DESC
					Arrays.sort(currentFolderFiles, new Comparator<File>() {
						public int compare(File f1, File f2) {
							return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
						}
					});

					// Rebuild dir cache file if latest modified directory is earlier than cache file
					for (File file : currentFolderFiles){
						if(file.isDirectory()){
							if(!file.getName().startsWith(".") && file.lastModified() > dircacheFile.lastModified()){
								isDirCacheFound = false;
							}
						}
					}

					try {

						if(!isDirCacheFound) {
							// Build folder filename cache

							for (File file : currentFolderFiles) {
								if (file.isDirectory() && !file.getName().startsWith(".")) {
									foldersMap.put(file.getName(), Helpers.decryptFilename(GalleryActivity.this, file.getName()));
								}
							}

							ByteArrayOutputStream out = new ByteArrayOutputStream();
							ObjectOutputStream objOut = new ObjectOutputStream(out);
							objOut.writeObject(foldersMap);
							objOut.close();

							FileOutputStream encOut = new FileOutputStream(dircacheFile);
							Helpers.getAESCrypt(GalleryActivity.this).encrypt(out.toByteArray(), encOut);

						}
					}
					catch (OptionalDataException e) {
						e.printStackTrace();
					}
					catch (StreamCorruptedException e) {
						e.printStackTrace();
					}
					catch (IOException e) {
						e.printStackTrace();
					}

                }
            }

			if(currentFolderFiles != null){

				int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;

				String fileExt = getString(R.string.file_extension);


				for (File file : currentFolderFiles) {
					if(file.isDirectory() && !file.getName().startsWith(".")){
						folders.add(file);
					}
					else if(file.isFile() && file.getName().endsWith(fileExt)) {
						files.add(file);
						
						if(file.length() >= maxFileSize){
							noThumbs.add(file);
						}
						else{
							String thumbPath = thumbsDir + "/" + Helpers.getThumbFileName(file);
							File thumb = new File(thumbPath);
							if (!thumb.exists()) {
								toGenerateThumbs.add(file);
							}
						}
					}
				}
		
				Collections.sort(folders, new Comparator<File>() {
					public int compare(File f1, File f2) {
						String f1Filename = foldersMap.get(f1.getName());
						String f2Filename = foldersMap.get(f2.getName());
						if(f1Filename != null && f2Filename != null) {
							return String.CASE_INSENSITIVE_ORDER.compare(f1Filename, f2Filename);
						}
						return 0;
					}
				});



				filesToReturn.addAll(folders);
				filesToReturn.addAll(files);

			}
			return filesToReturn;
			
		}

        class FileTypeComparator implements Comparator<File> {

            public int compare(File file1, File file2) {

                if (file1.isDirectory() && file2.isFile())
                    return -1;
                if (file1.isDirectory() && file2.isDirectory()) {
                    return 0;
                }
                if (file1.isFile() && file2.isFile()) {
                    return 0;
                }
                return 1;
            }
        }

        class FileNameComparator implements Comparator<File> {

            public int compare(File file1, File file2) {

                return String.CASE_INSENSITIVE_ORDER.compare(file2.getName(),
                        file1.getName());
            }
        }

		@SuppressLint("NewApi")
		@SuppressWarnings("unchecked")
		@Override
		protected void onPostExecute(ArrayList<File> files) {
			super.onPostExecute(files);

			GalleryActivity.files  = files;
			
			if(files.size() == 0 && GalleryActivity.files.size() == 0){
				findViewById(R.id.no_files).setVisibility(View.VISIBLE);
                findViewById(R.id.photosGrid).setVisibility(View.GONE);
			}
			else{
				findViewById(R.id.no_files).setVisibility(View.GONE);
                findViewById(R.id.photosGrid).setVisibility(View.VISIBLE);
			}
			
			thumbGenTask = new GenerateThumbs();
			if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
				thumbGenTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, toGenerateThumbs);
			}
			else {
				thumbGenTask.execute(toGenerateThumbs);
			}
			
			findViewById(R.id.fillFilesProgress).setVisibility(View.GONE);

            galleryAdapter.notifyDataSetChanged();

        }

	}
	
	@SuppressLint("NewApi")
	private void fillFilesList() {
		(new FillFilesList()).execute();
	}


    private void resetListVars(){
        GalleryActivity.files.clear();
        currentFolderFiles = null;
        selectedFiles.clear();
        toGenerateThumbs.clear();
        noThumbs.clear();

        if (thumbGenTask != null) {
            thumbGenTask.cancel(true);
            thumbGenTask = null;
        }
    }

	@SuppressLint("NewApi")
	private void changeDir(String newPath){
		currentPath = newPath;

		refreshList();
		try {
            if (!newPath.equals(Helpers.getHomeDir(this))) {
                String[] splittedPath = newPath.split("/");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                    if (splittedPath.length > 0) {
                        getActionBar().setTitle(Helpers.decryptFilename(GalleryActivity.this, splittedPath[splittedPath.length - 1]));
                    } else {
                        getActionBar().setTitle(getString(R.string.title_gallery));
                    }
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                    getActionBar().setTitle(getString(R.string.title_gallery));
                }
            }
        }
        catch (NullPointerException e) { }
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
			}
		}
	}

	@SuppressLint("NewApi")
	private void enterMultiSelect(){
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			mMode = startActionMode(new MultiselectMode());
		}
	    multiSelectModeActive = true;
	}
	
	@SuppressLint("NewApi")
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
	
	private ArrayList<File> getDirectoryTree(String path){
		File myFolder = new File(path);
		File[] dirs = myFolder.listFiles(new FileFilter() {
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
		
		ArrayList<File> dirsToReturn = new ArrayList<File>(); 
		
		for(File dir : dirs){
			dirsToReturn.add(dir);
			dirsToReturn.addAll(getDirectoryTree(dir.getAbsolutePath()));
		}
		
		return dirsToReturn;
	}
	
	private void moveSelected() {
		AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
		builder.setMessage(getString(R.string.choose_folder));
		
		final Spinner spinner = new Spinner(GalleryActivity.this);
		
		String homeDir = Helpers.getHomeDir(GalleryActivity.this);
		
		ArrayList<File> dirs = getDirectoryTree(homeDir);
		
		String[] folderNames;
		int count;
		final boolean isInHome;
		if(!currentPath.equals(Helpers.getHomeDir(GalleryActivity.this))){
			folderNames = new String[dirs.size()+2];
			folderNames[0] = "--------";
			folderNames[1] = getString(R.string.home_dir);
			count = 2;
			isInHome = false;
		}
		else{
			folderNames = new String[dirs.size()+1];
			folderNames[0] = "--------";
			count = 1;
			isInHome = true;
		}
		
		
		final SparseArray<String> realFolderPaths = new SparseArray<String>();
		for(File dir : dirs){
			String parentFolder = dir.getParent();
			String relativePath = parentFolder.substring(homeDir.length());
			
			realFolderPaths.put(count, dir.getPath());
			
			String[] relativePathMembers = relativePath.split("/");
			relativePath = "";
			for(String relativePathItem : relativePathMembers){
				if(relativePathItem.startsWith("/")){
					relativePathItem = relativePathItem.substring(1);
				}
				if(relativePathItem.endsWith("/")){
					relativePathItem.substring(0, relativePathItem.length()-1);
				}
				if(relativePathItem.length() > 0){
					relativePath += Helpers.decryptFilename(GalleryActivity.this, relativePathItem) + "/";
				}
			}
			
			folderNames[count] = relativePath + Helpers.decryptFilename(GalleryActivity.this, dir.getName());
			count++;
		}
		spinner.setAdapter(new ArrayAdapter<String>(GalleryActivity.this, android.R.layout.simple_spinner_dropdown_item, folderNames));
		builder.setView(spinner);
		
		builder.setPositiveButton(getString(R.string.move), new DialogInterface.OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(DialogInterface dialog, int whichButton) {
				File destDir = null;
				if(!isInHome && spinner.getSelectedItemPosition() == 1){
					destDir = new File(Helpers.getHomeDir(GalleryActivity.this));
				}
				else{
					if(realFolderPaths.indexOfKey(spinner.getSelectedItemPosition()) >= 0){
						String folderPath = realFolderPaths.get(spinner.getSelectedItemPosition());
						destDir = new File(folderPath);
					}
				}
				if(destDir != null && destDir.exists() && destDir.isDirectory()){
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
        resetListVars();
		fillFilesList();
		exitMultiSelect();
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

	@SuppressWarnings("unchecked")
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == REQUEST_LOGIN) {
				if(data.getBooleanExtra("login_ok", false)){
					startupActions();
				}
				else{
					finish();
				}
			}
			else if (requestCode == REQUEST_DECRYPT) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				File destinationFolder = new File(filePath);
				destinationFolder.mkdirs();
				new AsyncTasks.DecryptFiles(GalleryActivity.this, filePath, new OnAsyncTaskFinish() {
					@Override
					public void onFinish(java.util.ArrayList<File> decryptedFiles) {
						if(decryptedFiles != null){
							for(File file : decryptedFiles){
								sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
							}
						}
						exitMultiSelect();
					}
				}).execute(selectedFiles);
			}
			else if (requestCode == REQUEST_ENCRYPT) {
				final String[] filePaths = data.getStringArrayExtra(FileDialog.RESULT_PATH);
				
				Arrays.sort(filePaths, new Comparator<String>() {
					public int compare(String lhs, String rhs) {
						File f1 = new File(lhs);
						File f2 = new File(rhs);
						if(f1.lastModified() > f2.lastModified()){
							return 1;
						}
						else if(f1.lastModified() < f2.lastModified()){
							return -1;
						}
						return 0;
					}
				});
				
				new EncryptFiles(GalleryActivity.this, currentPath, new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						
						deleteOriginalsDialog(filePaths);
						
						refreshList();
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
				if(data.hasExtra("needToRefresh") && data.getBooleanExtra("needToRefresh", false)){
					refreshList();
				}
			}
		}
	}

	private void deleteOriginalsDialog(final String[] filePaths){
		AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
		builder.setTitle(getString(R.string.delete_original_files));
		
		View checkBoxView = View.inflate(this, R.layout.dialog_delete_originals, null);
		final CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.secureDelete);
		
		builder.setView(checkBoxView);
		builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
			
			@SuppressWarnings("unchecked")
			@SuppressLint("NewApi")
			public void onClick(DialogInterface dialog, int whichButton) {
				
				ArrayList<File> filesToDelete = new ArrayList<File>();
				for(String filePath : filePaths){
					File file = new File(filePath);
					if(file.exists() && file.isFile()){
						filesToDelete.add(file);
					}
				}
				
				boolean isSecure = checkBox.isChecked();
				
				AsyncTasks.DeleteFiles deleteOrigFiles = new AsyncTasks.DeleteFiles(GalleryActivity.this, new AsyncTasks.OnAsyncTaskFinish() {
					@Override
					public void onFinish(ArrayList<File> deletedFiles) {
						if(deletedFiles != null){
							for(File file : deletedFiles){
								sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
							}
						}
						Toast.makeText(GalleryActivity.this, getString(R.string.success_delete_originals), Toast.LENGTH_LONG).show();
					}
				}, isSecure);
				
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
					deleteOrigFiles.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,filesToDelete);
				}
			    else{
			    	deleteOrigFiles.execute(filesToDelete);
			    }
				
			}
		});
		builder.setNegativeButton(getString(R.string.no), null);
		builder.setCancelable(false);
		AlertDialog dialog = builder.create();
		dialog.show();
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
				@SuppressWarnings("unchecked")
				public void onClick(View v) {
					if(sendBackDecryptedFile){
						String filePath = Helpers.getHomeDir(GalleryActivity.this) + "/" + ".tmp";
						File destinationFolder = new File(filePath);
						destinationFolder.mkdirs();

						AsyncTasks.OnAsyncTaskFinish finalOnDecrypt = new AsyncTasks.OnAsyncTaskFinish() {
							@Override
							public void onFinish(java.util.ArrayList<File> processedFiles) {
								if (processedFiles != null && processedFiles.size() == 1) {
									Uri fileUri = Uri.fromFile(processedFiles.get(0));
									if(originalIntent != null){
										if (null != fileUri.getPath()) {
											/*Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND, fileUri); //Create a new intent. First parameter means that you want to send the file. The second parameter is the URI pointing to a file on the sd card. (openprev has the datatype File)

								            GalleryActivity.this.setResult(Activity.RESULT_OK, shareIntent); //set the file/intent as result
								            GalleryActivity.this.finish(); //close your application and get back to the requesting application like GMail and WhatsApp
											*/
											originalIntent.setDataAndType(fileUri, getContentResolver().getType(fileUri));
											setResult(Activity.RESULT_OK, originalIntent);
										}
										else {
											originalIntent.setDataAndType(null, "");
											setResult(RESULT_CANCELED, originalIntent);
										}
									}
									GalleryActivity.this.finish();
								}
							};
						};
						ArrayList<File> filesToDec = new ArrayList<File>();
						filesToDec.add(file);
						new AsyncTasks.DecryptFiles(GalleryActivity.this, filePath, finalOnDecrypt).execute(filesToDec);
					}
					else if (multiSelectModeActive) {
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
					if(!sendBackDecryptedFile){
						return doLongClick(file, v);
					}
					return false;
				}
			};
		}
		
		private boolean doLongClick(final File file, View v) {
			CharSequence[] listEntries = getResources().getStringArray(R.array.galleryItemActions);

			AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
			builder.setTitle(Helpers.decryptFilename(GalleryActivity.this, file.getName()));
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

			String labelFromMap = foldersMap.get(file.getName());
			if(labelFromMap != null){
				label.setText(labelFromMap);
			}
			else {
				String decFilename = labelCache.get(file.getName());
				if (decFilename != null) {
					label.setText(decFilename);
				} else {
					boolean found = false;
					for (Dec item : queue) {
						if (item.fileName.equals(file.getName())) {
							synchronized (item.labels) {
								item.labels.add(label);
							}
							found = true;
						}
					}

					if (!found) {
						queue.add(new Dec(file.getName(), label));
						if (!decryptor.isAlive()) {
							try {
								decryptor.start();
							} catch (IllegalThreadStateException e) {
							}
						}
					}
				}
			}

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
						String thumbPath = thumbsDir + "/" + Helpers.getThumbFileName(file);
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
										try{
											decryptor.start();
										}
										catch(IllegalThreadStateException e){ }
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
							builder.setTitle(Helpers.decryptFilename(GalleryActivity.this, file.getName()));
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
	
	private static class Dec{
        public static int TYPE_IMAGE = 0;
        public static int TYPE_LABEL = 1;

        protected int type = TYPE_IMAGE;
        protected String fileName;
        public CopyOnWriteArrayList<ImageView> images = new CopyOnWriteArrayList<ImageView>();
        public CopyOnWriteArrayList<TextView> labels = new CopyOnWriteArrayList<TextView>();

		public Dec(String fileName, ImageView image){
            this.type = TYPE_IMAGE;
			this.fileName = fileName;
			this.images.add(image);
		}
		public Dec(String fileName, TextView label){
            this.type = TYPE_LABEL;
			this.fileName = fileName;
			this.labels.add(label);
		}

        public int getType(){
            return this.type;
        }

        public String getFilename(){
            return this.fileName;
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
                            if(item.getType() == Dec.TYPE_IMAGE) {
                                FileInputStream input = new FileInputStream(new File(item.getFilename()));
                                byte[] decryptedData = Helpers.getAESCrypt(GalleryActivity.this).decrypt(input);

                                if (decryptedData != null) {
                                    final Bitmap bitmap = Helpers.decodeBitmap(decryptedData, Helpers.getThumbSize(GalleryActivity.this));
                                    decryptedData = null;
                                    if (bitmap != null) {
                                        if (memCache != null) {
                                            memCache.put(item.getFilename(), bitmap);
                                        }
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                if (item.images.size() > 0) {
                                                    for (ImageView image : item.images) {
                                                        image.setImageBitmap(bitmap);
                                                    }
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                            else if(item.getType() == Dec.TYPE_LABEL) {
                                final String decFilename = Helpers.decryptFilename(GalleryActivity.this, item.getFilename());
                                if(decFilename != null){
                                    labelCache.put(item.getFilename(), decFilename);
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            if (item.labels.size() > 0) {
                                                for (TextView label : item.labels) {
                                                    label.setText(decFilename);
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
	
	private AbsListView.OnScrollListener getOnScrollListener(){
		return new AbsListView.OnScrollListener() {
            private static final String TAG = "EndlessScrollListener";


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

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private final class MultiselectMode implements ActionMode.Callback {
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        	getMenuInflater().inflate(R.menu.gallery_menu_multiselect, menu);
        	
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        	if (multiSelectModeActive) {
	        	return performMenuAction(item);
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
		if(!sendBackDecryptedFile){
			getMenuInflater().inflate(R.menu.gallery_menu, menu);
		}
        return super.onCreateOptionsMenu(menu);
	}
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (Build.VERSION.SDK_INT < 11 && !sendBackDecryptedFile) {
            menu.clear();
            if(multiSelectModeActive){
                getMenuInflater().inflate(R.menu.gallery_menu_multiselect, menu);
            }
            else {
                getMenuInflater().inflate(R.menu.gallery_menu, menu);
            }
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
        return performMenuAction(item);
	}

	private boolean performMenuAction(MenuItem item){
        boolean returnVal = true;

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
                break;
            case R.id.multi_select:
                enterMultiSelect();
                break;
            case R.id.multiselect_exit:
                exitMultiSelect();
                break;
            case R.id.goto_camera:
                intent.setClass(GalleryActivity.this, CameraActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                startActivity(intent);
                finish();
                break;
            case R.id.add_new_folder:
                AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
                builder.setTitle(getString(R.string.new_folder));
                builder.setMessage(getString(R.string.enter_new_folder_name));
                final EditText input = new EditText(GalleryActivity.this);
                builder.setView(input);
                builder.setPositiveButton(getString(R.string.create), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        File newFolder = new File(currentPath + "/" + Helpers.getNextAvailableFilePrefix(currentPath) + Helpers.encryptString(GalleryActivity.this, input.getText().toString()));
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
                break;
            case R.id.importBtn:
                importClick();
                break;
            case R.id.select_all:
                enterMultiSelect();
                selectedFiles.clear();
                selectedFiles.addAll(files);
                galleryAdapter.notifyDataSetChanged();
                break;
            case R.id.deselect_all:
                selectedFiles.clear();
                galleryAdapter.notifyDataSetChanged();
                break;
            case R.id.change_password:
                intent.setClass(GalleryActivity.this, ChangePasswordActivity.class);
                startActivity(intent);
                break;
            case R.id.settings:
                intent.setClass(GalleryActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.read_security:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.security_page_link)));
                startActivity(browserIntent);
                break;
            case R.id.logout:
                Helpers.logout(GalleryActivity.this);
                break;
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

            default:
                return super.onOptionsItemSelected(item);
        }

        return returnVal;
    }

}
