package org.stingle.photos.Gallery.Albums;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.stingle.photos.AsyncTasks.OnAsyncTaskFinish;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Gallery.Gallery.GalleryActions;
import org.stingle.photos.Gallery.Helpers.AutoFitGridLayoutManager;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

import java.util.Objects;

public class AlbumsFragment extends Fragment{

	public static int VIEW_ALBUMS = 0;
	public static int VIEW_SHARES = 1;
	public static int VIEW_SELECTOR = 2;

	private RecyclerView recyclerView;
	private AlbumsAdapterPisasso adapter;
	private LinearLayoutManager layoutManager;
	private LinearLayout noSharingHolder;
	private Integer view = VIEW_ALBUMS;;

	private int lastScrollPosition = 0;

	private GalleryActivity parentActivity;

	private AlbumsAdapterPisasso.Listener adapterListener = new AlbumsAdapterPisasso.Listener() {
		@Override
		public void onClick(int index, int type) {
			if(type == AlbumsAdapterPisasso.TYPE_ADD){
				GalleryActions.addAlbum(getContext(), new OnAsyncTaskFinish() {
					@Override
					public void onFinish(Object set) {
						super.onFinish();
						updateDataSet();
					}

					@Override
					public void onFail() {
						super.onFail();
						Helpers.showAlertDialog(getContext(), getString(R.string.error), getString(R.string.add_album_failed));
					}
				});
			}
			else{
				StingleDbAlbum album = adapter.getAlbumAtPosition(index);
				parentActivity.showAlbum(album.albumId);
			}
		}

		@Override
		public void onLongClick(int index, int type) {

		}

		@Override
		public void onSelectionChanged(int count) {

		}
	};


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_albums,	container, false);

		recyclerView = view.findViewById(R.id.recycler_view);
		noSharingHolder = view.findViewById(R.id.no_sharing_holder);
		parentActivity = (GalleryActivity)getActivity();

		return view;
	}


	@Override
	public void onViewCreated(@NonNull View viewArg, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
		super.onViewCreated(viewArg, savedInstanceState);

		Bundle bundle = getArguments();
		if (bundle != null) {
			view = bundle.getInt("view", VIEW_ALBUMS);
		}

		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);
		if(view == VIEW_ALBUMS) {
			layoutManager = new AutoFitGridLayoutManager(getContext(), Helpers.getScreenWidthByColumns(getContext(), 2));
		}
		else if(view == VIEW_SHARES){
			layoutManager = new LinearLayoutManager(getContext());
		}
		recyclerView.setLayoutManager(layoutManager);
		if(view == VIEW_ALBUMS) {
			adapter = new AlbumsAdapterPisasso(getContext(), layoutManager, view, true, false);
			adapter.setLayoutStyle(AlbumsAdapterPisasso.LAYOUT_GRID);
		}
		else if(view == VIEW_SHARES){
			adapter = new AlbumsAdapterPisasso(getContext(), layoutManager, view, false, false);
			adapter.setLayoutStyle(AlbumsAdapterPisasso.LAYOUT_LIST);
		}
		adapter.setListener(adapterListener);

		recyclerView.setAdapter(adapter);
		handleNoSharing();
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d("function", "onResume");
		if (adapter != null) {
			adapter.updateDataSet();
		}

		Log.d("scrollResume", String.valueOf(lastScrollPosition));
		if(view == VIEW_ALBUMS) {
			layoutManager.scrollToPosition(parentActivity.albumsLastScrollPos);
		}
		else if(view == VIEW_SHARES) {
			layoutManager.scrollToPosition(parentActivity.sharingLastScrollPos);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d("function", "onPause");
		if(view == VIEW_ALBUMS) {
			parentActivity.albumsLastScrollPos = layoutManager.findFirstVisibleItemPosition();
		}
		else if(view == VIEW_SHARES) {
			parentActivity.sharingLastScrollPos = layoutManager.findFirstVisibleItemPosition();
		}
		Log.d("scrollSave", String.valueOf(lastScrollPosition));
		recyclerView.setAdapter(null);
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		Log.d("function", "onAttach");
	}

	@Override
	public void onDetach() {
		super.onDetach();
		Log.d("function", "onDetach");
	}


	public void updateDataSet(){
		if(recyclerView != null && adapter != null) {
			int lastScrollPos = recyclerView.getScrollY();
			adapter.updateDataSet();
			recyclerView.setScrollY(lastScrollPos);
			handleNoSharing();
		}
	}

	private void handleNoSharing(){
		if(adapter.getItemCount() == 0){
			recyclerView.setVisibility(View.GONE);
			noSharingHolder.setVisibility(View.VISIBLE);
		}
		else{
			recyclerView.setVisibility(View.VISIBLE);
			noSharingHolder.setVisibility(View.GONE);
		}
	}

	public void updateItem(int position){
		adapter.updateItem(position);
	}

	public void scrollToTop(){
		lastScrollPosition = 0;
		recyclerView.scrollToPosition(0);
	}

	public void updateAutoFit(){
		if(view == VIEW_ALBUMS && layoutManager != null) {
			((AutoFitGridLayoutManager)layoutManager).updateAutoFit();
		}
	}

	public static Boolean getAlbumIsHiddenByView(Integer view){
		Boolean isHidden = null;
		if(view == AlbumsFragment.VIEW_ALBUMS){
			isHidden = false;
		}
		else if(view == AlbumsFragment.VIEW_SHARES){
			isHidden = true;
		}
		return isHidden;
	}

	public static Boolean getAlbumIsSharedByView(Integer view){
		Boolean isShared = null;
		if(view == AlbumsFragment.VIEW_SHARES){
			isShared = true;
		}
		return isShared;
	}

}
