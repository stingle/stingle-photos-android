package org.stingle.photos.Gallery;

import org.stingle.photos.Db.StingleDbFile;

public interface GalleryFragmentParent {
	abstract boolean onClick(StingleDbFile file);
	abstract boolean onLongClick(int index);
	abstract boolean onSelectionChanged(int index);
	abstract void scrolledDown();
	abstract void scrolledUp();
}
