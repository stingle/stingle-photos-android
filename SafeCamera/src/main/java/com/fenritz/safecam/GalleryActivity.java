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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecam.Util.AsyncTasks;
import com.fenritz.safecam.Util.AsyncTasks.EncryptFiles;
import com.fenritz.safecam.Util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.LoginManager;
import com.fenritz.safecam.Util.MemoryCache;
import com.fenritz.safecam.Widget.CheckableLayout;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

public class GalleryActivity extends Activity {

	public final MemoryCache memCache = SafeCameraApplication.getCache();

	public static final int REQUEST_DECRYPT = 0;
	public static final int REQUEST_ENCRYPT = 1;
	public static final int REQUEST_VIEW_PHOTO = 2;
	public static final int REQUEST_LOGIN = 3;

	protected static final int ACTION_DECRYPT = 0;
	protected static final int ACTION_SHARE = 1;
	protected static final int ACTION_DELETE = 2;
	
	protected static final int ACTION_DELETE_FOLDER = 0;

	private boolean multiSelectModeActive = false;

	private GridView photosGrid;

	private ArrayList<GalleryItem> galleryItems = new ArrayList<GalleryItem>();
	private ArrayList<File> selectedFiles = new ArrayList<File>();

	private final ArrayList<GalleryItem> toGenerateThumbs = new ArrayList<GalleryItem>();
	private final ArrayList<File> noThumbs = new ArrayList<File>();
	private final GalleryAdapter galleryAdapter = new GalleryAdapter();

	private GenerateThumbs thumbGenTask;

	private BroadcastReceiver receiver;
	
