package org.stingle.photos.Editor.core;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.stingle.photos.R;

import java.util.ArrayList;
import java.util.List;

public enum AdjustOption {
	BRIGHTNESS("brightness", R.string.brightness, R.drawable.ic_brightness),
	CONTRAST("contrast", R.string.contrast, R.drawable.ic_contrast),
	WHITE_POINT("white_point", R.string.white_point, R.drawable.ic_white_point),
	HIGHLIGHTS("highlights", R.string.highlights, R.drawable.ic_highlights),
	SHADOWS("shadows", R.string.shadows, R.drawable.ic_shadows),
	BLACK_POINT("black_point", R.string.black_point, R.drawable.ic_black_point),
	SATURATION("saturation", R.string.saturation, R.drawable.ic_saturation),
	WARMTH("warmth", R.string.warmth, R.drawable.ic_warmth),
	TINT("tint", R.string.tint, R.drawable.ic_tint),
	SHARPEN("sharpen", R.string.sharpen, R.drawable.ic_sharpen),
	DENOISE("denoise", R.string.denoise, R.drawable.ic_denoise),
	VIGNETTE("vignette", R.string.vignette, R.drawable.ic_vignette);

	public static List<AdjustOption> getToolList() {
		List<AdjustOption> toolList = new ArrayList<>();
		toolList.add(AdjustOption.BRIGHTNESS);
		toolList.add(AdjustOption.CONTRAST);
		toolList.add(AdjustOption.WHITE_POINT);
		toolList.add(AdjustOption.HIGHLIGHTS);
		toolList.add(AdjustOption.SHADOWS);
		toolList.add(AdjustOption.BLACK_POINT);
		toolList.add(AdjustOption.SATURATION);
		toolList.add(AdjustOption.WARMTH);
		toolList.add(AdjustOption.TINT);
		toolList.add(AdjustOption.SHARPEN);
		toolList.add(AdjustOption.DENOISE);
		toolList.add(AdjustOption.VIGNETTE);

		return toolList;
	}

	@StringRes
	public final int titleResId;

	@DrawableRes
	public final int iconResId;

	public final String name;

	AdjustOption(String name, int titleResId, int iconResId) {
		this.name = name;
		this.titleResId = titleResId;
		this.iconResId = iconResId;
	}
}
