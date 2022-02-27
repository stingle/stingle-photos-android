package org.stingle.photos.Editor.math;

import java.util.Objects;

public class Vector {
	public static final Vector UP = new Vector(0f, 1f);
	public static final Vector RIGHT = new Vector(1f, 0f);
	public static final Vector DOWN = new Vector(0f, -1f);
	public static final Vector LEFT = new Vector(-1f, 0f);

	public float x;
	public float y;

	public Vector() {
		this(0f, 0f);
	}

	public Vector(float x, float y) {
		set(x, y);
	}

	public Vector(Vector v) {
		set(v.x, v.y);
	}

	public Vector(Point a, Point b) {
		set(a, b);
	}

	public Vector set(float x, float y) {
		this.x = x;
		this.y = y;

		return this;
	}

	public Vector set(Vector v) {
		return set(v.x, v.y);
	}

	public Vector set(Point a, Point b) {
		return set(b.x - a.x, b.y - a.y);
	}

	public Vector sub(Vector p) {
		x -= p.x;
		y -= p.y;

		return this;
	}

	public Vector add(Vector p) {
		x += p.x;
		y += p.y;

		return this;
	}

	public Vector scale(float s) {
		x *= s;
		y *= s;

		return this;
	}

	public Vector rotate(float radians) {
		float sin = (float) Math.sin(radians);
		float cos = (float) Math.cos(radians);

		set(x * cos - y * sin, x * sin + y * cos);

		return this;
	}

	public void map(Point p) {
		map(p, p);
	}

	public void map(Point inPoint, Point outPoint) {
		outPoint.set(inPoint.x + x, inPoint.y + y);
	}

	public Vector unit() {
		return scale(1 / length());
	}

	public float dot(Vector v) {
		return x * v.x + y * v.y;
	}

	public float length() {
		return (float) Math.sqrt(x * x + y * y);
	}

	public float rotation() {
		return (float) Math.atan2(y, x);
	}

	public float rotation(Vector v) {
		return (float) Math.acos(rotationCosine(v));
	}

	public float rotationCosine(Vector v) {
		return dot(v) / length() / v.length();
	}

	public Vector project(Vector targetVector) {
		float dot = dot(targetVector);
		float targetLength = targetVector.length();

		float projectedLength = dot / targetLength;

		return set(targetVector).unit().scale(projectedLength);
	}

	public static void project(Vector a, Vector b, Vector out) {
		out.set(b).unit().scale(a.dot(b) / b.length());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Vector vector = (Vector) o;
		return Float.compare(vector.x, x) == 0 && Float.compare(vector.y, y) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y);
	}
}
