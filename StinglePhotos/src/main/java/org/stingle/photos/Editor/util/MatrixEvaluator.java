package org.stingle.photos.Editor.util;

import android.animation.TypeEvaluator;

import com.google.android.material.math.MathUtils;

public class MatrixEvaluator implements TypeEvaluator<float[]> {
	private float[] matrix;

	public MatrixEvaluator() {
		matrix = new float[16];
	}

	@Override
	public float[] evaluate(float fraction, float[] startValue, float[] endValue) {
		for (int i = 0; i < 16; i++) {
			matrix[i] = MathUtils.lerp(startValue[i], endValue[i], fraction);
		}

		return matrix;
	}
}
