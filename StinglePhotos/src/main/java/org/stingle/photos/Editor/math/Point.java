package org.stingle.photos.Editor.math;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Objects;

public class Point implements Parcelable, Cloneable {
	public float x;
	public float y;

	public Point() {
		this(0f, 0f);
	}

	protected Point(Parcel in) {
		this(in.readFloat(), in.readFloat());
	}

	public Point(Point p) {
		this(p.x, p.y);
	}

	public Point(float x, float y) {
		set(x, y);
	}

	public void set(Point point) {
		set(point.x, point.y);
	}

	public void set(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public float distance(Point point) {
		float dx = point.x - x;
		float dy = point.y - y;

		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	public static void midPoint(Point a, Point b, Point outPoint) {
		outPoint.set((a.x + b.x) / 2f, (a.y + b.y) / 2f);
	}

	public Point rotate(float radians) {
		return rotate(0f, 0f, radians);
	}

	public Point rotate(float px, float py, float radians) {
		float sin = (float) Math.sin(radians);
		float cos = (float) Math.cos(radians);

		float x = this.x - px;
		float y = this.y - py;

		this.x = x * cos - y * sin + px;
		this.y = x * sin + y * cos + py;

		return this;
	}

	public Point scale(float s) {
		return scale(0f, 0f, s);
	}

	public Point scale(float px, float py, float s) {
		float x = this.x - px;
		float y = this.y - py;

		this.x = x * s + px;
		this.y = y * s + py;

		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Point point = (Point) o;
		return Float.compare(point.x, x) == 0 && Float.compare(point.y, y) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y);
	}

	@NonNull
	@Override
	public Point clone() {
		try {
			return (Point) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e.toString());
		}
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeFloat(x);
		dest.writeFloat(y);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public static final Creator<Point> CREATOR = new Creator<Point>() {
		@Override
		public Point createFromParcel(Parcel in) {
			return new Point(in);
		}

		@Override
		public Point[] newArray(int size) {
			return new Point[size];
		}
	};
}
