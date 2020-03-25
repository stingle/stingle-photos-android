package org.stingle.photos.Gallery.Gallery;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Environment;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.AsyncTasks.AddFilesToAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.DecryptFilesAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleFile;
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

	public static void shareSelected(GalleryActivity activity, final ArrayList<StingleFile> files) {
		ShareManager.shareDbFiles(activity, files, activity.getCurrentFolder());
	}

	public static void addToAlbumSelected(GalleryActivity activity, final ArrayList<StingleFile> files) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setView(R.layout.add_to_album_dialog);
		builder.setCancelable(true);
		final AlertDialog addAlbumDialog = builder.create();
		addAlbumDialog.show();

		RecyclerView recyclerView = addAlbumDialog.findViewById(R.id.recycler_view);

		recyclerView.setHasFixedSize(true);

		LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
		recyclerView.setLayoutManager(layoutManager);

		final AlbumsAdapterPisasso adapter = new AlbumsAdapterPisasso(activity, layoutManager);

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
					}

					@Override
					public void onFail() {
						super.onFail();
						Helpers.showAlertDialog(activity, activity.getString(R.string.something_went_wrong));
					}
				};

				if (index == 0) {
					GalleryHelpers.addAlbum(activity, new OnAsyncTaskFinish() {
						@Override
						public void onFinish(Object albumObj) {
							super.onFinish(albumObj);

							StingleDbAlbum album = (StingleDbAlbum) albumObj;
							(new AddFilesToAlbumAsyncTask(activity, album, files, onAddFinish)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

						}

						@Override
						public void onFail() {
							super.onFail();
						}
					});
				} else {
					(new AddFilesToAlbumAsyncTask(activity, adapter.getAlbumAtPosition(index), files, onAddFinish)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}

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

	public static void decryptSelected(GalleryActivity activity, final ArrayList<StingleFile> files) {
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
						decFilesJob.setPerformMediaScan(true);
						decFilesJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, files);
					}
				},
				null);
	}

	public static void trashSelected(GalleryActivity activity, final ArrayList<StingleFile> files) {
		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_trash_files), String.valueOf(files.size())), (dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.trashing_files), null);

					new SyncManager.MoveToTrashAsyncTask(activity, files, new SyncManager.OnFinish() {
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

	public static void restoreSelected(GalleryActivity activity, final ArrayList<StingleFile> files) {
		final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.restoring_files), null);

		new SyncManager.RestoreFromTrashAsyncTask(activity, files, new SyncManager.OnFinish() {
			@Override
			public void onFinish(Boolean needToUpdateUI) {
				activity.updateGalleryFragmentData();
				activity.exitActionMode();
				spinner.dismiss();
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

	}

	public static void deleteSelected(GalleryActivity activity, final ArrayList<StingleFile> files) {
		Helpers.showConfirmDialog(activity, String.format(activity.getString(R.string.confirm_delete_files), String.valueOf(files.size())), (dialog, which) -> {
					final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.deleting_files), null);

					new SyncManager.DeleteFilesAsyncTask(activity, files, new SyncManager.OnFinish() {
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

					new SyncManager.EmptyTrashAsyncTask(activity, new SyncManager.OnFinish(){
						@Override
						public void onFinish(Boolean needToUpdateUI) {
							activity.updateGalleryFragmentData();
							spinner.dismiss();
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				},
				null);
	}

}
