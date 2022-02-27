package org.stingle.photos.Editor.math;

import org.stingle.photos.Editor.util.MathUtils;

public class Line {
	private static final Vector tmp0 = new Vector();
	private static final Vector tmp1 = new Vector();
	private static final Point tmp2 = new Point();

	public final Point p0 = new Point();
	public final Point p1 = new Point();

	public Line(float x0, float y0, float x1, float y1) {
		this.p0.set(x0, y0);
		this.p1.set(x1, y1);
	}

	public Line(Point p0, Point p1) {
		this.p0.set(p0);
		this.p1.set(p1);
	}

	public Line() {

	}

	public void set(Line l) {
		p0.set(l.p0);
		p1.set(l.p1);
	}

	public void set(Point p0, Point p1) {
		this.p0.set(p0);
		this.p1.set(p1);
	}

	public void set(float x0, float y0, float x1, float y1) {
		p0.set(x0, y0);
		p1.set(x1, y1);
	}

	public float len() {
		return MathUtils.distance(p0, p1);
	}

	public float distanceTo(Point p) {
		// From https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line#Line_defined_by_two_points
		float dy = p1.y - p0.y;
		float dx = p1.x - p0.x;

		return (float) (Math.abs(dy * p.x - dx * p.y + p1.x * p0.y - p1.y * p0.x) /
				Math.sqrt(dy * dy + dx * dx));
	}

	public float computeProjectionLength(Point p) {
		tmp0.set(p0, p1);
		tmp1.set(p, p0);

		return tmp0.dot(tmp1) / len();
	}

	public void computeProjectionPoint(Point p, Point outPoint) {
		// From http://www.sunshine2k.de/coding/java/PointOnLine/PointOnLine.html
		tmp0.set(p0, p1);
		tmp1.set(p, p0);

		float len1 = tmp0.length();
		float len2 = tmp1.length();

		float cos = tmp0.dot(tmp1) / len1 / len2;
		float projectionDistance = cos * len2;

		tmp0.scale(projectionDistance / len1).map(p0, outPoint);
	}

	public void computeIntersectionPoint(Line l, Point outP) {
		// From https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection

		float d = (p0.x - p1.x) * (l.p0.y - l.p1.y) - (p0.y - p1.y) * (l.p0.x - l.p1.x);

		outP.x = ((p0.x * p1.y - p0.y * p1.x) * (l.p0.x - l.p1.x) - (p0.x - p1.x) * (l.p0.x * l.p1.y - l.p0.y * l.p1.x)) / d;
		outP.y = ((p0.x * p1.y - p0.y * p1.x) * (l.p0.y - l.p1.y) - (p0.y - p1.y) * (l.p0.x * l.p1.y - l.p0.y * l.p1.x)) / d;
	}

	public Point intersect(Line line) {
		float d = (p0.x - p1.x) * (line.p0.y - line.p1.y) - (p0.y - p1.y) * (line.p0.x - line.p1.x);
		
		float t = ((p0.x - line.p0.x) * (line.p0.y - line.p1.y) - (p0.y - line.p0.y) * (line.p0.x - line.p1.x)) / d;
		float u = ((p0.x - line.p0.x) * (p0.y - p1.y) - (p0.y - line.p0.y) * (p0.x - p1.x)) / d;

		if (t >= 0f && t <= 1f && u >= 0f && u <= 1f) {
			return new Point(p0.x + t * (p1.x - p0.x), p0.y + t * (p1.y - p0.y));
		} else {
			return null;
		}
	}

	public float intersectFraction(Line line) {
		float d = (p0.x - p1.x) * (line.p0.y - line.p1.y) - (p0.y - p1.y) * (line.p0.x - line.p1.x);

		float t = ((p0.x - line.p0.x) * (line.p0.y - line.p1.y) - (p0.y - line.p0.y) * (line.p0.x - line.p1.x)) / d;
		float u = ((p0.x - line.p0.x) * (p0.y - p1.y) - (p0.y - line.p0.y) * (p0.x - p1.x)) / d;

		if (t >= 0f && t <= 1f && u >= 0f && u <= 1f) {
			return t;
		} else {
			return Float.NaN;
		}
	}

	public Point findPointWithX(float x) {
		float t = (x - p0.x) / (p1.x - p0.x);
		if (t >= 0f && t <= 1f) {
			return new Point(x, (1 - t) * p0.y + t * p1.y);
		}

		return null;
	}

	public Point findPointWithY(float y) {
		float t = (y - p0.y) / (p1.y - p0.y);
		if (t >= 0f && t <= 1f) {
			return new Point((1 - t) * p0.x + t * p1.x, y);
		}

		return null;
	}

	public void findClosestPoint(Point p, Point outPoint) {
		tmp0.set(p, p0);
		tmp1.set(p0, p1);

		if (tmp0.rotation(tmp1) > Math.PI / 2f) {
			outPoint.set(p0);
		} else {
			tmp0.set(p, p1);
			tmp1.set(p1, p0);

			if (tmp0.rotation(tmp1) > Math.PI / 2f) {
				outPoint.set(p1);
			} else {
				computeProjectionPoint(p, outPoint);
			}
		}
	}
}
