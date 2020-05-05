package org.stingle.photos.Sharing;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.Gallery.EditAlbumPermissionsAsyncTask;
import org.stingle.photos.AsyncTasks.Gallery.UnshareAlbumAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Gallery.Albums.AlbumsFragment;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;


public class AlbumSettingsDialogFragment extends AppCompatDialogFragment {

	private SharingDialogStep2Fragment step2fragment;
	private String albumId;
	private String albumName;
	private StingleDbAlbum album;
	private AlbumMembersFragment membersFragment;
	private TextView albumNameText;


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(AppCompatDialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);

	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.dialog_album_settings_fragment, container, false);

		Toolbar toolbar = view.findViewById(R.id.toolbar);
		toolbar.setNavigationIcon(R.drawable.ic_close);
		toolbar.setNavigationOnClickListener(view1 -> dismiss());
		toolbar.setTitle(getString(R.string.album_settings));

		albumNameText = view.findViewById(R.id.albumName);

		step2fragment = new SharingDialogStep2Fragment();

		if(albumId == null || albumId.length() == 0) {
			requireDialog().dismiss();
		}

		step2fragment.hideAlbumName();
		step2fragment.setChangeListener(new SharingDialogStep2Fragment.PermissionChangeListener() {
			@Override
			public void onChange() {
				savePermissions();
			}
		});

		initAlbum();
		albumName = GalleryHelpers.getAlbumName(album);

		if (album.isShared && album.permissionsObj != null) {
			step2fragment.setPermissions(album.permissionsObj);
			albumNameText.setText(albumName);
			membersFragment = new AlbumMembersFragment(album);
			albumNameText.setOnClickListener(renameAlbum());

			FragmentTransaction ft = getChildFragmentManager().beginTransaction();
			ft.replace(R.id.permissionsContainer, step2fragment);
			ft.replace(R.id.membersContainer, membersFragment);
			ft.commit();
		}

		view.findViewById(R.id.unshare).setOnClickListener(unshare());

		return view;
	}

	private View.OnClickListener renameAlbum() {
		return v -> {
			GalleryActions.renameAlbum(requireContext(), albumId, albumName, new OnAsyncTaskFinish() {
				@Override
				public void onFinish(Object newAlbumName) {
					super.onFinish();
					if(newAlbumName != null && newAlbumName instanceof String){
						albumName = (String)newAlbumName;
						albumNameText.setText(albumName);
						Activity activity = requireActivity();
						if(activity instanceof GalleryActivity){
							((GalleryActivity)activity).initCurrentFragment();
						}
					}
				}

				@Override
				public void onFail() {
					super.onFail();
					Toast.makeText(requireContext(), R.string.something_went_wrong, Toast.LENGTH_LONG).show();
				}
			});
		};
	}

	private View.OnClickListener unshare() {
		return v -> {
			Helpers.showConfirmDialog(requireContext(), requireContext().getString(R.string.unshare_confirm, albumName), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					final ProgressDialog spinner = Helpers.showProgressDialog(getActivity(), requireContext().getString(R.string.spinner_unsharing), null);
					UnshareAlbumAsyncTask task = new UnshareAlbumAsyncTask(requireContext(), new OnAsyncTaskFinish() {
						@Override
						public void onFinish() {
							super.onFinish();
							spinner.dismiss();
							Snackbar.make(requireActivity().findViewById(R.id.drawer_layout), requireContext().getString(R.string.unshare_success, albumName), Snackbar.LENGTH_LONG).show();
							requireDialog().dismiss();
							Activity activity = requireActivity();
							if(activity instanceof GalleryActivity){
								((GalleryActivity)activity).showAlbumsList(AlbumsFragment.VIEW_ALBUMS);
							}
						}

						@Override
						public void onFail() {
							super.onFail();
							spinner.dismiss();
							Toast.makeText(requireContext(), R.string.something_went_wrong, Toast.LENGTH_LONG).show();
						}
					});
					task.setAlbumId(album.albumId);
					task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
			}, null);
		};
	}


	private void savePermissions(){
		final ProgressDialog spinner = Helpers.showProgressDialog(getActivity(), requireContext().getString(R.string.spinner_saving), null);
		EditAlbumPermissionsAsyncTask task = new EditAlbumPermissionsAsyncTask(requireContext(), new OnAsyncTaskFinish() {
			@Override
			public void onFinish() {
				super.onFinish();
				spinner.dismiss();
				Snackbar.make(requireActivity().findViewById(R.id.drawer_layout), requireContext().getString(R.string.save_success), Snackbar.LENGTH_LONG).show();
				initAlbum();
				step2fragment.setPermissions(album.permissionsObj);
			}

			@Override
			public void onFail() {
				super.onFail();
				spinner.dismiss();
				Snackbar.make(requireDialog().findViewById(R.id.drawer_layout), requireContext().getString(R.string.something_went_wrong), Snackbar.LENGTH_LONG).show();
				initAlbum();
				step2fragment.setPermissions(album.permissionsObj);
			}
		});
		task.setAlbumId(albumId);
		task.setPermissions(step2fragment.getPermissions());
		task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void setAlbumId(String albumId){
		this.albumId = albumId;
	}

	private void initAlbum(){
		AlbumsDb db = new AlbumsDb(requireContext());
		album = db.getAlbumById(albumId);
		db.close();
	}

}
