package org.stingle.photos.Editor.activities;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.FragmentManager;

import org.stingle.photos.AsyncTasks.LoadImageAsyncTask;
import org.stingle.photos.Editor.core.AdjustOption;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.core.Property;
import org.stingle.photos.Editor.core.Tool;
import org.stingle.photos.Editor.fragments.AdjustFragment;
import org.stingle.photos.Editor.fragments.AdjustOptionsFragment;
import org.stingle.photos.Editor.fragments.CropFragment;
import org.stingle.photos.Editor.fragments.MainFragment;
import org.stingle.photos.Editor.fragments.MiscToolsFragment;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Editor.views.GLImageView;
import org.stingle.photos.R;
import org.stingle.photos.ViewItem.ViewItemAsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditorActivity extends AppCompatActivity {
	private GLImageView editorView;

	private Image image;

	private MainFragment mainFragment;
	private CropFragment cropFragment;
	private AdjustFragment adjustFragment;
	private MiscToolsFragment miscToolsFragment;
	private AdjustOptionsFragment adjustOptionsFragment;

	private Tool currentTool;

	private ContentLoadingProgressBar progressBar;

	private Callback<Bitmap> bitmapCallback;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

		setContentView(R.layout.activity_editor);

		Intent intent = getIntent();

		int itemPosition = intent.getIntExtra("EXTRA_ITEM_POSITION", 0);
		int set = intent.getIntExtra("EXTRA_ITEM_SET", 0);
		String albumId = intent.getStringExtra("EXTRA_ITEM_ALBUM_ID");


		progressBar = findViewById(R.id.progress);
		progressBar.show();

		bitmapCallback = new Callback<Bitmap>() {
			@Override
			public void call(Bitmap bitmap) {
				progressBar.hide();

				if (bitmap != null) {
					image = new Image(bitmap);
					image.init();

					if (adjustFragment != null) {
						adjustFragment.setImage(image);
					}

					if (editorView != null) {
						editorView.setImage(image);
					}
				}
			}
		};

		loadImage(itemPosition, set, albumId, bitmapCallback);

		FragmentManager fragmentManager = getSupportFragmentManager();

		cropFragment = new CropFragment();

		miscToolsFragment = new MiscToolsFragment();

		mainFragment = new MainFragment();
		mainFragment.setListener(new MainFragment.ToolSelectedListener() {
			@Override
			public void onToolSelected(Tool tool) {
				if (currentTool != tool) {
					currentTool = tool;

					if (tool == Tool.CROP) {
						fragmentManager.beginTransaction().replace(R.id.tool_container1, cropFragment, null).commit();

						editorView.setEditorMode(GLImageView.EDITOR_MODE_CROP);
					} else if (tool == Tool.ADJUST) {
						fragmentManager.beginTransaction().replace(R.id.tool_container1, adjustFragment, null).commit();

						editorView.setEditorMode(GLImageView.EDITOR_MODE_NORMAL);
					} else if (tool == Tool.MISC) {
						fragmentManager.beginTransaction().replace(R.id.tool_container1, miscToolsFragment, null).commit();

						editorView.setEditorMode(GLImageView.EDITOR_MODE_NORMAL);
					}
				}
			}
		});

		View rootView = findViewById(R.id.root);

		View fragmentContainerView = findViewById(R.id.tool_container1);
		fragmentContainerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
				float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());

				if (left != oldLeft || top != oldTop || bottom != oldBottom || right != oldRight) {
					editorView.setViewportPadding(padding, padding, padding, rootView.getHeight() - top + padding);
				}
			}
		});

		adjustFragment = new AdjustFragment();
		if (image != null) {
			adjustFragment.setImage(image);
		}


		adjustOptionsFragment = new AdjustOptionsFragment();
		adjustOptionsFragment.setCallback(new AdjustOptionsFragment.Callback() {
			@Override
			public void onPropertyChanged(Property property) {
				adjustFragment.notifyPropertyChanged(property);

				editorView.requestRender();
			}

			@Override
			public void onDone() {
				adjustFragment.setActiveMode(false);

				fragmentManager.beginTransaction().replace(R.id.tool_container2, mainFragment, null).commit();
			}
		});

		fragmentManager.beginTransaction().replace(R.id.tool_container2, mainFragment, null).commit();

		adjustFragment.setListener(new AdjustFragment.AdjustOptionSelectedListener() {
			@Override
			public void onAdjustOptionSelectionStarted() {
				adjustOptionsFragment.setWheelEnabled(false);
			}

			@Override
			public void onAdjustOptionSelected(AdjustOption AdjustOption) {
				if (!adjustOptionsFragment.isAdded()) {
					fragmentManager.beginTransaction().replace(R.id.tool_container2, adjustOptionsFragment, null).commit();
				}

				adjustOptionsFragment.setWheelEnabled(true);

				Property property = image.findPropertyWithName(AdjustOption.name);
				if (property != null) {
					adjustOptionsFragment.setProperty(property);
				}
			}
		});

		editorView = findViewById(R.id.editor);
		if (image != null) {
			editorView.setImage(image);
		}

		fragmentManager.beginTransaction().replace(R.id.tool_container1, adjustFragment, null).commit();
	}

	public GLImageView getEditorView() {
		return editorView;
	}

	private void loadImage(int itemPosition, int set, String albumId, Callback<Bitmap> callback) {
		LoadImageAsyncTask loadImageAsyncTask = new LoadImageAsyncTask(this, callback, itemPosition, set, albumId);
		loadImageAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}