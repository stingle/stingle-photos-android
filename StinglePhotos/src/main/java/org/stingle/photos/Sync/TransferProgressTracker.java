package org.stingle.photos.Sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe, process-wide source of truth for live file transfers — both uploads
 * (see {@link org.stingle.photos.Sync.SyncSteps.UploadToCloud}) and downloads
 * (see {@link org.stingle.photos.AsyncTasks.Gallery.DownloadAsyncTask}).
 *
 * Holds one {@link TransferEntry} per in-flight file (with its byte-progress percent)
 * plus overall completed/total counts per direction. The UI re-reads {@link #snapshot()}
 * whenever a SYNC_STATUS broadcast pings it.
 */
public class TransferProgressTracker {

	private static final TransferProgressTracker instance = new TransferProgressTracker();

	public static TransferProgressTracker getInstance() {
		return instance;
	}

	private TransferProgressTracker() {}

	public enum Direction { UPLOAD, DOWNLOAD }

	public static class TransferEntry {
		public final String filename;
		public final String headers;
		public final int set;
		public final String albumId;
		public final Direction direction;
		public volatile int percent;

		public TransferEntry(String filename, String headers, int set, String albumId, Direction direction) {
			this.filename = filename;
			this.headers = headers;
			this.set = set;
			this.albumId = albumId;
			this.direction = direction;
			this.percent = 0;
		}
	}

	public static class Snapshot {
		public final List<TransferEntry> active;
		public final int uploadCompleted;
		public final int uploadTotal;
		public final int downloadCompleted;
		public final int downloadTotal;

		public Snapshot(List<TransferEntry> active, int uploadCompleted, int uploadTotal, int downloadCompleted, int downloadTotal) {
			this.active = active;
			this.uploadCompleted = uploadCompleted;
			this.uploadTotal = uploadTotal;
			this.downloadCompleted = downloadCompleted;
			this.downloadTotal = downloadTotal;
		}

		public boolean hasActiveUploads() {
			for (TransferEntry e : active) {
				if (e.direction == Direction.UPLOAD) { return true; }
			}
			return false;
		}

		public boolean hasActiveDownloads() {
			for (TransferEntry e : active) {
				if (e.direction == Direction.DOWNLOAD) { return true; }
			}
			return false;
		}
	}

	// Keyed by filename within each direction — each file is owned by a single worker.
	private final Map<String, TransferEntry> uploads = new ConcurrentHashMap<>();
	private final Map<String, TransferEntry> downloads = new ConcurrentHashMap<>();
	private final AtomicInteger uploadCompleted = new AtomicInteger(0);
	private final AtomicInteger uploadTotal = new AtomicInteger(0);
	private final AtomicInteger downloadCompleted = new AtomicInteger(0);
	private final AtomicInteger downloadTotal = new AtomicInteger(0);

	// --- Uploads ---

	public void resetUploads(int total) {
		uploads.clear();
		uploadCompleted.set(0);
		uploadTotal.set(total);
	}

	public void startUpload(String filename, String headers, int set, String albumId) {
		uploads.put(filename, new TransferEntry(filename, headers, set, albumId, Direction.UPLOAD));
	}

	public void updateUploadPercent(String filename, int percent) {
		TransferEntry entry = uploads.get(filename);
		if (entry != null) { entry.percent = percent; }
	}

	public void finishUpload(String filename) {
		if (uploads.remove(filename) != null) { uploadCompleted.incrementAndGet(); }
	}

	public void clearUploads() {
		uploads.clear();
		uploadCompleted.set(0);
		uploadTotal.set(0);
	}

	// --- Downloads ---

	public void resetDownloads(int total) {
		downloads.clear();
		downloadCompleted.set(0);
		downloadTotal.set(total);
	}

	public void startDownload(String filename, String headers, int set, String albumId) {
		downloads.put(filename, new TransferEntry(filename, headers, set, albumId, Direction.DOWNLOAD));
	}

	public void updateDownloadPercent(String filename, int percent) {
		TransferEntry entry = downloads.get(filename);
		if (entry != null) { entry.percent = percent; }
	}

	public void finishDownload(String filename) {
		if (downloads.remove(filename) != null) { downloadCompleted.incrementAndGet(); }
	}

	public void clearDownloads() {
		downloads.clear();
		downloadCompleted.set(0);
		downloadTotal.set(0);
	}

	// --- Read ---

	public Snapshot snapshot() {
		List<TransferEntry> active = new ArrayList<>(uploads.values());
		active.addAll(downloads.values());
		return new Snapshot(active,
				uploadCompleted.get(), uploadTotal.get(),
				downloadCompleted.get(), downloadTotal.get());
	}
}
