package org.stingle.photos.Editor.fragments;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import org.stingle.photos.Editor.adapters.AdjustOptionsAdapter;
import org.stingle.photos.Editor.core.AdjustOption;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.core.Property;
import org.stingle.photos.Editor.util.CenterOffsetItemDecoration;
import org.stingle.photos.R;

public class AdjustFragment extends Fragment {

	private boolean userInitiatedScroll;

	private AdjustOptionSelectedListener listener;

	private boolean activeMode;

	private RecyclerView toolListView;

	private SnapHelper snapHelper;

	private CenterOffsetItemDecoration itemDecoration;

	private AdjustOptionsAdapter toolAdapter;

	private Image image;

	public void setImage(Image image) {
		this.image = image;

		if (toolAdapter != null) {
			toolAdapter.setImage(image);
		}
	}

	public void setListener(AdjustOptionSelectedListener listener) {
		this.listener = listener;
	}

	public void setActiveMode(boolean activeMode) {
		if (itemDecoration != null && toolListView != null) {
			if (this.activeMode != activeMode) {
				this.activeMode = activeMode;

				if (activeMode) {
//					toolListView.addOnScrollListener(scrollListener);
					snapHelper.attachToRecyclerView(toolListView);
				} else {
//					toolListView.removeOnScrollListener(scrollListener);
					snapHelper.attachToRecyclerView(null);
					toolAdapter.setActiveTool(null);
				}

				LinearLayoutManager layoutManager = (LinearLayoutManager) toolListView.getLayoutManager();

				if (activeMode) {
					int listViewWidth = toolListView.getWidth();
					int itemWidth = getResources().getDimensionPixelSize(R.dimen.adjust_item_width);

					toolListView.setPadding((listViewWidth - itemWidth) / 2, 0, (listViewWidth - itemWidth) / 2, 0);
				} else {
					if (layoutManager != null) {
						int scrollAmount = 0;

						View firstChild = layoutManager.getChildAt(0);
						View lastChild = layoutManager.getChildAt(layoutManager.getChildCount() - 1);
						if (firstChild != null && layoutManager.getPosition(firstChild) == 0) {
							int left = layoutManager.getDecoratedLeft(firstChild);
							scrollAmount = Math.max(left, 0);
						} else if (lastChild != null && layoutManager.getPosition(lastChild) == layoutManager.getItemCount() - 1) {
							int right = layoutManager.getDecoratedRight(lastChild) - layoutManager.getWidth();
							scrollAmount = Math.min(right, 0);
						}

						if (scrollAmount != 0) {
							toolListView.smoothScrollBy(scrollAmount, 0);
						} else {
							toolListView.setPadding(0, 0, 0, 0);
						}
					}
				}
			}
		}
	}

	private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {

		@Override
		public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
			super.onScrollStateChanged(recyclerView, newState);

			if (activeMode) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					userInitiatedScroll = true;

					if (listener != null) {
						listener.onAdjustOptionSelectionStarted();
					}
				}

				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					if (listener != null) {
						listener.onAdjustOptionSelected(toolAdapter.getActiveTool());
					}
				}
			}
		}

		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
			super.onScrolled(recyclerView, dx, dy);

			RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
			if (layoutManager != null) {
				if (activeMode && userInitiatedScroll) {
					View snappingView = snapHelper.findSnapView(layoutManager);
					if (snappingView != null) {
						int position = layoutManager.getPosition(snappingView);
						if (position != RecyclerView.NO_POSITION) {
							toolListView.post(() -> {
								toolAdapter.setActiveTool(toolAdapter.getCurrentList().get(position));
							});
						}
					}
				} else if (!activeMode && toolListView.getPaddingLeft() != 0) {
					View firstChild = layoutManager.getChildAt(0);
					View lastChild = layoutManager.getChildAt(layoutManager.getChildCount() - 1);
					if (firstChild != null && layoutManager.getPosition(firstChild) == 0) {
						int left = layoutManager.getDecoratedLeft(firstChild);
						if (left <= 0) {
							toolListView.setPadding(0, 0, 0, 0);
						}
					} else if (lastChild != null && layoutManager.getPosition(lastChild) == layoutManager.getItemCount() - 1) {
						int right = layoutManager.getDecoratedRight(lastChild) - layoutManager.getWidth();
						if (right >= 0) {
							toolListView.setPadding(0, 0, 0, 0);
						}
					}
				}
			}
		}
	};

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		itemDecoration = new CenterOffsetItemDecoration(0);

		snapHelper = new LinearSnapHelper();

		toolAdapter = new AdjustOptionsAdapter(getContext());
		toolAdapter.submitList(AdjustOption.getToolList());
		toolAdapter.setImage(image);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_adjust, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);

		toolListView = view.findViewById(R.id.adjust_tool_list);
		toolListView.setLayoutManager(layoutManager);
		toolListView.setAdapter(toolAdapter);
		toolListView.setItemAnimator(null);
		toolListView.addItemDecoration(itemDecoration);
		toolListView.addOnScrollListener(scrollListener);


		toolAdapter.setCallback(new AdjustOptionsAdapter.Callback() {
			@Override
			public void onToolSelected(AdjustOption tool) {
				if (!tool.equals(toolAdapter.getActiveTool())) {
					int index = toolAdapter.findToolPosition(tool);

					LinearLayoutManager layoutManager = (LinearLayoutManager) toolListView.getLayoutManager();

					setActiveMode(true);

					toolAdapter.setActiveTool(tool);

					RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
						@Override
						public int calculateDxToMakeVisible(View view, int snapPreference) {
							int start = super.calculateDxToMakeVisible(view, LinearSmoothScroller.SNAP_TO_START);
							int end = super.calculateDxToMakeVisible(view, LinearSmoothScroller.SNAP_TO_END);

							return (start + end) / 2;
						}

						@Override
						protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
							return super.calculateSpeedPerPixel(displayMetrics) * 3f;
						}

						@Override
						protected void onStop() {
							super.onStop();
						}
					};
					smoothScroller.setTargetPosition(index);
					layoutManager.startSmoothScroll(smoothScroller);
				}
			}
		});
	}

	public void notifyPropertyChanged(Property property) {
		if (toolAdapter != null) {
			toolAdapter.notifyPropertyChanged(property);
		}
	}

	public interface AdjustOptionSelectedListener {
		void onAdjustOptionSelectionStarted();
		void onAdjustOptionSelected(AdjustOption AdjustOption);
	}
}
