package org.stingle.photos.Gallery.Gallery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.TransferProgressTracker;

/**
 * Drives the interactive cloud status icon in the gallery toolbar and the popup it
 * opens. The icon shows an animated spinner while sync/import/upload work is happening
 * and a cloud-with-checkmark when idle. Tapping it opens a popup (anchored to the icon)
 * listing the files currently uploading with per-file progress bars, or
 * "Backup and sync complete" when nothing is pending.
 *
 * Replaces the old scroll-anchored sync bar (SyncBarHandler).
 */
public class SyncStatusHandler {

	private final GalleryActivity activity;

	private View actionView;
	private ImageView icon;
	private ProgressBar spinner;
	private CircularProgressIndicator progress;

	private PopupWindow popupWindow;
	private View popupContent;
	private SyncQueueAdapter adapter;

	public SyncStatusHandler(GalleryActivity activity) {
		this.activity = activity;

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(activity);
		lbm.registerReceiver(syncStatusReceiver, new IntentFilter("SYNC_STATUS"));
	}

	private final BroadcastReceiver syncStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateIcon();
			refreshPopup();
		}
	};

	/** Wires the toolbar menu item's actionView. Called from onCreateOptionsMenu. */
	public void setActionView(View actionView) {
		this.actionView = actionView;
		if (actionView == null) {
			icon = null;
			spinner = null;
			progress = null;
			return;
		}
		icon = actionView.findViewById(R.id.sync_status_icon);
		spinner = actionView.findViewById(R.id.sync_status_spinner);
		progress = actionView.findViewById(R.id.sync_status_progress);
		actionView.setOnClickListener(v -> togglePopup());
		updateIcon();
	}

	public void updateIcon() {
		if (icon == null || spinner == null || progress == null) {
			return;
		}
		int status = StinglePhotosApplication.syncStatus;
		boolean syncBusy = status == SyncManager.STATUS_REFRESHING
				|| status == SyncManager.STATUS_IMPORTING
				|| status == SyncManager.STATUS_UPLOADING;

		TransferProgressTracker.Snapshot snap = TransferProgressTracker.getInstance().snapshot();
		// Downloads run independently of the sync engine, so also count when any transfer is live.
		boolean busy = syncBusy || !snap.active.isEmpty();
		int total = snap.uploadTotal + snap.downloadTotal;
		int completed = snap.uploadCompleted + snap.downloadCompleted;

		if (!busy) {
			// Idle / error: show a static cloud icon.
			icon.setVisibility(View.VISIBLE);
			spinner.setVisibility(View.GONE);
			progress.setVisibility(View.GONE);
			if (status == SyncManager.STATUS_DISABLED
					|| status == SyncManager.STATUS_NOT_WIFI
					|| status == SyncManager.STATUS_BATTERY_LOW
					|| status == SyncManager.STATUS_NO_SPACE_LEFT) {
				icon.setImageResource(R.drawable.ic_cloud_off);
			} else {
				icon.setImageResource(R.drawable.ic_cloud_done);
			}
			activity.updateQuotaInfo();
		} else if (total > 0) {
			// Transferring: determinate ring tracking file-count progress (not per-file bytes).
			icon.setVisibility(View.GONE);
			spinner.setVisibility(View.GONE);
			progress.setVisibility(View.VISIBLE);
			progress.setProgressCompat(completed * 100 / total, true);
		} else {
			// Refreshing / importing — no file count yet, show indeterminate spinner.
			icon.setVisibility(View.GONE);
			progress.setVisibility(View.GONE);
			spinner.setVisibility(View.VISIBLE);
		}
	}

	private void togglePopup() {
		if (popupWindow != null && popupWindow.isShowing()) {
			popupWindow.dismiss();
		} else {
			showPopup();
		}
	}

	private void showPopup() {
		if (actionView == null) {
			return;
		}

		popupContent = LayoutInflater.from(activity).inflate(R.layout.popup_sync_status, null);

		RecyclerView recycler = popupContent.findViewById(R.id.sync_queue_recycler);
		recycler.setLayoutManager(new LinearLayoutManager(activity));
		adapter = new SyncQueueAdapter(activity);
		recycler.setAdapter(adapter);

		popupWindow = new PopupWindow(popupContent,
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT,
				true);
		popupWindow.setAnimationStyle(R.style.SyncStatusPopupAnimation);
		popupWindow.setOutsideTouchable(true);
		popupWindow.setOnDismissListener(() -> {
			popupWindow = null;
			popupContent = null;
			adapter = null;
		});

		popupWindow.showAsDropDown(actionView);
		refreshPopup();
	}

	private void refreshPopup() {
		if (popupWindow == null || !popupWindow.isShowing() || popupContent == null) {
			return;
		}

		int status = StinglePhotosApplication.syncStatus;
		TransferProgressTracker.Snapshot snap = TransferProgressTracker.getInstance().snapshot();

		TextView header = popupContent.findViewById(R.id.sync_status_header);
		TextView message = popupContent.findViewById(R.id.sync_status_message);
		RecyclerView recycler = popupContent.findViewById(R.id.sync_queue_recycler);

		if (!snap.active.isEmpty()) {
			header.setVisibility(View.VISIBLE);
			header.setText(buildHeader(snap));
			message.setVisibility(View.GONE);
			recycler.setVisibility(View.VISIBLE);
			if (adapter != null) {
				adapter.setItems(snap.active);
			}
		} else {
			header.setVisibility(View.GONE);
			recycler.setVisibility(View.GONE);
			message.setVisibility(View.VISIBLE);
			message.setText(messageForStatus(status, snap));
		}
	}

	private String buildHeader(TransferProgressTracker.Snapshot snap) {
		StringBuilder sb = new StringBuilder();
		if (snap.hasActiveUploads()) {
			sb.append(activity.getString(R.string.uploading_file,
					String.valueOf(snap.uploadCompleted), String.valueOf(snap.uploadTotal)));
		}
		if (snap.hasActiveDownloads()) {
			if (sb.length() > 0) {
				sb.append("\n");
			}
			sb.append(activity.getString(R.string.downloading_file,
					String.valueOf(snap.downloadCompleted), String.valueOf(snap.downloadTotal)));
		}
		return sb.toString();
	}

	private String messageForStatus(int status, TransferProgressTracker.Snapshot snap) {
		switch (status) {
			case SyncManager.STATUS_REFRESHING:
				return activity.getString(R.string.refreshing);
			case SyncManager.STATUS_IMPORTING:
				return activity.getString(R.string.importing_files);
			case SyncManager.STATUS_UPLOADING:
				return activity.getString(R.string.uploading_file,
						String.valueOf(snap.uploadCompleted), String.valueOf(snap.uploadTotal));
			case SyncManager.STATUS_NO_SPACE_LEFT:
				return activity.getString(R.string.no_space_left);
			case SyncManager.STATUS_DISABLED:
				return activity.getString(R.string.sync_disabled);
			case SyncManager.STATUS_NOT_WIFI:
				return activity.getString(R.string.sync_not_on_wifi);
			case SyncManager.STATUS_BATTERY_LOW:
				return activity.getString(R.string.sync_battery_low);
			case SyncManager.STATUS_IDLE:
			default:
				return activity.getString(R.string.backup_complete);
		}
	}

	public void destroy() {
		if (popupWindow != null && popupWindow.isShowing()) {
			popupWindow.dismiss();
		}
		LocalBroadcastManager.getInstance(activity).unregisterReceiver(syncStatusReceiver);
	}
}
