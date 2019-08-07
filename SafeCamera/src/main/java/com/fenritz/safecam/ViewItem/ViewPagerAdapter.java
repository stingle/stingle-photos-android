package com.fenritz.safecam.ViewItem;


import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.R;
import com.fenritz.safecam.Sync.SyncManager;
import com.fenritz.safecam.Widget.ImageHolderLayout;
import com.google.android.exoplayer2.SimpleExoPlayer;

import java.util.ArrayList;
import java.util.HashMap;

public class ViewPagerAdapter extends PagerAdapter {

	private Context context;
	private LayoutInflater layoutInflater;
	private StingleDbHelper db;
	private int currentPosition = 0;
	private int folder = SyncManager.FOLDER_MAIN;
	private HashMap<Integer, SimpleExoPlayer> players = new HashMap<Integer, SimpleExoPlayer>();

	public ViewPagerAdapter(Context context, int folder){
		this.context = context;
		this.folder = folder;
		layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		db = new StingleDbHelper(context, (folder == SyncManager.FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));
	}

	@Override
	public int getCount() {
		return (int)db.getTotalFilesCount();
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

		(new ViewItemAsyncTask(context, this, position, parent, db, folder, null, null, null)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

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
