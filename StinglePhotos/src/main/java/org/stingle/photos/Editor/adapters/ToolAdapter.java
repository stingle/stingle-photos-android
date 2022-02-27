package org.stingle.photos.Editor.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.stingle.photos.Editor.core.Tool;
import org.stingle.photos.R;

import java.util.Objects;

public class ToolAdapter extends ListAdapter<Tool, ToolAdapter.ViewHolder> {
	private Tool activeTool;

	private LayoutInflater layoutInflater;
	private Context context;

	private Callback callback;

	public ToolAdapter(Context context) {
		super(new ToolDiffHelper());

		this.context = context;

		layoutInflater = LayoutInflater.from(context);
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public void setActiveTool(Tool tool) {
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

	public Tool getActiveTool() {
		return activeTool;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(layoutInflater.inflate(R.layout.item_tool, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		Tool tool = getItem(position);

		holder.itemTextView.setText(tool.titleResId);
		if (tool.hasIcon) {
			Drawable drawable = ContextCompat.getDrawable(context, tool.iconResId);
			if (drawable != null) {
				drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
			}

			holder.itemTextView.setCompoundDrawablesRelative(drawable, null, null, null);
		} else {
			holder.itemTextView.setCompoundDrawablesRelative(null, null, null, null);
		}
		holder.itemView.setSelected(Objects.equals(tool, activeTool));
		holder.itemView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				int adapterPosition = holder.getAdapterPosition();
				if (adapterPosition != RecyclerView.NO_POSITION) {
					if (callback != null) {
						callback.onToolSelected(getItem(adapterPosition));
					}
				}
			}
		});
	}

	public int findToolPosition(Tool tool) {
		for (int i = 0; i < getItemCount(); i++) {
			if (Objects.equals(getItem(i), tool)) {
				return i;
			}
		}

		return RecyclerView.NO_POSITION;
	}


	static class ViewHolder extends RecyclerView.ViewHolder {

		Button itemTextView;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);

			itemTextView = (Button) itemView;
		}
	}

	private static class ToolDiffHelper extends DiffUtil.ItemCallback<Tool> {
		@Override
		public boolean areItemsTheSame(@NonNull Tool oldItem, @NonNull Tool newItem) {
			return oldItem.equals(newItem);
		}

		@Override
		public boolean areContentsTheSame(@NonNull Tool oldItem, @NonNull Tool newItem) {
			return oldItem.equals(newItem);
		}
	}

	public interface Callback {
		void onToolSelected(Tool tool);
	}
}
