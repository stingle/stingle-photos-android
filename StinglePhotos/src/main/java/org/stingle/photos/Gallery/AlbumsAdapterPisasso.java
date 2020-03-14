package org.stingle.photos.Gallery;

import android.content.Context;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso3.Callback;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestCreator;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Db.AlbumFilesDb;
import org.stingle.photos.Db.AlbumsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Db.StingleDbAlbum;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;

import java.util.ArrayList;
import java.util.List;

public class AlbumsAdapterPisasso extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements IDragSelectAdapter {
	private Context context;
	private AlbumsDb db;
	private AlbumFilesDb filesDb;
	private final MemoryCache memCache = StinglePhotosApplication.getCache();
	private Listener listener;
	private final ArrayList<Integer> selectedIndices = new ArrayList<Integer>();
	private int thumbSize;
	private boolean isSelectModeActive = false;
	private RecyclerView.LayoutManager lm;
	private Picasso picasso;
	private LruCache<Integer, AlbumProps> filePropsCache = new LruCache<Integer, AlbumProps>(512);

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_ADD = 1;

	public static final int LAYOUT_GRID = 0;
	public static final int LAYOUT_LIST = 1;
	private int layoutStyle = LAYOUT_GRID;

	public AlbumsAdapterPisasso(Context context, RecyclerView.LayoutManager lm) {
		this.context = context;
		this.db = new AlbumsDb(context);
		this.filesDb = new AlbumFilesDb(context);
		this.thumbSize = Helpers.getThumbSize(context,2);
		this.lm = lm;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new AlbumsPicassoLoader(context, db, filesDb, thumbSize)).build();

	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		db.close();
	}

	public void setListener(Listener listener){
		this.listener = listener;
	}

	public void updateDataSet(){
		filePropsCache.evictAll();
		picasso.evictAll();
		notifyDataSetChanged();
	}

	public void setLayoutStyle(int layoutStyle){
		this.layoutStyle = layoutStyle;
	}


	// Provide a reference to the views for each data item
	// Complex data items may need more than one view per item, and
	// you provide access to all the views for a data item in a view holder
	public class AlbumVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public View layout;
		public ImageView image;
		public CheckBox checkbox;
		public TextView albumName;
		public int currentPos = -1;

		public AlbumVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			checkbox = v.findViewById(R.id.checkBox);
			albumName = v.findViewById(R.id.albumName);
			checkbox.setOnClickListener(this);
			image.setOnClickListener(this);
			image.setOnLongClickListener(this);
			albumName.setOnClickListener(this);
			albumName.setOnLongClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (listener != null) {
				listener.onClick(getAdapterPosition());
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if (listener != null) {
				listener.onLongClick(getAdapterPosition());
			}
			return true;
		}
	}

	public class AlbumAddVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public View layout;
		public ImageView image;
		public TextView albumName;

		public AlbumAddVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			albumName = v.findViewById(R.id.albumName);
			image.setOnClickListener(this);
			image.setOnLongClickListener(this);
			albumName.setOnClickListener(this);
			albumName.setOnLongClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (listener != null) {
				listener.onClick(getAdapterPosition());
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if (listener != null) {
				listener.onLongClick(getAdapterPosition());
			}
			return true;
		}
	}

	public interface Listener {
		void onClick(int index);

		void onLongClick(int index);

		void onSelectionChanged(int count);
	}


	public int translateDbPosToGalleryPos(int dbPos){
		return dbPos + 1;
	}
	public int translateGalleryPosToDbPos(int pos){
		return pos - 1;
	}


	public void updateItem(int dbPosition){
		int galleryPos = translateDbPosToGalleryPos(dbPosition);

		filePropsCache.remove(dbPosition);
		picasso.invalidate("a" + String.valueOf(dbPosition));
		//picasso.evictAll();
		notifyItemChanged(galleryPos);
	}

	// Create new views (invoked by the layout manager)
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		if(layoutStyle == LAYOUT_GRID) {
			if (viewType == TYPE_ITEM) {
				// create a new view
				RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.album_item, parent, false);

				AlbumVH vh = new AlbumVH(v);

				RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(thumbSize, thumbSize);
				vh.image.setLayoutParams(params);

				return vh;
			} else if (viewType == TYPE_ADD) {
				RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.album_add_item, parent, false);

				AlbumAddVH vh = new AlbumAddVH(v);

				return vh;
			}
		}
		else if(layoutStyle == LAYOUT_LIST) {
			if (viewType == TYPE_ITEM) {
				// create a new view
				LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.add_to_album_dialog_item, parent, false);

				AlbumVH vh = new AlbumVH(v);

				return vh;
			} else if (viewType == TYPE_ADD) {
				LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.add_to_album_dialog_item, parent, false);

				AlbumAddVH vh = new AlbumAddVH(v);

				return vh;
			}
		}

		return null;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {
		// - get element from your dataset at this position
		// - replace the contents of the view with that element
		//image.setImageDrawable(null);
		final int dbPos = translateGalleryPosToDbPos(rawPosition);

		if(holderObj instanceof AlbumVH) {
			AlbumVH holder = (AlbumVH)holderObj;
			if (isSelectModeActive) {
				int size = Helpers.convertDpToPixels(context, 20);
				holder.image.setPadding(size, size, size, size);
				holder.checkbox.setVisibility(View.VISIBLE);

				if (selectedIndices.contains(rawPosition)) {
					holder.checkbox.setChecked(true);
				} else {
					holder.checkbox.setChecked(false);
				}
			} else {
				int size = Helpers.convertDpToPixels(context, 2);
				holder.image.setPadding(size, size, size, size);
				holder.checkbox.setVisibility(View.GONE);
			}

			holder.image.setImageBitmap(null);
			holder.albumName.setText("");

			final RequestCreator req = picasso.load("a" + dbPos);
			req.networkPolicy(NetworkPolicy.NO_CACHE);
			req.tag(holder);
			req.addProp("pos", String.valueOf(dbPos));
			req.into(holder.image, new Callback() {
				@Override
				public void onSuccess(RequestHandler.Result result, Request request) {
					Integer pos = Integer.valueOf(request.getProp("pos"));
					if (pos == null) {
						return;
					}
					AlbumVH holder = (AlbumVH) request.tag;

					AlbumProps props = filePropsCache.get(pos);
					if (props == null) {
						props = (AlbumProps) result.getProperty("albumProps");
						if (props != null) {
							filePropsCache.put(pos, props);
						}
						else{
							props = new AlbumProps();
						}
					}

					if(props.name != null){
						holder.albumName.setText(props.name);
					}
				}

				@Override
				public void onError(@NonNull Throwable t) {

				}
			});
		}
		else if(holderObj instanceof AlbumAddVH) {
			AlbumAddVH holder = (AlbumAddVH)holderObj;
			holder.albumName.setText(context.getString(R.string.new_album));
			holder.image.setImageDrawable(context.getDrawable(R.drawable.ic_add_circle));
		}
	}



	@Override
	public int getItemViewType(int position) {
		if(position == 0){
			return TYPE_ADD;
		}
		else {
			return TYPE_ITEM;
		}
	}

	public StingleDbAlbum getAlbumAtPosition(int position){
		return db.getAlbumAtPosition(translateGalleryPosToDbPos(position), StingleDb.SORT_ASC);
	}



	@Override
	public void setSelected(int index, boolean selected) {

		if (!selected) {
			selectedIndices.remove((Integer) index);
		} else if (!selectedIndices.contains(index)) {
			selectedIndices.add(index);
		}
		notifyItemChanged(index);
		if (listener != null) {
			listener.onSelectionChanged(selectedIndices.size());
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
		if (listener != null) {
			listener.onSelectionChanged(selectedIndices.size());
		}
	}

	public void clearSelected() {
		selectedIndices.clear();
		if (listener != null) {
			listener.onSelectionChanged(0);
		}
		setSelectionModeActive(false);
	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {
		return (int)db.getTotalAlbumsCount() + 1;
	}


	public static class AlbumProps{
		public String name;
		public boolean isUploaded = true;
	}

}

