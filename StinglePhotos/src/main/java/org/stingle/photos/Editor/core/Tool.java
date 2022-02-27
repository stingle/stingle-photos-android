package org.stingle.photos.Editor.core;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.stingle.photos.R;

import java.util.ArrayList;
import java.util.List;

public enum Tool {
	CROP(R.string.crop),
	ADJUST(R.string.adjust),
	MISC(R.string.tools);

	public static List<Tool> getToolList() {
		List<Tool> toolList = new ArrayList<>();
		toolList.add(Tool.CROP);
		toolList.add(Tool.ADJUST);
		toolList.add(Tool.MISC);

		return toolList;
	}

	@StringRes
	public final int titleResId;

	@DrawableRes
	public final int iconResId;

	public final boolean hasIcon;

	Tool(int titleResId, int iconResId) {
		this.titleResId = titleResId;
		this.iconResId = iconResId;
		hasIcon = true;
	}

	Tool(int titleResId) {
		this.titleResId = titleResId;
		this.iconResId = 0;
		hasIcon = false;
	}
}
