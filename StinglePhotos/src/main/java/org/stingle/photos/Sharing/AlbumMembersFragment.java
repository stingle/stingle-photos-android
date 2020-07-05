package org.stingle.photos.Sharing;


import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.AsyncTasks.Gallery.RemoveMemberAsyncTask;
import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;


public class AlbumMembersFragment extends Fragment {

	private MembersAdapter adapter;
	private StingleDbAlbum album;
	private boolean hideRemoveButtons = false;
	private boolean hideAddMember = false;

	public AlbumMembersFragment(StingleDbAlbum album){
		this.album = album;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.fragment_album_members, container, false);

		RecyclerView membersList = view.findViewById(R.id.membersList);
		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
		membersList.setLayoutManager(layoutManager);
		adapter = new MembersAdapter(getContext(), album.albumId);
		adapter.setUnshareListener(unshare());
		adapter.setHideUnshareButtons(hideRemoveButtons);
		membersList.setAdapter(adapter);

		if(album.isOwner || (album.permissionsObj != null && album.permissionsObj.allowShare)) {
			view.findViewById(R.id.addMemberContainer).setOnClickListener(addMembers());
		}
		else{
			view.findViewById(R.id.addMemberContainer).setVisibility(View.GONE);
		}

		if(hideAddMember){
			view.findViewById(R.id.addMemberContainer).setVisibility(View.GONE);
		}

		return view;
	}

	public void setHideRemoveButtons(boolean hideRemoveButtons) {
		this.hideRemoveButtons = hideRemoveButtons;
	}
	public void setHideAddMember(boolean hideAddMember) {
		this.hideAddMember = hideAddMember;
	}

	private View.OnClickListener addMembers() {
		return v -> {
			GalleryActions.shareStingle((AppCompatActivity) requireActivity(), SyncManager.ALBUM, album.albumId, null, null, true, new OnAsyncTaskFinish() {
				@Override
				public void onFinish() {
					super.onFinish();
					adapter.initMembersList();
					adapter.notifyDataSetChanged();
				}
			});
		};
	}

	private MembersAdapter.UnshareListener unshare() {
		return new MembersAdapter.UnshareListener() {
			@Override
			public void onUnshare(StingleContact contact) {
				Helpers.showConfirmDialog(
						requireContext(),
						requireContext().getString(R.string.remove_member_question),
						requireContext().getString(R.string.unshare_member, contact.email),
						null,
						new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog spinner = Helpers.showProgressDialog(getActivity(), requireContext().getString(R.string.spinner_saving), null);
						RemoveMemberAsyncTask task = new RemoveMemberAsyncTask(requireContext(), new OnAsyncTaskFinish() {
							@Override
							public void onFinish() {
								super.onFinish();
								spinner.dismiss();
								Toast.makeText(requireContext(), R.string.save_success, Toast.LENGTH_LONG).show();
								adapter.initMembersList();
								adapter.notifyDataSetChanged();
							}

							@Override
							public void onFail() {
								super.onFail();
								spinner.dismiss();
								Toast.makeText(requireContext(), R.string.something_went_wrong, Toast.LENGTH_LONG).show();
								adapter.initMembersList();
								adapter.notifyDataSetChanged();
							}
						});
						task.setAlbumId(album.albumId);
						task.setMember(contact);
						task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
				}, null);
			}
		};
	}

}
