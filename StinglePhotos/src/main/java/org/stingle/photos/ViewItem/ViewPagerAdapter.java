package org.stingle.photos.ViewItem;


import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.exoplayer2.SimpleExoPlayer;

import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Widget.ImageHolderLayout;

import java.util.HashMap;

public class ViewPagerAdapter extends PagerAdapter {

	private Context context;
	private LayoutInflater layoutInflater;
	private FilesDb db;
	private int currentPosition = 0;
	private int lastFilesCount = -1;
	private int set = SyncManager.GALLERY;
	private String albumId = null;
	private HashMap<Integer, SimpleExoPlayer> players = new HashMap<Integer, SimpleExoPlayer>();
	private View.OnTouchListener gestureTouchListener;
	private View.OnClickListener onSingleClickListener;

	public ViewPagerAdapter(Context context, int set, String albumId, View.OnClickListener onSingleClickListener, View.OnTouchListener gestureTouchListener) {
		this.context = context;
		this.set = set;
		this.albumId = albumId;
		this.gestureTouchListener = gestureTouchListener;
		this.onSingleClickListener = onSingleClickListener;
		layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		switch (set) {
			case SyncManager.GALLERY:
				this.db = new GalleryTrashDb(context, SyncManager.GALLERY);
				break;
			case SyncManager.TRASH:
				this.db = new GalleryTrashDb(context, SyncManager.TRASH);
				break;
			case SyncManager.ALBUM:
				this.db = new AlbumFilesDb(context);
				break;
		}
	}

	@Override
	public int getCount() {
		if (lastFilesCount == -1) {
			lastFilesCount = (int) db.getTotalFilesCount(albumId);
		}
		return lastFilesCount;
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
		ViewGroup layout = (ViewGroup) layoutInflater.inflate(R.layout.item_view_item, container, false);
		ImageHolderLayout parent = layout.findViewById(R.id.parent_layout);
		ContentLoadingProgressBar loading = layout.findViewById(R.id.loading_spinner);

		(new ViewItemAsyncTask(context, this, position, parent, loading, db, set, albumId, onSingleClickListener, gestureTouchListener, null)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		container.addView(layout);
		return layout;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object view) {
		container.removeView((View) view);
		if (players.containsKey(position)) {
			SimpleExoPlayer player = players.get(position);
			if (player != null) {
				player.release();
			}
			players.remove(position);
		}
	}

	@Override
	public int getItemPosition(Object object) {
		return POSITION_NONE;
	}

	public void addPlayer(int position, SimpleExoPlayer player) {
		synchronized (this) {
			players.put(position, player);
		}
	}

	public void releasePlayers() {
		synchronized (this) {
			for (int pos : players.keySet()) {
				players.get(pos).release();
			}
			players.clear();
		}
	}

	public void pauseAllPlayers() {
		synchronized (this) {
			for (int pos : players.keySet()) {
				SimpleExoPlayer player = players.get(pos);
				if (player != null) {
					player.setPlayWhenReady(false);
				}
			}
		}
	}

	public void startPlaying(int position) {
		synchronized (this) {
			SimpleExoPlayer player = players.get(position);
			if (player != null) {
				player.setPlayWhenReady(true);
			}
		}
	}

	public SimpleExoPlayer getPlayer(int position) {
		return players.get(position);
	}

	public void setCurrentPosition(int pos) {
		currentPosition = pos;
	}

	public int getCurrentPosition() {
		return currentPosition;
	}
}
