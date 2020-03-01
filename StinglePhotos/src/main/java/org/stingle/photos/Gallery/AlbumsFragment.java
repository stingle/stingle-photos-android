package org.stingle.photos.Gallery;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.stingle.photos.Db.StingleDbFile;
import org.stingle.photos.R;
import org.stingle.photos.Util.Helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AlbumsFragment extends Fragment implements AlbumsAdapterPisasso.Listener{

	private DragSelectRecyclerView recyclerView;
	private AlbumsAdapterPisasso adapter;
	private AutoFitGridLayoutManager layoutManager;

	private int lastScrollPosition = 0;

	private AppCompatActivity parentActivity;


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.albums_fragment,	container, false);

		recyclerView = view.findViewById(R.id.albums_recycler_view);
		parentActivity = (AppCompatActivity)getActivity();

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);


		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);
		layoutManager = new AutoFitGridLayoutManager(getContext(), Helpers.getThumbSize(getContext(), 2));
		recyclerView.setLayoutManager(layoutManager);
		adapter = new AlbumsAdapterPisasso(getContext(), this, layoutManager);

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

	@Override
	public void onClick(int index) {
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
		//parentActivity.onSelectionChanged(count);
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

	public void clearSelected(){
		adapter.clearSelected();
		recyclerView.setDragSelectActive(false, 0);
	}

	public void updateAutoFit(){
		layoutManager.updateAutoFit();
	}

	public boolean isSelectionModeActive(){
		return adapter.isSelectionModeActive();
	}

	public ArrayList<StingleDbFile> getSelectedFiles(){
		List<Integer> indices = adapter.getSelectedIndices();
		ArrayList<StingleDbFile> files = new ArrayList<>();
//		for(Integer index : indices){
//			files.add(adapter.getStingleFileAtPosition(index));
//		}
		return files;
	}


}
