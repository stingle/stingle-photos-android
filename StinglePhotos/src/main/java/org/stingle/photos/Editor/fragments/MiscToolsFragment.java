package org.stingle.photos.Editor.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.dialogs.ResizeDialog2;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Editor.views.GLImageView;
import org.stingle.photos.R;

public class MiscToolsFragment extends ToolFragment {
	private RecyclerView toolListView;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_misc_tools, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);

		view.findViewById(R.id.btn_resize).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GLImageView editorView = getEditorView();
				editorView.getImage(new Callback<Image>() {
					@Override
					public void call(Image image) {
						ResizeDialog2 resizeDialog = new ResizeDialog2();
						resizeDialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.ThemeOverlay_App_AlertDialog);
						resizeDialog.setImageSize(image.getWidth(), image.getHeight());
						resizeDialog.show(getChildFragmentManager(), null);
					}
				});
			}
		});
	}
}
