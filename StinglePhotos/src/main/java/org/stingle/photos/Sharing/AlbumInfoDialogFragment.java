package org.stingle.photos.Sharing;


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

import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.R;


public class AlbumInfoDialogFragment extends AppCompatDialogFragment {

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
		toolbar.setTitle(getString(R.string.album_info));

		albumNameText = view.findViewById(R.id.albumName);

		step2fragment = new SharingDialogStep2Fragment();
		step2fragment.setIsRO(true);
		step2fragment.hideAlbumName();

		if(albumId == null || albumId.length() == 0) {
			requireDialog().dismiss();
		}

		album = GalleryHelpers.getAlbum(requireContext(), albumId);
		albumName = GalleryHelpers.getAlbumName(album);

		if (!album.isShared || album.isOwner || album.permissionsObj == null) {
			requireDialog().dismiss();
		}


		step2fragment.setPermissions(album.permissionsObj);
		albumNameText.setText(albumName);
		membersFragment = new AlbumMembersFragment(album);
		membersFragment.setHideRemoveButtons(true);
		if(album.permissionsObj == null || !album.permissionsObj.allowShare) {
			membersFragment.setHideAddMember(true);
		}

		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		ft.replace(R.id.permissionsContainer, step2fragment);
		ft.replace(R.id.membersContainer, membersFragment);
		ft.commit();

		view.findViewById(R.id.unshare).setVisibility(View.GONE);

		return view;
	}



	public void setAlbumId(String albumId){
		this.albumId = albumId;
	}

}
