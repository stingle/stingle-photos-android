package org.stingle.photos.Gallery.Gallery;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.stingle.photos.AsyncTasks.ShowEncThumbInImageView;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncService;
import org.stingle.photos.Util.Helpers;

public class SyncBarHandler {

	private GalleryActivity activity;

	protected ViewGroup syncBar;
	protected ProgressBar syncProgress;
	protected ProgressBar refreshCProgress;
	protected ImageView syncPhoto;
	protected TextView syncText;
	private ImageView backupCompleteIcon;

	public SyncBarHandler(GalleryActivity activity) {
		this.activity = activity;

		syncBar = activity.findViewById(R.id.syncBar);
		syncProgress = activity.findViewById(R.id.syncProgress);
		refreshCProgress = activity.findViewById(R.id.refreshCProgress);
		syncPhoto = activity.findViewById(R.id.syncPhoto);
		syncText = activity.findViewById(R.id.syncText);
		backupCompleteIcon = activity.findViewById(R.id.backupComplete);
	}

	public void handleMessage(Message msg) {
		if (msg.what == SyncService.MSG_RESP_SYNC_STATUS) {
			Bundle bundle = msg.getData();
			int syncStatus = bundle.getInt("syncStatus");
			int totalItemsNumber = bundle.getInt("totalItemsNumber");
			int uploadedFilesCount = bundle.getInt("uploadedFilesCount");
			String currentFile = bundle.getString("currentFile");

			if (syncStatus == SyncService.STATUS_UPLOADING) {
				syncProgress.setMax(totalItemsNumber);
				syncProgress.setProgress(uploadedFilesCount);
				syncText.setText(activity.getString(R.string.uploading_file, String.valueOf(uploadedFilesCount), String.valueOf(totalItemsNumber)));
				(new ShowEncThumbInImageView(activity, currentFile, syncPhoto)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				setSyncStatus(SyncService.STATUS_UPLOADING);
			} else if (syncStatus == SyncService.STATUS_REFRESHING) {
				setSyncStatus(SyncService.STATUS_REFRESHING);
			} else if (syncStatus == SyncService.STATUS_IDLE) {
				setSyncStatus(SyncService.STATUS_IDLE);
			}

		} else if (msg.what == SyncService.MSG_SYNC_CURRENT_FILE) {
			Bundle bundle = msg.getData();
			String currentFile = bundle.getString("currentFile");
			(new ShowEncThumbInImageView(activity, currentFile, syncPhoto)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

			setSyncStatus(SyncService.STATUS_UPLOADING);
		} else if (msg.what == SyncService.MSG_SYNC_UPLOAD_PROGRESS) {
			Bundle bundle = msg.getData();
			int totalItemsNumber = bundle.getInt("totalItemsNumber");
			int uploadedFilesCount = bundle.getInt("uploadedFilesCount");

			syncProgress.setMax(totalItemsNumber);
			syncProgress.setProgress(uploadedFilesCount);
			syncText.setText(activity.getString(R.string.uploading_file, String.valueOf(uploadedFilesCount), String.valueOf(totalItemsNumber)));

			setSyncStatus(SyncService.STATUS_UPLOADING);
		} else if (msg.what == SyncService.MSG_SYNC_STATUS_CHANGE) {
			Bundle bundle = msg.getData();
			int newStatus = bundle.getInt("newStatus");
			setSyncStatus(newStatus);
		} else if (msg.what == SyncService.MSG_REFRESH_GALLERY) {
			activity.updateGalleryFragmentData();
		} else if (msg.what == SyncService.MSG_REFRESH_GALLERY_ITEM) {
			Bundle bundle = msg.getData();
			int position = bundle.getInt("position");

			activity.updateGalleryFragmentItem(position);
		}
	}

	private void setSyncStatus(int syncStatus) {
		if (syncStatus == SyncService.STATUS_UPLOADING) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.VISIBLE);
			syncProgress.setVisibility(View.VISIBLE);
			backupCompleteIcon.setVisibility(View.GONE);
		} else if (syncStatus == SyncService.STATUS_REFRESHING) {
			refreshCProgress.setVisibility(View.VISIBLE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(activity.getString(R.string.refreshing));
			backupCompleteIcon.setVisibility(View.GONE);
		} else if (syncStatus == SyncService.STATUS_NO_SPACE_LEFT) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(activity.getString(R.string.no_space_left));
			backupCompleteIcon.setVisibility(View.GONE);
			activity.updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_DISABLED) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(activity.getString(R.string.sync_disabled));
			backupCompleteIcon.setVisibility(View.GONE);
			activity.updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_NOT_WIFI) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(activity.getString(R.string.sync_not_on_wifi));
			backupCompleteIcon.setVisibility(View.GONE);
			activity.updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_BATTERY_LOW) {
			refreshCProgress.setVisibility(View.GONE);
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			syncText.setText(activity.getString(R.string.sync_battery_low));
			backupCompleteIcon.setVisibility(View.GONE);
			activity.updateQuotaInfo();
		} else if (syncStatus == SyncService.STATUS_IDLE) {
			syncText.setText(activity.getString(R.string.backup_complete));
			syncPhoto.setVisibility(View.GONE);
			syncProgress.setVisibility(View.INVISIBLE);
			refreshCProgress.setVisibility(View.GONE);
			backupCompleteIcon.setVisibility(View.VISIBLE);
			activity.updateQuotaInfo();
		}
	}

	public void hideSyncBar(){
		syncBar.setVisibility(View.GONE);
	}
	public void showSyncBar(){
		syncBar.setVisibility(View.VISIBLE);
	}
	public void showSyncBarAnimated(){
		syncBar.animate().translationY(0).setInterpolator(new DecelerateInterpolator(4));
	}
	public void hideSyncBarAnimated(){
		syncBar.animate().translationY(-syncBar.getHeight() - Helpers.convertDpToPixels(activity, 20)).setInterpolator(new AccelerateInterpolator(4));
	}
}
