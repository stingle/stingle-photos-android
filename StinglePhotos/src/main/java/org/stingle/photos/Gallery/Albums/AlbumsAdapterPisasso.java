package org.stingle.photos.Gallery.Albums;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestCreator;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.ContactsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;

import java.io.IOException;

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
	private LruCache<Integer, StingleDbAlbum> albumsCache = new LruCache<Integer, StingleDbAlbum>(512);
	private LruCache<Integer, Crypto.AlbumData> albumsDataCache = new LruCache<Integer, Crypto.AlbumData>(512);

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_ADD = 1;
	public static final int TYPE_GALLERY = 2;

	public static int MEMBERS_IN_SUBTITLE = 3;

	public static final int LAYOUT_GRID = 0;
	public static final int LAYOUT_LIST = 1;
	private int layoutStyle = LAYOUT_GRID;
	private int otherItemsCount = 0;

	private Integer textSize;
	private Integer subTextSize;

	public AlbumsAdapterPisasso(Context context, RecyclerView.LayoutManager lm, Integer view, boolean showAddOption, boolean showGalleryOption) {
		this.context = context;
		this.db = new AlbumsDb(context);
		this.filesDb = new AlbumFilesDb(context);
		this.thumbSize = Helpers.getThumbSize(context,2);
		this.lm = lm;
		this.view = view;
		this.showGalleryOption = showGalleryOption;
		this.showAddOption = showAddOption;

		this.picasso = new Picasso.Builder(context).addRequestHandler(new AlbumsPicassoLoader(context, db, filesDb, view, thumbSize, albumsCache, albumsDataCache)).build();

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
		albumsCache.evictAll();
		albumsDataCache.evictAll();
		picasso.evictAll();
		notifyDataSetChanged();
	}

	public void setLayoutStyle(int layoutStyle){
		this.layoutStyle = layoutStyle;
	}

	public void setTextSize(Integer textSize) {
		this.textSize = textSize;
	}

	public void setSubTextSize(Integer subTextSize) {
		this.subTextSize = subTextSize;
	}


	public class AlbumVH extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
		public View layout;
		public ImageView image;
		public ImageView rightIcon;
		public TextView albumName;
		public TextView albumSubtitle;
		public int currentPos = -1;

		public AlbumVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			albumName = v.findViewById(R.id.albumName);
			albumSubtitle = v.findViewById(R.id.albumSubtitle);
			rightIcon = v.findViewById(R.id.rightIcon);
			image.setOnClickListener(this);
			albumName.setOnClickListener(this);
			albumSubtitle.setOnClickListener(this);
			if(rightIcon != null) {
				rightIcon.setOnClickListener(this);
			}
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
		public View layout;
		public ImageView image;
		public TextView albumName;

		public AlbumAddVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			albumName = v.findViewById(R.id.albumName);
			image.setOnClickListener(this);
			albumName.setOnClickListener(this);
			image.setElevation(0);
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
		public View layout;
		public ImageView image;
		public TextView albumName;

		public AlbumAddGalleryVH(View v) {
			super(v);
			layout = v;
			image = v.findViewById(R.id.thumbImage);
			albumName = v.findViewById(R.id.albumName);
			image.setOnClickListener(this);
			albumName.setOnClickListener(this);
			image.setElevation(0);
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

		albumsCache.remove(dbPosition);
		albumsDataCache.remove(dbPosition);
		picasso.invalidate("a" + String.valueOf(dbPosition));
		notifyItemChanged(galleryPos);
	}

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

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {

		final int dbPos = translateGalleryPosToDbPos(rawPosition);

		if(holderObj instanceof AlbumVH) {

			AlbumVH holder = (AlbumVH)holderObj;
			int size = Helpers.convertDpToPixels(context, 2);
			holder.image.setPadding(size, size, size, size);

			holder.image.setImageBitmap(null);
			holder.albumName.setText("");

			if(textSize != null){
				holder.albumName.setTextSize(textSize);
			}
			if(subTextSize != null){
				holder.albumSubtitle.setTextSize(subTextSize);
			}

			try{
				StingleDbAlbum album;
				album = albumsCache.get(dbPos);
				if(album == null) {
					album = db.getAlbumAtPosition(dbPos, StingleDb.SORT_DESC, AlbumsFragment.getAlbumIsHiddenByView(view), AlbumsFragment.getAlbumIsSharedByView(view));
					albumsCache.put(dbPos, album);
				}
				Crypto.AlbumData albumData;
				albumData = albumsDataCache.get(dbPos);
				if(albumData == null) {
					albumData = StinglePhotosApplication.getCrypto().parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);
					albumsDataCache.put(dbPos, albumData);
				}

				if(album != null && albumData != null) {
					holder.albumName.setText(albumData.metadata.name);

					if(view == AlbumsFragment.VIEW_SHARES){
						holder.albumSubtitle.setText(getMembersString(album));
						if(album.isOwner){
							holder.rightIcon.setImageDrawable(context.getDrawable(R.drawable.ic_sent));
						}
						else{
							holder.rightIcon.setImageDrawable(context.getDrawable(R.drawable.ic_received));
						}
						holder.rightIcon.setVisibility(View.VISIBLE);
					}
					else if(view == AlbumsFragment.VIEW_ALL && album.isShared && album.isHidden){
						holder.albumSubtitle.setText(getMembersString(album));
					}
					else{
						holder.albumSubtitle.setText(context.getString(R.string.album_items_count, filesDb.getTotalFilesCount(album.albumId)));
					}

				}

			} catch (IOException | CryptoException e) {
				e.printStackTrace();
			}

			final RequestCreator req = picasso.load("a" + dbPos);
			req.networkPolicy(NetworkPolicy.NO_CACHE);
			req.noFade();
			req.addProp("pos", String.valueOf(dbPos));
			req.into(holder.image);
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
			if(textSize != null){
				holder.albumName.setTextSize(textSize);
			}
		}
		else if(holderObj instanceof AlbumAddGalleryVH) {
			AlbumAddGalleryVH holder = (AlbumAddGalleryVH)holderObj;
			holder.albumName.setText(context.getString(R.string.main_gallery));
			holder.image.setImageDrawable(context.getDrawable(R.drawable.ic_image_red));
			holder.image.setImageTintMode(PorterDuff.Mode.SRC_ATOP);
			ColorStateList csl = AppCompatResources.getColorStateList(context, R.color.primaryColor);
			holder.image.setImageTintList(csl);

			if(textSize != null){
				holder.albumName.setTextSize(textSize);
			}
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

	@Override
	public int getItemCount() {
		return (int)db.getTotalAlbumsCount(AlbumsFragment.getAlbumIsHiddenByView(view), AlbumsFragment.getAlbumIsSharedByView(view)) + otherItemsCount;
	}


	private String getMembersString(StingleDbAlbum album){
		ContactsDb contactsDb = new ContactsDb(context);
		String myUserId = Helpers.getPreference(context, StinglePhotosApplication.USER_ID, "");
		StringBuilder membersStr = new StringBuilder();
		int count = 0;
		boolean addOthers = false;
		for(String userId : album.members){
			if(userId.equals(myUserId)){
				continue;
			}
			if(count >= MEMBERS_IN_SUBTITLE){
				addOthers = true;
				break;
			}
			StingleContact contact = contactsDb.getContactByUserId(Long.parseLong(userId));
			if(contact != null){
				if(membersStr.length() > 0){
					membersStr.append(", ");
				}
				membersStr.append(contact.email);
			}
			count++;
		}

		if(addOthers){
			int othersCount = album.members.size() - count - 1;
			if(othersCount > 0){
				membersStr.append(" " + context.getString(R.string.count_others, othersCount));
			}
		}

		return membersStr.toString();
	}

}

