package com.fenritz.safecam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.MemoryCache;

public class ImportPhotosActivity extends Activity {
	
	private final HashSet<Integer> selectedItems = new HashSet<Integer>();
	private final ArrayList<String> arrPath = new ArrayList<String>();
	private final ArrayList<Integer> imgIds = new ArrayList<Integer>();
	private final ArrayList<Integer> imgOrientations = new ArrayList<Integer>();
	private ImageAdapter imageAdapter = null;
	private BroadcastReceiver receiver;
	private final MemoryCache cache = SafeCameraApplication.getCache();
	private final HashMap<Integer, AsyncTasks.ShowSystemImageThumb> tasks = new HashMap<Integer, AsyncTasks.ShowSystemImageThumb>();

	/** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_photos);
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			getActionBar().setDisplayHomeAsUpEnabled(true);
			getActionBar().setHomeButtonEnabled(true);
		}

		final String[] columns = { MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID, MediaStore.Images.Media.ORIENTATION };
		final String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC";
		Cursor imagecursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, orderBy);
		if(imagecursor != null){
			int image_column_index = imagecursor.getColumnIndex(MediaStore.Images.Media._ID);
			int count = imagecursor.getCount();
			for (int i = 0; i < count; i++) {
				imagecursor.moveToPosition(i);
				int dataColumnIndex = imagecursor.getColumnIndex(MediaStore.Images.Media.DATA);
				imgIds.add(imagecursor.getInt(image_column_index));
				
				/*int orientationIndex = imagecursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION);
				String orientation = imagecursor.getString(orientationIndex);
				if(orientation != null){
					imgOrientations.add(Integer.valueOf(orientation));
				}*/
				imgOrientations.add(imagecursor.getInt(imagecursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)));
				arrPath.add(imagecursor.getString(dataColumnIndex));
			}
			imagecursor.close();
		}
		GridView imagegrid = (GridView) findViewById(R.id.PhoneImageGrid);
		imagegrid.setOnScrollListener(getOnScrollListener());
		imageAdapter = new ImageAdapter();
		imagegrid.setColumnWidth(Helpers.getThumbSize(this)-10);
		imagegrid.setAdapter(imageAdapter);

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(receiver != null){
			unregisterReceiver(receiver);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		Helpers.setLockedTime(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);
	}

	public class ImageAdapter extends BaseAdapter {
		private final LayoutInflater mInflater;

		public ImageAdapter() {
			mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public int getCount() {
			return arrPath.size();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		private void toggleCheckboxSelected(CheckBox cb){
			int id = Integer.valueOf(((String)cb.getTag()).split("-")[1]);
			if (cb.isChecked()) {
				if(Helpers.isDemo(ImportPhotosActivity.this)){
					if(selectedItems.size() >= 1){
						Helpers.warnProVersion(ImportPhotosActivity.this);
						cb.setChecked(false);
						return;
					}
				}
				selectedItems.add(id);
			}
			else {
				selectedItems.remove(id);
			}
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = mInflater.inflate(R.layout.gallery_item, null);
			int thumbSize = Helpers.getThumbSize(ImportPhotosActivity.this);
			view.setLayoutParams(new GridView.LayoutParams(thumbSize, thumbSize));
			ImageView imageview = (ImageView) view.findViewById(R.id.thumbImage);
			CheckBox checkbox = (CheckBox) view.findViewById(R.id.itemCheckBox);

			
			checkbox.setTag("chk-" + String.valueOf(position));
			imageview.setTag("img-" + String.valueOf(position));
			checkbox.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					toggleCheckboxSelected((CheckBox)v);
				}
			});

			imageview.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					String chkTag = "chk-" + ((String)v.getTag()).split("-")[1];
					CheckBox checkBox = ((CheckBox)((RelativeLayout)v.getParent()).findViewWithTag(chkTag));
					checkBox.setChecked(!checkBox.isChecked());
					toggleCheckboxSelected(checkBox);
				} 
			});

			final AsyncTasks.ShowSystemImageThumb task = new AsyncTasks.ShowSystemImageThumb(imageview, imgIds.get(position), imgOrientations.get(position), cache);
			AsyncTasks.OnAsyncTaskFinish onFinish = new AsyncTasks.OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					tasks.remove(task);
				}
			};
			task.setOnFinish(onFinish);
			task.execute(new File(arrPath.get(position)));
			tasks.put(position, task);

			if (selectedItems.contains(position)) {
				checkbox.setChecked(true);
			}
			return view;
		}
	}
	
	private int lastPosition = 0;
	
	private OnScrollListener getOnScrollListener(){
		return new OnScrollListener() {
			
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				if(scrollState == SCROLL_STATE_IDLE){
					
				}
			}
			
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if(Math.abs(lastPosition - firstVisibleItem) > visibleItemCount){
					for(Integer key : tasks.keySet()){
						if(firstVisibleItem - visibleItemCount > key || firstVisibleItem + (visibleItemCount * 2) < key){
							tasks.get(key).cancel(true);
						}
					}
					lastPosition = firstVisibleItem;
				}
			}
		};
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.import_photos_menu, menu);
        return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.select_all:
				if(selectedItems.size() < arrPath.size()){
					for(int i=0; i<arrPath.size(); i++){
						selectedItems.add(i);
					}
				}
				else{
					selectedItems.clear();
				}
				imageAdapter.notifyDataSetChanged();
				return true;
			case R.id.importBtn:
				final int len = selectedItems.size();
				
				if (len == 0) {
					Toast.makeText(getApplicationContext(), "Please select at least one image", Toast.LENGTH_LONG).show();
				}
				else {
					String[] filePaths = new String[selectedItems.size()];
					int counter = 0;
					for(Integer selectedFileId : selectedItems){
						filePaths[counter++] = arrPath.get(selectedFileId);
					}
					getIntent().putExtra("RESULT_PATH", filePaths);
					setResult(RESULT_OK, getIntent());
					finish();
				}
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
}