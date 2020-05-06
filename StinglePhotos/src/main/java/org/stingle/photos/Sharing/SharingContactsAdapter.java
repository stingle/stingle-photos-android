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
	ArrayList<String> excludedIds = new ArrayList<>();

	private static int TYPE_DB = 0;
	private static int TYPE_ADDED = 1;

	private String filter = "";

	private RecipientSelectionChangeListener changeListener;

	public SharingContactsAdapter(Context context) {
		this.context = context;
		this.db = new ContactsDb(context);
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		db.close();
	}

	public void setFilter(String str){
		filter = str;
		notifyDataSetChanged();
	}

	public void setRecipientSelectionChangeListener(RecipientSelectionChangeListener changeListener){
		this.changeListener = changeListener;
	}

	public void setExcludedIds(ArrayList<String> excludedIds){
		this.excludedIds = excludedIds;
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

			checbox.setOnClickListener(checkbox -> {
				if(((CheckBox)checkbox).isChecked()){
					addToSelection(getAdapterPosition());
				}
				else{
					removeFromSelection(getAdapterPosition());
				}
			});
		}
	}

	public int translateGalleryPosToDbPos(int pos){
		if(filter.length() == 0){
			return pos - addedRecipients.size();
		}
		return pos;
	}


	// Create new views (invoked by the layout manager)
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_contact, parent, false);

		ContactVH vh = new ContactVH(v);
		vh.type = viewType;
		return vh;
	}

	// Replace the contents of a view (invoked by the layout manager)
	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holderObj, final int rawPosition) {
		ContactVH holder = (ContactVH)holderObj;

		if(filter.length() == 0 && rawPosition < addedRecipients.size()){
			StingleContact contact = addedRecipients.get(rawPosition);
			holder.label.setText(contact.email);
			if(selectedRecipients.contains(contact)){
				holder.checbox.setChecked(true);
			}
			else{
				holder.checbox.setChecked(false);
			}
		}
		else {
			final int dbPos = translateGalleryPosToDbPos(rawPosition);
			StingleContact contact = db.getContactAtPosition(dbPos, StingleDb.SORT_DESC, filter, excludedIds);
			holder.label.setText(contact.email);
			if(selectedRecipients.contains(contact)){
				holder.checbox.setChecked(true);
			}
			else{
				holder.checbox.setChecked(false);
			}
		}
	}

	private void addToSelection(int pos) {
		StingleContact recp;
		if(filter.length() == 0 && pos < addedRecipients.size()){
			recp = addedRecipients.get(pos);
		}
		else{
			recp = db.getContactAtPosition(translateGalleryPosToDbPos(pos), StingleDb.SORT_DESC, filter, excludedIds);
		}

		if(recp != null) {
			selectedRecipients.add(recp);
			if(changeListener != null){
				changeListener.onAdd(recp);
			}
		}
	}

	public void addToSelection(StingleContact contact){
		if(!selectedRecipients.contains(contact)) {
			selectedRecipients.add(contact);
			if(changeListener != null){
				changeListener.onAdd(contact);
			}
		}
		notifyDataSetChanged();
	}

	private void removeFromSelection(int pos) {
		StingleContact recp;
		if(filter.length() == 0 && pos < addedRecipients.size()){
			recp = addedRecipients.get(pos);
		}
		else{
			recp = db.getContactAtPosition(translateGalleryPosToDbPos(pos), StingleDb.SORT_DESC, filter, excludedIds);
		}

		if(recp != null) {
			selectedRecipients.remove(recp);
			if(changeListener != null){
				changeListener.onRemove(recp);
			}
		}
	}

	public void removeFromSelection(StingleContact contact) {
		selectedRecipients.remove(contact);
		notifyDataSetChanged();
	}

	@Override
	public int getItemViewType(int position) {
		if(filter.length() == 0 && position < addedRecipients.size()){
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
			addToSelection(contact);
		}
		notifyDataSetChanged();
	}



	public ArrayList<StingleContact> getSelectedRecipients(){
		return selectedRecipients;
	}

	@Override
	public int getItemCount() {
		if(filter.length() == 0){
			return (int)db.getTotalContactsCount(filter, excludedIds) + addedRecipients.size();
		}
		return (int)db.getTotalContactsCount(filter, excludedIds);
	}

	public abstract static class RecipientSelectionChangeListener {
		public abstract void onAdd(StingleContact contact);
		public abstract void onRemove(StingleContact contact);
	}

}

