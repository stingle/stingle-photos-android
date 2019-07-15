package com.fenritz.safecam.Gallery;

public interface IDragSelectAdapter {

	void setSelected(int index, boolean selected);

	boolean isIndexSelectable(int index);

	int getItemCount();
}
