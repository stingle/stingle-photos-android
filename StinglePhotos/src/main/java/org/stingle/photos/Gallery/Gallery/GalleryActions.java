package org.stingle.photos.Gallery.Gallery;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.RadioButton;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.AsyncTasks.Gallery.DeleteAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.FileMoveAsyncTask;
import org.stingle.photos.AsyncTasks.DecryptFilesAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.DeleteFilesAsyncTask;
import org.stingle.photos.AsyncTasks.Sync.EmptyTrashAsyncTask;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Files.ShareManager;
import org.stingle.photos.Gallery.Albums.AlbumsAdapterPisasso;
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

	public static void addToAlbumSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files, boolean isFromAlbum) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.add_move_to_album);
		builder.setView(R.layout.add_to_album_dialog);
		builder.setCancelable(true);
		final AlertDialog addAlbumDialog = builder.create();
		addAlbumDialog.show();

		RecyclerView recyclerView = addAlbumDialog.findViewById(R.id.recycler_view);

		recyclerView.setHasFixedSize(true);

		LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
		recyclerView.setLayoutManager(layoutManager);

		final AlbumsAdapterPisasso adapter = new AlbumsAdapterPisasso(activity, layoutManager, isFromAlbum);

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

				FileMoveAsyncTask addSyncTask = new FileMoveAsyncTask(activity, files, onAddFinish);

				boolean isMoving = ((RadioButton)addAlbumDialog.findViewById(R.id.move_to_album)).isChecked();
				addSyncTask.setIsMoving(isMoving);

				if (isFromAlbum && index == 0) {
					addSyncTask.setFromFolder(SyncManager.FOLDER_ALBUM);
					addSyncTask.setToFolder(SyncManager.FOLDER_MAIN);
					addSyncTask.setFromFolderId(activity.getCurrentFolderId());
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

			private void addToAlbum(FileMoveAsyncTask addSyncTask, String albumId){
				if(isFromAlbum) {
					addSyncTask.setFromFolder(SyncManager.FOLDER_ALBUM);
					addSyncTask.setFromFolderId(activity.getCurrentFolderId());
				}
				else{
					addSyncTask.setFromFolder(SyncManager.FOLDER_MAIN);
				}
				addSyncTask.setToFolder(SyncManager.FOLDER_ALBUM);
				addSyncTask.setToFolderId(albumId);
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

					FileMoveAsyncTask moveTask = new FileMoveAsyncTask(activity, files, new OnAsyncTaskFinish() {
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
					moveTask.setToFolder(SyncManager.FOLDER_TRASH);
					moveTask.setIsMoving(true);
					moveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

	public static void restoreSelected(GalleryActivity activity, final ArrayList<StingleDbFile> files) {
		final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.restoring_files), null);

		FileMoveAsyncTask moveTask = new FileMoveAsyncTask(activity, files, new OnAsyncTaskFinish() {
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

		moveTask.setFromFolder(SyncManager.FOLDER_TRASH);
		moveTask.setToFolder(SyncManager.FOLDER_MAIN);
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

					new DeleteAlbumAsyncTask(activity, activity.getCurrentFolderId(), new OnAsyncTaskFinish() {
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

}
