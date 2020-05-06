package org.stingle.photos.Gallery.Albums;

import android.content.Context;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;

public class AlbumsAdapterPisasso extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private Context context;
	private AlbumsDb db;
	private AlbumFilesDb filesDb;
	private Integer view;
	private boolean showGalleryOption;
	private boolean showAddOption;
	private final MemoryCache memCache = StinglePhotosApplication.getCache();
	private Listener listener;
	private int thumbSize;
	private RecyclerView.LayoutManager lm;
	private Picasso picasso;
	private LruCache<Integer, AlbumProps> filePropsCache = new LruCache<Integer, AlbumProps>(512);

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_ADD = 1;
	public static final int TYPE_GALLERY = 2;

	public static final int LAYOUT_GRID = 0;
	public static final int LAYOUT_LIST = 1;
	private int layoutStyle = LAYOUT_GRID;
	private int otherItemsCount = 0;

	public AlbumsAdapterPisasso(Context context, RecyclerView.LayoutManager lm, Integer view, boolean showAddOption, boolean showGalleryOption) {
		this.context = context;
		this.db = new AlbumsDb(context);
		this.filesDb = new AlbumFilesDb(context);
		this.thumbSize = Helpers.getThumbSize(context,2);
		this.lm = lm;
		this.view = view;
		this.showGalleryOption = showGalleryOption;
		this.showAddOption = showAddOption;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new AlbumsPicassoLoader(context, db, filesDb, view, thumbSize)).build();

		if(showAddOption){
			otherItemsCount++;
		}
		if(showGalleryOption){
			otherItemsCount++;
		}
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
		public ImageView rightIcon;
		//public CheckBox checkbox;
		public TextView albumName;
		public TextView albumSubtitle;
		public int currentPos = -1;

		public AlbumVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			//checkbox = v.findViewById(R.id.checkBox);
			albumName = v.findViewById(R.id.albumName);
			albumSubtitle = v.findViewById(R.id.albumSubtitle);
			rightIcon = v.findViewById(R.id.rightIcon);
			//checkbox.setOnClickListener(this);
			image.setOnClickListener(this);
			//image.setOnLongClickListener(this);
			albumName.setOnClickListener(this);
			//albumName.setOnLongClickListener(this);
			albumSubtitle.setOnClickListener(this);
			if(rightIcon != null) {
				rightIcon.setOnClickListener(this);
			}
			//albumItemsCount.setOnLongClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (listener != null) {
				listener.onClick(getAdapterPosition(), TYPE_ITEM);
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if (listener != null) {
				listener.onLongClick(getAdapterPosition(), TYPE_ITEM);
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
			//image.setOnLongClickListener(this);
			albumName.setOnClickListener(this);
			//albumName.setOnLongClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (listener != null) {
				listener.onClick(getAdapterPosition(), TYPE_ADD);
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if (listener != null) {
				listener.onLongClick(getAdapterPosition(), TYPE_ADD);
			}
			return true;
		}
	}

	public interface Listener {
		void onClick(int index, int type);

		void onLongClick(int index, int type);

		void onSelectionChanged(int count);
	}

	public class AlbumAddGalleryVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public View layout;
		public ImageView image;
		public TextView albumName;

		public AlbumAddGalleryVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			albumName = v.findViewById(R.id.albumName);
			image.setOnClickListener(this);
			//image.setOnLongClickListener(this);
			albumName.setOnClickListener(this);
			//albumName.setOnLongClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (listener != null) {
				listener.onClick(getAdapterPosition(), TYPE_GALLERY);
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if (listener != null) {
				listener.onLongClick(getAdapterPosition(), TYPE_GALLERY);
			}
			return true;
		}
	}

	public int translateDbPosToGalleryPos(int dbPos){
		return dbPos + otherItemsCount;
	}
	public int translateGalleryPosToDbPos(int pos){
		return pos - otherItemsCount;
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
						.inflate(R.layout.item_albums_grid, parent, false);

				AlbumVH vh = new AlbumVH(v);

				RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(thumbSize, thumbSize);
				vh.image.setLayoutParams(params);

				return vh;
			} else if (viewType == TYPE_ADD) {
				RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.item_album_add_grid, parent, false);

				AlbumAddVH vh = new AlbumAddVH(v);

				return vh;
			}
		}
		else if(layoutStyle == LAYOUT_LIST) {
			if (viewType == TYPE_ITEM) {
				// create a new view
				RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.item_albums_list, parent, false);

				AlbumVH vh = new AlbumVH(v);

				return vh;
			} else if (viewType == TYPE_ADD) {
				RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.item_albums_list, parent, false);

				AlbumAddVH vh = new AlbumAddVH(v);

				return vh;
			} else if (viewType == TYPE_GALLERY) {
				RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.item_albums_list, parent, false);

				AlbumAddGalleryVH vh = new AlbumAddGalleryVH(v);

				return vh;
			}
		}

		return null;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {

		final int dbPos = translateGalleryPosToDbPos(rawPosition);

		if(holderObj instanceof AlbumVH) {
			AlbumVH holder = (AlbumVH)holderObj;
			int size = Helpers.convertDpToPixels(context, 2);
			holder.image.setPadding(size, size, size, size);
			//holder.checkbox.setVisibility(View.GONE);

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
					if(props.subtitle != null) {
						holder.albumSubtitle.setText(props.subtitle);
					}

					if(props.rightIcon != AlbumProps.ICON_NONE){
						if(props.rightIcon == AlbumProps.ICON_RECEIVED) {
							holder.rightIcon.setImageDrawable(context.getDrawable(R.drawable.ic_received));
						}
						else if(props.rightIcon == AlbumProps.ICON_SENT) {
							holder.rightIcon.setImageDrawable(context.getDrawable(R.drawable.ic_sent));
						}
						holder.rightIcon.setVisibility(View.VISIBLE);
					}
				}

				@Override
				public void onError(@NonNull Throwable t) {

				}
			});
		}
		else if(holderObj instanceof AlbumAddVH) {
			AlbumAddVH holder = (AlbumAddVH)holderObj;
			holder.albumName.setText(context.getString(R.string.create_new_album));
			holder.image.setImageDrawable(context.getDrawable(R.drawable.ic_add_circle_outline));
			holder.image.setColorFilter(context.getColor(R.color.primaryColor), android.graphics.PorterDuff.Mode.SRC_IN);

			if(layoutStyle == LAYOUT_GRID) {
				int margin = Helpers.convertDpToPixels(context, 10);
				int size = Helpers.getThumbSize(context, 2) - margin;
				ViewGroup.LayoutParams params = holder.layout.getLayoutParams();
				params.height = size - margin;
				holder.layout.setLayoutParams(params);
			}

		}
		else if(holderObj instanceof AlbumAddGalleryVH) {
			AlbumAddGalleryVH holder = (AlbumAddGalleryVH)holderObj;
			holder.albumName.setText(context.getString(R.string.main_gallery));
			holder.image.setImageDrawable(context.getDrawable(R.drawable.ic_image_red));
		}
	}



	@Override
	public int getItemViewType(int position) {
		if(showAddOption && showGalleryOption){
			if(position == 0) {
				return TYPE_GALLERY;
			}
			else if (position == 1){
				return TYPE_ADD;
			}
		}
		else if(showAddOption && position == 0){
			return TYPE_ADD;
		}
		else if(showGalleryOption && position == 0){
			return TYPE_GALLERY;
		}

		return TYPE_ITEM;
	}

	public StingleDbAlbum getAlbumAtPosition(int position){
		return db.getAlbumAtPosition(translateGalleryPosToDbPos(position), StingleDb.SORT_DESC, AlbumsFragment.getAlbumIsHiddenByView(view), AlbumsFragment.getAlbumIsSharedByView(view));
	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {
		return (int)db.getTotalAlbumsCount(AlbumsFragment.getAlbumIsHiddenByView(view), AlbumsFragment.getAlbumIsSharedByView(view)) + otherItemsCount;
	}


	public static class AlbumProps {
		public static int ICON_NONE = 0;
		public static int ICON_SENT = 1;
		public static int ICON_RECEIVED = 2;

		public String name;
		public String subtitle;
		public Integer rightIcon = ICON_NONE;
	}

}

