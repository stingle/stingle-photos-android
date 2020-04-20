package org.stingle.photos.Gallery.Gallery;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.DecryptFilesAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.DeleteAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.DeleteFilesAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.EmptyTrashAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.MoveFileAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.ShareAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.GetContactAsyncTask;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Albums.AlbumsAdapterPisasso;
import org.stingle.photos.Gallery.Albums.AlbumsFragment;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sharing.SharingPermissions;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;

public class GalleryActions {

	public static void shareSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		ShareManager.shareDbFiles(activity, files, activity.getCurrentSet(), activity.getCurrentAlbumId());
	}

	public static void addToAlbumSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files, boolean isFromAlbum) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
		builder.setTitle(R.string.add_move_to_album);
		builder.setView(R.layout.add_to_album_dialog);
		builder.setCancelable(true);
		final AlertDialog addAlbumDialog = builder.create();
		addAlbumDialog.show();

		RecyclerView recyclerView = addAlbumDialog.findViewById(R.id.recycler_view);

		recyclerView.setHasFixedSize(true);

		LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
		recyclerView.setLayoutManager(layoutManager);

		final AlbumsAdapterPisasso adapter = new AlbumsAdapterPisasso(activity, layoutManager, AlbumsFragment.VIEW_ALBUMS, isFromAlbum);

		adapter.setLayoutStyle(AlbumsAdapterPisasso.LAYOUT_LIST);
		adapter.setListener(new AlbumsAdapterPisasso.Listener() {
			@Override
			public void onClick(int index) {

				OnAsyncTaskFinish onAddFinish = new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						addAlbumDialog.dismiss();
						activity.exitActionMode();
						activity.updateGalleryFragmentData();
					}

					@Override
					public void onFail() {
						super.onFail();
						Helpers.showAlertDialog(activity, activity.getString(R.string.something_went_wrong));
					}
				};

				MoveFileAsyncTask addSyncTask = new MoveFileAsyncTask(activity, files, onAddFinish);

				boolean isMoving = ((RadioButton)addAlbumDialog.findViewById(R.id.move_to_album)).isChecked();
				addSyncTask.setIsMoving(isMoving);

				if (isFromAlbum && index == 0) {
					addSyncTask.setFromSet(SyncManager.ALBUM);
					addSyncTask.setToSet(SyncManager.GALLERY);
					addSyncTask.setFromAlbumId(activity.getCurrentAlbumId());
					addSyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
				else if ((isFromAlbum && index == 1) || (!isFromAlbum && index == 0)){
					GalleryHelpers.addAlbum(activity, new OnAsyncTaskFinish() {
						@Override
						public void onFinish(Object albumObj) {
							super.onFinish(albumObj);

							StingleDbAlbum album = (StingleDbAlbum) albumObj;
							addToAlbum(addSyncTask, album.albumId);
						}

						@Override
						public void onFail() {
							super.onFail();
						}
					});
				}
				else {
					addToAlbum(addSyncTask, adapter.getAlbumAtPosition(index).albumId);
				}

			}

			private void addToAlbum(MoveFileAsyncTask addSyncTask, String albumId){
				if(isFromAlbum) {
					addSyncTask.setFromSet(SyncManager.ALBUM);
					addSyncTask.setFromAlbumId(activity.getCurrentAlbumId());
				}
				else{
					addSyncTask.setFromSet(SyncManager.GALLERY);
				}
				addSyncTask.setToSet(SyncManager.ALBUM);
				addSyncTask.setToAlbumId(albumId);
				addSyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}


			@Override
			public void onLongClick(int index) {

			}

			@Override
			public void onSelectionChanged(int count) {

			}
		});

		recyclerView.setAdapter(adapter);
	}

	public static void decryptSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_decrypt_files)), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.decrypting_files), null);

						File decryptDir = new File(Environment.getExternalStorageDirectory().getPath() + "/" + FileManager.DECRYPT_DIR);
						DecryptFilesAsyncTask decFilesJob = new DecryptFilesAsyncTask(activity, decryptDir, new OnAsyncTaskFinish() {
							@Override
							public void onFinish(ArrayList<File> files) {
								super.onFinish(files);
								activity.exitActionMode();
								spinner.dismiss();
							}
						});
						decFilesJob.setSet(activity.getCurrentSet());
						decFilesJob.setAlbumId(activity.getCurrentAlbumId());
						decFilesJob.setPerformMediaScan(true);
						decFilesJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, files);
					}
				},
				null);
	}

	public static void trashSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_trash_files), String.valueOf(files.size())), (dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.trashing_files), null);

					MoveFileAsyncTask moveTask = new MoveFileAsyncTask(activity, files, new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
						}

						@Override
						public void onFail() {
							super.onFail();
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
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
		final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.restoring_files), null);

		MoveFileAsyncTask moveTask = new MoveFileAsyncTask(activity, files, new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				activity.updateGalleryFragmentData();
				activity.exitActionMode();
				spinner.dismiss();
			}

			@Override
			public void onFail() {
				super.onFail();
				activity.updateGalleryFragmentData();
				activity.exitActionMode();
				spinner.dismiss();
			}
		});

		moveTask.setFromSet(SyncManager.TRASH);
		moveTask.setToSet(SyncManager.GALLERY);
		moveTask.setIsMoving(true);
		moveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

	}

	public static void deleteSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_delete_files), String.valueOf(files.size())), (dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.deleting_files), null);

					new DeleteFilesAsyncTask(activity, files, new SyncManager.OnFinish() {
						@Override
						public void onFinish(Boolean needToUpdateUI) {
							activity.updateGalleryFragmentData();
							activity.exitActionMode();
							spinner.dismiss();
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void emptyTrash(GalleryActivity activity){
		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_empty_trash)), (dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.emptying_trash), null);

					new EmptyTrashAsyncTask(activity, new SyncManager.OnFinish(){
						@Override
						public void onFinish(Boolean needToUpdateUI) {
							activity.updateGalleryFragmentData();
							spinner.dismiss();
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void deleteAlbum(GalleryActivity activity) {

		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_delete_album), activity.getCurrentAlbumName()), (dialog, which) -> {
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

	public static void showSharingSheet(GalleryActivity activity, boolean isAlbum, ArrayList<StingleDbFile> files) {
		View dialogView = activity.getLayoutInflater().inflate(R.layout.sharing_bottom_sheet, null);
		BottomSheetDialog dialog = new BottomSheetDialog(activity);
		dialog.setContentView(dialogView);
		BottomSheetBehavior mBehavior = dialog.getBehavior();

		dialog.setOnShowListener(dialogInterface -> {
			mBehavior.setPeekHeight(dialogView.getHeight());
		});

		/*mBehavior.setFitToContents(false);
		mBehavior.setHalfExpandedRatio((float) 0.6);
		mBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);

		View bottomSheet = dialog.findViewById(R.id.design_bottom_sheet);
		bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;*/


		if(isAlbum){
			dialog.findViewById(R.id.otherAppsShareContainer).setVisibility(View.GONE);
		}

		dialog.findViewById(R.id.close_bottom_sheet).setOnClickListener(v -> dialog.dismiss());

		Switch allowEdit = dialog.findViewById(R.id.allow_edit);
		Switch allowInvite = dialog.findViewById(R.id.allow_invite);
		Switch allowCopy = dialog.findViewById(R.id.allow_copy);

		dialog.findViewById(R.id.allow_edit_text).setOnClickListener(v -> allowEdit.toggle());
		dialog.findViewById(R.id.allow_invite_text).setOnClickListener(v -> allowInvite.toggle());
		dialog.findViewById(R.id.allow_copy_text).setOnClickListener(v -> allowCopy.toggle());
		dialog.findViewById(R.id.shareToOtherApps).setOnClickListener(v -> {
			shareSelected(activity, files);
			dialog.dismiss();
		});

		EditText recipient = dialog.findViewById(R.id.recipient);
		LinearLayout chipsContainer = dialog.findViewById(R.id.chipsContainer);

		String ownEmail = Helpers.getPreference(activity, StinglePhotosApplication.USER_EMAIL, "");

		ArrayList<StingleContact> recipients = new ArrayList<>();

		recipient.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_GO) {
				EditText textBox = ((EditText)v);
				String text = textBox.getText().toString();
				if(Helpers.isValidEmail(text)) {
					if(text.equals(ownEmail)){
						Toast.makeText(activity, activity.getString(R.string.cant_share_to_self), Toast.LENGTH_SHORT).show();
						return true;
					}
					(new GetContactAsyncTask(activity, text, new OnAsyncTaskFinish() {
						@Override
						public void onFinish(Object contact) {
							super.onFinish(contact);
							Chip chip = new Chip(activity);
							chip.setText(text);
							chip.setCloseIconVisible(true);
							chip.setCloseIcon(activity.getDrawable(R.drawable.ic_close));
							chip.setOnCloseIconClickListener(v1 -> {
								chipsContainer.removeView(chip);
							});
							chipsContainer.addView(chip);
							textBox.setText("");

							recipients.add((StingleContact) contact);
						}

						@Override
						public void onFail() {
							super.onFail();
							Toast.makeText(activity, activity.getString(R.string.not_on_stingle), Toast.LENGTH_SHORT).show();
						}
					})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

				}
				else{
					Toast.makeText(activity, activity.getString(R.string.invalid_email), Toast.LENGTH_SHORT).show();
				}
				return true;
			}
			return false;
		});

		dialog.findViewById(R.id.share).setOnClickListener(v -> {
			final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.spinner_sharing), null);
			ShareAlbumAsyncTask shareTask = new ShareAlbumAsyncTask(activity, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					spinner.dismiss();
					dialog.dismiss();
					activity.exitActionMode();
				}

				@Override
				public void onFail() {
					super.onFail();
					spinner.dismiss();
					dialog.dismiss();
					Snackbar mySnackbar = Snackbar.make(activity.findViewById(R.id.drawer_layout), activity.getString(R.string.failed_to_share), Snackbar.LENGTH_SHORT);
					mySnackbar.show();
				}
			});

			if(isAlbum) {
				shareTask.setAlbumId(activity.getCurrentAlbumId());
			}
			else{
				shareTask.setFiles(files);
				shareTask.setSourceAlbumId(activity.getCurrentAlbumId());
				shareTask.setSourceSet(activity.getCurrentSet());
			}
			shareTask.setRecipients(recipients);

			SharingPermissions permissions = new SharingPermissions();
			permissions.allowEditing = ((Switch)dialog.findViewById(R.id.allow_edit)).isChecked();
			permissions.allowResharing = ((Switch)dialog.findViewById(R.id.allow_invite)).isChecked();
			permissions.allowCopying = ((Switch)dialog.findViewById(R.id.allow_copy)).isChecked();

			shareTask.setPermissions(permissions);

			shareTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		});

		dialog.show();
	}
}
