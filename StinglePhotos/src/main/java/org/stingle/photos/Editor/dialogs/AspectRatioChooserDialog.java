package org.stingle.photos.Editor.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.stingle.photos.Editor.adapters.CropRatioAdapter;
import org.stingle.photos.Editor.core.CropRatio;
import org.stingle.photos.R;

import java.util.ArrayList;
import java.util.List;

public class AspectRatioChooserDialog extends BottomSheetDialogFragment {

	private CropRatioAdapter cropRatioAdapter;

	private CropRatio currentRatio;
	private int imageWidth;
	private int imageHeight;

	private Callback callback;

	public AspectRatioChooserDialog() {
		currentRatio = CropRatio.FREE;
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public void setCurrentRatio(CropRatio currentRatio) {
		this.currentRatio = currentRatio;
	}

	public void setImageSize(int imageWidth, int imageHeight) {
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		boolean isPortrait = currentRatio != null && currentRatio.isPortrait();

		List<CropRatio> cropRatioList = new ArrayList<>();
		cropRatioList.add(CropRatio.FREE);
		cropRatioList.add(new CropRatio(imageWidth, imageHeight, CropRatio.SPECIAL_ORIGINAL));
		cropRatioList.add(new CropRatio(1, 1));
		if (isPortrait) {
			cropRatioList.add(new CropRatio(4, 5));
			cropRatioList.add(new CropRatio(3, 4));
			cropRatioList.add(new CropRatio(2, 3));
			cropRatioList.add(new CropRatio(9, 16));
		} else {
			cropRatioList.add(new CropRatio(5, 4));
			cropRatioList.add(new CropRatio(4, 3));
			cropRatioList.add(new CropRatio(3, 2));
			cropRatioList.add(new CropRatio(16, 9));
		}

		cropRatioAdapter = new CropRatioAdapter(getContext());
		cropRatioAdapter.submitList(cropRatioList);
		cropRatioAdapter.setActiveCropRatio(currentRatio);
		cropRatioAdapter.setCallback(new CropRatioAdapter.Callback() {
			@Override
			public void onCropRatioSelected(CropRatio cropRatio) {
				if (callback != null) {
					dismiss();

					callback.onRatioSelected(cropRatio);
				}
			}
		});
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dialog_aspect_ratio_chooser, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		View divider = view.findViewById(R.id.divider);

		Button flipButton = view.findViewById(R.id.btn_flip);
		if (currentRatio.isPortrait()) {
			divider.setVisibility(View.VISIBLE);

			flipButton.setVisibility(View.VISIBLE);
			flipButton.setText(R.string.flip_to_landscape);
		} else if (currentRatio.isLandscape()) {
			divider.setVisibility(View.VISIBLE);

			flipButton.setVisibility(View.VISIBLE);
			flipButton.setText(R.string.flip_to_portrait);
		} else {
			divider.setVisibility(View.GONE);

			flipButton.setVisibility(View.GONE);
		}
		flipButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (callback != null) {
					dismiss();

					callback.onRatioSelected(currentRatio.flip());
				}
			}
		});

		RecyclerView aspectRatioListView = view.findViewById(R.id.list_aspect_ratio);
		aspectRatioListView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
		aspectRatioListView.setAdapter(cropRatioAdapter);
	}

	public interface Callback {
		void onRatioSelected(CropRatio cropRatio);
	}
}
