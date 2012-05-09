package com.fenritz.safecamera;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.LinearLayout;

import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;
import com.fenritz.safecamera.util.MemoryCache;

public class ViewImageActivity extends Activity {

	private DecryptAndShowImage task;
	private BroadcastReceiver receiver;
	private ImageAdapter adapter;
	private String currentImage;
	private File[] files;
	private final MemoryCache memCache = new MemoryCache(1024*1024);
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		setContentView(R.layout.view_photo);
		getWindow().addFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN );

		Intent intent = getIntent();
		currentImage = intent.getStringExtra("EXTRA_IMAGE_PATH");

		File currentImageFile = new File(currentImage);
		
		FilenameFilter filter = new FilenameFilter() {
			
			public boolean accept(File dir, String filename) {
				if (filename.endsWith(getString(R.string.file_extension))) {
					return true;
				}
				return false;
			}
		};
		files = currentImageFile.getParentFile().listFiles(filter);

		Arrays.sort(files, new Comparator<File>() {
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
		
		
        adapter = new ImageAdapter();
        ((Gallery)findViewById(R.id.photos)).setAdapter(adapter);
		
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
		unregisterReceiver(receiver);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Helpers.setLockedTime(this);
		if(task != null){
			task.cancel(true);
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	    super.onConfigurationChanged(newConfig);
	    
	    adapter.notifyDataSetChanged();
	}
	
	public class ImageAdapter extends BaseAdapter {

		public int getCount() {
            return files.length;
        }

		public Object getItem(int position) {
            return position;
        }

		public long getItemId(int position) {
            return position;
        }

		public View getView(int position, View convertView, ViewGroup parent) {
			Log.d("qaq", String.valueOf(position) + " - " + files[position].getPath());
            LinearLayout layout = new LinearLayout(ViewImageActivity.this);
            Display display = getWindowManager().getDefaultDisplay(); 
            layout.setLayoutParams(new Gallery.LayoutParams(display.getWidth(), display.getHeight()));
            
            
            if(task != null){
            	task.cancel(false);
            	task = null;
            }
            task = new DecryptAndShowImage(files[position+1].getPath(), layout, null, null, memCache, false){
            	@Override
            	protected void onFinish() {
            		super.onFinish();
            		task = null;
            	}
            };
            synchronized (task) {
	    		task.execute();
            }
            return layout;
        }
        
    }

}
