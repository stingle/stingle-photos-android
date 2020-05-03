package org.stingle.photos.Sharing;


import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.Gallery.EditAlbumPermissionsAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;


public class AlbumPermissionsDialogFragment extends AppCompatDialogFragment {

	private SharingDialogStep2Fragment fragment;
	private Toolbar toolbar;

	private String albumId;


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(AppCompatDialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);

	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.sharing_edit_parent_fragment, container, false);

		toolbar = view.findViewById(R.id.toolbar);
		toolbar.setNavigationIcon(R.drawable.ic_close);
		toolbar.setNavigationOnClickListener(view1 -> dismiss());
		toolbar.setTitle(getString(R.string.permissions));
		toolbar.inflateMenu(R.menu.sharing_dialog_edit);
		toolbar.setOnMenuItemClickListener(onMenuClicked());

		fragment = new SharingDialogStep2Fragment();

		if(albumId == null || albumId.length() == 0) {
			requireDialog().dismiss();
		}

		fragment.hideAlbumName();
		fragment.hidePermissionsTitle();

		AlbumsDb db = new AlbumsDb(requireContext());
		StingleDbAlbum album = db.getAlbumById(albumId);
		if(album.isShared && album.permissionsObj != null) {
			fragment.setPermissions(album.permissionsObj);
		}

		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		ft.replace(R.id.fragmentContainer, fragment);
		ft.commit();

		return view;
	}

	private Toolbar.OnMenuItemClickListener onMenuClicked() {
		return item -> {
			int id = item.getItemId();

			if (id == R.id.saveButton) {
				final ProgressDialog spinner = Helpers.showProgressDialog(getActivity(), requireContext().getString(R.string.spinner_saving), null);
				EditAlbumPermissionsAsyncTask task = new EditAlbumPermissionsAsyncTask(requireContext(), new OnAsyncTaskFinish() {
					@Override
					public void onFinish() {
						super.onFinish();
						spinner.dismiss();
						requireDialog().dismiss();
						Snackbar.make(requireActivity().findViewById(R.id.drawer_layout), requireContext().getString(R.string.save_success), Snackbar.LENGTH_LONG).show();
					}

					@Override
					public void onFail() {
						super.onFail();
						spinner.dismiss();
						Snackbar.make(requireDialog().findViewById(R.id.drawer_layout), requireContext().getString(R.string.something_went_wrong), Snackbar.LENGTH_LONG).show();
					}
				});
				task.setAlbumId(albumId);
				task.setPermissions(fragment.getPermissions());
				task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			return false;
		};
	}


	public void setAlbumId(String albumId){
		this.albumId = albumId;
	}


}
