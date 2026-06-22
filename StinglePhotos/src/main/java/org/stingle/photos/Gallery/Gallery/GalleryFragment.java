package org.stingle.photos.Gallery.Gallery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Gallery.Helpers.AutoFitGridLayoutManager;
import org.stingle.photos.Gallery.Helpers.DragSelectRecyclerView;
import org.stingle.photos.Gallery.Helpers.HidingScrollListener;
import org.stingle.photos.GalleryActivity;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.ViewItemActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GalleryFragment extends Fragment implements GalleryAdapterPisasso.Listener {

	private DragSelectRecyclerView recyclerView;
	private GalleryAdapterPisasso adapter;
	private AutoFitGridLayoutManager layoutManager;
	private LinearLayout noPhotosHolder;

	private int lastScrollPosition = 0;
	private int lastScrollOffset = 0;

	private GalleryFragmentParent parentActivity;

	private int currentSet = SyncManager.GALLERY;
	private String albumId = null;
	private View scrollBarWithTooltip;
	private boolean isScrollBarDragging = false;
	private int lastScrollbarTargetPosition = -1;
	// Drag tracking for the scrollbar thumb: we move the thumb by the exact finger delta from
	// where it was grabbed, so the grabbed point stays under the finger (no jump-to-center).
	private float dragStartRawY = 0;
	private float dragStartThumbTop = 0;
	private ImageView scrollbarThumb;
	final Handler handler = new Handler();


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_gallery, container, false);

		recyclerView = view.findViewById(R.id.recycler_view);
		// Edge-to-edge: extend the grid's bottom padding by the navigation-bar inset so
		// the last row clears the (also inset-lifted) bottom navigation bar.
		Helpers.applyBottomInsetPadding(recyclerView);
		noPhotosHolder = view.findViewById(R.id.no_photos_holder);
		parentActivity = (GalleryFragmentParent) getActivity();
		scrollBarWithTooltip = view.findViewById(R.id.scrollbar_with_tooltip);
		scrollbarThumb = view.findViewById(R.id.scrollbar_thumb);
		setupDraggableScrollbar(view);

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		Log.d("GalleryFragment", "onActivityCreated");
		Bundle bundle = getArguments();
		boolean initNow = false;
		if (bundle != null) {
			currentSet = bundle.getInt("set", SyncManager.GALLERY);
			albumId = bundle.getString("albumId");
			initNow = bundle.getBoolean("initNow", true);
		}

		if (currentSet == SyncManager.TRASH) {
			((TextView) noPhotosHolder.findViewById(R.id.no_photos_text)).setText(R.string.no_photos_trash);
			((TextView) noPhotosHolder.findViewById(R.id.no_photos_text_desc)).setText(R.string.no_photos_trash_desc);
		}

		((SimpleItemAnimator) Objects.requireNonNull(recyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
		recyclerView.setHasFixedSize(true);


		adapter = new GalleryAdapterPisasso(getContext(), this, layoutManager, currentSet, albumId);
		layoutManager = new AutoFitGridLayoutManager(getContext(), Helpers.getScreenWidthByColumns(getContext()));
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
		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				super.onScrollStateChanged(recyclerView, newState);
				if(newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING){
					scrollBarWithTooltip.setVisibility(View.VISIBLE);
				}
				else {
					handler.removeCallbacksAndMessages(null);
					handler.postDelayed(() -> {
						if (!isScrollBarDragging) {
							scrollBarWithTooltip.setVisibility(View.GONE);
						}
					}, 2000);

				}
				updateScrollTabPosition();
			}
		});

		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				setScrollbarThumbPosition();
			}
		});

		if (savedInstanceState != null && savedInstanceState.containsKey("scroll")) {
			lastScrollPosition = savedInstanceState.getInt("scroll");
		}

		if (initNow) {
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
		Log.d("GalleryFragment", "onResume");
		if (adapter != null) {
			adapter.updateDataSet();
			handleNoPhotos();
		}
		Log.d("lastScrollPosition", lastScrollPosition + "");
		// Restore the exact scroll position INCLUDING the pixel offset of the first visible
		// row. scrollToPosition() alone snaps that row flush to the top, dropping the partial
		// offset and visibly shifting the grid down when returning from the photo viewer.
		layoutManager.scrollToPositionWithOffset(lastScrollPosition, lastScrollOffset);
		setScrollbarThumbPosition();

		if (recyclerView != null) {
			recyclerView.setPadding(recyclerView.getPaddingLeft(), (int) getResources().getDimension(R.dimen.gallery_top_padding_without_syncbar), recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
		}

	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d("GalleryFragment", "onPause");
		lastScrollPosition = layoutManager.findFirstVisibleItemPosition();
		// Remember how far the first visible row is scrolled past the top so onResume can
		// restore the exact position (see scrollToPositionWithOffset there).
		View firstChild = layoutManager.findViewByPosition(lastScrollPosition);
		lastScrollOffset = (firstChild != null && recyclerView != null)
				? firstChild.getTop() - recyclerView.getPaddingTop() : 0;
		if (recyclerView != null) {
			recyclerView.setAdapter(null);
		}
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		Log.d("GalleryFragment", "onAttach");
	}

	@Override
	public void onDetach() {
		super.onDetach();
		if (recyclerView != null) {
			recyclerView.setAdapter(null);
		}
		adapter = null;
		Log.d("GalleryFragment", "onDetach");
	}

	public void init() {
		Log.d("GalleryFragment", "init");
		if (recyclerView != null) {
			recyclerView.setAdapter(adapter);
		}
		handleNoPhotos();
	}

	private void handleNoPhotos() {
		if (recyclerView != null) {
			if (adapter.getItemCount() == 0) {
				recyclerView.setVisibility(View.GONE);
				noPhotosHolder.setVisibility(View.VISIBLE);
			} else {
				recyclerView.setVisibility(View.VISIBLE);
				noPhotosHolder.setVisibility(View.GONE);
			}
		}
	}

	@Override
	public void onClick(int index) {
		StingleDbFile file = adapter.getStingleFileAtPosition(index);
		if (!parentActivity.onClick(file)) {
			return;
		}

		if (adapter.isSelectionModeActive()) {
			adapter.toggleSelected(index);
		} else {
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
		if (!parentActivity.onLongClick(index)) {
			return;
		}

		if (recyclerView != null) {
			recyclerView.setDragSelectActive(true, index);
		}
		if (!adapter.isSelectionModeActive()) {
			onSelectionChanged(1);
		}
		adapter.setSelectionModeActive(true);
	}


	@Override
	public void onSelectionChanged(int count) {
		parentActivity.onSelectionChanged(count);
	}

	public void updateDataSet() {
		if (recyclerView == null) {
			return;
		}
		// Preserve the scroll position across the adapter's notifyDataSetChanged. NOTE:
		// recyclerView.getScrollY()/setScrollY() are no-ops on a RecyclerView (it scrolls via
		// internal offsets, not the View scroll position), so the old code did NOT preserve
		// position — every sync/refresh tick let the grid re-anchor and visibly shift. Capture
		// the first visible item + its pixel offset and restore it explicitly.
		int firstPos = -1;
		int offset = 0;
		if (layoutManager != null) {
			firstPos = layoutManager.findFirstVisibleItemPosition();
			View firstChild = (firstPos >= 0) ? layoutManager.findViewByPosition(firstPos) : null;
			if (firstChild != null) {
				offset = firstChild.getTop() - recyclerView.getPaddingTop();
			}
		}
		if (adapter != null) {
			adapter.updateDataSet();
			handleNoPhotos();
		}
		if (layoutManager != null && firstPos >= 0) {
			layoutManager.scrollToPositionWithOffset(firstPos, offset);
		}
	}

	public void updateItem(int position) {
		if (adapter != null) {
			adapter.updateItem(position);
		}
	}

	public void scrollToTop() {
		lastScrollPosition = 0;
		if (recyclerView != null) {
			recyclerView.scrollToPosition(0);
		}
	}

	public void scrollToDate(Long date) {
		if (adapter != null && layoutManager != null) {
			lastScrollPosition = adapter.getPositionFromDate(date);
			layoutManager.scrollToPositionWithOffset(lastScrollPosition, 0);
		}
	}

	public void clearSelected() {
		if (adapter != null) {
			adapter.clearSelected();
		}
		if (recyclerView != null) {
			recyclerView.setDragSelectActive(false, 0);
		}
	}

	public void updateAutoFit() {
		if (layoutManager != null) {
			layoutManager.updateAutoFit();
		}
	}

	public boolean isSelectionModeActive() {
		if (adapter != null) {
			return adapter.isSelectionModeActive();
		}
		return false;
	}

	public ArrayList<StingleDbFile> getSelectedFiles() {
		List<Integer> indices = adapter.getSelectedIndices();
		ArrayList<StingleDbFile> files = new ArrayList<>();
		for (Integer index : indices) {
			files.add(adapter.getStingleFileAtPosition(index));
		}
		return files;
	}

	public int getFirstVisibleItemNumber() {
		if (layoutManager != null) {
			return layoutManager.findFirstVisibleItemPosition();
		}
		return 0;
	}

	@SuppressLint("ClickableViewAccessibility")
	private void setupDraggableScrollbar(View view) {
		final TextView dateTooltip = view.findViewById(R.id.date_tooltip);

		float statusBarHeight = getStatusBarHeight(getContext());
		float toolbarHeight = getToolbarHeight(getContext());
		int bottomNavHeight = getResources().getDimensionPixelSize(R.dimen.bottom_nav_height);
		int topOffset = getResources().getDimensionPixelSize(R.dimen.gallery_top_padding_without_syncbar);
		final int scrollbarBottomClearance = getScrollbarBottomClearance();

		/*Log.e("statusBarHeight", "statusBarHeight - " + statusBarHeight + "");
		Log.e("toolbarHeight", "toolbarHeight - " + toolbarHeight + "");
		Log.e("bottomNavHeight", "bottomNavHeight - " + bottomNavHeight + "");*/



		// Add a touch listener to handle dragging the scrollbar thumb
		scrollbarThumb.setOnTouchListener((view1, event) -> {

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					dateTooltip.setVisibility(View.VISIBLE);
					((GalleryActivity) parentActivity).disablePullToRefresh();
					isScrollBarDragging = true;
					// Remember where the finger grabbed the thumb so we can move it by the exact
					// finger delta and keep that point under the finger.
					dragStartRawY = event.getRawY();
					dragStartThumbTop = scrollbarThumb.getY();
					if (adapter != null) {
						adapter.setSkipImageLoad(true);
					}
					break;

				case MotionEvent.ACTION_MOVE:
					isScrollBarDragging = true;
					int scrollBarHeight = scrollbarThumb.getHeight();
					int halfScrollBarHeight = (scrollBarHeight / 2);
					float recyclerViewHeight = recyclerView.getHeight();

					// Move the thumb by the finger's delta from the grab point — keeps the grabbed
					// point under the finger instead of snapping the thumb's centre to it.
					float thumbTop = dragStartThumbTop + (event.getRawY() - dragStartRawY);

					// Usable track for the thumb's top. Reserve clearance at the bottom so the thumb
					// floats above the FAB (+ button) rather than disappearing behind it.
					float maxTop = recyclerViewHeight - scrollBarHeight - scrollbarBottomClearance;
					if (maxTop < 0) {
						maxTop = 0;
					}
					if (thumbTop < 0) {
						thumbTop = 0;
					}
					if (thumbTop > maxTop) {
						thumbTop = maxTop;
					}

					int totalItemCount = recyclerView.getAdapter().getItemCount();
					// Map the thumb's position along the track to a scroll position.
					float fraction = (maxTop > 0) ? (thumbTop / maxTop) : 0f;
					int targetScrollPosition = Math.round(fraction * (totalItemCount - 1));
					if (targetScrollPosition < 0) {
						targetScrollPosition = 0;
					}
					if (targetScrollPosition >= totalItemCount) {
						targetScrollPosition = totalItemCount - 1;
					}

					// Update the RecyclerView's scroll position only when the target actually
					// changes, so we don't thrash the list on every touch-move event.
					if (targetScrollPosition != lastScrollbarTargetPosition) {
						lastScrollbarTargetPosition = targetScrollPosition;
						layoutManager.setUpdateSpanCount(false);
						layoutManager.scrollToPositionWithOffset(targetScrollPosition, 0);
						layoutManager.setUpdateSpanCount(true);
					}

					dateTooltip.setText(getDateForScrollPosition(targetScrollPosition));
					scrollbarThumb.setY(thumbTop);
					dateTooltip.setY(thumbTop + halfScrollBarHeight - (dateTooltip.getHeight() / 2f));

					break;

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					dateTooltip.setVisibility(View.GONE);
					((GalleryActivity) parentActivity).enablePullToRefresh();
					isScrollBarDragging = false;
					lastScrollbarTargetPosition = -1;
					// Resume thumbnail loading once at the final position. Post it so the layout
					// has settled and the visible range is accurate (computing it inline at
					// ACTION_UP can miss a row, leaving it blank). Notify a small buffer beyond
					// the visible range to be safe.
					if (adapter != null && recyclerView != null) {
						recyclerView.post(() -> {
							adapter.setSkipImageLoad(false);
							// Load the final visible thumbnails directly (no notify -> no relayout
							// -> no scroll-position shift).
							adapter.loadVisibleThumbnails(recyclerView);
						});
					}
					break;
			}

			return true;
		});
	}

	// Reserve room at the bottom of the scrollbar track so the thumb floats above the floating
	// + button (FAB) instead of disappearing behind it (the FAB is a sibling drawn on top of the
	// fragment, so elevation can't lift the thumb above it — we keep the thumb out of its area).
	private int getScrollbarBottomClearance() {
		return getResources().getDimensionPixelSize(R.dimen.fab_margin) * 2
				+ Helpers.convertDpToPixels(getContext(), 56);
	}

	private void updateScrollTabPosition() {
		if (recyclerView != null && recyclerView.getAdapter() != null && recyclerView.getLayoutManager() != null) {
			int totalItemCount = recyclerView.getAdapter().getItemCount();
			int firstVisibleItemPosition = ((AutoFitGridLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
			int scrollBarHeight = scrollbarThumb.getHeight();

			float recyclerViewHeight = recyclerView.getHeight();
			// Same usable track as the drag handler (reserves FAB clearance at the bottom) so the
			// thumb position is consistent whether the list is scrolled normally or dragged.
			float maxTop = recyclerViewHeight - scrollBarHeight - getScrollbarBottomClearance();
			if (maxTop < 0) {
				maxTop = 0;
			}
			float scrollbarThumbPosition = (totalItemCount > 1) ? (float) firstVisibleItemPosition / (totalItemCount - 1) : 0f;
			float thumbTop = scrollbarThumbPosition * maxTop;
			if (thumbTop < 0) {
				thumbTop = 0;
			}
			if (thumbTop > maxTop) {
				thumbTop = maxTop;
			}

			if (scrollbarThumb != null) {
				scrollbarThumb.setY(thumbTop);
			}
		}
	}

	private int getStatusBarHeight(Context context) {
		int result = 0;
		int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = context.getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}
	private int getToolbarHeight(Context context) {
		TypedArray styledAttributes = context.getTheme().obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.actionBarSize});
		int toolbarHeight = (int) styledAttributes.getDimension(0, 0);
		styledAttributes.recycle();
		return toolbarHeight;
	}


	private String getDateForScrollPosition(int position) {
		if (adapter == null) {
			return "";
		}

		String date = adapter.getDateForPosition(position); // Implement this method in your GalleryAdapterPisasso class
		if (date != null) {
			return date;
		}
		return "";
	}

	private void setScrollbarThumbPosition() {
		/*if (recyclerView != null && recyclerView.getAdapter() != null && recyclerView.getLayoutManager() != null) {
			int totalItemCount = recyclerView.getAdapter().getItemCount();
			int firstVisibleItemPosition = ((AutoFitGridLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();

			float scrollbarThumbPosition = (float) firstVisibleItemPosition / totalItemCount;
			float recyclerViewHeight = recyclerView.getHeight();
			float thumbTop = scrollbarThumbPosition * recyclerViewHeight;

			ImageView scrollbarThumb = getActivity().findViewById(R.id.scrollbar_thumb);
			if (scrollbarThumb != null) {
				scrollbarThumb.setY(thumbTop);
			}
		}*/
	}


}


