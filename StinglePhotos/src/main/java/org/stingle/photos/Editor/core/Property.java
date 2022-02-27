package org.stingle.photos.Editor.core;

import com.google.android.material.math.MathUtils;

public class Property {
	public final String name;
	public final int minValue;
	public final int maxValue;
	private final float minNormalizedValue;
	private final float normalNormalizedValue;
	private final float maxNormalizedValue;
	public int value;

	public Property(String name, int minValue, int maxValue) {
		this(name, minValue, maxValue, -1f, 0f, 1f);
	}

	public Property(String name, int minValue, int maxValue, float minNormalizedValue, float normalNormalizedValue, float maxNormalizedValue) {
		this.name = name;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.minNormalizedValue = minNormalizedValue;
		this.normalNormalizedValue = normalNormalizedValue;
		this.maxNormalizedValue = maxNormalizedValue;
	}

	public float getNormalizedValue() {
		if (value == 0) {
			return normalNormalizedValue;
		} else if (value < 0) {
			return MathUtils.lerp(normalNormalizedValue, minNormalizedValue, (float) value / minValue);
		} else {
			return MathUtils.lerp(normalNormalizedValue, maxNormalizedValue, (float) value / maxValue);
		}
	}
}
