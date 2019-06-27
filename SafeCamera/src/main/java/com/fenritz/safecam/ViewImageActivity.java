package com.fenritz.safecam;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecam.Util.AsyncTasks;
import com.fenritz.safecam.Util.AsyncTasks.DeleteFiles;
import com.fenritz.safecam.Util.AsyncTasks.OnAsyncTaskFinish;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.DecryptAndShowImage;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Auth.LoginManager;
import com.fenritz.safecam.Video.StingleDataSourceFactory;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.FileDataSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class ViewImageActivity extends Activity {

	private final ArrayList<File> files = new ArrayList<File>();
	
	private BroadcastReceiver receiver;
	private int currentPosition = 0;
	
	private static final int SWIPE_MIN_DISTANCE = 100;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    
    private GestureDetector gestureDetector;
    private View.OnTouchListener gestureListener;
    
    private final Handler handler = new Handler();
    private boolean isViewsVisible = true;;
    
    private final ArrayList<DecryptAndShowImage> taskStack = new ArrayList<DecryptAndShowImage>();
    
    private final ArrayList<View> viewsHideShow = new ArrayList<View>();
	private SimpleExoPlayer player;
	private PlayerView playerView;
    
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        
		super.onCreate(savedInstanceState);
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.ab_bg_black));

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.view_photo);

		
		Intent intent = getIntent();
		String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");
		
		fillFilesList();
		
		File photo = new File(imagePath);
		
		currentPosition = files.indexOf(photo);
		
		gestureDetector = new GestureDetector(new SwipeGestureDetector());
		gestureListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        };

		showImage(photo);
        
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.fenritz.safecam.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
		
		viewsHideShow.add(findViewById(R.id.countLabel)); 
		
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

		LoginManager.setLockedTime(this);
		cancelPendingTasks();
		releasePlayer();
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		LoginManager.checkLogin(this);
		LoginManager.disableLockTimer(this);
		
		hideViews();
	}
	
	private void cancelPendingTasks(){
		for(DecryptAndShowImage task : taskStack){
			task.cancel(true);
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
                		releasePlayer();
    					showImage(files.get(++currentPosition));
    				}
                }  
                else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                	if(currentPosition > 0){
                		cancelPendingTasks();
						releasePlayer();
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
	
	@SuppressLint("NewApi")
	private void hideViewsInternal(){
		getActionBar().hide();
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
	
	@SuppressLint("NewApi")
	private void showViews(){
		handler.removeCallbacksAndMessages(null);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			getActionBar().show();
		}
		for(View view : viewsHideShow){
		    if(view.getVisibility() != View.VISIBLE) {
		    	view.startAnimation(AnimationUtils.loadAnimation(ViewImageActivity.this, android.R.anim.fade_in));
		    	view.setVisibility(View.VISIBLE);
		    }
		}
		isViewsVisible = true;
		hideViews();
	}
	
	@SuppressLint("NewApi")
	private void showImage(File photo){
		String realFilename = Helpers.decryptFilename(photo.getPath());

		int fileType = Crypto.FILE_TYPE_PHOTO;

		try {
			Crypto.Header fileHeader = SafeCameraApplication.getCrypto().getFileHeader(new FileInputStream(photo));
			fileType = fileHeader.fileType;
		}
		catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		if(fileType == Crypto.FILE_TYPE_PHOTO) {
			boolean isGif = false;
			if (realFilename.endsWith(".gif")) {
				isGif = true;
			}
			DecryptAndShowImage task = new DecryptAndShowImage(photo.getPath(), ((LinearLayout) findViewById(R.id.parent_layout)), showControls(), null, null, true, gestureListener, isGif) {
				@Override
				protected void onFinish() {
					super.onFinish();
					//task = null;
					taskStack.remove(this);
				}
			};
			task.execute();
			taskStack.add(task);
		}
		else if(fileType == Crypto.FILE_TYPE_VIDEO){
			ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
			playerView = new PlayerView(this);
			playerView.setLayoutParams(params);

			LinearLayout parentView = (LinearLayout) findViewById(R.id.parent_layout);
			parentView.removeAllViews();
			parentView.addView(playerView);
			if(gestureListener != null){
				parentView.setOnTouchListener(gestureListener);
			}
			parentView.setOnClickListener(showControls());

			player = ExoPlayerFactory.newSimpleInstance(
					new DefaultRenderersFactory(this),
					new DefaultTrackSelector(), new DefaultLoadControl());

			playerView.setPlayer(player);

			//String path = Helpers.getHomeDir(this) + "/vid1.sc";
			Uri uri = Uri.fromFile(photo);

			//Uri uri = Uri.parse("https://www.safecamera.org/vid1.sc");

			//StingleHttpDataSource http = new StingleHttpDataSource("stingle", null);
			FileDataSource file = new FileDataSource();
			StingleDataSourceFactory stingle = new StingleDataSourceFactory(this, file);

			MediaSource mediaSource = new ExtractorMediaSource.Factory(stingle).createMediaSource(uri);
			player.setPlayWhenReady(true);
			playerView.setShowShuffleButton(false);
			player.prepare(mediaSource, true, false);

		}
		
		getActionBar().setTitle(realFilename);
		((TextView)findViewById(R.id.countLabel)).setText(String.valueOf(currentPosition+1) + "/" + String.valueOf(files.size()));
	}

	private void releasePlayer() {
		if (player != null) {
			//playbackPosition = player.getCurrentPosition();
			//currentWindow = player.getCurrentWindowIndex();
			//playWhenReady = player.getPlayWhenReady();
			player.release();
			player = null;
		}
	}
	
	@SuppressWarnings("unchecked")
	private void fillFilesList() {
		File dir = new File(Helpers.getHomeDir(this));
		File[] folderFiles = dir.listFiles();

		Arrays.sort(folderFiles, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
			}
		});

		files.clear();
		
		int maxFileSize = Integer.valueOf(getString(R.string.max_file_size)) * 1024 * 1024;
		for (File file : folderFiles) {
			if (file.getName().endsWith(SafeCameraApplication.FILE_EXTENSION)) {
				files.add(file);
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.view_photo_menu, menu);;
        return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		final ArrayList<File> selectedFiles = new ArrayList<File>();
		AlertDialog dialog;
		AlertDialog.Builder builder = new AlertDialog.Builder(ViewImageActivity.this);
		selectedFiles.add(files.get(currentPosition));
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.decrypt:
				final AsyncTasks.OnAsyncTaskFinish finishTask = new AsyncTasks.OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						
						Toast.makeText(ViewImageActivity.this, getString(R.string.success_decrypt), Toast.LENGTH_LONG).show();
					}
				};
				
				builder.setMessage(getString(R.string.confirm_decrypt_files_s));
				builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						Helpers.decryptSelected(ViewImageActivity.this, selectedFiles, finishTask);
					}
				});
				builder.setNegativeButton(getString(R.string.no), null);
				dialog = builder.create();
				dialog.show();
				
				return true;
			case R.id.share:
				Helpers.share(ViewImageActivity.this, selectedFiles, null);
				return true;
			case R.id.delete:
				cancelPendingTasks();
				
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
				dialog = builder.create();
				dialog.show();
				return true;
			case R.id.rotateCW:
				new AsyncTasks.RotatePhoto(this, files.get(currentPosition).getPath(), AsyncTasks.RotatePhoto.ROTATION_CW, new OnAsyncTaskFinish() {
					@Override
					public void onFinish(Integer result) {
						super.onFinish(result);
						if(result == AsyncTasks.RotatePhoto.STATUS_OK){
							String key = Helpers.getThumbsDir(ViewImageActivity.this) + "/" + files.get(currentPosition).getName();
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
							String key = Helpers.getThumbsDir(ViewImageActivity.this) + "/" + files.get(currentPosition).getName();
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
