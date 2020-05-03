package org.stingle.photos.Sharing;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.stingle.photos.R;


public class SharingDialogStep2Fragment extends Fragment {

	private EditText albumName;
	private Switch allowAdd;
	private Switch allowShare;
	private Switch allowCopy;

	private PermissionChangeListener changeListener;

	private String albumNameStr = "";
	private SharingPermissions permissions = new SharingPermissions();
	private boolean disableAlbumName = false;
	private boolean hideAlbumName = false;
	private boolean hidePermissionsTitle = false;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.sharing_step2_fragment, container, false);

		albumName = view.findViewById(R.id.albumName);
		allowAdd = view.findViewById(R.id.allow_add);
		allowShare = view.findViewById(R.id.allow_share);
		allowCopy = view.findViewById(R.id.allow_copy);

		updatePermissionsView();

		albumName.setText(albumNameStr);

		if(disableAlbumName) {
			albumName.setEnabled(false);
		}
		if(hideAlbumName) {
			albumName.setVisibility(View.GONE);
			view.findViewById(R.id.albumNameTitle).setVisibility(View.GONE);
		}
		if(hidePermissionsTitle) {
			view.findViewById(R.id.permissionsTitle).setVisibility(View.GONE);
		}

		view.findViewById(R.id.allow_add_text).setOnClickListener(v -> allowAdd.toggle());
		view.findViewById(R.id.allow_add_text_desc).setOnClickListener(v -> allowAdd.toggle());
		view.findViewById(R.id.allow_share_text).setOnClickListener(v -> allowShare.toggle());
		view.findViewById(R.id.allow_share_text_desc).setOnClickListener(v -> allowShare.toggle());
		view.findViewById(R.id.allow_copy_text).setOnClickListener(v -> allowCopy.toggle());
		view.findViewById(R.id.allow_copy_text_desc).setOnClickListener(v -> allowCopy.toggle());

		if(changeListener != null){
			allowAdd.setOnClickListener(v -> changeListener.onChange());
			allowShare.setOnClickListener(v -> changeListener.onChange());
			allowCopy.setOnClickListener(v -> changeListener.onChange());
		}

		return view;
	}

	public SharingPermissions getPermissions(){
		SharingPermissions permissions = new SharingPermissions();
		permissions.allowAdd = allowAdd.isChecked();
		permissions.allowShare = allowShare.isChecked();
		permissions.allowCopy = allowCopy.isChecked();

		return  permissions;
	}

	public void setPermissions(SharingPermissions permissions){
		this.permissions = permissions;
		updatePermissionsView();
	}

	public void setChangeListener(PermissionChangeListener changeListener){
		this.changeListener = changeListener;
	}

	public String getAlbumName(){
		return albumName.getText().toString();
	}

	public void setAlbumName(String name){
		this.albumNameStr = name;
	}

	public void disableAlbumName(){
		disableAlbumName = true;
	}
	public void hideAlbumName(){
		hideAlbumName = true;
	}
	public void hidePermissionsTitle(){
		hidePermissionsTitle = true;
	}

	private void updatePermissionsView(){
		if(allowAdd != null && allowShare != null && allowCopy != null) {
			allowAdd.setChecked(permissions.allowAdd);
			allowShare.setChecked(permissions.allowShare);
			allowCopy.setChecked(permissions.allowCopy);
		}
	}

	public abstract static class PermissionChangeListener{
		public abstract void onChange();
	}
}
