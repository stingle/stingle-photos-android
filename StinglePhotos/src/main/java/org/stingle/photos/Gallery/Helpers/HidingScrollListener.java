package org.stingle.photos.Gallery.Helpers;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class HidingScrollListener extends RecyclerView.OnScrollListener {

	private boolean mControlsVisible = true;

	@Override
	public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
		super.onScrolled(recyclerView, dx, dy);

		int firstVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();

		if (firstVisibleItem < 6) {
			if(!mControlsVisible) {
				onShow();
				mControlsVisible = true;
			}
		} else {
			if(mControlsVisible) {
				onHide();
				mControlsVisible = false;
			}
		}
	}

	public abstract void onHide();
	public abstract void onShow();
}