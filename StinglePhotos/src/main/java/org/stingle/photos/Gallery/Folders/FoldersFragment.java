package org.stingle.photos.Gallery.Folders;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleDbFolder;
import org.stingle.photos.Gallery.Helpers.AutoFitGridLayoutManager;
import org.stingle.photos.Gallery.Helpers.GalleryHelpers;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.util.Objects;

public class FoldersFragment extends Fragment{

	private RecyclerView recyclerView;
	private FoldersAdapterPisasso adapter;
	private AutoFitGridLayoutManager layoutManager;

	private int lastScrollPosition = 0;

	private GalleryActivity parentActivity;

	private FoldersAdapterPisasso.Listener adaptorListener = new FoldersAdapterPisasso.Listener() {
		@Override
		public void onClick(int index) {
			if(index == 0){
				GalleryHelpers.addFolder(getContext(), new OnAsyncTaskFinish() {
					@Override
					public void onFinish(Object folder) {
						super.onFinish();
						updateDataSet();
					}

					@Override
					public void onFail() {
						super.onFail();
						Helpers.showAlertDialog(getContext(), getString(R.string.add_album_failed));
					}
				});
			}
			else{
				StingleDbFolder folder = adapter.getFolderAtPosition(index);
				String folderName = "";
				try {
					Crypto.FolderData folderData = StinglePhotosApplication.getCrypto().parseFolderData(folder.data);
					folderName = folderData.name;
					folderData = null;
				} catch (IOException | CryptoException e) {
					e.printStackTrace();
				}
				parentActivity.showFolder(folder.folderId, folderName);
			}
		/*StingleDbFile file = adapter.getStingleFileAtPosition(index);
		if(!parentActivity.onClick(file)){
			return;
		}

		if (adapter.isSelectionModeActive()){
			adapter.toggleSelected(index);
		}
		else {
			Intent intent = new Intent();
			intent.setClass(getContext(), ViewItemActivity.class);
			intent.putExtra("EXTRA_ITEM_POSITION", adapter.getDbPositionFromRaw(index));
			intent.putExtra("EXTRA_ITEM_FOLDER", currentFolder);
			startActivity(intent);
		}*/
		}

		@Override
		public void onLongClick(int index) {

		}

		@Override
		public void onSelectionChanged(int count) {

		}
	};


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.folders_fragment,	container, false);

		recyclerView = view.findViewById(R.id.recycler_view);
		parentActivity = (GalleryActivity)getActivity();

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);


		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);
		layoutManager = new AutoFitGridLayoutManager(getContext(), Helpers.getThumbSize(getContext(), 2));
		recyclerView.setLayoutManager(layoutManager);
		adapter = new FoldersAdapterPisasso(getContext(), layoutManager, false);
		adapter.setListener(adaptorListener);

		if(savedInstanceState != null && savedInstanceState.containsKey("scroll")){
			lastScrollPosition = savedInstanceState.getInt("scroll");
		}

		recyclerView.setAdapter(adapter);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt("scroll", layoutManager.findFirstVisibleItemPosition());
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.e("function", "onResume");
		if (adapter != null) {
			adapter.updateDataSet();
		}

		layoutManager.scrollToPosition(lastScrollPosition);
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.e("function", "onPause");
		lastScrollPosition = layoutManager.findFirstVisibleItemPosition();
		recyclerView.setAdapter(null);
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		Log.e("function", "onAttach");
	}

	@Override
	public void onDetach() {
		super.onDetach();
		Log.e("function", "onDetach");
	}

	public void updateDataSet(){
		int lastScrollPos = recyclerView.getScrollY();
		adapter.updateDataSet();
		recyclerView.setScrollY(lastScrollPos);
	}

	public void updateItem(int position){
		adapter.updateItem(position);
	}

	public void scrollToTop(){
		lastScrollPosition = 0;
		recyclerView.scrollToPosition(0);
	}

	public void updateAutoFit(){
		layoutManager.updateAutoFit();
	}

}
