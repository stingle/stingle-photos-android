package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.AsyncTasks.ShowEncThumbInImageView;
import org.stingle.photos.R;
import org.stingle.photos.Sync.TransferProgressTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the live transfer queue inside the sync-status popup: one row per file
 * currently uploading or downloading (thumbnail + filename + per-file byte progress
 * + an up/down direction icon).
 */
public class SyncQueueAdapter extends RecyclerView.Adapter<SyncQueueAdapter.QueueVH> {

	private final Context context;
	private List<TransferProgressTracker.TransferEntry> items = new ArrayList<>();

	public SyncQueueAdapter(Context context){
		this.context = context;
	}

	public void setItems(List<TransferProgressTracker.TransferEntry> items){
		this.items = items;
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public QueueVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(context).inflate(R.layout.item_sync_queue, parent, false);
		return new QueueVH(view);
	}

	@Override
	public void onBindViewHolder(@NonNull QueueVH holder, int position) {
		TransferProgressTracker.TransferEntry entry = items.get(position);

		holder.filename.setText(entry.filename);
		holder.progress.setProgress(entry.percent);
		holder.direction.setImageResource(entry.direction == TransferProgressTracker.Direction.DOWNLOAD
				? R.drawable.ic_cloud_download
				: R.drawable.ic_cloud_upload);

		// Decrypting a thumbnail is expensive, so only (re)load when the row's file
		// actually changes — progress ticks rebind frequently for the same file.
		if (!entry.filename.equals(holder.thumb.getTag())) {
			holder.thumb.setTag(entry.filename);
			holder.thumb.setImageDrawable(null);
			// Download targets are remote files whose thumb isn't in the local thumbs dir —
			// fetch it via the remote/cache path (same as the gallery grid does for remote files).
			boolean isRemote = entry.direction == TransferProgressTracker.Direction.DOWNLOAD;
			(new ShowEncThumbInImageView(context, entry.filename, holder.thumb))
					.setHeaders(entry.headers)
					.setSet(entry.set)
					.setAlbumId(entry.albumId)
					.setIsRemote(isRemote)
					.setCircle(false)
					.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	static class QueueVH extends RecyclerView.ViewHolder {
		ImageView thumb;
		TextView filename;
		ProgressBar progress;
		ImageView direction;

		QueueVH(@NonNull View itemView) {
			super(itemView);
			thumb = itemView.findViewById(R.id.queue_thumb);
			filename = itemView.findViewById(R.id.queue_filename);
			progress = itemView.findViewById(R.id.queue_progress);
			direction = itemView.findViewById(R.id.queue_direction);
		}
	}
}
