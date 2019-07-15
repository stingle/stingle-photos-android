package com.fenritz.safecam.Gallery;

import android.graphics.Bitmap;

import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Util.MemcacheSizeable;

public class GalleryDecItem implements MemcacheSizeable {
	public Bitmap bitmap = null;
	public int fileType = Crypto.FILE_TYPE_PHOTO;

	@Override
	public int getSize() {
		return bitmap.getRowBytes() * bitmap.getHeight();
	}
}
