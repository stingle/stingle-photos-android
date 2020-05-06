package org.stingle.photos.Sharing;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.stingle.photos.R;


public class SharingDialogStep2Fragment extends Fragment {

	private EditText albumName;
	private Switch allowAdd;
	private Switch allowShare;
	private Switch allowCopy;

	private TextView allowAddRO;
	private TextView allowShareRO;
	private TextView allowCopyRO;

	private PermissionChangeListener changeListener;

	private String albumNameStr = "";
	private SharingPermissions permissions = new SharingPermissions();
	private boolean disableAlbumName = false;
	private boolean hideAlbumName = false;
	private boolean hidePermissionsTitle = false;
	private boolean isRO = false;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.sharing_step2_fragment, container, false);

		albumName = view.findViewById(R.id.albumName);
		allowAdd = view.findViewById(R.id.allow_add);
		allowShare = view.findViewById(R.id.allow_share);
		allowCopy = view.findViewById(R.id.allow_copy);

		allowAddRO = view.findViewById(R.id.allow_add_ro);
		allowShareRO = view.findViewById(R.id.allow_share_ro);
		allowCopyRO = view.findViewById(R.id.allow_copy_ro);

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

		if(!isRO && changeListener != null){
			allowAdd.setOnClickListener(v -> changeListener.onChange());
			allowShare.setOnClickListener(v -> changeListener.onChange());
			allowCopy.setOnClickListener(v -> changeListener.onChange());
		}

		if(isRO){
			allowAdd.setVisibility(View.GONE);
			allowShare.setVisibility(View.GONE);
			allowCopy.setVisibility(View.GONE);

			allowAddRO.setVisibility(View.VISIBLE);
			allowShareRO.setVisibility(View.VISIBLE);
			allowCopyRO.setVisibility(View.VISIBLE);
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

	public void setIsRO(boolean ro){
		this.isRO = ro;
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
		if(!isRO) {
			if (allowAdd != null && allowShare != null && allowCopy != null) {
				allowAdd.setChecked(permissions.allowAdd);
				allowShare.setChecked(permissions.allowShare);
				allowCopy.setChecked(permissions.allowCopy);
			}
		}
		else{
			if (allowAddRO != null && allowShareRO != null && allowCopyRO != null) {
				allowAddRO.setText((permissions.allowAdd ? requireContext().getString(R.string.yes) : requireContext().getString(R.string.no)));
				allowShareRO.setText((permissions.allowShare ? requireContext().getString(R.string.yes) : requireContext().getString(R.string.no)));
				allowCopyRO.setText((permissions.allowCopy ? requireContext().getString(R.string.yes) : requireContext().getString(R.string.no)));
			}
		}
	}

	public abstract static class PermissionChangeListener{
		public abstract void onChange();
	}
}
