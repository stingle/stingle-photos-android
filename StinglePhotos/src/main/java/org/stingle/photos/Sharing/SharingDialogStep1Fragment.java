package org.stingle.photos.Sharing;


import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.GetContactAsyncTask;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;


public class SharingDialogStep1Fragment extends Fragment {

	private EditText searchField;
	private SharingContactsAdapter adapter;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.sharing_step1_fragment, container, false);

		RecyclerView contactsList = view.findViewById(R.id.contactsList);
		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
		contactsList.setLayoutManager(layoutManager);
		adapter = new SharingContactsAdapter(getContext());
		contactsList.setAdapter(adapter);

		view.findViewById(R.id.addButton).setOnClickListener(v -> addRecipient());

		searchField = view.findViewById(R.id.searchField);
		searchField.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_GO) {
				addRecipient();
				return true;
			}
			return false;
		});

		return view;
	}

	public ArrayList<StingleContact> getSelectedRecipients(){
		if(adapter != null){
			return adapter.getSelectedRecipients();
		}
		return null;
	}


	private void addRecipient() {
		String ownEmail = Helpers.getPreference(getContext(), StinglePhotosApplication.USER_EMAIL, "");
		String text = searchField.getText().toString();
		if (Helpers.isValidEmail(text)) {
			if (text.equals(ownEmail)) {
				Toast.makeText(getContext(), getContext().getString(R.string.cant_share_to_self), Toast.LENGTH_SHORT).show();
				return;
			}
			(new GetContactAsyncTask(getContext(), text, new OnAsyncTaskFinish() {
				@Override
				public void onFinish(Object contact) {
					super.onFinish(contact);
					if(!adapter.isAlreadyAdded((StingleContact) contact)) {
						adapter.addRecipient((StingleContact) contact);
					}
					else{
						adapter.addToSelection((StingleContact) contact);
						Toast.makeText(getContext(), getContext().getString(R.string.already_added), Toast.LENGTH_SHORT).show();
					}
					searchField.setText("");
				}

				@Override
				public void onFail() {
					super.onFail();
					Toast.makeText(getContext(), getContext().getString(R.string.not_on_stingle), Toast.LENGTH_LONG).show();
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		} else {
			Toast.makeText(getContext(), getContext().getString(R.string.invalid_email), Toast.LENGTH_LONG).show();
		}
	}
}
