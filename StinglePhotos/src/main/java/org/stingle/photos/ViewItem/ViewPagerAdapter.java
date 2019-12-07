package org.stingle.photos.ViewItem;


import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.viewpager.widget.PagerAdapter;

import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Db.StingleDbHelper;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Widget.ImageHolderLayout;
import com.google.android.exoplayer2.SimpleExoPlayer;

import java.util.HashMap;

public class ViewPagerAdapter extends PagerAdapter {

	private Context context;
	private LayoutInflater layoutInflater;
	private StingleDbHelper db;
	private int currentPosition = 0;
	private int lastFilesCount = -1;
	private int folder = SyncManager.FOLDER_MAIN;
	private HashMap<Integer, SimpleExoPlayer> players = new HashMap<Integer, SimpleExoPlayer>();
	private View.OnTouchListener gestureTouchListener;

	public ViewPagerAdapter(Context context, int folder, View.OnTouchListener gestureTouchListener){
		this.context = context;
		this.folder = folder;
		this.gestureTouchListener = gestureTouchListener;
		layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		db = new StingleDbHelper(context, (folder == SyncManager.FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));
	}

	@Override
	public int getCount() {
		if(lastFilesCount == -1) {
			lastFilesCount = (int) db.getTotalFilesCount();
		}
		return lastFilesCount;
		/*
		int count = (int)db.getTotalFilesCount();
		countDifference = count - lastFilesCount;
		if(countDifference < 0){
			countDifference = 0;
		}

		if(lastFilesCount == -1){
			lastFilesCount = count;
		}

		return lastFilesCount;*/
	}

	@Override
	public void notifyDataSetChanged() {
		lastFilesCount = -1;
		super.notifyDataSetChanged();
	}

	@Override
	public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
		return view == object;
	}

	@NonNull
	@Override
	public Object instantiateItem(@NonNull ViewGroup container, int position) {
		ViewGroup layout = (ViewGroup) layoutInflater.inflate(R.layout.view_item_item, container, false);
		ImageHolderLayout parent = (ImageHolderLayout)layout.findViewById(R.id.parent_layout);
		ContentLoadingProgressBar loading = (ContentLoadingProgressBar)layout.findViewById(R.id.loading_spinner);

		(new ViewItemAsyncTask(context, this, position, parent, loading, db, folder, null, gestureTouchListener, null)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		container.addView(layout);
		return layout;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object view) {
		container.removeView((View) view);
		if(players.containsKey(position)){
			SimpleExoPlayer player = players.get(position);
			if(player != null) {
				player.release();
			}
			players.remove(position);
		}
	}

	@Override
	public int getItemPosition(Object object) {
		return POSITION_NONE;
	}

	public void addPlayer(int position, SimpleExoPlayer player){
		synchronized (this) {
			players.put(position, player);
		}
	}

	public void releasePlayers(){
		synchronized (this) {
			for (int pos : players.keySet()) {
				players.get(pos).release();
			}
			players.clear();
		}
	}

	public void pauseAllPlayers(){
		synchronized (this) {
			for (int pos : players.keySet()) {
				SimpleExoPlayer player = players.get(pos);
				if (player != null) {
					player.setPlayWhenReady(false);
				}
			}
		}
	}

	public void startPlaying(int position){
		synchronized (this) {
			SimpleExoPlayer player = players.get(position);
			if (player != null) {
				player.setPlayWhenReady(true);
			}
		}
	}

	public SimpleExoPlayer getPlayer(int position){
		return players.get(position);
	}

	public void setCurrentPosition(int pos){
		currentPosition = pos;
	}

	public int getCurrentPosition(){
		return currentPosition;
	}
}
