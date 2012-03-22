package com.fenritz.safecamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;
import com.fenritz.safecamera.util.MemoryCache;
import com.fenritz.safecamera.widget.CheckableLayout;

public class GalleryActivity extends Activity {

	public MemoryCache memCache = new MemoryCache();
	ArrayList<File> files = new ArrayList<File>();

	private final static int MULTISELECT_OFF = 0;
	private final static int MULTISELECT_ON = 1;
	private int multiSelectMode = MULTISELECT_OFF;
	
	private GridView photosGrid;
	
	//private final HashMap<String, Integer> selectedFiles = new HashMap<String, Integer>();
	private final ArrayList<String> selectedFiles = new ArrayList<String>();
	
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

		photosGrid = (GridView) findViewById(R.id.photosGrid);
		photosGrid.setAdapter(new GalleryAdapter());
		
		findViewById(R.id.multi_select).setOnClickListener(multiSelectClick());
		findViewById(R.id.deleteSelected).setOnClickListener(deleteSelectedClick());
	}

	private OnClickListener multiSelectClick(){
		return new OnClickListener() {
			
			public void onClick(View v) {
				if(multiSelectMode == MULTISELECT_OFF){
					((ImageButton)v).setImageResource(R.drawable.checkbox_checked);
					multiSelectMode = MULTISELECT_ON;
				}
				else{
					((ImageButton)v).setImageResource(R.drawable.checkbox_unchecked);
					multiSelectMode = MULTISELECT_OFF;
				}
			}
		};
	}
	
	private OnClickListener deleteSelectedClick(){
		return new OnClickListener() {
			
			public void onClick(View v) {
				if(multiSelectMode == MULTISELECT_ON){
					for(String filePath : selectedFiles){
						Log.d("qaq", filePath);
					}
				}
			}
		};
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
			final CheckableLayout layout = new CheckableLayout(GalleryActivity.this);
			layout.setLayoutParams(new GridView.LayoutParams(Integer.valueOf(getString(R.string.thumb_size)), Integer
						.valueOf(getString(R.string.thumb_size))));

			final File file = files.get(position);
			
			OnClickListener onClick = new View.OnClickListener() {
				public void onClick(View v) {
					if(multiSelectMode == MULTISELECT_ON){
						layout.toggle();
						if(layout.isChecked()){
							selectedFiles.add(file.getPath());
						}
						else{
							selectedFiles.remove(file.getPath());
						}
					}
					else{
						Intent intent = new Intent();
						intent.setClass(GalleryActivity.this, ViewImageActivity.class);
						intent.putExtra("EXTRA_IMAGE_PATH", file.getPath());
						startActivity(intent);
					}
				}
			};
			
			String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + file.getName();
			
			Bitmap image = memCache.get(thumbPath);
			if(image != null){
				ImageView imageView = new ImageView(GalleryActivity.this);
				imageView.setImageBitmap(image);
				imageView.setOnClickListener(onClick);
				imageView.setPadding(3, 3, 3, 3);
				layout.addView(imageView);
			}
			else{
				new DecryptAndShowImage(thumbPath, layout, onClick, memCache, false).execute();
			}
			
			return layout;
		}
	}

}
