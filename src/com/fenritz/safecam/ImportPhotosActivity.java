package com.fenritz.safecam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
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

public class ImportPhotosActivity extends Activity {
	private int count;
	private Bitmap[] thumbnails;
	private boolean[] thumbnailsselection;
	private String[] arrPath;
	private ImageAdapter imageAdapter;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_photos);

		final String[] columns = { MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID };
		final String orderBy = MediaStore.Images.Media.DATE_TAKEN;
		Cursor imagecursor = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, orderBy);
		int image_column_index = imagecursor.getColumnIndex(MediaStore.Images.Media._ID);
		this.count = imagecursor.getCount();
		this.thumbnails = new Bitmap[this.count];
		this.arrPath = new String[this.count];
		this.thumbnailsselection = new boolean[this.count];
		for (int i = 0; i < this.count; i++) {
			imagecursor.moveToPosition(i);
			int id = imagecursor.getInt(image_column_index);
			int dataColumnIndex = imagecursor.getColumnIndex(MediaStore.Images.Media.DATA);
			thumbnails[i] =
					MediaStore.Images.Thumbnails.getThumbnail(getApplicationContext().getContentResolver(), id,
							MediaStore.Images.Thumbnails.MICRO_KIND, null);
			arrPath[i] = imagecursor.getString(dataColumnIndex);
		}
		GridView imagegrid = (GridView) findViewById(R.id.PhoneImageGrid);
		imageAdapter = new ImageAdapter();
		imagegrid.setAdapter(imageAdapter);
		imagecursor.close();

		final Button selectBtn = (Button) findViewById(R.id.selectBtn);
		selectBtn.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				final int len = thumbnailsselection.length;
				int cnt = 0;
				String selectImages = "";
				for (int i = 0; i < len; i++) {
					if (thumbnailsselection[i]) {
						cnt++;
						selectImages = selectImages + arrPath[i] + "|";
					}
				}
				if (cnt == 0) {
					Toast.makeText(getApplicationContext(), "Please select at least one image", Toast.LENGTH_LONG).show();
				}
				else {
					Toast.makeText(getApplicationContext(), "You've selected Total " + cnt + " image(s).", Toast.LENGTH_LONG).show();
					Log.d("SelectedImages", selectImages);
				}
			}
		});
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
			ViewHolder holder;
			holder = new ViewHolder();
			View view = mInflater.inflate(R.layout.gallery_item, null);
			holder.imageview = (ImageView) view.findViewById(R.id.thumbImage);
			holder.checkbox = (CheckBox) view.findViewById(R.id.itemCheckBox);

			view.setTag(holder);
			
			holder.checkbox.setId(position);
			holder.imageview.setId(position);
			holder.checkbox.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					CheckBox cb = (CheckBox) v;
					int id = cb.getId();
					if (thumbnailsselection[id]) {
						cb.setChecked(false);
						thumbnailsselection[id] = false;
					}
					else {
						cb.setChecked(true);
						thumbnailsselection[id] = true;
					}
				}
			});
			
			final int thisPosition = position;
			holder.imageview.setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setAction(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.parse("file://" + arrPath[thisPosition]), "image/*");
					startActivity(intent);
				}
			});
			holder.imageview.setImageBitmap(thumbnails[position]);
			holder.checkbox.setChecked(thumbnailsselection[position]);
			holder.id = position;
			return view;
		}
	}

	class ViewHolder {
		ImageView imageview;
		CheckBox checkbox;
		int id;
	}
}