package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Gallery.Helpers.AutoFitGridLayoutManager;
import org.stingle.photos.Gallery.Helpers.DragSelectRecyclerView;
import org.stingle.photos.Gallery.Helpers.HidingScrollListener;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.ViewItemActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GalleryFragment extends Fragment implements GalleryAdapterPisasso.Listener{

	private DragSelectRecyclerView recyclerView;
	private GalleryAdapterPisasso adapter;
	private AutoFitGridLayoutManager layoutManager;

	private int lastScrollPosition = 0;

	private GalleryFragmentParent parentActivity;

	private int currentFolder = SyncManager.FOLDER_MAIN;
	private String folderId = null;


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.gallery_fragment,	container, false);

		recyclerView = view.findViewById(R.id.recycler_view);
		parentActivity = (GalleryFragmentParent)getActivity();

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Log.e("function", "onActivityCreated");
		Bundle bundle = getArguments();
		boolean initNow = false;
		if (bundle != null) {
			currentFolder = bundle.getInt("currentFolder", SyncManager.FOLDER_MAIN);
			folderId = bundle.getString("folderId");
			initNow = bundle.getBoolean("initNow", true);
		}

		if(!parentActivity.isSyncBarDisabled()){
			recyclerView.setPadding(recyclerView.getPaddingLeft(), (int) getResources().getDimension(R.dimen.gallery_top_padding_with_syncbar), recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
		}
		else{
			recyclerView.setPadding(recyclerView.getPaddingLeft(), (int) getResources().getDimension(R.dimen.gallery_top_padding_without_syncbar), recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
		}

		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);
		adapter = new GalleryAdapterPisasso(getContext(), this, layoutManager, currentFolder, folderId);
		layoutManager = new AutoFitGridLayoutManager(getContext(), Helpers.getThumbSize(getContext()));
		layoutManager.setSpanSizeLookup(new AutoFitGridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				if (adapter.getItemViewType(position) == GalleryAdapterPisasso.TYPE_DATE) {
					return layoutManager.getCurrentCalcSpanCount();
				}
				return 1;
			}
		});
		recyclerView.setLayoutManager(layoutManager);
		recyclerView.addOnScrollListener(new HidingScrollListener() {
			@Override
			public void onHide() {
				parentActivity.scrolledDown();
			}


			@Override
			public void onShow() {
				parentActivity.scrolledUp();
			}
		});

		if(savedInstanceState != null && savedInstanceState.containsKey("scroll")){
			lastScrollPosition = savedInstanceState.getInt("scroll");
		}

		if(initNow){
			init();
		}
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
		Log.d("lastScrollPosition", lastScrollPosition + "");
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
		recyclerView.setAdapter(null);
		adapter = null;
		Log.e("function", "onDetach");
	}

	public void init(){
		Log.e("function", "init");
		recyclerView.setAdapter(adapter);
	}

	@Override
	public void onClick(int index) {
		StingleDbFile file = adapter.getStingleFileAtPosition(index);
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
			intent.putExtra("EXTRA_ITEM_FOLDER_ID", folderId);
			startActivity(intent);
		}
	}

	@Override
	public void onLongClick(int index) {
		if(!parentActivity.onLongClick(index)){
			return;
		}

		recyclerView.setDragSelectActive(true, index);
		if(!adapter.isSelectionModeActive()){
			onSelectionChanged(1);
		}
		adapter.setSelectionModeActive(true);
	}


	@Override
	public void onSelectionChanged(int count) {
		parentActivity.onSelectionChanged(count);
	}

	public void updateDataSet(){
		int lastScrollPos = recyclerView.getScrollY();
		if(adapter != null) {
			adapter.updateDataSet();
		}
		if(recyclerView != null) {
			recyclerView.setScrollY(lastScrollPos);
		}
	}

	public void updateItem(int position){
		adapter.updateItem(position);
	}

	public void scrollToTop(){
		lastScrollPosition = 0;
		recyclerView.scrollToPosition(0);
	}

	public void clearSelected(){
		if(adapter != null) {
			adapter.clearSelected();
		}
		recyclerView.setDragSelectActive(false, 0);
	}

	public void updateAutoFit(){
		layoutManager.updateAutoFit();
	}

	public boolean isSelectionModeActive(){
		if(adapter != null) {
			return adapter.isSelectionModeActive();
		}
		return false;
	}

	public ArrayList<StingleDbFile> getSelectedFiles(){
		List<Integer> indices = adapter.getSelectedIndices();
		ArrayList<StingleDbFile> files = new ArrayList<>();
		for(Integer index : indices){
			files.add(adapter.getStingleFileAtPosition(index));
		}
		return files;
	}

}
