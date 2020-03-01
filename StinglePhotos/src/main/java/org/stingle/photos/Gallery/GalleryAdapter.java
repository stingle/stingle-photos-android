package org.stingle.photos.Gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.Auth.KeyManagement;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.FilesTrashDb;
import org.stingle.photos.Db.StingleDbContract;
import org.stingle.photos.Db.StingleDbFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.Util.MemoryCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.GalleryVH> implements IDragSelectAdapter {
	private Context context;
	private FilesTrashDb db;
	private final MemoryCache memCache = StinglePhotosApplication.getCache();
	private final HashMap<Integer, GetDecryptedBitmap> tasksQueue = new HashMap<Integer, GetDecryptedBitmap>();
	private final Listener callback;
	private final ArrayList<Integer> selectedIndices = new ArrayList<Integer>();
	private int thumbSize;
	private boolean isSelectModeActive = false;
	private AutoFitGridLayoutManager lm;

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
	public GalleryAdapter(Context context, Listener callback, AutoFitGridLayoutManager lm) {
		this.context = context;
		this.callback = callback;
		this.db = new FilesTrashDb(context, StingleDbContract.Files.TABLE_NAME_FILES);
		this.thumbSize = Helpers.getThumbSize(context);
		this.lm = lm;


	}

	// Create new views (invoked by the layout manager)
	@Override
	public GalleryAdapter.GalleryVH onCreateViewHolder(ViewGroup parent, int viewType) {
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
	public void onBindViewHolder(GalleryVH holder, int position) {
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

		/*GalleryDecItem item = (GalleryDecItem) memCache.get(String.valueOf(position));
		if(item != null){
			Log.e("cached", String.valueOf(position));
			holder.image.setImageBitmap(item.bitmap);
			if(item.fileType == Crypto.FILE_TYPE_VIDEO){
				holder.videoIcon.setVisibility(View.VISIBLE);
			}
			else{
				holder.videoIcon.setVisibility(View.GONE);
			}
		}
		else{*/
			if(holder.currentPos != -1 && holder.currentPos != position && tasksQueue.containsKey(holder.currentPos)){
				tasksQueue.get(holder.currentPos).cancel(true);
				tasksQueue.remove(holder.currentPos);
				Log.e("interrupt", String.valueOf(holder.currentPos) + " - " + String.valueOf(position));
			}
			holder.currentPos = position;
			if (!tasksQueue.containsKey(position)) {
				getItemAtPos(position, holder);
			}
			else {
				Log.e("alreadyinqueue", String.valueOf(position));
			}
		//}
	}

	private void getItemAtPos(int position, GalleryVH holder){
		GetDecryptedBitmap decTask = new GetDecryptedBitmap(context, db, position, holder);
		decTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		tasksQueue.put(position, decTask);
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

	public class GetDecryptedBitmap extends AsyncTask<Void, Void, GalleryDecItem> {

		protected Context context;
		protected FilesTrashDb db;
		protected int position;
		protected GalleryVH holder;

		public GetDecryptedBitmap(Context context, FilesTrashDb db, int position, GalleryVH holder){
			this.context = context;
			this.db = db;
			this.position = position;
			this.holder = holder;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			//image.setImageResource(R.drawable.file);
		}

		@Override
		protected GalleryDecItem doInBackground(Void... params) {
			Log.d("start", String.valueOf(position));
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				//Log.e("interrupted", String.valueOf(position));
			}
			StingleDbFile file = db.getFileAtPosition(position);
			if(file.isLocal) {
				try {
					File fileToDec = new File(FileManager.getThumbsDir(context) +"/"+ file.filename);
					FileInputStream input = new FileInputStream(fileToDec);
					byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(input);

					if (decryptedData != null) {
						GalleryDecItem item = new GalleryDecItem();
						Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);

						FileInputStream streamForHeader = new FileInputStream(fileToDec);
						Crypto.Header header = StinglePhotosApplication.getCrypto().getFileHeader(streamForHeader);
						streamForHeader.close();

						item.bitmap = bitmap;
						item.fileType = header.fileType;

						memCache.put(String.valueOf(position), item);
						return item;
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (CryptoException e) {
					e.printStackTrace();
				}

			}
			else if(!file.isLocal && file.isRemote){

				HashMap<String, String> postParams = new HashMap<String, String>();

				postParams.put("token", KeyManagement.getApiToken(context));
				postParams.put("file", file.filename);
				postParams.put("thumb", "1");

				try {
					byte[] encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);

					if(encFile == null || encFile.length == 0){
						return null;
					}

					//Log.d("encFile", new String(encFile, "UTF-8"));

					/*FileOutputStream tmp = new FileOutputStream(Helpers.getHomeDir(activity) + "/qaq.sp");
					tmp.write(encFile);
					tmp.close();*/

					byte[] decryptedData = StinglePhotosApplication.getCrypto().decryptFile(encFile);

					if (decryptedData != null) {
						GalleryDecItem item = new GalleryDecItem();

						Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);

						Crypto.Header header = StinglePhotosApplication.getCrypto().getFileHeader(encFile);

						item.bitmap = bitmap;
						item.fileType = header.fileType;

						memCache.put(String.valueOf(position), item);
						return item;
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (CryptoException e) {
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				} catch (KeyManagementException e) {
					e.printStackTrace();
				}
			}

			return null;
		}

		@Override
		protected void onPostExecute(GalleryDecItem item) {
			super.onPostExecute(item);
			Log.d("finish", String.valueOf(position));
			if(holder.currentPos != position) {
				tasksQueue.remove(position);
				memCache.remove(String.valueOf(position));
				Log.e("tagnotmatch", String.valueOf(holder.currentPos) + " - " + String.valueOf(position));
				//getItemAtPos(holder.currentPos, holder);
				return;
			}

			if(item == null){
				item = new GalleryDecItem();
			}

			if(item.fileType == Crypto.FILE_TYPE_VIDEO){
				holder.videoIcon.setVisibility(View.VISIBLE);
			}
			else{
				holder.videoIcon.setVisibility(View.GONE);
			}

			if(item.bitmap == null) {
				item.bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.file);
				memCache.put(String.valueOf(position), item);
			}

			holder.image.setImageBitmap(item.bitmap);

			tasksQueue.remove(position);
			//adapter.notifyItemChanged(position);
		}
	}
}

