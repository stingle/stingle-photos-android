package com.fenritz.safecam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.Helpers;
import com.fenritz.safecam.util.MemoryCache;

public class ImportPhotosActivity extends SherlockActivity {
	
	private final HashSet<Integer> selectedItems = new HashSet<Integer>();
	private final ArrayList<String> arrPath = new ArrayList<String>();
	private ImageAdapter imageAdapter = null;
	private BroadcastReceiver receiver;
	private final MemoryCache cache = SafeCameraApplication.getCache();
	private final HashMap<Integer, AsyncTasks.ShowImageThumb> tasks = new HashMap<Integer, AsyncTasks.ShowImageThumb>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_photos);

		final String[] columns = { MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID };
		final String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC";
		Cursor imagecursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, orderBy);
		int count = imagecursor.getCount();
		for (int i = 0; i < count; i++) {
			imagecursor.moveToPosition(i);
			int dataColumnIndex = imagecursor.getColumnIndex(MediaStore.Images.Media.DATA);
			arrPath.add(imagecursor.getString(dataColumnIndex));
		}
		imagecursor.close();
		
		GridView imagegrid = (GridView) findViewById(R.id.PhoneImageGrid);
		imagegrid.setOnScrollListener(getOnScrollListener());
		imageAdapter = new ImageAdapter();
		imagegrid.setColumnWidth(Helpers.getThumbSize(this)-10);
		imagegrid.setAdapter(imageAdapter);

		findViewById(R.id.selectBtn).setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if(selectedItems.size() < arrPath.size()){
					for(int i=0; i<arrPath.size(); i++){
						selectedItems.add(i);
					}
					((Button)v).setText(getString(R.string.deselect_all));
				}
				else{
					selectedItems.clear();
					((Button)v).setText(getString(R.string.select_all));
				}
				imageAdapter.notifyDataSetChanged();
			}
		});
		
		findViewById(R.id.importBtn).setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
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
			}
		});
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
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
				selectedItems.add(id);
			}
			else {
				selectedItems.remove(id);
				((Button) findViewById(R.id.selectBtn)).setText(getString(R.string.select_all));
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

			Bitmap image = cache.get(arrPath.get(position));
			if(image != null){
				imageview.setImageBitmap(image);
			}
			else{
				
				final AsyncTasks.ShowImageThumb task = new AsyncTasks.ShowImageThumb(imageview, cache);
				
				AsyncTasks.OnAsyncTaskFinish onFinish = new AsyncTasks.OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						tasks.remove(task);
					}
				};
				
				task.setOnFinish(onFinish);
				
				task.execute(new File(arrPath.get(position)));
				tasks.put(position, task);
			}

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
							Log.d("qaq", "removed - " + String.valueOf(key));
						}
					}
					lastPosition = firstVisibleItem;
				}
			}
		};
	}
	
	
	
}