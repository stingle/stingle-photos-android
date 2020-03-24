package org.stingle.photos.Gallery.Helpers;

public interface IDragSelectAdapter {

	void setSelected(int index, boolean selected);

	boolean isIndexSelectable(int index);

	int getItemCount();
}
