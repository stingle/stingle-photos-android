package com.fenritz.safecamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;

import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;
import com.fenritz.safecamera.util.MemoryCache;

public class GalleryActivity extends Activity {

	public MemoryCache memCache = new MemoryCache();
	ArrayList<File> files = new ArrayList<File>();
	//private final ImageLoader imgLoader=new ImageLoader(this);
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gallery);

		File dir = new File(Helpers.getHomeDir(this)); 
		File[] folderFiles = dir.listFiles();
		
		Arrays.sort(folderFiles);
		
		for(File file : folderFiles){
			if(file.getName().endsWith(getString(R.string.file_extension))){
				files.add(file);
			}
		}
		
		Log.d("sc", String.valueOf(files.size()) + " files");
		GridView photosGrid = (GridView)findViewById(R.id.photosGrid);
		photosGrid.setAdapter(new GalleryAdapter(GalleryActivity.this, files));
		
		/*ListView filesList = (ListView)findViewById(R.id.files);
		FilesAdapter adapter = new FilesAdapter();
		filesList.setAdapter(adapter);
		
		filesList.setOnItemClickListener(FilesClick());*/
	}

	
	public class GalleryAdapter extends BaseAdapter{
	    private final Activity activity;
	    protected final ArrayList<File> files;

	    public GalleryAdapter(Activity pActivity, ArrayList<File> pFiles){
	    	activity = pActivity;
	    	files = pFiles;
	    }

		public int getCount() {
	        return files.size();
	    }

		public Object getItem(int position) {
	        return files.get(position);
	    }

		public long getItemId(int position) {
	        return position;
	    }

		public View getView(int position, View convertView, ViewGroup parent){
    		
			final File file = files.get(position);
			
			LinearLayout layout;
			if (convertView == null) {
				layout = new LinearLayout(activity);
				
				layout.setLayoutParams(new GridView.LayoutParams(Integer.valueOf(getString(R.string.thumb_size)), Integer.valueOf(getString(R.string.thumb_size))));
	        } 
			else {
	        	layout = (LinearLayout) convertView;
	        }
			
			String thumbPath = Helpers.getThumbsDir(activity) + "/" + file.getName();
			
			OnClickListener onClick = new View.OnClickListener() {
				
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(GalleryActivity.this, ViewImageActivity.class);
					intent.putExtra("EXTRA_IMAGE_PATH", file.getPath());
					startActivity(intent);
				}
			};
			
			new DecryptAndShowImage(thumbPath, layout, onClick, memCache, false).execute();
			
			return layout;
	    }
	}    
	
}
