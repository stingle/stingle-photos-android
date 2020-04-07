package org.stingle.photos.Gallery.Gallery;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.RadioButton;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.AsyncTasks.Gallery.DeleteFolderAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.MoveFileAsyncTask;
import org.stingle.photos.AsyncTasks.DecryptFilesAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Gallery.DeleteFilesAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.EmptyTrashAsyncTask;
import org.stingle.photos.Db.Objects.StingleDbFolder;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Folders.FoldersAdapterPisasso;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.util.ArrayList;

public class GalleryActions {

	public static void shareSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		ShareManager.shareDbFiles(activity, files, activity.getCurrentFolder(), activity.getCurrentFolderId());
	}

	public static void addToFolderSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files, boolean isFromFolder) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.add_move_to_album);
		builder.setView(R.layout.add_to_folder_dialog);
		builder.setCancelable(true);
		final AlertDialog addFolderDialog = builder.create();
		addFolderDialog.show();

		RecyclerView recyclerView = addFolderDialog.findViewById(R.id.recycler_view);

		recyclerView.setHasFixedSize(true);

		LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
		recyclerView.setLayoutManager(layoutManager);

		final FoldersAdapterPisasso adapter = new FoldersAdapterPisasso(activity, layoutManager, isFromFolder);

		adapter.setLayoutStyle(FoldersAdapterPisasso.LAYOUT_LIST);
		adapter.setListener(new FoldersAdapterPisasso.Listener() {
			@Override
			public void onClick(int index) {

				OnAsyncTaskFinish onAddFinish = new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						addFolderDialog.dismiss();
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

				boolean isMoving = ((RadioButton)addFolderDialog.findViewById(R.id.move_to_folder)).isChecked();
				addSyncTask.setIsMoving(isMoving);

				if (isFromFolder && index == 0) {
					addSyncTask.setFromFolder(SyncManager.FOLDER);
					addSyncTask.setToFolder(SyncManager.GALLERY);
					addSyncTask.setFromFolderId(activity.getCurrentFolderId());
					addSyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
				else if ((isFromFolder && index == 1) || (!isFromFolder && index == 0)){
					GalleryHelpers.addFolder(activity, new OnAsyncTaskFinish() {
						@Override
						public void onFinish(Object folderObj) {
							super.onFinish(folderObj);

							StingleDbFolder folder = (StingleDbFolder) folderObj;
							addToFolder(addSyncTask, folder.folderId);
						}

						@Override
						public void onFail() {
							super.onFail();
						}
					});
				}
				else {
					addToFolder(addSyncTask, adapter.getFolderAtPosition(index).folderId);
				}

			}

			private void addToFolder(MoveFileAsyncTask addSyncTask, String folderId){
				if(isFromFolder) {
					addSyncTask.setFromFolder(SyncManager.FOLDER);
					addSyncTask.setFromFolderId(activity.getCurrentFolderId());
				}
				else{
					addSyncTask.setFromFolder(SyncManager.GALLERY);
				}
				addSyncTask.setToFolder(SyncManager.FOLDER);
				addSyncTask.setToFolderId(folderId);
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
						decFilesJob.setFolder(activity.getCurrentFolder());
						decFilesJob.setFolderId(activity.getCurrentFolderId());
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
					moveTask.setFromFolder(activity.getCurrentFolder());
					moveTask.setFromFolderId(activity.getCurrentFolderId());
					moveTask.setToFolder(SyncManager.TRASH);
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

		moveTask.setFromFolder(SyncManager.TRASH);
		moveTask.setToFolder(SyncManager.GALLERY);
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

	public static void deleteFolder(GalleryActivity activity) {

		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_delete_album), activity.getCurrentFolderName()), (dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.deleting_album), null);

					new DeleteFolderAsyncTask(activity, activity.getCurrentFolderId(), new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							activity.exitActionMode();
							spinner.dismiss();
							activity.showFoldersList();
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

}