	private boolean isWentToLogin = false;
	private boolean sendBackDecryptedFile = false;
	private Intent originalIntent = null;
	
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
	}
	
	protected void startupActions(){
        resetListVars();

		photosGrid = (GridView) findViewById(R.id.photosGrid);
		photosGrid.setAdapter(galleryAdapter);
		photosGrid.setColumnWidth(Helpers.getThumbSize(GalleryActivity.this)-10);
		//photosGrid.setOnScrollListener(getOnScrollListener());

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

		handleIntentFilters(getIntent());
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);

		LoginManager.checkLogin(this, new LoginManager.UserLogedinCallback() {
			@Override
			public void onUserLoginSuccess() {
				Handler mainHandler = new Handler(Looper.getMainLooper());
				Runnable myRunnable = new Runnable() {
					@Override
					public void run() {
						startupActions();
						if(thumbGenTask == null) {
							thumbGenTask = new GenerateThumbs();
							thumbGenTask.execute(toGenerateThumbs);
						}
						fillFilesList();
					}
				};
				mainHandler.post(myRunnable);
			}

			@Override
			public void onUserLoginFail() {
				LoginManager.redirectToDashboard(GalleryActivity.this);
			}
		});
		LoginManager.disableLockTimer(this);
	}

	@Override
	protected void onPause() {
		super.onPause();

		LoginManager.setLockedTime(this);

		if (thumbGenTask != null) {
			thumbGenTask.cancel(true);
			thumbGenTask = null;
		}
		if(receiver != null){
			unregisterReceiver(receiver);
		}

		decryptor.interrupt();
	}

	@Override
	protected void onDestroy() {
		decryptor.interrupt();
		
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
			new EncryptFiles(GalleryActivity.this, Helpers.getHomeDir(this), new OnAsyncTaskFinish() {
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
			
			new EncryptFiles(GalleryActivity.this, Helpers.getHomeDir(this), new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					
					deleteOriginalsDialog(finalFilePaths);
					
					refreshList();
				}
			}).execute(filePaths);
		}
	}
	
	private class FillFilesList extends AsyncTask<Void, Void, Void> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			findViewById(R.id.fillFilesProgress).setVisibility(View.VISIBLE);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected Void doInBackground(Void... params) {

			if(galleryItems.size() == 0){

				int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;
				File dir = new File(Helpers.getHomeDir(GalleryActivity.this));

				File[] currentFolderFiles = dir.listFiles();

				for (File file : currentFolderFiles) {
					if(file.isFile() && file.getName().endsWith(SafeCameraApplication.FILE_EXTENSION)) {

						GalleryItem item = new GalleryItem();

						Crypto.Header header = null;

						try {
							header = SafeCameraApplication.getCrypto().getFileHeader(new FileInputStream(file));
						}
						catch (IOException | CryptoException e) { }

						item.file = file;
						item.header = header;

						if(header != null && (header.fileType == Crypto.FILE_TYPE_PHOTO || header.fileType == Crypto.FILE_TYPE_VIDEO)){
							String thumbPath = thumbsDir + "/" + file.getName();
							File thumb = new File(thumbPath);
							if (!thumb.exists() && (header == null || header.fileType != Crypto.FILE_TYPE_VIDEO)) {
								toGenerateThumbs.add(item);
							}
						}
						else{
							noThumbs.add(file);
						}

						galleryItems.add(item);
					}
				}
		
				Collections.sort(galleryItems, new Comparator<GalleryItem>() {
					public int compare(GalleryItem f1, GalleryItem f2) {
						long mod1 = f1.file.lastModified();
						long mod2 = f2.file.lastModified();
						if(mod1 != 0L && mod2 != 0L) {
							if(mod1<mod2){
								return 1;
							}
							else{
								return -1;
							}
						}
						return 0;
					}
				});
			}
			return null;
		}


		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			if(galleryItems.size() == 0){
				findViewById(R.id.no_files).setVisibility(View.VISIBLE);
                findViewById(R.id.photosGrid).setVisibility(View.GONE);
			}
			else{
				findViewById(R.id.no_files).setVisibility(View.GONE);
                findViewById(R.id.photosGrid).setVisibility(View.VISIBLE);
			}

			thumbGenTask = new GenerateThumbs();
			thumbGenTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, toGenerateThumbs);

			findViewById(R.id.fillFilesProgress).setVisibility(View.GONE);

            galleryAdapter.notifyDataSetChanged();

        }

	}
	
	@SuppressLint("NewApi")
	private void fillFilesList() {
		(new FillFilesList()).execute();
	}


    private void resetListVars(){
		galleryItems.clear();
        selectedFiles.clear();
        toGenerateThumbs.clear();
        noThumbs.clear();

        if (thumbGenTask != null) {
            thumbGenTask.cancel(true);
            thumbGenTask = null;
        }
    }


	@SuppressLint("NewApi")
	private void enterMultiSelect(){
		mMode = startActionMode(new MultiselectMode());
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
				
				new EncryptFiles(GalleryActivity.this, Helpers.getHomeDir(this), new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						
						deleteOriginalsDialog(filePaths);
						
						refreshList();
					}
				}).execute(filePaths);
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
			return galleryItems.size();
		}

		public Object getItem(int position) {
			return galleryItems.get(position);
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
			builder.setTitle(Helpers.decryptFilename(file.getPath()));
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
						case ACTION_DELETE:
							deleteSelected();
							break;
					}
					
					dialog.dismiss();
				}
			}).show();

			return true;
		}
		
		private TextView getLabel(GalleryItem item){
			TextView label = new TextView(GalleryActivity.this);

			String labelFromMap = null;
			if(item.header != null){
				labelFromMap = item.header.filename;
			}
			if(labelFromMap != null){
				label.setText(labelFromMap);
			}

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
			int thumbSize = Helpers.getThumbSize(GalleryActivity.this);
			layout.setGravity(Gravity.CENTER);
			layout.setLayoutParams(new GridView.LayoutParams(thumbSize, thumbSize));
			//layout.setOrientation(LinearLayout.VERTICAL);
			
			final GalleryItem item = galleryItems.get(position);

			if(item.file != null){
				if(item.file.isFile()){
					if(selectedFiles.contains(item.file)){
						layout.setChecked(true);
					}
		
					OnClickListener onClick = getOnClickListener(layout, item.file);
					OnLongClickListener onLongClick = getOnLongClickListener(item.file);
		
					if(noThumbs.contains(item.file)){
						ImageView fileImage = new ImageView(GalleryActivity.this);
						fileImage.setImageResource(R.drawable.file);
						fileImage.setPadding(3, 3, 3, 3);
						fileImage.setOnClickListener(getNoThumbClickListener(layout, item.file));
						fileImage.setOnLongClickListener(onLongClick);
						layout.addView(fileImage);
						layout.addView(getLabel(item));
					}
					else{
						String thumbPath = thumbsDir + "/" + item.file.getName();
						if (toGenerateThumbs.contains(item.file)) {
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
								for(Dec ditem : queue){
									if(ditem.fileName.equals(thumbPath)){
										synchronized (ditem.images) {
											ditem.images.add(imageView);
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

							if(item.header != null && item.header.fileType == Crypto.FILE_TYPE_VIDEO){
								ImageView videoIcon = new ImageView(GalleryActivity.this);
								videoIcon.setImageResource(R.drawable.ic_video);
								videoIcon.setTop(0);
								RelativeLayout.LayoutParams layParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
								layParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
								videoIcon.setLayoutParams(layParams);
								layout.addView(videoIcon);
							}
						}
					}
				}
			}
			return layout;
		}
	}
	
	private static class Dec{
        public static int TYPE_IMAGE = 0;

        protected int type = TYPE_IMAGE;
        protected String fileName;
        public CopyOnWriteArrayList<ImageView> images = new CopyOnWriteArrayList<ImageView>();
        public CopyOnWriteArrayList<TextView> labels = new CopyOnWriteArrayList<TextView>();

		public Dec(String fileName, ImageView image){
            this.type = TYPE_IMAGE;
			this.fileName = fileName;
			this.images.add(image);
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
					if (size > 0) {

						if (size > currentVisibleItemCount + 3) {
							for (int i = 0; i < size - (currentVisibleItemCount + 3); i++) {
								queue.remove(queue.get(i));
								i--;
								size--;
							}
						}

						for (int i = 0; i < size; i++) {
							final Dec item = queue.get(i);
							try {
								if (item.getType() == Dec.TYPE_IMAGE) {
									FileInputStream input = new FileInputStream(new File(item.getFilename()));
									byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(input);

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

								queue.remove(item);
								i--;
								size--;
							}
							catch (Exception e) {
								queue.remove(item);
								i--;
								size--;
							}
						}
					}
					sleep(50);
				}
				catch (InterruptedException e) { }
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
	
	
	private class GenerateThumbs extends AsyncTask<ArrayList<GalleryItem>, Integer, Void> {

		@Override
		protected Void doInBackground(ArrayList<GalleryItem>... params) {

			int i = 0;
			while (toGenerateThumbs.size() > 0) {
				GalleryItem item = toGenerateThumbs.get(0);

				if (item.file.exists() && item.file.isFile()) {
					try {
						FileInputStream inputStream = new FileInputStream(item.file);
						//byte[] decryptedData = Helpers.getAESCrypt(GalleryActivity.this).decrypt(inputStream, null, this);
						byte[] decryptedData = SafeCameraApplication.getCrypto().decryptFile(inputStream);

						String key = thumbsDir + item.file.getName();
						if (decryptedData != null) {
							memCache.put(key, Helpers.generateThumbnail(GalleryActivity.this, decryptedData, item.file.getName(), item.header.fileId));
						}
						
						if(memCache.get(key) == null){
							noThumbs.add(item.file);
						}

						publishProgress(++i);

						if (isCancelled()) {
							break;
						}
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (CryptoException e) {
						e.printStackTrace();
					}
					toGenerateThumbs.remove(item);
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
		if (!sendBackDecryptedFile) {
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
            case R.id.multi_select:
                enterMultiSelect();
                break;
            case R.id.multiselect_exit:
                exitMultiSelect();
                break;
            case R.id.goto_camera:
                intent.setClass(GalleryActivity.this, Camera2Activity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                startActivity(intent);
                finish();
                break;
            case R.id.importBtn:
				Intent photosIntent = new Intent();
				photosIntent.setClass(GalleryActivity.this, ImportPhotosActivity.class);
				startActivityForResult(photosIntent, REQUEST_ENCRYPT);
                break;
            case R.id.select_all:
                enterMultiSelect();
                selectedFiles.clear();
                for(Object galleryItem : galleryItems.toArray()){
					selectedFiles.add(((GalleryItem)galleryItem).file);
				}
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
                LoginManager.logout(GalleryActivity.this);
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
            case R.id.delete:
                deleteSelected();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return returnVal;
    }

    public class GalleryItem{
		public File file = null;
		public Crypto.Header header;
	}

}
