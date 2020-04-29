package org.stingle.photos.Sharing;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Query.ContactsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.R;

import java.util.ArrayList;

public class SharingContactsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private Context context;
	private ContactsDb db;

	ArrayList<StingleContact> addedRecipients = new ArrayList<>();
	ArrayList<StingleContact> selectedRecipients = new ArrayList<>();

	private static int TYPE_DB = 0;
	private static int TYPE_ADDED = 1;

	public SharingContactsAdapter(Context context) {
		this.context = context;
		this.db = new ContactsDb(context);
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		db.close();
	}


	public class ContactVH extends RecyclerView.ViewHolder {
		// each data item is just a string in this case
		public View layout;
		public CheckBox checbox;
		public TextView label;
		public int type;

		public ContactVH(View v) {
			super(v);
			layout = v;
			checbox = v.findViewById(R.id.checkbox);
			label = v.findViewById(R.id.label);

			checbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
				if(isChecked){
					addToSelection(getAdapterPosition());
				}
				else{
					removeFromSelection(getAdapterPosition());
				}
			});
		}
	}

	public int translateDbPosToGalleryPos(int dbPos){
		return dbPos + addedRecipients.size();
	}
	public int translateGalleryPosToDbPos(int pos){
		return pos - addedRecipients.size();
	}


	// Create new views (invoked by the layout manager)
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.contact_item, parent, false);

		ContactVH vh = new ContactVH(v);
		vh.type = viewType;
		return vh;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {
		ContactVH holder = (ContactVH)holderObj;

		if(rawPosition < addedRecipients.size()){
			StingleContact contact = addedRecipients.get(rawPosition);
			holder.label.setText(contact.email);
			if(selectedRecipients.contains(contact)){
				holder.checbox.setChecked(true);
			}
		}
		else {
			final int dbPos = translateGalleryPosToDbPos(rawPosition);
			StingleContact contact = db.getContactAtPosition(dbPos, StingleDb.SORT_DESC);
			holder.label.setText(contact.email);
			if(selectedRecipients.contains(contact)){
				holder.checbox.setChecked(true);
			}
		}
	}

	private void addToSelection(int pos) {
		if(pos < addedRecipients.size()){
			selectedRecipients.add(addedRecipients.get(pos));
		}
		else{
			final int dbPos = translateGalleryPosToDbPos(pos);
			selectedRecipients.add(db.getContactAtPosition(dbPos, StingleDb.SORT_DESC));
		}
	}

	private void removeFromSelection(int pos) {
		if(pos < addedRecipients.size()){
			selectedRecipients.remove(addedRecipients.get(pos));
		}
		else{
			selectedRecipients.remove(db.getContactAtPosition(translateGalleryPosToDbPos(pos), StingleDb.SORT_DESC));
		}
	}

	@Override
	public int getItemViewType(int position) {
		if(position < addedRecipients.size()){
			return TYPE_ADDED;
		}
		else {
			return TYPE_DB;
		}
	}

	public boolean isAlreadyAdded(StingleContact contact){
		if(addedRecipients.contains(contact)){
			return true;
		}

		StingleContact dbContact = db.getContactByUserId(contact.userId);
		if(dbContact != null){
			return true;
		}

		return false;
	}

	public void addRecipient(StingleContact contact){
		if(!addedRecipients.contains(contact)) {
			addedRecipients.add(contact);
			selectedRecipients.add(contact);
		}
		notifyDataSetChanged();
	}

	public void addToSelection(StingleContact contact){
		if(!selectedRecipients.contains(contact)) {
			selectedRecipients.add(contact);
		}
		notifyDataSetChanged();
	}

	public ArrayList<StingleContact> getSelectedRecipients(){
		return selectedRecipients;
	}

	@Override
	public int getItemCount() {
		return (int)db.getTotalContactsCount() + addedRecipients.size();
	}

}

