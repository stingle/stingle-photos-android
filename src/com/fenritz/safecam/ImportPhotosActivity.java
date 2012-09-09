package com.fenritz.safecam;

import java.util.HashSet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.fenritz.safecam.util.Helpers;

public class ImportPhotosActivity extends SherlockActivity {
	private int count;
	private Bitmap[] thumbnails;
	private final HashSet<Integer> selectedItems = new HashSet<Integer>();
	private String[] arrPath;
	private ImageAdapter imageAdapter;
	private BroadcastReceiver receiver;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_photos);

		//String[] projection = { MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATA }; String selection = ""; String[] selectionArgs = null; mImageExternalCursor = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null); mImageInternalCursor = managedQuery(MediaStore.Images.Media.INTERNAL_CONTENT_URI, projection, selection, selectionArgs, null); then mImageExternalCursor.getString(mImageExternalCursor.getColumnIndexOrThrow(Media‌​Store.Images.ImageColumns.DATA));
		
		final String[] columns = { MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID };
		final String orderBy = MediaStore.Images.Media.DATE_TAKEN + " DESC";
		Cursor imagecursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, orderBy);
		int image_column_index = imagecursor.getColumnIndex(MediaStore.Images.Media._ID);
		this.count = imagecursor.getCount();
		this.thumbnails = new Bitmap[this.count];
		this.arrPath = new String[this.count];
		for (int i = 0; i < this.count; i++) {
			imagecursor.moveToPosition(i);
			int id = imagecursor.getInt(image_column_index);
			int dataColumnIndex = imagecursor.getColumnIndex(MediaStore.Images.Media.DATA);
			thumbnails[i] = MediaStore.Images.Thumbnails.getThumbnail(getApplicationContext().getContentResolver(), id,	MediaStore.Images.Thumbnails.MICRO_KIND, null);
			arrPath[i] = imagecursor.getString(dataColumnIndex);
		}
		GridView imagegrid = (GridView) findViewById(R.id.PhoneImageGrid);
		imageAdapter = new ImageAdapter();
		imagegrid.setAdapter(imageAdapter);
		imagecursor.close();

		findViewById(R.id.selectBtn).setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if(selectedItems.size() < count){
					for(int i=0; i<count; i++){
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
						filePaths[counter++] = arrPath[selectedFileId];
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
			return count;
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = mInflater.inflate(R.layout.gallery_item, null);
			ImageView imageview = (ImageView) view.findViewById(R.id.thumbImage);
			CheckBox checkbox = (CheckBox) view.findViewById(R.id.itemCheckBox);

			checkbox.setId(position);
			imageview.setId(position);
			checkbox.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					CheckBox cb = (CheckBox) v;
					int id = cb.getId();
					if (cb.isChecked()) {
						selectedItems.add(id);
					}
					else {
						selectedItems.remove(id);
						((Button) findViewById(R.id.selectBtn)).setText(getString(R.string.select_all));
					}
				}
			});

			/*
			 * imageview.setOnClickListener(new OnClickListener() {
			 * 
			 * public void onClick(View v) { Intent intent = new Intent();
			 * intent.setAction(Intent.ACTION_VIEW);
			 * intent.setDataAndType(Uri.parse("file://" + arrPath[v.getId()]),
			 * "image/*"); startActivity(intent); } });
			 */

			imageview.setImageBitmap(thumbnails[position]);

			if (selectedItems.contains(position)) {
				checkbox.setChecked(true);
			}
			return view;
		}
	}
}