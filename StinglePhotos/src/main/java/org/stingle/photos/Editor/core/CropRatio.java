package org.stingle.photos.Editor.core;

import android.content.Context;

import org.stingle.photos.R;

import java.util.Objects;

public class CropRatio {
	public static final int SPECIAL_NONE = 0;
	public static final int SPECIAL_FREE = 1;
	public static final int SPECIAL_ORIGINAL = 2;

	public static final CropRatio FREE = new CropRatio(0, 0, SPECIAL_FREE);
	public static final CropRatio ORIGINAL = new CropRatio(0, 0, SPECIAL_ORIGINAL);

	public final int special;

	public final int w;
	public final int h;

	public CropRatio(int w, int h) {
		this(w, h, SPECIAL_NONE);
	}

	public CropRatio(int w, int h, int special) {
		this.w = w;
		this.h = h;
		this.special = special;
	}

	public boolean isFree() {
		return special == SPECIAL_FREE;
	}

	public boolean isOriginal() {
		return special == SPECIAL_ORIGINAL;
	}

	public boolean isPortrait() {
		return w < h;
	}

	public boolean isLandscape() {
		return w > h;
	}

	public float getWidth(float height) {
		return height * w / h;
	}

	public float getHeight(float width) {
		return width * h / w;
	}

	public float getRatio() {
		return (float) w / h;
	}

	public CropRatio flip() {
		return new CropRatio(h, w, SPECIAL_NONE);
	}

	public String getName(Context context) {
		if (special == SPECIAL_FREE) {
			return context.getString(R.string.free);
		} else if (special == SPECIAL_ORIGINAL) {
			return context.getString(R.string.original);
		} else if (w == h) {
			return context.getString(R.string.square);
		} else {
			return context.getString(R.string.ratio_formatted, w, h);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CropRatio cropRatio = (CropRatio) o;
		return special == cropRatio.special && w == cropRatio.w && h == cropRatio.h;
	}

	@Override
	public int hashCode() {
		return Objects.hash(special, w, h);
	}
}