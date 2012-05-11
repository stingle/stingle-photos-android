package com.fenritz.safecamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;

public class ViewImageActivity extends Activity {

	private DecryptAndShowImage task;
	private final ArrayList<File> files = new ArrayList<File>();
	
	private BroadcastReceiver receiver;
	private int currentPosition = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		setContentView(R.layout.view_photo);
		getWindow().addFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN );

		Intent intent = getIntent();
		String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");

		fillFilesList();
		
		File photo = new File(imagePath);
		
		currentPosition = files.indexOf(photo);
		showImage(photo);
		
		((ImageButton)findViewById(R.id.previousButton)).setOnClickListener(navigateLeft());
		((ImageButton)findViewById(R.id.nextButton)).setOnClickListener(navigateRight());
		((LinearLayout)findViewById(R.id.parent_layout)).setOnTouchListener(imageTouchListener());
		
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
	
	private OnTouchListener imageTouchListener(){
		return new OnTouchListener() {
			
			public boolean onTouch(View v, MotionEvent event) {
				Log.d("qaq", "mtav");
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						Log.d("qaq", "down");
						break;
					case MotionEvent.ACTION_MOVE:
						Log.d("qaq", "move");
						break;
				}
				return false;
			}
		};
	}
	
	private OnClickListener navigateLeft(){
		return new OnClickListener() {
			public void onClick(View v) {
				if(currentPosition > 0){
					showImage(files.get(--currentPosition));
				}
			}
		};
	}
	
	private OnClickListener navigateRight(){
		return new OnClickListener() {
			public void onClick(View v) {
				if(currentPosition < files.size()-1){
					showImage(files.get(++currentPosition));
				}
			}
		};
	}
	
	private void showImage(File photo){
		if(task != null){
			task.cancel(true);
			task = null;
		}
		
		task = new DecryptAndShowImage(photo.getPath(), ((LinearLayout)findViewById(R.id.parent_layout)), null, null, null, true){
			@Override
			protected void onFinish() {
				super.onFinish();
				task = null;
			}
		};
		task.execute();
	}
	
	private void fillFilesList() {
		File dir = new File(Helpers.getHomeDir(this));
		File[] folderFiles = dir.listFiles();

		Arrays.sort(folderFiles, new Comparator<File>() {
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
		
		files.clear();
		
		int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;
		for (File file : folderFiles) {
			if (file.getName().endsWith(getString(R.string.file_extension)) && file.length() < maxFileSize) {
				files.add(file);
			}
		}
	}

}
