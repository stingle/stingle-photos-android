package org.stingle.photos.Gallery.Folders;

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

import org.stingle.photos.Db.Objects.StingleDbFolder;
import org.stingle.photos.Db.Query.FolderFilesDb;
import org.stingle.photos.Db.Query.FoldersDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;

public class FoldersAdapterPisasso extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private Context context;
	private FoldersDb db;
	private FolderFilesDb filesDb;
	private boolean showGalleryOption;
	private final MemoryCache memCache = StinglePhotosApplication.getCache();
	private Listener listener;
	private int thumbSize;
	private RecyclerView.LayoutManager lm;
	private Picasso picasso;
	private LruCache<Integer, FolderProps> filePropsCache = new LruCache<Integer, FolderProps>(512);

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_ADD = 1;
	public static final int TYPE_GALLERY = 2;

	public static final int LAYOUT_GRID = 0;
	public static final int LAYOUT_LIST = 1;
	private int layoutStyle = LAYOUT_GRID;
	private int otherItemsCount = 1;

	public FoldersAdapterPisasso(Context context, RecyclerView.LayoutManager lm, boolean showGalleryOption) {
		this.context = context;
		this.db = new FoldersDb(context);
		this.filesDb = new FolderFilesDb(context);
		this.thumbSize = Helpers.getThumbSize(context,2);
		this.lm = lm;
		this.showGalleryOption = showGalleryOption;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new FoldersPicassoLoader(context, db, filesDb, thumbSize)).build();

		if(showGalleryOption){
			otherItemsCount = 2;
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
	public class FolderVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public View layout;
		public ImageView image;
		public CheckBox checkbox;
		public TextView folderName;
		public int currentPos = -1;

		public FolderVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			checkbox = v.findViewById(R.id.checkBox);
			folderName = v.findViewById(R.id.folderName);
			checkbox.setOnClickListener(this);
			image.setOnClickListener(this);
			image.setOnLongClickListener(this);
			folderName.setOnClickListener(this);
			folderName.setOnLongClickListener(this);
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

	public class FolderAddVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public View layout;
		public ImageView image;
		public TextView folderName;

		public FolderAddVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			folderName = v.findViewById(R.id.folderName);
			image.setOnClickListener(this);
			image.setOnLongClickListener(this);
			folderName.setOnClickListener(this);
			folderName.setOnLongClickListener(this);
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

	public class FolderAddGalleryVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public View layout;
		public ImageView image;
		public TextView folderName;

		public FolderAddGalleryVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			folderName = v.findViewById(R.id.folderName);
			image.setOnClickListener(this);
			image.setOnLongClickListener(this);
			folderName.setOnClickListener(this);
			folderName.setOnLongClickListener(this);
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
						.inflate(R.layout.folder_item, parent, false);

				FolderVH vh = new FolderVH(v);

				RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(thumbSize, thumbSize);
				vh.image.setLayoutParams(params);

				return vh;
			} else if (viewType == TYPE_ADD) {
				RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.folder_add_item, parent, false);

				FolderAddVH vh = new FolderAddVH(v);

				return vh;
			}
		}
		else if(layoutStyle == LAYOUT_LIST) {
			if (viewType == TYPE_ITEM) {
				// create a new view
				LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.add_to_folder_dialog_item, parent, false);

				FolderVH vh = new FolderVH(v);

				return vh;
			} else if (viewType == TYPE_ADD) {
				LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.add_to_folder_dialog_item, parent, false);

				FolderAddVH vh = new FolderAddVH(v);

				return vh;
			} else if (viewType == TYPE_GALLERY) {
				LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
						.inflate(R.layout.add_to_folder_dialog_item, parent, false);

				FolderAddGalleryVH vh = new FolderAddGalleryVH(v);

				return vh;
			}
		}

		return null;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {

		final int dbPos = translateGalleryPosToDbPos(rawPosition);

		if(holderObj instanceof FolderVH) {
			FolderVH holder = (FolderVH)holderObj;
			int size = Helpers.convertDpToPixels(context, 2);
			holder.image.setPadding(size, size, size, size);
			holder.checkbox.setVisibility(View.GONE);

			holder.image.setImageBitmap(null);
			holder.folderName.setText("");

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
					FolderVH holder = (FolderVH) request.tag;

					FolderProps props = filePropsCache.get(pos);
					if (props == null) {
						props = (FolderProps) result.getProperty("folderProps");
						if (props != null) {
							filePropsCache.put(pos, props);
						}
						else{
							props = new FolderProps();
						}
					}

					if(props.name != null){
						holder.folderName.setText(props.name);
					}
				}

				@Override
				public void onError(@NonNull Throwable t) {

				}
			});
		}
		else if(holderObj instanceof FolderAddVH) {
			FolderAddVH holder = (FolderAddVH)holderObj;
			holder.folderName.setText(context.getString(R.string.new_album));
			holder.image.setImageDrawable(context.getDrawable(R.drawable.ic_add_circle_outline));
		}
		else if(holderObj instanceof FolderAddGalleryVH) {
			FolderAddGalleryVH holder = (FolderAddGalleryVH)holderObj;
			holder.folderName.setText(context.getString(R.string.main_gallery));
			holder.image.setImageDrawable(context.getDrawable(R.drawable.ic_image_red));
		}
	}



	@Override
	public int getItemViewType(int position) {
		if(showGalleryOption){
			if(position == 0) {
				return TYPE_GALLERY;
			}
			else if (position == 1){
				return TYPE_ADD;
			}
		}
		else if(position == 0){
			return TYPE_ADD;
		}

		return TYPE_ITEM;
	}

	public StingleDbFolder getFolderAtPosition(int position){
		return db.getFolderAtPosition(translateGalleryPosToDbPos(position), StingleDb.SORT_ASC);
	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {
		return (int)db.getTotalFoldersCount() + otherItemsCount;
	}


	public static class FolderProps {
		public String name;
		public boolean isUploaded = true;
	}

}

