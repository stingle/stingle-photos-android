package org.stingle.photos.Gallery.Gallery;

import org.stingle.photos.Db.Objects.StingleFile;

public interface GalleryFragmentParent {
	abstract boolean onClick(StingleFile file);
	abstract boolean onLongClick(int index);
	abstract boolean onSelectionChanged(int index);
	abstract void scrolledDown();
	abstract void scrolledUp();
	abstract boolean isSyncBarDisabled();

}
