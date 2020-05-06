package org.stingle.photos.Sharing;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.ContactsDb;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;

public class MembersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private Context context;
	private ContactsDb db;
	private String albumId;
	private ArrayList<Long> memberIds = new ArrayList<>();

	private UnshareListener unshareListener;
	private boolean hideUnshareButtons = false;

	public MembersAdapter(Context context, String albumId) {
		this.context = context;
		this.db = new ContactsDb(context);
		this.albumId = albumId;
		initMembersList();
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		db.close();
	}

	public void setHideUnshareButtons(boolean hideUnshareButtons) {
		this.hideUnshareButtons = hideUnshareButtons;
	}

	public void initMembersList(){
		String myUserId = Helpers.getPreference(context, StinglePhotosApplication.USER_ID, "");

		AlbumsDb adb = new AlbumsDb(context);
		StingleDbAlbum album = adb.getAlbumById(albumId);
		db.close();

		memberIds.clear();
		for(String userId : album.members){
			if(!userId.equals(myUserId)){
				memberIds.add(Long.parseLong(userId));
			}
		}
	}

	public void setUnshareListener(UnshareListener unshareListener){
		this.unshareListener = unshareListener;
	}


	public class MemberVH extends RecyclerView.ViewHolder {
		public View layout;
		public TextView label;
		public Button unshare;

		public MemberVH(View v) {
			super(v);
			layout = v;
			unshare = v.findViewById(R.id.unshare);
			label = v.findViewById(R.id.label);

			unshare.setOnClickListener(checkbox -> {
				if(unshareListener != null){
					unshareListener.onUnshare(getContactAtPosition(getAdapterPosition()));
				}
			});
		}
	}

	private StingleContact getContactAtPosition(int position){
		return db.getContactByUserId(memberIds.get(position));
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.members_list_item, parent, false);

		return new MemberVH(v);
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, int position) {
		MemberVH holder = (MemberVH)holderObj;

		StingleContact contact = getContactAtPosition(position);
		if(contact != null) {
			holder.label.setText(contact.email);
		}
		if(hideUnshareButtons){
			holder.unshare.setVisibility(View.GONE);
		}
	}



	@Override
	public int getItemViewType(int position) {
		return 0;
	}

	@Override
	public int getItemCount() {
		return (int)memberIds.size();
	}

	public abstract static class UnshareListener {
		public abstract void onUnshare(StingleContact contact);
	}

}

