package com.fenritz.safecam;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.fenritz.safecam.util.AsyncTasks;
import com.fenritz.safecam.util.AsyncTasks.DeleteFiles;
import com.fenritz.safecam.util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.util.DecryptAndShowImage;
import com.fenritz.safecam.util.Helpers;

public class ViewImageActivity extends SherlockActivity {

	private final ArrayList<File> files = new ArrayList<File>();
	
	private BroadcastReceiver receiver;
	private int currentPosition = 0;
	
	private static final int SWIPE_MIN_DISTANCE = 100;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    
    private static final int REQUEST_DECRYPT = 0;
	
    private GestureDetector gestureDetector;
    private View.OnTouchListener gestureListener;
    
    private final Handler handler = new Handler();
    private String currentPath;
    private boolean isViewsVisible = true;;
    
    private final ArrayList<DecryptAndShowImage> taskStack = new ArrayList<DecryptAndShowImage>();
    
    private final ArrayList<View> viewsHideShow = new ArrayList<View>();
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		super.onCreate(savedInstanceState);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		setContentView(R.layout.view_photo);

		getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.ab_bg_black));
		
		Intent intent = getIntent();
		String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");
		
		if(intent.hasExtra("EXTRA_CURRENT_PATH")){
			currentPath = intent.getStringExtra("EXTRA_CURRENT_PATH");
		}
		else{
			currentPath = Helpers.getHomeDir(this);
		}

		fillFilesList();
		
		File photo = new File(imagePath);
		
		currentPosition = files.indexOf(photo);
		
		
		((ImageButton)findViewById(R.id.previousButton)).setOnClickListener(navigateLeft());
		((ImageButton)findViewById(R.id.nextButton)).setOnClickListener(navigateRight());
		
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
		
		viewsHideShow.add(findViewById(R.id.countLabel)); 
		viewsHideShow.add(findViewById(R.id.previousButton)); 
		viewsHideShow.add(findViewById(R.id.nextButton)); 
		
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
		cancelPendingTasks();
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
					cancelPendingTasks();
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
					cancelPendingTasks();
					showImage(files.get(++currentPosition));
					showViews();
				}
			}
		};
	}
	
	private void cancelPendingTasks(){
		for(DecryptAndShowImage task : taskStack){
			task.cancel(true);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == REQUEST_DECRYPT) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				File destinationFolder = new File(filePath);
				destinationFolder.mkdirs();
				final ArrayList<File> selectedFiles = new ArrayList<File>();
				selectedFiles.add(files.get(currentPosition));
				new AsyncTasks.DecryptFiles(ViewImageActivity.this, filePath, new OnAsyncTaskFinish() {
					@Override
					public void onFinish(ArrayList<File> decryptedFiles) {
						super.onFinish(decryptedFiles);
						for(File file : decryptedFiles){
							sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
						}
						Toast.makeText(ViewImageActivity.this, getString(R.string.success_decrypt), Toast.LENGTH_LONG).show();
					}
				}).execute(selectedFiles);
			}
		}
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
                		cancelPendingTasks();
    					showImage(files.get(++currentPosition));
    				}
                }  
                else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                	if(currentPosition > 0){
                		cancelPendingTasks();
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
				if(!isViewsVisible){
					showViews();
				}
				else{
					hideViewsInternal();
				}
			}
		};
	}
	
	private void hideViewsInternal(){
		getSupportActionBar().hide();
		for(View view : viewsHideShow){
			if(view.getVisibility() == View.VISIBLE) {
				view.startAnimation(AnimationUtils.loadAnimation(ViewImageActivity.this, android.R.anim.fade_out));
				view.setVisibility(View.INVISIBLE);
			}
		}
		isViewsVisible = false;
	}
	
	private void hideViews(){
		handler.postDelayed(new Runnable() {
		  public void run() {
			  hideViewsInternal();
		  }
		}, 4000);
	}
	
	private void showViews(){
		handler.removeCallbacksAndMessages(null);
		getSupportActionBar().show();
		for(View view : viewsHideShow){
		    if(view.getVisibility() != View.VISIBLE) {
		    	view.startAnimation(AnimationUtils.loadAnimation(ViewImageActivity.this, android.R.anim.fade_in));
		    	view.setVisibility(View.VISIBLE);
		    }
		}
		isViewsVisible = true;
		hideViews();
	}
	
	private void showImage(File photo){
		DecryptAndShowImage task = new DecryptAndShowImage(photo.getPath(), ((LinearLayout)findViewById(R.id.parent_layout)), showControls(), null, null, true, gestureListener){
			@Override
			protected void onFinish() {
				super.onFinish();
				//task = null;
				taskStack.remove(this);
			}
		};
		task.execute();
		taskStack.add(task);
		
		getSupportActionBar().setTitle(photo.getName());
		((TextView)findViewById(R.id.countLabel)).setText(String.valueOf(currentPosition+1) + "/" + String.valueOf(files.size()));
	}
	
	private void fillFilesList() {
		File dir = new File(currentPath);
		File[] folderFiles = dir.listFiles();

		Arrays.sort(folderFiles, Collections.reverseOrder());
		/*Arrays.sort(folderFiles, new Comparator<File>() {
			public int compare(File lhs, File rhs) {
				if(rhs.lastModified() > lhs.lastModified()){
					return 1;
				}
				else if(rhs.lastModified() < lhs.lastModified()){
					return -1;
				}
				return 0;
			}
		});*/
		
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
		getSupportMenuInflater().inflate(R.menu.view_photo_menu, menu);;
        return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		final ArrayList<File> selectedFiles = new ArrayList<File>();
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.decrypt:
				intent = new Intent(getBaseContext(), FileDialog.class);
				intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

				// can user select directories or not
				intent.putExtra(FileDialog.CAN_SELECT_FILE, false);
				intent.putExtra(FileDialog.CAN_SELECT_DIR, true);

				startActivityForResult(intent, REQUEST_DECRYPT);
				return true;
			case R.id.share:
				selectedFiles.add(files.get(currentPosition));
				
				Helpers.share(ViewImageActivity.this, selectedFiles, null);
				return true;
			case R.id.delete:
				cancelPendingTasks();
				selectedFiles.add(files.get(currentPosition));
				
				AlertDialog.Builder builder = new AlertDialog.Builder(ViewImageActivity.this);
				builder.setMessage(getString(R.string.confirm_delete_photo));
				builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
					@SuppressWarnings("unchecked")
					public void onClick(DialogInterface dialog, int whichButton) {
						new DeleteFiles(ViewImageActivity.this, new OnAsyncTaskFinish() {
							@Override
							public void onFinish() {
								super.onFinish();
								
								fillFilesList();
								if(currentPosition > 0){
									--currentPosition;
								}
								
								Intent resultIntent = new Intent();
								resultIntent.putExtra("needToRefresh", true);
								setResult(RESULT_OK, resultIntent);
								
								if(files.size() > 0){
									showImage(files.get(currentPosition));
									showViews();
								}
								else{
									ViewImageActivity.this.finish();
								}
							}
						}).execute(selectedFiles);
					}
				});
				builder.setNegativeButton(getString(R.string.no), null);
				AlertDialog dialog = builder.create();
				dialog.show();
				return true;
			case R.id.rotateCW:
				new AsyncTasks.RotatePhoto(this, files.get(currentPosition).getPath(), AsyncTasks.RotatePhoto.ROTATION_CW, new OnAsyncTaskFinish() {
					@Override
					public void onFinish(Integer result) {
						super.onFinish(result);
						if(result == AsyncTasks.RotatePhoto.STATUS_OK){
							String key = Helpers.getThumbsDir(ViewImageActivity.this) + "/" + Helpers.getThumbFileName(files.get(currentPosition));
							Log.d("qaq", key);
							SafeCameraApplication.getCache().remove(key);
							showImage(files.get(currentPosition));
							Intent resultIntent = new Intent();
							resultIntent.putExtra("needToRefresh", true);
							setResult(RESULT_OK, resultIntent);
						}
					}
				}).execute();
				return true;
			case R.id.rotateCCW:
				new AsyncTasks.RotatePhoto(this, files.get(currentPosition).getPath(), AsyncTasks.RotatePhoto.ROTATION_CCW, new OnAsyncTaskFinish() {
					@Override
					public void onFinish(Integer result) {
						super.onFinish(result);
						if(result == AsyncTasks.RotatePhoto.STATUS_OK){
							String key = Helpers.getThumbsDir(ViewImageActivity.this) + "/" + Helpers.getThumbFileName(files.get(currentPosition));
							Log.d("qaq", key);
							SafeCameraApplication.getCache().remove(key);
							showImage(files.get(currentPosition));
							Intent resultIntent = new Intent();
							resultIntent.putExtra("needToRefresh", true);
							setResult(RESULT_OK, resultIntent);
						}
					}
				}).execute();
				return true;
			case R.id.gotoGallery:
				intent.setClass(ViewImageActivity.this, GalleryActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
				startActivity(intent);
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
}
