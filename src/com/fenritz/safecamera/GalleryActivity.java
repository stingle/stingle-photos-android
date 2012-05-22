package com.fenritz.safecamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
	protected static final int ACTION_DELETE = 2;

	private int multiSelectMode = MULTISELECT_OFF;

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
		findViewById(R.id.encryptFiles).setOnClickListener(encryptFilesClick());
		findViewById(R.id.import_btn).setOnClickListener(importClick());
		findViewById(R.id.share).setOnClickListener(shareClick());

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
			new EncryptFiles(GalleryActivity.this, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					
					fillFilesList();
					galleryAdapter.notifyDataSetChanged();
					clearMutliSelect();
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
			new EncryptFiles(GalleryActivity.this, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					
					fillFilesList();
					galleryAdapter.notifyDataSetChanged();
					clearMutliSelect();
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
		
		thumbGenTask = new GenerateThumbs();
		thumbGenTask.execute(toGenerateThumbs);
	}
	
	private void changeDir(String newPath){
		currentPath = newPath;
		selectedFiles.clear();
		pageNumber = 1;
		fillFilesList();
		galleryAdapter.notifyDataSetChanged();
		
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

	private OnClickListener importClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				Intent intent = new Intent(getBaseContext(), FileDialog.class);
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
		};
	}

	private OnClickListener shareClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_ON) {
					Helpers.share(GalleryActivity.this, selectedFiles, new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							fillFilesList();
							galleryAdapter.notifyDataSetChanged();
							clearMutliSelect();
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

	private void deleteSelected() {
		AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
		builder.setMessage(String.format(getString(R.string.confirm_delete_files), String.valueOf(selectedFiles.size())));
		builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(DialogInterface dialog, int whichButton) {
				new AsyncTasks.DeleteFiles(GalleryActivity.this, new AsyncTasks.OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						fillFilesList();
						galleryAdapter.notifyDataSetChanged();
						clearMutliSelect();
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

	private OnClickListener encryptFilesClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				Intent intent = new Intent(getBaseContext(), FileDialog.class);
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
			}
		};
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
				String[] filePaths = data.getStringArrayExtra(FileDialog.RESULT_PATH);
				new EncryptFiles(GalleryActivity.this).execute(filePaths);
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

						new ImportFiles(GalleryActivity.this, new OnAsyncTaskFinish() {
							@Override
							public void onFinish(Integer result) {
								super.onFinish();
								fillFilesList();
								galleryAdapter.notifyDataSetChanged();
								clearMutliSelect();

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
					fillFilesList();
					galleryAdapter.notifyDataSetChanged();
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
									fillFilesList();
									galleryAdapter.notifyDataSetChanged();
									clearMutliSelect();
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
						fileImage.setOnClickListener(getOnClickListenerLongAction(file));
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
							layout.addView(imageView);
						}
						else {
							if (toGenerateThumbs.contains(file)) {
								ProgressBar progress = new ProgressBar(GalleryActivity.this);
								progress.setOnClickListener(onClick);
								progress.setOnLongClickListener(onLongClick);
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
							changeDir(file.getPath());
						}
					});
					//fileImage.setOnLongClickListener(onLongClick);
					
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
					File file = files.get(i);
					if(file != null){
						try {
							String thumbPath = thumbsDir + file.getName();
							if(memCache.get(thumbPath) == null){
								memCache.put(thumbPath, Helpers.decodeBitmap(Helpers.getAESCrypt(appContext).decrypt(new FileInputStream(thumbPath), this), 300));
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
					if(!reverse){
						i++;
					}
					else{
						i--;
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
			case R.id.change_password:
				intent.setClass(GalleryActivity.this, ChangePasswordActivity.class);
				startActivity(intent);
				return true;
			case R.id.settings:
				intent.setClass(GalleryActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	

}
