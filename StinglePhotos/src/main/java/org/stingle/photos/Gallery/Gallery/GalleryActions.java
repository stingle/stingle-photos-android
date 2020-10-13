package org.stingle.photos.Gallery.Gallery;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.CalculateFreeUpSpaceAsyncTask;
import org.stingle.photos.AsyncTasks.DecryptFilesAsyncTask;
import org.stingle.photos.AsyncTasks.FreeUpSpaceAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.AddAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.DeleteAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.DeleteFilesAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.EmptyTrashAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.LeaveAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.MoveFileAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.RenameAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Albums.AlbumsAdapterPisasso;
import org.stingle.photos.Gallery.Albums.AlbumsFragment;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sharing.AlbumInfoDialogFragment;
import org.stingle.photos.Sharing.AlbumSettingsDialogFragment;
import org.stingle.photos.Sharing.SharingDialogFragment;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class GalleryActions {

	public static void refreshGallery(Context context){
		LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("REFRESH_GALLERY"));
	}

	public static void refreshGalleryItem(Context context, String filename, int set, String albumId){
		Intent intent = new Intent("REFRESH_GALLERY_ITEM");

		Bundle params = new Bundle();
		params.putString("filename", filename);
		params.putInt("set", set);
		params.putString("albumId", albumId);
		intent.putExtras(params);

		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	}

	public static void shareSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		ShareManager.shareDbFiles(activity, files, activity.getCurrentSet(), activity.getCurrentAlbumId());
	}

	public static void addToAlbumSelected(AppCompatActivity activity, final ArrayList<StingleDbFile> files, String albumId, boolean isFromAlbum) {
		StingleDbAlbum album = GalleryHelpers.getAlbum(activity, albumId);
		if(files != null && album != null && !album.isOwner && !SyncManager.areFilesAlreadyUploaded(files)){
			Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.wait_for_upload), Snackbar.LENGTH_LONG).show();
			return;
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
		builder.setView(R.layout.dialog_move_items);
		builder.setCancelable(true);
		final AlertDialog addAlbumDialog = builder.create();
		addAlbumDialog.show();

		RecyclerView recyclerView = addAlbumDialog.findViewById(R.id.recycler_view);
		LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
		final AlbumsAdapterPisasso adapter = new AlbumsAdapterPisasso(activity, layoutManager, AlbumsFragment.VIEW_SELECTOR, true, isFromAlbum);

		recyclerView.setHasFixedSize(true);

		Switch deleteOriginal = addAlbumDialog.findViewById(R.id.delete_original);
		deleteOriginal.setChecked(Helpers.getPreference(activity, "delete_original", true));

		final HashMap<String, Boolean> props = new HashMap<>();

		if(isFromAlbum) {
			if(album.isShared && !album.isOwner){
				deleteOriginal.setVisibility(View.GONE);
				deleteOriginal.setChecked(false);
				props.put("dontSaveState", true);
				adapter.setDisableOthersAlbums(true);
			}
		}

		recyclerView.setLayoutManager(layoutManager);

		adapter.setTextSize(18);
		adapter.setSubTextSize(12);
		adapter.setLayoutStyle(AlbumsAdapterPisasso.LAYOUT_LIST);
		adapter.setListener(new AlbumsAdapterPisasso.Listener() {
			ProgressDialog spinner;
			@Override
			public void onClick(int index, int type) {
				OnAsyncTaskFinish onAddFinish = new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						if(spinner != null) {
							spinner.dismiss();
						}
						addAlbumDialog.dismiss();
						if(activity instanceof GalleryActivity) {
							((GalleryActivity)activity).exitActionMode();
							((GalleryActivity)activity).updateGalleryFragmentData();
						}
					}

					@Override
					public void onFail() {
						super.onFail();
						spinner.dismiss();
						//Helpers.showAlertDialog(activity, activity.getString(R.string.cant_move_to_this_album));
					}
				};

				MoveFileAsyncTask addSyncTask = new MoveFileAsyncTask(activity, files, onAddFinish);

				boolean isMoving = ((Switch)addAlbumDialog.findViewById(R.id.delete_original)).isChecked();
				if(!(props.containsKey("dontSaveState") && props.get("dontSaveState"))) {
					Helpers.storePreference(activity, "delete_original", isMoving);
				}
				addSyncTask.setIsMoving(isMoving);

				if (type == AlbumsAdapterPisasso.TYPE_GALLERY) {
					spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.processing), null);
					addSyncTask.setFromSet(SyncManager.ALBUM);
					addSyncTask.setToSet(SyncManager.GALLERY);
					addSyncTask.setFromAlbumId(albumId);
					addSyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
				else if (type == AlbumsAdapterPisasso.TYPE_ADD){
					addAlbum(activity, new OnAsyncTaskFinish() {
						@Override
						public void onFinish(Object albumObj) {
							super.onFinish(albumObj);

							StingleDbAlbum album = (StingleDbAlbum) albumObj;
							spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.processing), null);
							addToAlbum(addSyncTask, album.albumId);
						}

						@Override
						public void onFail() {
							super.onFail();
						}
					});
				}
				else {
					spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.processing), null);
					addToAlbum(addSyncTask, adapter.getAlbumAtPosition(index).albumId);
				}

			}

			private void addToAlbum(MoveFileAsyncTask addSyncTask, String thisAlbumId){
				StingleDbAlbum album = GalleryHelpers.getAlbum(activity, thisAlbumId);
				if(files != null && album != null && !album.isOwner && !SyncManager.areFilesAlreadyUploaded(files)){
					Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.wait_for_upload), Snackbar.LENGTH_LONG).show();
					spinner.dismiss();
					return;
				}
				if(isFromAlbum) {
					addSyncTask.setFromSet(SyncManager.ALBUM);
					addSyncTask.setFromAlbumId(albumId);
				}
				else{
					addSyncTask.setFromSet(SyncManager.GALLERY);
				}
				addSyncTask.setToSet(SyncManager.ALBUM);
				addSyncTask.setToAlbumId(thisAlbumId);
				addSyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}


			@Override
			public void onLongClick(int index, int type) {

			}

			@Override
			public void onSelectionChanged(int count) {

			}
		});

		recyclerView.setAdapter(adapter);
	}

	public static void decryptSelected(Activity activity, final ArrayList<StingleDbFile> files, int set, String albumId, OnAsyncTaskFinish onFinish) {
		Helpers.showConfirmDialog(activity, activity.getString(R.string.decrypt_files), String.format(activity.getString(R.string.confirm_decrypt_files)), R.drawable.ic_action_unlock, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.decrypting_files), null);

						File decryptDir = new File(Environment.getExternalStorageDirectory().getPath() + "/" + FileManager.DECRYPT_DIR);
						DecryptFilesAsyncTask decFilesJob = new DecryptFilesAsyncTask(activity, decryptDir, new OnAsyncTaskFinish() {
							@Override
							public void onFinish(ArrayList<File> files) {
								super.onFinish(files);
								if(activity instanceof GalleryActivity) {
									((GalleryActivity)activity).exitActionMode();
								}
								if(onFinish != null){
									onFinish.onFinish();
								}
								spinner.dismiss();
							}
						});
						decFilesJob.setSet(set);
						decFilesJob.setAlbumId(albumId);
						decFilesJob.setPerformMediaScan(true);
						decFilesJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, files);
					}
				},
				null);
	}

	public static void trashSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		/*if(files != null && !SyncManager.areFilesAlreadyUploaded(files)){
			Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.wait_for_upload), Snackbar.LENGTH_LONG).show();
			return;
		}*/
		Helpers.showConfirmDialog(
				activity,
				activity.getString(R.string.move_to_trash_question),
				String.format(activity.getString(R.string.confirm_trash_files), String.valueOf(files.size())),
				R.drawable.ic_action_delete,
				(dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.trashing_files), null);

					SyncManager.stopSync(activity);
					MoveFileAsyncTask moveTask = new MoveFileAsyncTask(activity, files, new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
							SyncManager.startSync(activity);
						}

						@Override
						public void onFail() {
							super.onFail();
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
							SyncManager.startSync(activity);
						}
					});
					moveTask.setFromSet(activity.getCurrentSet());
					moveTask.setFromAlbumId(activity.getCurrentAlbumId());
					moveTask.setToSet(SyncManager.TRASH);
					moveTask.setIsMoving(true);
					moveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void restoreSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		/*if(files != null && !SyncManager.areFilesAlreadyUploaded(files)){
			Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.wait_for_upload), Snackbar.LENGTH_LONG).show();
			return;
		}*/
		final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.restoring_files), null);
		SyncManager.stopSync(activity);
		MoveFileAsyncTask moveTask = new MoveFileAsyncTask(activity, files, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				activity.updateGalleryFragmentData();
				activity.exitActionMode();
				spinner.dismiss();
				SyncManager.startSync(activity);
			}

			@Override
			public void onFail() {
				super.onFail();
				activity.updateGalleryFragmentData();
				activity.exitActionMode();
				spinner.dismiss();
				SyncManager.startSync(activity);
			}
		});

		moveTask.setFromSet(SyncManager.TRASH);
		moveTask.setToSet(SyncManager.GALLERY);
		moveTask.setIsMoving(true);
		moveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

	}

	public static void deleteSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		/*if(files != null && !SyncManager.areFilesAlreadyUploaded(files)){
			Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.wait_for_upload), Snackbar.LENGTH_LONG).show();
			return;
		}*/
		Helpers.showConfirmDialog(
				activity,
				activity.getString(R.string.delete_question),
				String.format(activity.getString(R.string.confirm_delete_files), String.valueOf(files.size())),
				R.drawable.ic_action_delete,
				(dialog, which) -> {
					SyncManager.stopSync(activity);
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.deleting_files), null);

					new DeleteFilesAsyncTask(activity, files, new SyncManager.OnFinish() {
						@Override
						public void onFinish(Boolean needToUpdateUI) {
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
							SyncManager.startSync(activity);
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void emptyTrash(GalleryActivity activity){
		Helpers.showConfirmDialog(
				activity,
				activity.getString(R.string.empty_trash_question),
				String.format(activity.getString(R.string.confirm_empty_trash)),
				R.drawable.ic_action_delete,
				(dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.emptying_trash), null);
					SyncManager.stopSync(activity);
					new EmptyTrashAsyncTask(activity, new SyncManager.OnFinish(){
						@Override
						public void onFinish(Boolean needToUpdateUI) {
							activity.updateGalleryFragmentData();
							spinner.dismiss();
							SyncManager.startSync(activity);
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void addAlbum(Context context, OnAsyncTaskFinish onFinish){
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
		builder.setView(R.layout.dialog_new_album);
		builder.setCancelable(true);
		AlertDialog addAlbumDialog = builder.create();
		addAlbumDialog.show();

		Button okButton = addAlbumDialog.findViewById(R.id.okButton);
		Button cancelButton = addAlbumDialog.findViewById(R.id.cancelButton);
		final EditText albumNameText = addAlbumDialog.findViewById(R.id.album_name);

		final InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

		okButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			String albumName = albumNameText.getText().toString();
			final ProgressDialog spinner = Helpers.showProgressDialog(context, context.getString(R.string.processing), null);
			(new AddAlbumAsyncTask(context, albumName, new OnAsyncTaskFinish() {
				@Override
				public void onFinish(Object album) {
					super.onFinish(album);
					spinner.dismiss();
					addAlbumDialog.dismiss();
					onFinish.onFinish(album);
				}

				@Override
				public void onFail() {
					super.onFail();
					spinner.dismiss();
					addAlbumDialog.dismiss();
					onFinish.onFail();
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		});

		cancelButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			addAlbumDialog.dismiss();
		});
	}

	public static void deleteAlbum(GalleryActivity activity) {
		Helpers.showConfirmDialog(
				activity,
				activity.getString(R.string.delete_question),
				String.format(activity.getString(R.string.confirm_delete_album), activity.getCurrentAlbumName()),
				R.drawable.ic_action_delete,
				(dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.deleting_album), null);

					new DeleteAlbumAsyncTask(activity, activity.getCurrentAlbumId(), new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							activity.exitActionMode();
							spinner.dismiss();
							activity.showAlbumsList();
						}

						@Override
						public void onFail() {
							super.onFail();
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void shareStingle(AppCompatActivity activity, int set, String albumId, String albumName, ArrayList<StingleDbFile> files, boolean onlyAddMembers, OnAsyncTaskFinish onFinish) {
		if(files != null && !SyncManager.areFilesAlreadyUploaded(files)){
			Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.wait_for_upload), Snackbar.LENGTH_LONG).show();
			return;
		}
		if(albumId != null && !SyncManager.areFilesAlreadyUploaded(activity, albumId)){
			Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.wait_for_upload), Snackbar.LENGTH_LONG).show();
			return;
		}

		if(files != null && albumId != null) {
			StingleDbAlbum album = GalleryHelpers.getAlbum(activity, albumId);
			if (album != null && !album.isOwner) {
				Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.cant_share_others_album), Snackbar.LENGTH_LONG).show();
				return;
			}
		}

		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		SharingDialogFragment newFragment = new SharingDialogFragment();

		newFragment.setSourceSet(set);
		newFragment.setAlbumId(albumId);
		newFragment.setFiles(files);
		if(files == null) {
			newFragment.setAlbumName(albumName);
		}
		newFragment.setOnlyAddMembers(onlyAddMembers);
		newFragment.setOnFinish(onFinish);

		newFragment.show(ft, "dialog");
	}

	public static void albumSettings(GalleryActivity activity) {
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		AlbumSettingsDialogFragment newFragment = new AlbumSettingsDialogFragment();

		newFragment.setAlbumId(activity.getCurrentAlbumId());

		newFragment.show(ft, "dialog");
	}

	public static void albumInfo(GalleryActivity activity) {
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		AlbumInfoDialogFragment newFragment = new AlbumInfoDialogFragment();

		newFragment.setAlbumId(activity.getCurrentAlbumId());

		newFragment.show(ft, "dialog");
	}

	public static void renameAlbum(Context context, String albumId, String currentAlbumName, OnAsyncTaskFinish onFinish) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
		builder.setView(R.layout.dialog_new_album);
		builder.setCancelable(true);
		AlertDialog renameAlbumDialog = builder.create();
		renameAlbumDialog.show();

		Button okButton = renameAlbumDialog.findViewById(R.id.okButton);
		Button cancelButton = renameAlbumDialog.findViewById(R.id.cancelButton);
		final EditText albumNameText = renameAlbumDialog.findViewById(R.id.album_name);

		((TextView)renameAlbumDialog.findViewById(R.id.titleTextView)).setText(R.string.rename_album);

		final InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

		albumNameText.setText(currentAlbumName);
		albumNameText.selectAll();

		okButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			String albumName = albumNameText.getText().toString();
			final ProgressDialog spinner = Helpers.showProgressDialog(context, context.getString(R.string.processing), null);
			(new RenameAlbumAsyncTask(context, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					spinner.dismiss();
					renameAlbumDialog.dismiss();
					if(onFinish != null){
						onFinish.onFinish(albumName);
					}
				}

				@Override
				public void onFail() {
					super.onFail();
					spinner.dismiss();
					renameAlbumDialog.dismiss();
					if(onFinish != null){
						onFinish.onFail();
					}
				}
			})).setAlbumId(albumId).setAlbumName(albumName).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		});

		cancelButton.setOnClickListener(v -> {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			renameAlbumDialog.dismiss();
		});
	}

	public static void leaveAlbum(GalleryActivity activity) {

		Helpers.showConfirmDialog(
				activity,
				activity.getString(R.string.leave_question),
				String.format(activity.getString(R.string.confirm_leave_album)),
				R.drawable.ic_arrow_back,
				(dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.leaving_album), null);

					new LeaveAlbumAsyncTask(activity, new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							activity.exitActionMode();
							spinner.dismiss();
							activity.showAlbumsList();
						}

						@Override
						public void onFail() {
							super.onFail();
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
						}
					}).setAlbumId(activity.getCurrentAlbumId()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void freeUpSpace(GalleryActivity activity){
		final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.processing), null);

		new CalculateFreeUpSpaceAsyncTask(activity, new OnAsyncTaskFinish() {
			@Override
			public void onFinish(Long totalSize) {
				super.onFinish();
				spinner.dismiss();

				if(totalSize == 0){
					Helpers.showInfoDialog(activity, activity.getString(R.string.free_up_space_nothing), activity.getString(R.string.free_up_space_nothing_desc));
					return;
				}

				int sizeMb = (int)(totalSize / (1024 * 1024));

				String size = Helpers.formatSpaceUnits(sizeMb);

				Helpers.showConfirmDialog(
						activity,
						activity.getString(R.string.free_up_space_confirm, size),
						activity.getString(R.string.free_up_space_confirm_desc, size),
						null,
						(dialog, which) -> {
							final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.processing), null);

							new FreeUpSpaceAsyncTask(activity, new OnAsyncTaskFinish() {
								@Override
								public void onFinish() {
									super.onFinish();
									activity.updateGalleryFragmentData();
									spinner.dismiss();
									Helpers.showInfoDialog(activity, activity.getString(R.string.success), activity.getString(R.string.free_up_space_success, size));
								}

								@Override
								public void onFail() {
									super.onFail();
									activity.updateGalleryFragmentData();
									spinner.dismiss();
									Helpers.showAlertDialog(activity, activity.getString(R.string.success), activity.getString(R.string.free_up_space_fail));
								}
							}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
						},
						null);
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

}
