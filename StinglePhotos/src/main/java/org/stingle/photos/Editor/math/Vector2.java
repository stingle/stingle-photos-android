package org.stingle.photos.Editor.math;

public class Vector2 {
	private final float[] values;

	public Vector2() {
		values = new float[4];
		values[3] = 1f;
	}

	public Vector2(float x, float y) {
		values = new float[4];
		values[0] = x;
		values[1] = y;
		values[3] = 1f;
	}

	public void set(float x, float y) {
		values[0] = x;
		values[1] = y;
		values[2] = 0f;
		values[3] = 1f;
	}

	public void set(Vector2 p) {
		values[0] = p.values[0];
		values[1] = p.values[1];
		values[2] = 0f;
		values[3] = 1f;
	}

	public float getX() {
		return values[0];
	}

	public float getY() {
		return values[1];
	}

	public float length() {
		return (float) Math.hypot(values[0], values[1]);
	}

	public Vector2 unit() {
		return scale(1 / length());
	}

	public void translate(float dx, float dy) {
		values[0] += dx;
		values[1] += dy;
	}

	public Vector2 scale(float s) {
		values[0] *= s;
		values[1] *= s;

		return this;
	}

	public void rotate(float radians) {
		rotate(0f, 0f, radians);
	}

	public void rotate(float px, float py, float radians) {
		float sin = (float) Math.sin(radians);
		float cos = (float) Math.cos(radians);

		float x = values[0] - px;
		float y = values[1] - py;

		values[0] = x * cos - y * sin + px;
		values[1] = x * sin + y * cos + py;
	}

	public float dot(Vector2 v) {
		return values[0] * v.values[0] + values[1] * v.values[1];
	}

	public void project(Vector2 v) {
		float dot = dot(v);
		float targetLength = v.length();

		float projectedLength = dot / targetLength;

		set(v);
		unit();
		scale(projectedLength);
	}

	public float[] getValues() {
		return values;
	}
}
