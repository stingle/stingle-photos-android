package com.fenritz.safecam.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbFile;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Sync.SyncManager;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Util.MemoryCache;
import com.squareup.picasso3.Callback;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestCreator;
import com.squareup.picasso3.RequestHandler;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GalleryAdapterPisasso extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements IDragSelectAdapter {
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
	private ArrayList<DateGroup> dates = new ArrayList<DateGroup>();
	private ArrayList<DatePosition> datePositions = new ArrayList<DatePosition>();
	private int folderType;

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_DATE = 1;

	public GalleryAdapterPisasso(Context context, Listener callback, AutoFitGridLayoutManager lm, int folderType) {
		this.context = context;
		this.callback = callback;
		this.folderType = folderType;
		this.db = new StingleDbHelper(context, (folderType == SyncManager.FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));
		this.thumbSize = Helpers.getThumbSize(context);
		this.lm = lm;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new StinglePicassoLoader(context, db, thumbSize)).build();

		getAvailableDates();
		calculateDatePositions();
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		db.close();
	}

	public void updateDataSet(){
		typesCache.evictAll();
		picasso.evictAll();
		getAvailableDates();
		calculateDatePositions();
		notifyDataSetChanged();
	}

	public void setFolder(int folder){
		synchronized (this){
			this.db = new StingleDbHelper(context, (folder == SyncManager.FOLDER_TRASH ? StingleDbContract.Files.TABLE_NAME_TRASH : StingleDbContract.Files.TABLE_NAME_FILES));
			folderType = folder;
			picasso.cancelAll();
			this.picasso = new Picasso.Builder(context).addRequestHandler(new StinglePicassoLoader(context, db, thumbSize)).build();
			updateDataSet();

		}
	}

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
			checkbox.setOnClickListener(this);
			image.setOnClickListener(this);
			image.setOnLongClickListener(this);
		}

		@Override
		public void onClick(View v) {
			if (callback != null) {
				callback.onClick(getAdapterPosition());
			}
			//Log.d("calcPos", String.valueOf(translatePos(getAdapterPosition()).dbPosition));
		}

		@Override
		public boolean onLongClick(View v) {
			if (callback != null) {
				callback.onLongClick(getAdapterPosition());
			}
			return true;
		}
	}
	public class GalleryDate extends RecyclerView.ViewHolder {
		// each data item is just a string in this case
		public RelativeLayout layout;
		public TextView text;
		public int currentPos = -1;

		public GalleryDate(RelativeLayout v) {
			super(v);
			layout = v;
			text = v.findViewById(R.id.text);
		}

	}

	public interface Listener {
		void onClick(int index);

		void onLongClick(int index);

		void onSelectionChanged(int count);
	}

	protected class DateGroup{
		public String date;
		public int itemCount;

		public DateGroup(String date, int itemCount){
			this.date = date;
			this.itemCount = itemCount;
		}
	}
	protected class DatePosition{
		public int position;
		public String date;

		public DatePosition(int position, String date){
			this.position = position;
			this.date = date;
		}
	}

	protected class PosTranslate{
		public int type;
		public int dbPosition;
		public String date = null;
	}

	protected void getAvailableDates(){
		dates.clear();
		Cursor result = db.getAvailableDates();
		while(result.moveToNext()) {
			DateGroup group = new DateGroup(result.getString(0),result.getInt(1));
			dates.add(group);
			//Log.d("dates", group.date + " - " + group.itemCount);
		}
	}

	protected void calculateDatePositions(){
		datePositions.clear();
		int totalItems = 0;
		for(DateGroup group : dates){
			datePositions.add(new DatePosition(totalItems, group.date));
			//Log.d("datepos", String.valueOf(totalItems) + " - " + group.date);

			totalItems += group.itemCount + 1;
		}
	}

	protected boolean isPositionIsDate(int position){
		for(DatePosition dp : datePositions){
			if(dp.position == position){
				return true;
			}
		}
		return false;
	}

	protected PosTranslate translatePos(int pos){
		int datesCount = 1;
		for(int i=0;i<datePositions.size();i++){
			DatePosition datePos = datePositions.get(i);
			DatePosition nextDatePos = null;
			if(i+1 < datePositions.size()){
				nextDatePos = datePositions.get(i+1);
			}

			if(pos > datePos.position && (nextDatePos == null || pos < nextDatePos.position)){
				PosTranslate posObj = new PosTranslate();
				posObj.type = TYPE_ITEM;
				posObj.dbPosition = pos - datesCount;

				return posObj;
			}
			else if(pos == datePos.position){
				PosTranslate posObj = new PosTranslate();
				posObj.type = TYPE_DATE;
				posObj.date = datePos.date;

				return posObj;
			}

			datesCount++;
		}
		return null;
	}

	public int getDbPositionFromRaw(int pos){
		return ((PosTranslate)translatePos(pos)).dbPosition;
	}

	// Create new views (invoked by the layout manager)
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		if(viewType == TYPE_ITEM) {
			// create a new view
			RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
					.inflate(R.layout.gallery_item, parent, false);

			GalleryVH vh = new GalleryVH(v);

			RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(thumbSize, thumbSize);
			vh.image.setLayoutParams(params);

			return vh;
		}
		else if(viewType == TYPE_DATE){
			RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
					.inflate(R.layout.gallery_item_date, parent, false);

			GalleryDate vh = new GalleryDate(v);

			return vh;
		}

		return null;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {
		// - get element from your dataset at this position
		// - replace the contents of the view with that element
		//image.setImageDrawable(null);
		PosTranslate transPos = translatePos(rawPosition);

		if(holderObj instanceof GalleryVH) {
			final int position = transPos.dbPosition;
			GalleryVH holder = (GalleryVH)holderObj;
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
				int size = Helpers.convertDpToPixels(context, 5);
				holder.image.setPadding(size, size, size, size);
				holder.checkbox.setVisibility(View.GONE);
			}

			holder.image.setImageBitmap(null);
			holder.videoIcon.setVisibility(View.GONE);
			holder.image.setBackgroundColor(context.getResources().getColor(R.color.galery_item_bg));

			String folder = "m";
			if(folderType == SyncManager.FOLDER_TRASH){
				folder = "t";
			}

			final RequestCreator req = picasso.load("p" + folder + String.valueOf(position));
			req.networkPolicy(NetworkPolicy.NO_CACHE);
			req.tag(holder);
			req.addProp("pos", String.valueOf(position));
			req.into(holder.image, new Callback() {
				@Override
				public void onSuccess(RequestHandler.Result result, Request request) {
					Integer fileType;
					Integer pos = Integer.valueOf(request.getProp("pos"));
					if (pos == null) {
						return;
					}
					GalleryVH holder = (GalleryVH) request.tag;
					Integer cachedFileType = typesCache.get(pos);
					if (cachedFileType != null) {
						fileType = cachedFileType;
					} else {
						fileType = (Integer) result.getProperty("fileType");
						if (fileType != null) {
							typesCache.put(pos, fileType);
						}
					}
					if (fileType != null && fileType == Crypto.FILE_TYPE_VIDEO) {
						holder.videoIcon.setVisibility(View.VISIBLE);
					} else {
						holder.videoIcon.setVisibility(View.GONE);
					}
				}

				@Override
				public void onError(@NonNull Throwable t) {

				}
			});
		}
		else if(holderObj instanceof GalleryDate) {
			GalleryDate holder = (GalleryDate)holderObj;
			holder.text.setText(transPos.date);
		}
	}



	@Override
	public int getItemViewType(int position) {
		return (isPositionIsDate(position) ? TYPE_DATE : TYPE_ITEM);
	}

	public StingleDbFile getStingleFileAtPosition(int position){
		return db.getFileAtPosition(translatePos(position).dbPosition);
	}

	@Override
	public void setSelected(int index, boolean selected) {
		if(isPositionIsDate(index)){
			return;
		}
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
		if(isPositionIsDate(index)){
			return;
		}
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
		selectedIndices.clear();
		if (callback != null) {
			callback.onSelectionChanged(0);
		}
		setSelectionModeActive(false);
	}

	// Return the size of your dataset (invoked by the layout manager)
	@Override
	public int getItemCount() {
		int totalItems = 0;
		for(DateGroup group : dates){
			totalItems += group.itemCount + 1;
		}

		return totalItems;
		//Log.d("totalCount", String.valueOf(db.getTotalFilesCount()));
		//return (int)db.getTotalFilesCount() + dates.size();
	}



}

