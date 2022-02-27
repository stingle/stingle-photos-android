package org.stingle.photos.Editor.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.Editor.core.CropRatio;
import org.stingle.photos.R;

import java.util.Objects;

public class CropRatioAdapter extends ListAdapter<CropRatio, CropRatioAdapter.ViewHolder> {
	private CropRatio activeCropRatio;

	private LayoutInflater layoutInflater;

	private Context context;

	private Callback callback;

	public CropRatioAdapter(Context context) {
		super(new CropRatioDiffHelper());

		this.context = context;
		layoutInflater = LayoutInflater.from(context);
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public void setActiveCropRatio(CropRatio cropRatio) {
		int previousPosition = findCropRatioPosition(cropRatio);
		int currentPosition = findCropRatioPosition(activeCropRatio);

		activeCropRatio = cropRatio;

		if (previousPosition != RecyclerView.NO_POSITION) {
			notifyItemChanged(previousPosition);
		}

		if (currentPosition != RecyclerView.NO_POSITION) {
			notifyItemChanged(currentPosition);
		}
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(layoutInflater.inflate(R.layout.item_crop_ratio, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		CropRatio cropRatio = getItem(position);

		holder.itemButton.setText(cropRatio.getName(context));
		holder.itemButton.setActivated(Objects.equals(cropRatio, activeCropRatio));
		holder.itemButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int adapterPosition = holder.getAdapterPosition();
				if (adapterPosition != RecyclerView.NO_POSITION) {
					if (callback != null) {
						callback.onCropRatioSelected(getItem(adapterPosition));
					}
				}
			}
		});
	}

	private int findCropRatioPosition(CropRatio ratio) {
		for (int i = 0; i < getItemCount(); i++) {
			if (Objects.equals(getItem(i), ratio)) {
				return i;
			}
		}

		return RecyclerView.NO_POSITION;
	}


	static class ViewHolder extends RecyclerView.ViewHolder {

		Button itemButton;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);

			itemButton = (Button) itemView;
		}
	}

	private static class CropRatioDiffHelper extends DiffUtil.ItemCallback<CropRatio> {
		@Override
		public boolean areItemsTheSame(@NonNull CropRatio oldItem, @NonNull CropRatio newItem) {
			return oldItem.equals(newItem);
		}

		@Override
		public boolean areContentsTheSame(@NonNull CropRatio oldItem, @NonNull CropRatio newItem) {
			return oldItem.equals(newItem);
		}
	}

	public interface Callback {
		void onCropRatioSelected(CropRatio cropRatio);
	}
}
