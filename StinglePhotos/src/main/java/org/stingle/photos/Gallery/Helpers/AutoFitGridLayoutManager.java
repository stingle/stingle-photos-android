package org.stingle.photos.Gallery.Helpers;

import android.content.Context;
import android.util.Log;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AutoFitGridLayoutManager extends GridLayoutManager {

	private int columnWidth;
	private boolean columnWidthChanged = true;
	protected int currentCalcSpanCount = 1;
	private boolean updateSpanCount = true;

	public AutoFitGridLayoutManager(Context context, int columnWidth) {
		super(context, 1);
		Log.d("columnWidth", String.valueOf(columnWidth));
		setColumnWidth(columnWidth);
	}

	public void setColumnWidth(int newColumnWidth) {
		if (newColumnWidth > 0 && newColumnWidth != columnWidth) {
			columnWidth = newColumnWidth;
			columnWidthChanged = true;
		}
	}

	public int getCurrentCalcSpanCount() {
		return currentCalcSpanCount;
	}

	public void updateAutoFit() {
		columnWidthChanged = true;
	}

	public void setUpdateSpanCount(boolean updateSpanCount) {
		this.updateSpanCount = updateSpanCount;
	}

	@Override
	public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
		if (columnWidthChanged && columnWidth > 0 && updateSpanCount) {
			int totalSpace;
			if (getOrientation() == RecyclerView.VERTICAL) {
				totalSpace = getWidth() - getPaddingRight() - getPaddingLeft();
			} else {
				totalSpace = getHeight() - getPaddingTop() - getPaddingBottom();
			}
			int spanCount = Math.max(1, totalSpace / columnWidth);
			Log.d("spanCount", spanCount + "");
			setSpanCount(spanCount);
			currentCalcSpanCount = spanCount;
			columnWidthChanged = false;
		}
		super.onLayoutChildren(recycler, state);
	}
}
