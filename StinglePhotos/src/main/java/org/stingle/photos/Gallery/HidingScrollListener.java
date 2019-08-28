package org.stingle.photos.Gallery;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public abstract class HidingScrollListener extends RecyclerView.OnScrollListener {

	private boolean mControlsVisible = true;

	@Override
	public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
		super.onScrolled(recyclerView, dx, dy);

		int firstVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();

		if (firstVisibleItem == 0) {
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