package org.stingle.photos.Editor.util;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class CenterOffsetItemDecoration extends RecyclerView.ItemDecoration {
	private int itemMargin;
	private boolean activeMode;

	public CenterOffsetItemDecoration(int itemMargin) {
		this.itemMargin = itemMargin;
	}

	@Override
	public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, RecyclerView parent,
							   @NonNull RecyclerView.State state) {
		int adapterPosition = parent.getChildAdapterPosition(view);
		int adapterItemCount = 0;

		RecyclerView.Adapter<?> adapter = parent.getAdapter();
		if (adapter != null) {
			adapterItemCount = adapter.getItemCount();
		}

		RecyclerView.LayoutManager parentLayoutManager = parent.getLayoutManager();

		if (parentLayoutManager != null) {
			int childWidth = getChildWidth(view, parentLayoutManager);

			if (adapterPosition == 0) {
				if (activeMode) {
					outRect.set((int) (parentLayoutManager.getWidth() / 2f - childWidth / 2f), 0, itemMargin, 0);
				} else {
					outRect.set(0, 0, itemMargin, 0);
				}
			} else if (adapterPosition == adapterItemCount - 1) {
				if (activeMode) {
					outRect.set(itemMargin, 0, (int) (parentLayoutManager.getWidth() / 2f - childWidth / 2f), 0);
				} else {
					outRect.set(itemMargin, 0, 0, 0);
				}
			} else {
				outRect.set(itemMargin, 0, itemMargin, 0);
			}
		}
	}

	private int getChildWidth(View view, RecyclerView.LayoutManager parentLayoutManager) {
		int childWidth = view.getWidth();
		if (childWidth == 0) {
			int widthSpec = View.MeasureSpec.makeMeasureSpec(parentLayoutManager.getWidth(), View.MeasureSpec.EXACTLY);
			int heightSpec = View.MeasureSpec.makeMeasureSpec(parentLayoutManager.getHeight(), View.MeasureSpec.UNSPECIFIED);
			int childWidthSpec = ViewGroup.getChildMeasureSpec(
					widthSpec, parentLayoutManager.getPaddingLeft() + parentLayoutManager.getPaddingRight(), view.getLayoutParams().width
			);
			int childHeightSpec = ViewGroup.getChildMeasureSpec(
					heightSpec, parentLayoutManager.getPaddingTop() + parentLayoutManager.getPaddingBottom(), view.getLayoutParams().height
			);
			view.measure(childWidthSpec, childHeightSpec);

			childWidth = view.getMeasuredWidth();
		}

		return childWidth;
	}

	public void setActiveMode(boolean activeMode) {
		this.activeMode = activeMode;
	}
}