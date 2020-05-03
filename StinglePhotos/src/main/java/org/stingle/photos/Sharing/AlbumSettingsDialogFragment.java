package org.stingle.photos.Sharing;


import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;

import org.stingle.photos.AsyncTasks.Gallery.EditAlbumPermissionsAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;


public class AlbumSettingsDialogFragment extends AppCompatDialogFragment {

	private SharingDialogStep2Fragment step2fragment;
	private String albumId;
	private StingleDbAlbum album;
	private AlbumMembersFragment membersFragment;
	private TextView albumName;


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

		albumName = view.findViewById(R.id.albumName);

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
		Crypto crypto = StinglePhotosApplication.getCrypto();
		Crypto.AlbumData albumData = null;
		try {
			albumData = crypto.parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);
		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}

		if (albumData != null && album.isShared && album.permissionsObj != null) {
			step2fragment.setPermissions(album.permissionsObj);
			albumName.setText(albumData.name);
			membersFragment = new AlbumMembersFragment(album);

			FragmentTransaction ft = getChildFragmentManager().beginTransaction();
			ft.replace(R.id.permissionsContainer, step2fragment);
			ft.replace(R.id.membersContainer, membersFragment);
			ft.commit();
		}

		return view;
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
