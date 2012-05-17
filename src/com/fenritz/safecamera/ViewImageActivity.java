package com.fenritz.safecamera;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fenritz.safecamera.util.AsyncTasks.DeleteFiles;
import com.fenritz.safecamera.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;

public class ViewImageActivity extends Activity {

	private DecryptAndShowImage task;
	private final ArrayList<File> files = new ArrayList<File>();
	
	private BroadcastReceiver receiver;
	private int currentPosition = 0;
	
	private static final int SWIPE_MIN_DISTANCE = 100;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	
    private GestureDetector gestureDetector;
    private View.OnTouchListener gestureListener;
    
    private final Handler handler = new Handler();
    
    private final ArrayList<View> viewsHideShow = new ArrayList<View>();
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.view_photo);

		Intent intent = getIntent();
		String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");

		fillFilesList();
		
		File photo = new File(imagePath);
		
		currentPosition = files.indexOf(photo);
		
		
		((ImageButton)findViewById(R.id.previousButton)).setOnClickListener(navigateLeft());
		((ImageButton)findViewById(R.id.nextButton)).setOnClickListener(navigateRight());
		((ImageButton)findViewById(R.id.delete)).setOnClickListener(deleteClick());
		
		gestureDetector = new GestureDetector(new SwipeGestureDetector());
		gestureListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        };
        
        showImage(photo);
        
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
		
		viewsHideShow.add(findViewById(R.id.topPanel)); 
		viewsHideShow.add(findViewById(R.id.countLabel)); 
		viewsHideShow.add(findViewById(R.id.previousButton)); 
		viewsHideShow.add(findViewById(R.id.nextButton)); 
		
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
		
		hideViews();
	}
	
	private OnClickListener navigateLeft(){
		return new OnClickListener() {
			public void onClick(View v) {
				if(currentPosition > 0){
					showImage(files.get(--currentPosition));
					showViews();
				}
			}
		};
	}
	
	private OnClickListener navigateRight(){
		return new OnClickListener() {
			public void onClick(View v) {
				if(currentPosition < files.size()-1){
					showImage(files.get(++currentPosition));
					showViews();
				}
			}
		};
	}
	
	private OnClickListener deleteClick(){
		return new OnClickListener() {
			@SuppressWarnings("unchecked")
			public void onClick(View v) {
				final ArrayList<File> selectedFiles = new ArrayList<File>();
				selectedFiles.add(files.get(currentPosition));
				
				AlertDialog.Builder builder = new AlertDialog.Builder(ViewImageActivity.this);
				builder.setMessage(getString(R.string.confirm_delete_photo));
				builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						new DeleteFiles(ViewImageActivity.this, new OnAsyncTaskFinish() {
							@Override
							public void onFinish() {
								super.onFinish();
								
								fillFilesList();
								if(currentPosition > 0){
									--currentPosition;
								}
								showImage(files.get(currentPosition));
								showViews();
								
								Intent resultIntent = new Intent();
								resultIntent.putExtra("needToRefresh", true);
								setResult(RESULT_OK, resultIntent);
							}
						}).execute(selectedFiles);
					}
				});
				builder.setNegativeButton(getString(R.string.no), null);
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		};
	}
	
	class SwipeGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH){
                    return false;
                }
                
                if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                	if(currentPosition < files.size()-1){
    					showImage(files.get(++currentPosition));
    				}
                }  
                else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                	if(currentPosition > 0){
    					showImage(files.get(--currentPosition));
    				}
                }
            } catch (Exception e) { }
            return false;
        }

    }
	
	
	private OnClickListener showControls(){
		return new OnClickListener() {
			public void onClick(View v) {
				showViews();
			}
		};
	}
	
	private void hideViews(){
		handler.postDelayed(new Runnable() {
		  public void run() {
			  for(View view : viewsHideShow){
				    if(view.getVisibility() == View.VISIBLE) {
				    	view.startAnimation(AnimationUtils.loadAnimation(ViewImageActivity.this, android.R.anim.fade_out));
				    	view.setVisibility(View.INVISIBLE);
				    }
				}
		  }
		}, 4000);
	}
	
	private void showViews(){
		handler.removeCallbacksAndMessages(null);
		for(View view : viewsHideShow){
		    if(view.getVisibility() != View.VISIBLE) {
		    	view.startAnimation(AnimationUtils.loadAnimation(ViewImageActivity.this, android.R.anim.fade_in));
		    	view.setVisibility(View.VISIBLE);
		    }
		}
		hideViews();
	}
	
	private void showImage(File photo){
		if(task != null){
			task.cancel(false);
			task = null;
		}
		
		// XIMIA!
		task = new DecryptAndShowImage(photo.getPath(), ((LinearLayout)findViewById(R.id.parent_layout)), showControls(), null, null, true, gestureListener){
			@Override
			protected void onFinish() {
				super.onFinish();
				task = null;
			}
		};
		task.execute();
		
		((TextView)findViewById(R.id.title)).setText(photo.getName());
		((TextView)findViewById(R.id.countLabel)).setText(String.valueOf(currentPosition+1) + "/" + String.valueOf(files.size()));
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
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.view_photo_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		switch (item.getItemId()) {
			case R.id.delete:
				
				return true;
			case R.id.decrypt:
				
				return true;
			case R.id.share:
				
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

}
