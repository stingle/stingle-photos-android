package com.fenritz.safecamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.fenritz.safecamera.util.Helpers;

public class GalleryActivity extends Activity {

	ArrayList<File> files = new ArrayList<File>();
	
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
			
			ImageView image = new ImageView(activity);
			image.setImageResource(R.drawable.no);
			
			String thumbPath = Helpers.getThumbsDir(activity) + "/" + file.getName();
			File thumbFile = new File(thumbPath);
			if(thumbFile.exists() && thumbFile.isFile()) {
				try {
					
					FileInputStream input = new FileInputStream(thumbPath);
					byte[] decryptedData = SafeCameraActivity.crypto.decrypt(input);
	
					if (decryptedData != null) {
						Bitmap bitmap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.length);
						if(bitmap != null){
							image.setImageBitmap(bitmap);
						}
						
					}
					else{
						Log.d("sc", "Unable to decrypt: " + thumbPath);
					}
					
				}
				catch (FileNotFoundException e) { }
			}
			
			image.setOnClickListener(new View.OnClickListener() {
				
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(GalleryActivity.this, ViewImageActivity.class);
					intent.putExtra("EXTRA_IMAGE_PATH", file.getPath());
					startActivity(intent);
				}
			});
			
			return image;
	    }
	}    
	
	/*public class FilesAdapter extends BaseAdapter{

		public int getCount() {
			return files.length;
		}

		public Object getItem(int position) {
			return files[position];
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			TextView text = new TextView(GalleryActivity.this);
			text.setText(files[position].getName().toString());
			return text;
		}

		
	}*/
}
