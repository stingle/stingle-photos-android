package com.fenritz.safecam.Widget;

public interface IDragSelectAdapter {

	void setSelected(int index, boolean selected);

	boolean isIndexSelectable(int index);

	int getItemCount();
}
