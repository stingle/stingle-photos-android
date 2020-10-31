package org.stingle.photos.Gallery.Gallery;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

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
	private LinearLayout noPhotosHolder;

	private int lastScrollPosition = 0;

	private GalleryFragmentParent parentActivity;

	private int currentSet = SyncManager.GALLERY;
	private String albumId = null;


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_gallery,	container, false);

		recyclerView = view.findViewById(R.id.recycler_view);
		noPhotosHolder = view.findViewById(R.id.no_photos_holder);
		parentActivity = (GalleryFragmentParent)getActivity();

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Log.e("GalleryFragment", "onActivityCreated");
		Bundle bundle = getArguments();
		boolean initNow = false;
		if (bundle != null) {
			currentSet = bundle.getInt("set", SyncManager.GALLERY);
			albumId = bundle.getString("albumId");
			initNow = bundle.getBoolean("initNow", true);
		}

		if(currentSet == SyncManager.TRASH){
			((TextView)noPhotosHolder.findViewById(R.id.no_photos_text)).setText(R.string.no_photos_trash);
			((TextView)noPhotosHolder.findViewById(R.id.no_photos_text_desc)).setText(R.string.no_photos_trash_desc);
		}

		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);
		adapter = new GalleryAdapterPisasso(getContext(), this, layoutManager, currentSet, albumId);
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
		Log.e("GalleryFragment", "onResume");
		if (adapter != null) {
			adapter.updateDataSet();
			handleNoPhotos();
		}
		Log.d("lastScrollPosition", lastScrollPosition + "");
		layoutManager.scrollToPosition(lastScrollPosition);

		if(!parentActivity.isSyncBarDisabled()){
			recyclerView.setPadding(recyclerView.getPaddingLeft(), (int) getResources().getDimension(R.dimen.gallery_top_padding_with_syncbar), recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
		}
		else{
			recyclerView.setPadding(recyclerView.getPaddingLeft(), (int) getResources().getDimension(R.dimen.gallery_top_padding_without_syncbar), recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.e("GalleryFragment", "onPause");
		lastScrollPosition = layoutManager.findFirstVisibleItemPosition();
		recyclerView.setAdapter(null);
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		Log.e("GalleryFragment", "onAttach");
	}

	@Override
	public void onDetach() {
		super.onDetach();
		recyclerView.setAdapter(null);
		adapter = null;
		Log.e("GalleryFragment", "onDetach");
	}

	public void init(){
		Log.e("GalleryFragment", "init");
		recyclerView.setAdapter(adapter);
		handleNoPhotos();
	}

	private void handleNoPhotos(){
		if(adapter.getItemCount() == 0){
			recyclerView.setVisibility(View.GONE);
			noPhotosHolder.setVisibility(View.VISIBLE);
		}
		else{
			recyclerView.setVisibility(View.VISIBLE);
			noPhotosHolder.setVisibility(View.GONE);
		}
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
			intent.putExtra("EXTRA_ITEM_SET", currentSet);
			intent.putExtra("EXTRA_ITEM_ALBUM_ID", albumId);
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
		if(recyclerView == null){
			return;
		}
		int lastScrollPos = recyclerView.getScrollY();
		if(adapter != null) {
			adapter.updateDataSet();
			handleNoPhotos();
		}
		if(recyclerView != null) {
			recyclerView.setScrollY(lastScrollPos);
		}
	}

	public void updateItem(int position){
		if(adapter != null) {
			adapter.updateItem(position);
		}
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
		if(layoutManager != null) {
			layoutManager.updateAutoFit();
		}
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

	public int getFirstVisibleItemNumber(){
		if(layoutManager != null) {
			return layoutManager.findFirstVisibleItemPosition();
		}
		return 0;
	}

}
