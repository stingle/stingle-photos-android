package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.database.Cursor;
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

import com.squareup.picasso3.Callback;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestCreator;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Gallery.Helpers.AutoFitGridLayoutManager;
import org.stingle.photos.Gallery.Helpers.IDragSelectAdapter;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class GalleryAdapterPisasso extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements IDragSelectAdapter {
	private Context context;
	private FilesDb db;
	private final MemoryCache memCache = StinglePhotosApplication.getCache();
	private final Listener callback;
	private final ArrayList<Integer> selectedIndices = new ArrayList<Integer>();
	private int thumbSize;
	private boolean isSelectModeActive = false;
	private AutoFitGridLayoutManager lm;
	private Picasso picasso;
	private LruCache<Integer, FileProps> filePropsCache = new LruCache<Integer, FileProps>(512);
	private ArrayList<DateGroup> dates = new ArrayList<DateGroup>();
	private ArrayList<DatePosition> datePositions = new ArrayList<DatePosition>();
	private int set = SyncManager.GALLERY;
	private String albumId = null;
	private int DB_SORT = StingleDb.SORT_DESC;

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_DATE = 1;

	public GalleryAdapterPisasso(Context context, Listener callback, AutoFitGridLayoutManager lm, int set, String albumId) {
		this.context = context;
		this.callback = callback;
		this.set = set;
		this.albumId = albumId;
		switch (set){
			case SyncManager.GALLERY:
				this.db = new GalleryTrashDb(context, SyncManager.GALLERY);
				break;
			case SyncManager.TRASH:
				this.db = new GalleryTrashDb(context, SyncManager.TRASH);
				break;
			case SyncManager.ALBUM:
				DB_SORT = StingleDb.SORT_ASC;
				this.db = new AlbumFilesDb(context);
				break;
		}
		this.thumbSize = Helpers.getThumbSize(context);
		this.lm = lm;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new StinglePicassoLoader(context, db, thumbSize, albumId)).build();

		getAvailableDates();
		calculateDatePositions();
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		db.close();
	}

	public void updateDataSet(){
		filePropsCache.evictAll();
		picasso.evictAll();
		getAvailableDates();
		calculateDatePositions();
		notifyDataSetChanged();
	}

	// Provide a reference to the views for each data item
	// Complex data items may need more than one view per item, and
	// you provide access to all the views for a data item in a view holder
	public class GalleryVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		// each data item is just a string in this case
		public RelativeLayout layout;
		public ImageView image;
		public ImageView videoIcon;
		public ImageView noCloudIcon;
		public TextView videoDuration;
		public CheckBox checkbox;

		public GalleryVH(RelativeLayout v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			checkbox = v.findViewById(R.id.checkBox);
			videoIcon = v.findViewById(R.id.videoIcon);
			noCloudIcon = v.findViewById(R.id.noCloudIcon);
			videoDuration = v.findViewById(R.id.videoDuration);
			checkbox.setOnClickListener(this);
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
	public class GalleryDate extends RecyclerView.ViewHolder {
		public RelativeLayout layout;
		public TextView text;

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
		Cursor result = db.getAvailableDates(albumId, DB_SORT);
		while(result.moveToNext()) {
			DateGroup group = new DateGroup(convertDate(result.getString(0)), result.getInt(1));
			dates.add(group);
		}
	}

	private String convertDate(String dateString){
		try {
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
			Date date = fmt.parse(dateString);

			SimpleDateFormat fmtOut = new SimpleDateFormat("MMMM d, y");
			return fmtOut.format(date);
		}
		catch (ParseException ignored) {}

		return dateString;
	}

	private void calculateDatePositions(){
		datePositions.clear();
		int totalItems = 0;
		for(DateGroup group : dates){
			datePositions.add(new DatePosition(totalItems, group.date));

			totalItems += group.itemCount + 1;
		}
	}

	private boolean isPositionIsDate(int position){
		for(DatePosition dp : datePositions){
			if(dp.position == position){
				return true;
			}
		}
		return false;
	}

	private PosTranslate translatePos(int pos){
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

	private int translateDbPosToGalleryPos(int pos){
		for(int i=0;i<datePositions.size();i++){
			DatePosition datePos = datePositions.get(i);
			if(datePos.position <= pos){
				pos++;
			}
			else{
				break;
			}
		}
		return pos;
	}

	public int getDbPositionFromRaw(int pos){
		return Objects.requireNonNull(translatePos(pos)).dbPosition;
	}

	public void updateItem(int dbPosition){
		int galleryPos = translateDbPosToGalleryPos(dbPosition);

		String set = "m";
		if(this.set == SyncManager.TRASH){
			set = "t";
		}

		filePropsCache.remove(dbPosition);
		picasso.invalidate("p" + set + dbPosition);
		notifyItemChanged(galleryPos);
	}

	// Create new views (invoked by the layout manager)
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		if(viewType == TYPE_ITEM) {
			// create a new view
			RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_gallery, parent, false);

			GalleryVH vh = new GalleryVH(v);

			RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(thumbSize, thumbSize);
			vh.image.setLayoutParams(params);

			return vh;
		}
		else if(viewType == TYPE_DATE){
			RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_gallery_date, parent, false);

			GalleryDate vh = new GalleryDate(v);

			return vh;
		}

		return null;
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {
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
				holder.layout.setElevation(0);
			} else {
				int size = Helpers.convertDpToPixels(context, 2);
				holder.image.setPadding(size, size, size, size);
				holder.checkbox.setVisibility(View.GONE);
				holder.layout.setElevation(3);
			}

			holder.image.setImageBitmap(null);
			holder.videoIcon.setVisibility(View.GONE);
			holder.videoDuration.setVisibility(View.GONE);
			holder.noCloudIcon.setVisibility(View.GONE);

			String set = "m";
			if(this.set == SyncManager.TRASH){
				set = "t";
			}
			else if(this.set == SyncManager.ALBUM){
				set = "a";
			}

			final RequestCreator req = picasso.load("p" + set + position);
			req.networkPolicy(NetworkPolicy.NO_CACHE);
			req.tag(holder);
			req.noFade();
			req.addProp("pos", String.valueOf(position));
			req.into(holder.image, new Callback() {
				@Override
				public void onSuccess(RequestHandler.Result result, Request request) {
					Integer pos = Integer.valueOf(request.getProp("pos"));
					GalleryVH holder = (GalleryVH) request.tag;

					if(holder == null){
						return;
					}

					FileProps props = filePropsCache.get(pos);
					if (props == null) {
						props = (FileProps) result.getProperty("fileProps");
						if (props != null) {
							filePropsCache.put(pos, props);
						}
						else{
							props = new FileProps();
						}
					}

					if (props.fileType == Crypto.FILE_TYPE_VIDEO) {
						holder.videoIcon.setVisibility(View.VISIBLE);
					} else {
						holder.videoIcon.setVisibility(View.GONE);
					}

					if (props.fileType == Crypto.FILE_TYPE_VIDEO && props.videoDuration >= 0) {
						holder.videoDuration.setText(Helpers.formatVideoDuration(props.videoDuration));
						holder.videoDuration.setVisibility(View.VISIBLE);
					} else {
						holder.videoDuration.setVisibility(View.GONE);
					}

					if (!props.isUploaded) {
						holder.noCloudIcon.setVisibility(View.VISIBLE);
					} else {
						holder.noCloudIcon.setVisibility(View.GONE);
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
		return db.getFileAtPosition(translatePos(position).dbPosition, albumId, DB_SORT);
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

	@Override
	public int getItemCount() {
		int totalItems = 0;
		for(DateGroup group : dates){
			totalItems += group.itemCount + 1;
		}

		return totalItems;
	}


	public static class FileProps{
		public int fileType = Crypto.FILE_TYPE_PHOTO;
		public int videoDuration = 0;
		public boolean isUploaded = true;
	}

}

