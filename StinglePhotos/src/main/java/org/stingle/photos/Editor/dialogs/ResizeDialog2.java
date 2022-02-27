package org.stingle.photos.Editor.dialogs;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputLayout;

import org.stingle.photos.R;

public class ResizeDialog2 extends DialogFragment {
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 16_000;

	private int imageWidth;
	private int imageHeight;

	private int selectedWidth;
	private int selectedHeight;

	private SizeSelectedListener listener;

	private EditText widthInput;
	private EditText heightInput;

	public void setImageSize(int width, int height) {
		this.imageWidth = width;
		this.imageHeight = height;

		selectedWidth = width;
		selectedHeight = height;
	}

	public void setListener(SizeSelectedListener listener) {
		this.listener = listener;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dialog_resize2, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		view.findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		view.findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();

				if (listener != null) {
					listener.onSizeSelected(selectedWidth, selectedHeight);
				}
			}
		});

		widthInput = ((TextInputLayout) view.findViewById(R.id.et_width)).getEditText();
		heightInput = ((TextInputLayout) view.findViewById(R.id.et_height)).getEditText();

		if (widthInput != null && heightInput != null) {
			widthInput.setText(String.valueOf(imageWidth));
			heightInput.setText(String.valueOf(imageHeight));

			widthInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						int inputWidth = -1;
						try {
							inputWidth = Integer.parseInt(widthInput.getText().toString());
						} catch (Exception e) {
							e.printStackTrace();
						}

						if (inputWidth >= 0) {
							setSelectedWidth(inputWidth);
						}
					}

					return false;
				}
			});

			heightInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						int inputHeight = -1;
						try {
							inputHeight = Integer.parseInt(heightInput.getText().toString());
						} catch (Exception e) {
							e.printStackTrace();
						}

						if (inputHeight >= 0) {
							setSelectedHeight(inputHeight);
						}
					}

					return false;
				}
			});
		}
	}

	private void setSelectedWidth(int selectedWidth) {
		this.selectedWidth = Math.max(MIN_SIZE, Math.min(MAX_SIZE, selectedWidth));
		this.selectedHeight = (int) (selectedWidth * (double) imageHeight / imageWidth);

		int clampedSelectedHeight = Math.max(MIN_SIZE, Math.min(MAX_SIZE, selectedHeight));
		if (clampedSelectedHeight != selectedHeight) {
			selectedHeight = clampedSelectedHeight;

			this.selectedWidth = (int) (selectedHeight * (double) imageWidth / imageHeight);
		}

		widthInput.setText(String.valueOf(this.selectedWidth));
		heightInput.setText(String.valueOf(this.selectedHeight));
	}

	private void setSelectedHeight(int selectedHeight) {
		this.selectedHeight = Math.max(MIN_SIZE, Math.min(MAX_SIZE, selectedHeight));
		this.selectedWidth = (int) (selectedHeight * (double) imageWidth / imageHeight);

		int clampedSelectedWidth = Math.max(MIN_SIZE, Math.min(MAX_SIZE, selectedWidth));
		if (clampedSelectedWidth != selectedWidth) {
			selectedWidth = clampedSelectedWidth;

			this.selectedHeight = (int) (selectedWidth * (double) imageHeight / imageWidth);
		}

		widthInput.setText(String.valueOf(this.selectedWidth));
		heightInput.setText(String.valueOf(this.selectedHeight));
	}

	public interface SizeSelectedListener {
		void onSizeSelected(int width, int height);
	}
}
