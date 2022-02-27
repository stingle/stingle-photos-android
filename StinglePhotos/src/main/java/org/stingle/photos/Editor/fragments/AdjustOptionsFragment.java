package org.stingle.photos.Editor.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.stingle.photos.Editor.core.Property;
import org.stingle.photos.Editor.views.ScrollingWheel;
import org.stingle.photos.R;

public class AdjustOptionsFragment extends Fragment {

	private Callback callback;

	private Property property;

	private ScrollingWheel inputWheel;

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public void setProperty(Property property) {
		this.property = property;

		if (inputWheel != null) {
			inputWheel.setLimits(property.minValue, property.maxValue);
			inputWheel.setValue(property.value);
		}
	}

	public void setWheelEnabled(boolean enabled) {
		if (inputWheel != null) {
			inputWheel.setEnabled(enabled);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_adjust_options, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		inputWheel = view.findViewById(R.id.input);
		if (property != null) {
			inputWheel.setLimits(property.minValue, property.maxValue);
			inputWheel.setValue(property.value);
		}
		inputWheel.setListener(new ScrollingWheel.Listener() {
			@Override
			public void onValueChanged(int value) {
				if (property != null) {
					property.value = value;

					if (callback != null) {
						callback.onPropertyChanged(property);
					}
				}
			}
		});

		view.findViewById(R.id.btn_done).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (callback != null) {
					callback.onDone();
				}
			}
		});
	}

	public interface Callback {
		void onPropertyChanged(Property property);
		void onDone();
	}
}
