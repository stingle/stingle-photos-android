package org.stingle.photos.Editor.util;

import org.stingle.photos.Editor.math.Point;

public class MathUtils {
	public static float distance(Point p0, Point p1) {
		return distance(p0.x, p0.y, p1.x, p1.y);
	}

	public static float distance(float x1, float y1, float x2, float y2) {
		return (float) Math.hypot(x1 - x2, y1 - y2);
	}

	public static void rotatePoint(float[] point, float px, float py, float radians) {
		float sin = (float) Math.sin(radians);
		float cos = (float) Math.cos(radians);

		float x = point[0] - px;
		float y = point[1] - py;

		point[0] = x * cos - y * sin + px;
		point[1] = x * sin + y * cos + py;
	}
}
