package org.stingle.photos.Editor.fragments;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.telecom.Call;
import android.util.DisplayMetrics;
import android.util.Log;
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

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.stingle.photos.AsyncTasks.SaveEditedImageAsyncTask;
import org.stingle.photos.CameraX.helpers.MediaSaveHelper;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.GalleryTrashDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Editor.activities.EditorActivity;
import org.stingle.photos.Editor.adapters.ToolAdapter;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.core.Tool;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Editor.util.CenterOffsetItemDecoration;
import org.stingle.photos.Editor.util.ImageSaver;
import org.stingle.photos.R;
import org.stingle.photos.Sync.SyncManager;

import java.lang.ref.WeakReference;

public class MainFragment extends ToolFragment implements Runnable {

	private boolean userInitiatedScroll;

	private ToolSelectedListener listener;

	public void setListener(ToolSelectedListener listener) {
		this.listener = listener;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_main, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		view.findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showSaveOptions();
			}
		});

		ToolAdapter toolAdapter = new ToolAdapter(getContext());
		toolAdapter.submitList(Tool.getToolList());
		toolAdapter.setActiveTool(Tool.ADJUST);

		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);

		int itemMargin = getResources().getDimensionPixelOffset(R.dimen.tool_list_item_margin);

		RecyclerView toolListView = view.findViewById(R.id.tool_list);
		toolListView.setLayoutManager(layoutManager);
		toolListView.setAdapter(toolAdapter);
		toolListView.setItemAnimator(null);

		CenterOffsetItemDecoration itemDecoration = new CenterOffsetItemDecoration(itemMargin);
		itemDecoration.setActiveMode(true);

		toolListView.addItemDecoration(itemDecoration);

		LinearSnapHelper snapHelper = new LinearSnapHelper();
		snapHelper.attachToRecyclerView(toolListView);

		toolListView.addOnScrollListener(new RecyclerView.OnScrollListener() {

			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				super.onScrollStateChanged(recyclerView, newState);

				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					userInitiatedScroll = true;
				}

				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					if (listener != null) {
						listener.onToolSelected(toolAdapter.getActiveTool());
					}
				}
			}

			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);

//				if (recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
				if (userInitiatedScroll) {
					View snappingView = snapHelper.findSnapView(layoutManager);
					if (snappingView != null) {
						int position = layoutManager.getPosition(snappingView);
						if (position != RecyclerView.NO_POSITION) {
							toolListView.post(() -> {
								toolAdapter.setActiveTool(toolAdapter.getCurrentList().get(position));
							});

							if (listener != null && recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_DRAGGING) {
								listener.onToolSelected(toolAdapter.getCurrentList().get(position));
							}
						}
					}
				}
//				}
			}
		});

		toolAdapter.setCallback(new ToolAdapter.Callback() {
			@Override
			public void onToolSelected(Tool tool) {
				if (!tool.equals(toolAdapter.getActiveTool())) {
					toolAdapter.setActiveTool(tool);

					if (listener != null) {
						listener.onToolSelected(tool);
					}

					LinearLayoutManager layoutManager = (LinearLayoutManager) toolListView.getLayoutManager();

					if (layoutManager != null && !layoutManager.isSmoothScrolling()) {
						int index = toolAdapter.findToolPosition(tool);

						RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
							@Override
							public int calculateDxToMakeVisible(View view, int snapPreference) {
								int start = super.calculateDxToMakeVisible(view, LinearSmoothScroller.SNAP_TO_START);
								int end = super.calculateDxToMakeVisible(view, LinearSmoothScroller.SNAP_TO_END);

//								return layoutManager.getWidth() / 2 - layoutManager.getDecoratedLeft(view);
								return (start + end) / 2;
							}

							@Override
							protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
								return super.calculateSpeedPerPixel(displayMetrics) * 3f;
							}
						};

						userInitiatedScroll = false;

						smoothScroller.setTargetPosition(index);
						layoutManager.startSmoothScroll(smoothScroller);

						Log.d("Stingle", "Smooth scrolling to " + index + " position");
					}
				}
			}
		});
	}

	@Override
	public void run() {
		getEditorActivity().finish();
	}

	private void showSaveOptions() {
		if (canFileBeReplaced()) {
			SaveOptionsDialogFragment fragment = new SaveOptionsDialogFragment(this::saveImage);
			fragment.show(getChildFragmentManager(), null);
		} else {
			saveImage(false);
		}
	}

	private void saveImage(boolean replace) {
		getImage(new Callback<Image>() {
			@Override
			public void call(Image image) {
				EditorActivity editorActivity = getEditorActivity();
				editorActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						SaveEditedImageAsyncTask asyncTask = new SaveEditedImageAsyncTask(
								editorActivity.getItemPosition(), editorActivity.getSet(), editorActivity.getAlbumId(),
								new Image(image), replace, editorActivity, MainFragment.this);
						asyncTask.execute();
					}
				});
			}
		});
	}

	private boolean canFileBeReplaced() {
		GalleryTrashDb galleryDb = new GalleryTrashDb(getContext(), SyncManager.GALLERY);
		AlbumFilesDb albumFilesDb = new AlbumFilesDb(getContext());
		GalleryTrashDb trashDb = new GalleryTrashDb(getContext(), SyncManager.TRASH);

		boolean existsInOtherSets = false;

		EditorActivity editorActivity = getEditorActivity();

		int itemPosition = editorActivity.getItemPosition();
		int set = editorActivity.getSet();
		String albumId = editorActivity.getAlbumId();

		if (set == SyncManager.GALLERY) {
			StingleDbFile file = galleryDb.getFileAtPosition(itemPosition, albumId, StingleDb.SORT_DESC);
			existsInOtherSets = albumFilesDb.getFileIfExists(file.filename) != null || trashDb.getFileIfExists(file.filename) != null;
		} else if (set == SyncManager.ALBUM) {
			StingleDbFile file = albumFilesDb.getFileAtPosition(itemPosition, albumId, StingleDb.SORT_DESC);
			existsInOtherSets = galleryDb.getFileIfExists(file.filename) != null || trashDb.getFileIfExists(file.filename) != null;
		} else {
			StingleDbFile file = trashDb.getFileAtPosition(itemPosition, albumId, StingleDb.SORT_DESC);
			existsInOtherSets = galleryDb.getFileIfExists(file.filename) != null || albumFilesDb.getFileIfExists(file.filename) != null;
		}

		return !existsInOtherSets;
	}

	public static class SaveOptionsDialogFragment extends BottomSheetDialogFragment {
		private WeakReference<Callback> callbackWeakReference;

		public SaveOptionsDialogFragment(Callback callback) {
			callbackWeakReference = new WeakReference<>(callback);
		}

		@Nullable
		@Override
		public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
			return inflater.inflate(R.layout.fragment_save_options, container, false);
		}

		@Override
		public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
			super.onViewCreated(view, savedInstanceState);

			Callback callback = callbackWeakReference.get();

			view.findViewById(R.id.btn_replace_original).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dismiss();
					if (callback != null) {
						callback.onSavePressed(true);
					}
				}
			});

			view.findViewById(R.id.btn_make_copy).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dismiss();
					if (callback != null) {
						callback.onSavePressed(false);
					}
				}
			});
		}

		static interface Callback {
			void onSavePressed(boolean replace);
		}
	}

	public interface ToolSelectedListener {
		void onToolSelected(Tool tool);
	}
}
