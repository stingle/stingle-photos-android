package org.stingle.photos.Editor.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.stingle.photos.Editor.core.CropRatio;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.dialogs.AspectRatioChooserDialog;
import org.stingle.photos.Editor.opengl.Camera;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Editor.views.GLImageView;
import org.stingle.photos.Editor.views.ScrollingWheel;
import org.stingle.photos.R;

public class CropFragment extends ToolFragment {

	private Button ratioButton;
	private Button rotateButton;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_crop, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		GLImageView editorView = getEditorView();

		ScrollingWheel scrollingWheel = view.findViewById(R.id.rotation_wheel);
		scrollingWheel.setLimits(-45, 45);
		scrollingWheel.setValueFormatter(value -> getString(R.string.degree_formatted, value));
		scrollingWheel.setListener(new ScrollingWheel.Listener() {
			@Override
			public void onValueChanged(int value) {
				if (editorView != null) {
					editorView.getImage(new Callback<Image>() {
						@Override
						public void call(Image image) {
							image.setCropRotation(-value);

							editorView.getCamera(new Callback<Camera>() {
								@Override
								public void call(Camera camera) {
									camera.lookAtImage(image);
								}
							});
						}
					});
				}
			}
		});

		ratioButton = view.findViewById(R.id.btn_aspect_ratio);
		ratioButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (editorView != null) {
					editorView.getImage(new Callback<Image>() {
						@Override
						public void call(final Image image) {
							v.post(new Runnable() {
								@Override
								public void run() {
									AspectRatioChooserDialog aspectRatioChooserDialog = new AspectRatioChooserDialog();
									aspectRatioChooserDialog.setCurrentRatio(image.getCropRatio());
									aspectRatioChooserDialog.setCallback(new AspectRatioChooserDialog.Callback() {
										@Override
										public void onRatioSelected(CropRatio cropRatio) {
											ratioButton.setActivated(!cropRatio.isFree());

											editorView.getImage(new Callback<Image>() {
												@Override
												public void call(Image image) {
													image.setCropRatio(cropRatio);

													editorView.getCamera(new Callback<Camera>() {
														@Override
														public void call(Camera camera) {
															camera.lookAtImage(image);
														}
													});
												}
											});
										}
									});
									aspectRatioChooserDialog.setImageSize(image.getWidth(), image.getHeight());
									aspectRatioChooserDialog.show(getChildFragmentManager(), null);
								}
							});
						}
					});
				}
			}
		});

		rotateButton = view.findViewById(R.id.btn_rotate);
		rotateButton.setActivated(editorView != null);
		rotateButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				editorView.getImage(new Callback<Image>() {
					@Override
					public void call(Image image) {
						editorView.getCamera(new Callback<Camera>() {
							@Override
							public void call(Camera camera) {
								camera.animateRotate(image);
							}
						});
					}
				});
			}
		});

		view.findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				editorView.getImage(new Callback<Image>() {
					@Override
					public void call(Image image) {
						image.reset();
						scrollingWheel.setValue(0);

						editorView.getCamera(new Callback<Camera>() {
							@Override
							public void call(Camera camera) {
								camera.lookAtImage(image);
							}
						});
					}
				});
			}
		});

		editorView.getImage(new Callback<Image>() {
			@Override
			public void call(Image image) {
				scrollingWheel.setValue(image.getCropRotation());
				ratioButton.setActivated(!image.getCropRatio().isFree());
			}
		});
	}
}
