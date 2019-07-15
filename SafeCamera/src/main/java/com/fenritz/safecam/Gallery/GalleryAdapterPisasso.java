package com.fenritz.safecam.Gallery;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Util.MemoryCache;
import com.squareup.picasso3.Callback;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestCreator;
import com.squareup.picasso3.RequestHandler;


import java.util.ArrayList;
import java.util.List;

public class GalleryAdapterPisasso extends RecyclerView.Adapter<GalleryAdapterPisasso.GalleryVH> implements IDragSelectAdapter {
	private Context context;
	private StingleDbHelper db;
	private final MemoryCache memCache = SafeCameraApplication.getCache();
	private final Listener callback;
	private final ArrayList<Integer> selectedIndices = new ArrayList<Integer>();
	private int thumbSize;
	private boolean isSelectModeActive = false;
	private AutoFitGridLayoutManager lm;
	private Picasso picasso;
	private LruCache<Integer, Integer> typesCache = new LruCache<Integer, Integer>(512);

	// Provide a reference to the views for each data item
	// Complex data items may need more than one view per item, and
	// you provide access to all the views for a data item in a view holder
	public class GalleryVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public RelativeLayout layout;
		public ImageView image;
		public ImageView videoIcon;
		public CheckBox checkbox;
		public int currentPos = -1;

		public GalleryVH(RelativeLayout v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			checkbox = v.findViewById(R.id.checkBox);
			videoIcon = v.findViewById(R.id.videoIcon);
			image.setOnClickListener(this);
			image.setOnLongClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (callback != null) {
				callback.onClick(getAdapterPosition());
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if (callback != null) {
				callback.onLongClick(getAdapterPosition());
			}
			return true;
		}
	}

	public interface Listener {
		void onClick(int index);

		void onLongClick(int index);

		void onSelectionChanged(int count);
	}

	// Provide a suitable constructor (depends on the kind of dataset)
	public GalleryAdapterPisasso(Context context, Listener callback, AutoFitGridLayoutManager lm) {
		this.context = context;
		this.callback = callback;
		this.db = new StingleDbHelper(context);
		this.thumbSize = Helpers.getThumbSize(context);
		this.lm = lm;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new StinglePicassoLoader(context, db, thumbSize)).build();
	}

	// Create new views (invoked by the layout manager)
	@Override
	public GalleryAdapterPisasso.GalleryVH onCreateViewHolder(ViewGroup parent, int viewType) {
		// create a new view
		RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.gallery_item, parent, false);

		GalleryVH vh = new GalleryVH(v);

		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(thumbSize, thumbSize);
		vh.image.setLayoutParams(params);

		return vh;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(GalleryVH holder, final int position) {
		// - get element from your dataset at this position
		// - replace the contents of the view with that element
		//image.setImageDrawable(null);
		if(isSelectModeActive){
			int size = Helpers.convertDpToPixels(context, 20);
			holder.image.setPadding(size,size, size, size);
			holder.checkbox.setVisibility(View.VISIBLE);

			if (selectedIndices.contains(position)) {
				holder.checkbox.setChecked(true);
			}
			else{
				holder.checkbox.setChecked(false);
			}
		}
		else{
			int size = Helpers.convertDpToPixels(context, 5);
			holder.image.setPadding(size,size, size, size);
			holder.checkbox.setVisibility(View.GONE);
		}

		holder.image.setImageBitmap(null);
		holder.videoIcon.setVisibility(View.GONE);
		holder.image.setBackgroundColor(context.getResources().getColor(R.color.galery_item_bg));



		final RequestCreator req = picasso.load("p" + String.valueOf(position));
		req.networkPolicy(NetworkPolicy.NO_CACHE);
		req.tag(holder);
		req.addProp("pos", String.valueOf(position));
		req.into(holder.image, new Callback() {
			@Override
			public void onSuccess(RequestHandler.Result result, Request request) {
				Integer fileType;
				Integer pos = Integer.valueOf(request.getProp("pos"));
				if(pos == null){
					return;
				}
				GalleryVH holder = (GalleryVH)request.tag;
				Integer cachedFileType = typesCache.get(pos);
				if(cachedFileType != null){
					fileType = cachedFileType;
				}
				else{
					fileType = (Integer) result.getProperty("fileType");
					if(fileType != null) {
						typesCache.put(pos, fileType);
					}
				}
				if(fileType != null && fileType == Crypto.FILE_TYPE_VIDEO){
					holder.videoIcon.setVisibility(View.VISIBLE);
				}
				else{
					holder.videoIcon.setVisibility(View.GONE);
				}
			}

			@Override
			public void onError(@NonNull Throwable t) {

			}
		});
	}



	@Override
	public void setSelected(int index, boolean selected) {
		Log.d("MainAdapter", "setSelected(" + index + ", " + selected + ")");
		if (!selected) {
			selectedIndices.remove((Integer) index);
		} else if (!selectedIndices.contains(index)) {
			selectedIndices.add(index);
		}
		notifyItemChanged(index);
		if (callback != null) {
			callback.onSelectionChanged(selectedIndices.size());
		}
	}

	public void setSelectionModeActive(boolean active){
		isSelectModeActive = active;
		notifyDataSetChanged();
	}

	public boolean isSelectionModeActive(){
		return isSelectModeActive;
	}

	public List<Integer> getSelectedIndices() {
		return selectedIndices;
	}

	@Override
	public boolean isIndexSelectable(int index) {
		// Return false if you don't want this position to be selectable.
		// Useful for items like section headers.
		return true;
	}

	public void toggleSelected(int index) {
		if (selectedIndices.contains(index)) {
			selectedIndices.remove((Integer) index);
		} else {
			selectedIndices.add(index);
		}
		notifyItemChanged(index);
		if (callback != null) {
			callback.onSelectionChanged(selectedIndices.size());
		}
	}

	public void clearSelected() {
		if (selectedIndices.isEmpty()) {
			return;
		}
		selectedIndices.clear();
		notifyDataSetChanged();
		if (callback != null) {
			callback.onSelectionChanged(0);
		}
		setSelectionModeActive(false);
	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {
		return (int)db.getTotalFilesCount();
	}



}

