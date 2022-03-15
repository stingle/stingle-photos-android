package org.stingle.photos.Editor.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.stingle.photos.Editor.activities.EditorActivity;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Editor.views.GLImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class ToolFragment extends Fragment {
	protected ExecutorService backgroundExecutor;
	protected Handler mainThreadExecutor;
	private Future<?> currentTask;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		backgroundExecutor = Executors.newSingleThreadExecutor();
		mainThreadExecutor = new Handler(Looper.getMainLooper());
	}

	@Override
	public void onStop() {
		super.onStop();

		if (backgroundExecutor != null) {
			backgroundExecutor.shutdown();
			backgroundExecutor = null;
		}
	}

	protected void runInBackground(Runnable action) {
		if (currentTask != null && !currentTask.isDone() && !currentTask.isCancelled()) {
			currentTask.cancel(false);
		}

		if (backgroundExecutor != null && !backgroundExecutor.isTerminated()) {
			currentTask = backgroundExecutor.submit(action);
		}
	}

	public EditorActivity getEditorActivity() {
		return (EditorActivity) getActivity();
	}

	public GLImageView getEditorView() {
		EditorActivity editorActivity = getEditorActivity();
		if (editorActivity != null) {
			return editorActivity.getEditorView();
		}

		return null;
	}

	public void getImage(Callback<Image> imageCallback) {
		GLImageView imageView = getEditorView();
		if (imageView != null) {
			imageView.getImage(imageCallback);
		}
	}

	public Bitmap processImage(Bitmap originalBitmap) {
		return originalBitmap;
	}
}
