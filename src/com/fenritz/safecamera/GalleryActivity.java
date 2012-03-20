package com.fenritz.safecamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;
import com.fenritz.safecamera.util.MemoryCache;

public class GalleryActivity extends Activity {

	public MemoryCache memCache = new MemoryCache();
	ArrayList<File> files = new ArrayList<File>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gallery);

		File dir = new File(Helpers.getHomeDir(this));
		File[] folderFiles = dir.listFiles();

		Arrays.sort(folderFiles);

		for (File file : folderFiles) {
			if (file.getName().endsWith(getString(R.string.file_extension))) {
				files.add(file);
			}
		}

		GridView photosGrid = (GridView) findViewById(R.id.photosGrid);
		photosGrid.setAdapter(new GalleryAdapter());
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

		public View getView(int position, View convertView, ViewGroup parent) {
			LinearLayout layout = new LinearLayout(GalleryActivity.this);
			layout.setLayoutParams(new GridView.LayoutParams(Integer.valueOf(getString(R.string.thumb_size)), Integer
						.valueOf(getString(R.string.thumb_size))));

			final File file = files.get(position);
			
			OnClickListener onClick = new View.OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(GalleryActivity.this, ViewImageActivity.class);
					intent.putExtra("EXTRA_IMAGE_PATH", file.getPath());
					startActivity(intent);
				}
			};
			
			String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + file.getName();
			
			Bitmap image = memCache.get(thumbPath);
			if(image != null){
				ImageView imageView = new ImageView(GalleryActivity.this);
				imageView.setImageBitmap(image);
				imageView.setOnClickListener(onClick);
				layout.addView(imageView);
			}
			else{
				new DecryptAndShowImage(thumbPath, layout, onClick, memCache, false).execute();
			}
			
			return layout;
		}
	}

}
