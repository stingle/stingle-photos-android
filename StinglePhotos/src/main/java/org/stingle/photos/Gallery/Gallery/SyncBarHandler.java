package org.stingle.photos.Gallery.Gallery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.AsyncTasks.ShowEncThumbInImageView;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

public class SyncBarHandler {

	private GalleryActivity activity;

	private ViewGroup syncBar;
	private ProgressBar syncProgress;
	private ProgressBar refreshCProgress;
	private ImageView syncPhoto;
	private TextView syncText;
	private ImageView backupCompleteIcon;

	public SyncBarHandler(GalleryActivity activity) {
		this.activity = activity;

		syncBar = activity.findViewById(R.id.syncBar);
		syncProgress = activity.findViewById(R.id.syncProgress);
		refreshCProgress = activity.findViewById(R.id.refreshCProgress);
		syncPhoto = activity.findViewById(R.id.syncPhoto);
		syncText = activity.findViewById(R.id.syncText);
		backupCompleteIcon = activity.findViewById(R.id.backupComplete);

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(activity);
		lbm.registerReceiver(syncStatusReceiver, new IntentFilter("SYNC_STATUS"));
	}

	private BroadcastReceiver syncStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateSyncBar();
		}
	};

	public void updateSyncBar(){
		switch (StinglePhotosApplication.syncStatus){
			case SyncManager.STATUS_IDLE:
				syncText.setText(activity.getString(R.string.backup_complete));
				syncPhoto.setVisibility(View.GONE);
				syncPhoto.setImageDrawable(null);
				syncProgress.setVisibility(View.INVISIBLE);
				refreshCProgress.setVisibility(View.GONE);
				backupCompleteIcon.setVisibility(View.VISIBLE);
				activity.updateQuotaInfo();
				break;
			case SyncManager.STATUS_REFRESHING:
				refreshCProgress.setVisibility(View.VISIBLE);
				syncPhoto.setVisibility(View.GONE);
				syncPhoto.setImageDrawable(null);
				syncProgress.setVisibility(View.INVISIBLE);
				syncText.setText(activity.getString(R.string.refreshing));
				backupCompleteIcon.setVisibility(View.GONE);
				break;
			case SyncManager.STATUS_IMPORTING:
				refreshCProgress.setVisibility(View.VISIBLE);
				syncPhoto.setVisibility(View.GONE);
				syncPhoto.setImageDrawable(null);
				syncProgress.setVisibility(View.INVISIBLE);
				syncText.setText(activity.getString(R.string.importing_files));
				backupCompleteIcon.setVisibility(View.GONE);

				if(StinglePhotosApplication.syncStatusParams != null) {
					Bundle params = StinglePhotosApplication.syncStatusParams;
					syncProgress.setMax(params.getInt("totalFilesCount"));
					syncProgress.setProgress(params.getInt("importedFilesCount"));
					syncText.setText(activity.getString(R.string.importing_file, String.valueOf(params.getInt("importedFilesCount")), String.valueOf(params.getInt("totalFilesCount"))));
				}
				break;
			case SyncManager.STATUS_UPLOADING:
				refreshCProgress.setVisibility(View.GONE);
				syncPhoto.setVisibility(View.VISIBLE);
				syncProgress.setVisibility(View.VISIBLE);
				backupCompleteIcon.setVisibility(View.GONE);

				if(StinglePhotosApplication.syncStatusParams != null) {
					Bundle params = StinglePhotosApplication.syncStatusParams;
					syncProgress.setMax(params.getInt("totalFilesCount"));
					syncProgress.setProgress(params.getInt("uploadedFilesCount"));
					syncText.setText(activity.getString(R.string.uploading_file, String.valueOf(params.getInt("uploadedFilesCount")), String.valueOf(params.getInt("totalFilesCount"))));
					if (params.getString("filename") != null && params.getString("headers") != null) {
						(new ShowEncThumbInImageView(activity, params.getString("filename"), syncPhoto))
								.setHeaders(params.getString("headers"))
								.setSet(params.getInt("set"))
								.setAlbumId(params.getString("albumId"))
								.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				}

				break;
			case SyncManager.STATUS_NO_SPACE_LEFT:
				refreshCProgress.setVisibility(View.GONE);
				syncPhoto.setVisibility(View.GONE);
				syncPhoto.setImageDrawable(null);
				syncProgress.setVisibility(View.INVISIBLE);
				syncText.setText(activity.getString(R.string.no_space_left));
				backupCompleteIcon.setVisibility(View.GONE);
				activity.updateQuotaInfo();
				break;
			case SyncManager.STATUS_DISABLED:
				refreshCProgress.setVisibility(View.GONE);
				syncPhoto.setVisibility(View.GONE);
				syncPhoto.setImageDrawable(null);
				syncProgress.setVisibility(View.INVISIBLE);
				syncText.setText(activity.getString(R.string.sync_disabled));
				backupCompleteIcon.setVisibility(View.GONE);
				activity.updateQuotaInfo();
				break;
			case SyncManager.STATUS_NOT_WIFI:
				refreshCProgress.setVisibility(View.GONE);
				syncPhoto.setVisibility(View.GONE);
				syncPhoto.setImageDrawable(null);
				syncProgress.setVisibility(View.INVISIBLE);
				syncText.setText(activity.getString(R.string.sync_not_on_wifi));
				backupCompleteIcon.setVisibility(View.GONE);
				activity.updateQuotaInfo();
				break;
			case SyncManager.STATUS_BATTERY_LOW:
				refreshCProgress.setVisibility(View.GONE);
				syncPhoto.setVisibility(View.GONE);
				syncPhoto.setImageDrawable(null);
				syncProgress.setVisibility(View.INVISIBLE);
				syncText.setText(activity.getString(R.string.sync_battery_low));
				backupCompleteIcon.setVisibility(View.GONE);
				activity.updateQuotaInfo();
				break;
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
