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

	private GalleryFragmentParent parentActivity;

	private int currentSet = SyncManager.GALLERY;
	private String albumId = null;
	private View scrollBarWithTooltip;
	private boolean isScrollBarDragging = false;
	private ImageView scrollbarThumb;
	final Handler handler = new Handler();


	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_gallery, container, false);

		recyclerView = view.findViewById(R.id.recycler_view);
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
		layoutManager.scrollToPosition(lastScrollPosition);
		setScrollbarThumbPosition();

		if (recyclerView != null) {
			if (!parentActivity.isSyncBarDisabled()) {
				recyclerView.setPadding(recyclerView.getPaddingLeft(), (int) getResources().getDimension(R.dimen.gallery_top_padding_with_syncbar), recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
			} else {
				recyclerView.setPadding(recyclerView.getPaddingLeft(), (int) getResources().getDimension(R.dimen.gallery_top_padding_without_syncbar), recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
			}
		}

	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d("GalleryFragment", "onPause");
		lastScrollPosition = layoutManager.findFirstVisibleItemPosition();
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
		int lastScrollPos = recyclerView.getScrollY();
		if (adapter != null) {
			adapter.updateDataSet();
			handleNoPhotos();
		}
		recyclerView.setScrollY(lastScrollPos);
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
		int topOffset = getResources().getDimensionPixelSize(R.dimen.gallery_top_padding_with_syncbar);

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
					break;

				case MotionEvent.ACTION_MOVE:
					isScrollBarDragging = true;
					float y = event.getRawY();
					float recyclerViewHeight = recyclerView.getHeight();
					int scrollBarHeight = scrollbarThumb.getHeight();
					int halfScrollBarHeight = (scrollBarHeight/2);

					/*Log.e("scrollbarWithTooltip", "scrollbarWithTooltip - " + scrollBarWithTooltip.getHeight() + "");
					Log.e("recyclerView.getHeight()", "recyclerViewHeight - " + recyclerViewHeight + "");
					Log.e("topOffset", "topOffset - " + topOffset + "");
					Log.e("bottomOffset", "bottomOffset - " + bottomNavHeight + "");
					Log.e("scrollbarThumb.getHeight()", "scrollbarThumb.getHeight() - " + scrollbarThumb.getHeight() + "");
					Log.e("scrollbarThumb.getHeight()", "scrollbarThumb.getHeight()/2 - " + (scrollbarThumb.getHeight()/2) + "");
					Log.e("y", "y - " + y);*/

					if(y > recyclerViewHeight - halfScrollBarHeight){
						y = recyclerViewHeight  - halfScrollBarHeight;
					}
					else if(y < topOffset + halfScrollBarHeight){
						y = topOffset + halfScrollBarHeight;
					}
					//Log.e("y2", "y2 - " + y);
					// Calculate the thumbTop value considering status bar height and parent view position
					float thumbTop = y - topOffset - halfScrollBarHeight-20;
					if(thumbTop < 0){
						thumbTop = 0;
					}
					//Log.e("thumbTop", "thumbTop - " + thumbTop + "");

					// Get the total number of items in the adapter
					int totalItemCount = recyclerView.getAdapter().getItemCount();
					//Log.e("totalItemCount", "totalItemCount - " + totalItemCount + "");

					// Calculate the scroll position based on the scrollbar thumb position
//					float factor = thumbTop * 100 / totalItemCount;
//					float scrollbarThumbPosition = thumbTop * factor / recyclerViewHeight;
//					Log.e("scrollbarThumbPosition", "scrollbarThumbPosition - " + scrollbarThumbPosition + "");

					// Calculate the target scroll position
					int targetScrollPosition = (int) (((y - topOffset - halfScrollBarHeight) * totalItemCount) / (recyclerViewHeight - topOffset - scrollBarHeight));
					//Log.e("targetScrollPosition", "targetScrollPosition - " + targetScrollPosition + "");
					//Log.e("delim", "----------------------------------------------------");

					// Update the RecyclerView's scroll position
					layoutManager.setUpdateSpanCount(false);
					layoutManager.scrollToPositionWithOffset(targetScrollPosition, 0);
					layoutManager.setUpdateSpanCount(true);

					// Update the date tooltip based on the new scroll position
					String date = getDateForScrollPosition(targetScrollPosition); // Implement this method to fetch the date based on the current scroll position
					dateTooltip.setText(date);

					// Update the scrollbar thumb and date tooltip positions
					scrollbarThumb.setY(thumbTop);
					dateTooltip.setY(thumbTop + halfScrollBarHeight - (dateTooltip.getHeight() / 2));

					break;

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					dateTooltip.setVisibility(View.GONE);
					((GalleryActivity) parentActivity).enablePullToRefresh();
					isScrollBarDragging = false;
					break;
			}

			return true;
		});
	}

	private void updateScrollTabPosition() {
		if (recyclerView != null && recyclerView.getAdapter() != null && recyclerView.getLayoutManager() != null) {
			int totalItemCount = recyclerView.getAdapter().getItemCount();
			int firstVisibleItemPosition = ((AutoFitGridLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
			int topOffset = getResources().getDimensionPixelSize(R.dimen.gallery_top_padding_with_syncbar);
			int scrollBarHeight = scrollbarThumb.getHeight();
			int halfScrollBarHeight = (scrollBarHeight/2);


			float scrollbarThumbPosition = (float) firstVisibleItemPosition / totalItemCount;
			float recyclerViewHeight = recyclerView.getHeight();
			float thumbTop = scrollbarThumbPosition * (recyclerViewHeight - topOffset - scrollBarHeight);


			// Calculate the thumbTop value considering status bar height and parent view position
			thumbTop -= 20;
			if(thumbTop < 0){
				thumbTop = 0;
			}

			//Log.e("updateScrollTabPosition", "ThumbTop - " + thumbTop);
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


