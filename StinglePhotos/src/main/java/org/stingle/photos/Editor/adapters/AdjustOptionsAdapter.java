package org.stingle.photos.Editor.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.stingle.photos.Editor.core.AdjustOption;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.core.Property;
import org.stingle.photos.R;

import java.util.Objects;

public class AdjustOptionsAdapter extends ListAdapter<AdjustOption, AdjustOptionsAdapter.ViewHolder> {
	private AdjustOption activeTool;

	private LayoutInflater layoutInflater;
	private Context context;

	private Callback callback;

	private Image image;

	public AdjustOptionsAdapter(Context context) {
		super(new AdjustOptionDiffHelper());

		this.context = context;

		layoutInflater = LayoutInflater.from(context);
	}

	public void setImage(Image image) {
		this.image = image;
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public void setActiveTool(AdjustOption tool) {
		if (!Objects.equals(activeTool, tool)) {
			int previousPosition = findToolPosition(tool);
			int currentPosition = findToolPosition(activeTool);

			activeTool = tool;

			if (previousPosition != RecyclerView.NO_POSITION) {
				notifyItemChanged(previousPosition);
			}

			if (currentPosition != RecyclerView.NO_POSITION) {
				notifyItemChanged(currentPosition);
			}
		}
	}

	public AdjustOption getActiveTool() {
		return activeTool;
	}

	public void notifyPropertyChanged(Property property) {
		for (int i = 0; i < getItemCount(); i++) {
			if (Objects.equals(getItem(i).name, property.name)) {
				notifyItemChanged(i);
				break;
			}
		}
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(layoutInflater.inflate(R.layout.item_adjust_tool, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		AdjustOption tool = getItem(position);
		Property property = image != null ? image.findPropertyWithName(tool.name) : null;

		holder.textView.setText(tool.titleResId);
		holder.iconView.setIconResource(tool.iconResId);
		holder.itemView.setSelected(Objects.equals(tool, activeTool));
		holder.itemView.setActivated(property != null && property.value != 0);

		View.OnClickListener itemClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int adapterPosition = holder.getAdapterPosition();
				if (adapterPosition != RecyclerView.NO_POSITION) {
					if (callback != null) {
						callback.onToolSelected(getItem(adapterPosition));
					}
				}
			}
		};

		holder.itemView.setOnClickListener(itemClickListener);
		holder.iconView.setOnClickListener(itemClickListener);
	}

	public int findToolPosition(AdjustOption tool) {
		for (int i = 0; i < getItemCount(); i++) {
			if (Objects.equals(getItem(i), tool)) {
				return i;
			}
		}

		return RecyclerView.NO_POSITION;
	}


	static class ViewHolder extends RecyclerView.ViewHolder {
		MaterialButton iconView;
		TextView textView;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);

			iconView = itemView.findViewById(R.id.icon);
			textView = itemView.findViewById(R.id.text);
		}
	}

	private static class AdjustOptionDiffHelper extends DiffUtil.ItemCallback<AdjustOption> {
		@Override
		public boolean areItemsTheSame(@NonNull AdjustOption oldItem, @NonNull AdjustOption newItem) {
			return oldItem.equals(newItem);
		}

		@Override
		public boolean areContentsTheSame(@NonNull AdjustOption oldItem, @NonNull AdjustOption newItem) {
			return oldItem.equals(newItem);
		}
	}

	public interface Callback {
		void onToolSelected(AdjustOption tool);
	}
}
