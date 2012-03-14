package com.fenritz.safecamera;

import java.io.File;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecamera.util.Helpers;

public class GalleryActivity extends Activity {

	File[] files;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gallery);

		File dir = new File(Helpers.getHomeDir(this)); 
		files = dir.listFiles();
		
		Arrays.sort(files);
		
		ListView filesList = (ListView)findViewById(R.id.files);
		FilesAdapter adapter = new FilesAdapter();
		filesList.setAdapter(adapter);
		
		filesList.setOnItemClickListener(FilesClick());

		// ((Button)
		// findViewById(R.id.take_photo)).setOnClickListener(takePhoto());
	}

	private OnItemClickListener FilesClick(){
		return new OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Toast.makeText(GalleryActivity.this, files[position].getName(), Toast.LENGTH_SHORT).show();
				Intent intent = new Intent();
				intent.setClass(GalleryActivity.this, ViewImageActivity.class);
				intent.putExtra("EXTRA_IMAGE_PATH", files[position].getPath());
				startActivity(intent);
			}
		};
	}
	
	public class FilesAdapter extends BaseAdapter{

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

		
	}
}
