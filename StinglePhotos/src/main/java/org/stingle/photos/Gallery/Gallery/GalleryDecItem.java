package org.stingle.photos.Gallery.Gallery;

import android.graphics.Bitmap;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Util.MemcacheSizeable;

public class GalleryDecItem implements MemcacheSizeable {
	public Bitmap bitmap = null;
	public int fileType = Crypto.FILE_TYPE_PHOTO;

	@Override
	public int getSize() {
		return bitmap.getRowBytes() * bitmap.getHeight();
	}
}
