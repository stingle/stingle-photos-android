package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.database.Cursor;
import android.text.format.DateFormat;
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
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AutoCloseableCursor;
import org.stingle.photos.Db.Query.FilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Gallery.Helpers.AutoFitGridLayoutManager;
import org.stingle.photos.Gallery.Helpers.IDragSelectAdapter;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GalleryAdapterPisasso extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements IDragSelectAdapter {
	private final Context context;
	private FilesDb db;
	private final Listener callback;
	private final ArrayList<Integer> selectedIndices = new ArrayList<>();
	private final int thumbSize;
	private boolean isSelectModeActive = false;
	private final AutoFitGridLayoutManager lm;
	private final Picasso picasso;
	private final LruCache<Integer, FileProps> filePropsCache = new LruCache<>(512);
	private final HashMap<Integer, String> datePositions = new HashMap<>();
	private final ArrayList<Integer> datePositionsPlain = new ArrayList<>();
	int totalItemCount = 0;
	private final int set;
	private final String albumId;
	private final int DB_SORT = StingleDb.SORT_DESC;

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
				//DB_SORT = StingleDb.SORT_ASC;
				this.db = new AlbumFilesDb(context);
				break;
		}
		this.thumbSize = Helpers.getScreenWidthByColumns(context);
		this.lm = lm;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new StinglePicassoLoader(context, db, thumbSize, albumId)).build();

		getAvailableDates();
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
	public class GalleryDate extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		public RelativeLayout layout;
		public TextView text;
		public CheckBox checkbox;

		public GalleryDate(RelativeLayout v) {
			super(v);
			layout = v;
			text = v.findViewById(R.id.text);
			text.setOnLongClickListener(this);
			checkbox = v.findViewById(R.id.checkBox);
			checkbox.setOnClickListener(this);
		}

		@Override
		public void onClick(View v) {
			int index = getAdapterPosition();
			if(isSelectModeActive) {
				setSelectionInDate(index, !isAllItemsSelectedInDate(index));
			}
		}

		@Override
		public boolean onLongClick(View v) {
			int index = getAdapterPosition();
			if(!isSelectModeActive) {
				setSelectionModeActive(true);
				if(callback != null){
					callback.onLongClick(index);
				}
				setSelectionInDate(index, true);
			}
			else{
				setSelectionInDate(index, !isAllItemsSelectedInDate(index));
			}
			return true;
		}
	}

	public interface Listener {
		void onClick(int index);
		void onLongClick(int index);
		void onSelectionChanged(int count);
	}

	protected boolean isAllItemsSelectedInDate(int dateIndex){
		boolean isAllSelected = true;
		int endIndex = getEndIndexOfDate(dateIndex);
		for(int i=dateIndex+1;i<endIndex; i++){
			if (!selectedIndices.contains(i)) {
				isAllSelected = false;
				break;
			}
		}

		return isAllSelected;
	}

	protected void setSelectionInDate(int dateIndex, boolean switchTo){
		int endIndex = getEndIndexOfDate(dateIndex);

		for(int i=dateIndex+1;i<endIndex; i++){
			setSelected(i, switchTo);
		}
	}

	protected int getEndIndexOfDate(int dateIndex){
		int itemNum = datePositionsPlain.indexOf(dateIndex);
		if(datePositionsPlain.size()-1 > itemNum){
			return datePositionsPlain.get(itemNum+1);
		}
		else{
			return getItemCount();
		}
	}

	protected static class PosTranslate{
		public int type;
		public int dbPosition;
		public String date = null;
	}

	protected void getAvailableDates(){
		datePositions.clear();
		datePositionsPlain.clear();
		totalItemCount = 0;
		try(AutoCloseableCursor autoCloseableCursor = db.getAvailableDates(albumId, DB_SORT)) {
			Cursor result = autoCloseableCursor.getCursor();
			while (result.moveToNext()) {
				datePositions.put(totalItemCount, convertDate(result.getString(0)));
				datePositionsPlain.add(totalItemCount);
				totalItemCount += result.getInt(1) + 1;
			}
		}
	}

	private final SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
	private final SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM d, y", Locale.getDefault());

	private synchronized String convertDate(String dateString){
		try {
			Date date = inputFormat.parse(dateString);
			if(date != null) {
				return outputFormat.format(date);
			}
		}
		catch (ParseException ignored) {}

		return dateString;
	}

	public static String getDbDate(long time) {
		Calendar cal = Calendar.getInstance(Locale.ENGLISH);
		cal.setTimeInMillis(time);
		return DateFormat.format("yyyy-MM-dd", cal).toString();
	}

	private boolean isPositionIsDate(int position){
		return datePositions.containsKey(position);
	}

	private int getItemDatePosition(int index){
		return (-Collections.binarySearch(datePositionsPlain, index) - 2);
	}

	public String getDateForPosition(int position) {
		String date = null;
		for (int i = 0; i < datePositionsPlain.size(); i++) {
			if (position >= datePositionsPlain.get(i)) {
				date = datePositions.get(datePositionsPlain.get(i));
			} else {
				break;
			}
		}
		return date;
	}


	private PosTranslate translatePos(int pos){
		if(datePositions.containsKey(pos)){
			PosTranslate posObj = new PosTranslate();
			posObj.type = TYPE_DATE;
			posObj.date = datePositions.get(pos);

			return posObj;
		}

		int leftIndex = (-Collections.binarySearch(datePositionsPlain, pos) - 2);

		PosTranslate posObj = new PosTranslate();
		posObj.type = TYPE_ITEM;
		posObj.dbPosition = pos - (leftIndex + 1);

		return posObj;
	}

	private int translateDbPosToGalleryPos(int pos){
		for(int i=0;i<datePositionsPlain.size();i++){
			int datePos = datePositionsPlain.get(i);
			if(datePos <= pos){
				pos++;
			}
			else{
				break;
			}
		}
		return pos;
	}

	public int getPositionFromDate(Long date){
		return getPositionFromDate(convertDate(getDbDate(date)));
	}
	public int getPositionFromDate(String date){
		Integer result = Helpers.getKeyByValue(datePositions, date);
		if(result != null){
			return result;
		}
		return 0;
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
	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		if(viewType == TYPE_ITEM) {
			// create a new view
			RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_gallery, parent, false);

			GalleryVH vh = new GalleryVH(v);

			RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(thumbSize, thumbSize);
			vh.image.setLayoutParams(params);

			return vh;
		}
		else {
			RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_gallery_date, parent, false);

			return new GalleryDate(v);
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderObj, final int rawPosition) {
		PosTranslate transPos = translatePos(rawPosition);

		if(holderObj instanceof GalleryVH) {
			final int position = transPos.dbPosition;
			GalleryVH holder = (GalleryVH)holderObj;
			if (isSelectModeActive) {
				int size = Helpers.convertDpToPixels(context, 20);
				holder.image.setPadding(size, size, size, size);
				holder.checkbox.setVisibility(View.VISIBLE);

				holder.checkbox.setChecked(selectedIndices.contains(rawPosition));
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
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)holder.text.getLayoutParams();

			if (isSelectModeActive) {
				holder.checkbox.setVisibility(View.VISIBLE);

				holder.checkbox.setChecked(isAllItemsSelectedInDate(rawPosition));
				holder.layout.setElevation(0);

				params.setMarginStart(Helpers.convertDpToPixels(context, 25));

			} else {
				holder.checkbox.setVisibility(View.GONE);
				params.setMarginStart(Helpers.convertDpToPixels(context, 10));
			}
			holder.text.setLayoutParams(params);
		}
	}



	@Override
	public int getItemViewType(int position) {
		return (isPositionIsDate(position) ? TYPE_DATE : TYPE_ITEM);
	}

	public StingleDbFile getStingleFileAtPosition(int position){
		if(db == null){
			return null;
		}
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
		int pos = getItemDatePosition(index);
		if (pos >= 0 && pos < datePositionsPlain.size()) {
			notifyItemChanged(datePositionsPlain.get(pos));
		}
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
		notifyItemChanged(datePositionsPlain.get(getItemDatePosition(index)));
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
		return totalItemCount;
	}


	public static class FileProps{
		public int fileType = Crypto.FILE_TYPE_PHOTO;
		public int videoDuration = 0;
		public boolean isUploaded = true;
	}

}

