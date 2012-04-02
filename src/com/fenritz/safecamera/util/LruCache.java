package com.fenritz.safecamera.util;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

class LruCacheLinkedHashMap extends LinkedHashMap {
	private static final long serialVersionUID = -2143302001548963893L;
	private final int maxEntries;

	public LruCacheLinkedHashMap(final int maxEntries) {
		super(maxEntries + 1, 1.0f, true);
		this.maxEntries = maxEntries;
	}

	@Override
	protected boolean removeEldestEntry(final Map.Entry eldest) {
		return super.size() > maxEntries;
	}

	@Override
	public Object remove(java.lang.Object key) {
		SoftReference softReferanceDrawable = (SoftReference) get(key);
		unBindDrawable((Drawable)softReferanceDrawable.get());
		return super.remove(key);
	}

	private void unBindDrawable(Drawable drawable) {
		if (drawable != null) {
			drawable.setCallback(null);
			if (!((BitmapDrawable) drawable).getBitmap().isRecycled()) {
				((BitmapDrawable) drawable).getBitmap().recycle();
			}
			drawable = null;
		}
	}

}