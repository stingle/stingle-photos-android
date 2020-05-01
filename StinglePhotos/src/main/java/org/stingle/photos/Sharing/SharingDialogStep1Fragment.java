package org.stingle.photos.Sharing;


import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.chip.Chip;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.AsyncTasks.Sync.GetContactAsyncTask;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;


public class SharingDialogStep1Fragment extends Fragment {

	private EditText searchField;
	private FlexboxLayout chipsContainer;
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

		adapter.setRecipientSelectionChangeListener(new SharingContactsAdapter.RecipientSelectionChangeListener() {
			@Override
			public void onAdd(StingleContact contact) {
				addChip(contact);
			}

			@Override
			public void onRemove(StingleContact contact) {
				removeChip(contact);
			}
		});

		contactsList.setAdapter(adapter);

		view.findViewById(R.id.addButton).setOnClickListener(v -> addRecipient());

		chipsContainer = view.findViewById(R.id.chipsContainer);
		searchField = view.findViewById(R.id.searchField);
		searchField.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_GO) {
				addRecipient();
				return true;
			}
			return false;
		});
		searchField.addTextChangedListener(searchKeyListener());

		return view;
	}

	public ArrayList<StingleContact> getSelectedRecipients(){
		if(adapter != null){
			return adapter.getSelectedRecipients();
		}
		return null;
	}

	private TextWatcher searchKeyListener() {
		return new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				adapter.setFilter(s.toString());
			}
		};
	}

	public void addChip(StingleContact contact){
		Chip chip = new Chip(requireContext());
		chip.setText(contact.email);
		chip.setCloseIconVisible(true);
		chip.setCloseIcon(requireContext().getDrawable(R.drawable.ic_close));
		chip.setTag(contact.email);

		int margin = Helpers.convertDpToPixels(requireContext(), 4);
		FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,	ViewGroup.LayoutParams.WRAP_CONTENT);
		params.setMargins(margin, 0, margin, 0);
		chip.setLayoutParams(params);

		chip.setOnCloseIconClickListener(v1 -> {
			chipsContainer.removeView(chip);
			adapter.removeFromSelection(contact);
		});
		chipsContainer.addView(chip);
	}

	private void removeChip(StingleContact contact){
		Chip chip = chipsContainer.findViewWithTag(contact.email);
		if(chip != null) {
			chipsContainer.removeView(chip);
		}
	}

	public void addRecipient() {
		addRecipient(null);
	}

	public void addRecipient(OnAsyncTaskFinish onFinish) {
		String ownEmail = Helpers.getPreference(requireContext(), StinglePhotosApplication.USER_EMAIL, "");
		String text = searchField.getText().toString();

		if(text.length() == 0){
			if(onFinish != null){
				onFinish.onFinish();
			}
			return;
		}

		if (Helpers.isValidEmail(text)) {
			if (text.equals(ownEmail)) {
				Toast.makeText(getContext(), requireContext().getString(R.string.cant_share_to_self), Toast.LENGTH_SHORT).show();
				return;
			}
			(new GetContactAsyncTask(getContext(), text, new OnAsyncTaskFinish() {
				@Override
				public void onFinish(Object contact) {
					super.onFinish(contact);
					if(!adapter.isAlreadyAdded((StingleContact) contact)) {
						adapter.addRecipient((StingleContact) contact);
						if(onFinish != null){
							onFinish.onFinish();
						}
					}
					else{
						adapter.addToSelection((StingleContact) contact);
						Toast.makeText(getContext(), requireContext().getString(R.string.already_added), Toast.LENGTH_SHORT).show();
						if(onFinish != null){
							onFinish.onFail();
						}
					}
					searchField.setText("");
				}

				@Override
				public void onFail() {
					super.onFail();
					Toast.makeText(getContext(), requireContext().getString(R.string.not_on_stingle), Toast.LENGTH_LONG).show();
					if(onFinish != null){
						onFinish.onFail();
					}
				}
			})).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		} else {
			Toast.makeText(getContext(), requireContext().getString(R.string.invalid_email), Toast.LENGTH_LONG).show();
			if(onFinish != null){
				onFinish.onFail();
			}
		}
	}

}
