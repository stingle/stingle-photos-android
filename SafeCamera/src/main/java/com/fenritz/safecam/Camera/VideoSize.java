package com.fenritz.safecam.Camera;

import android.util.Size;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VideoSize {
	public final int width;
	public final int height;
	public boolean supports_burst; // for photo
	final List<int[]> fps_ranges; // for video
	public final boolean high_speed; // for video

	public VideoSize(int width, int height, List<int[]> fps_ranges, boolean high_speed) {
		this.width = width;
		this.height = height;
		this.supports_burst = true;
		this.fps_ranges = fps_ranges;
		this.high_speed = high_speed;
		Collections.sort(this.fps_ranges, new RangeSorter());
	}

	public VideoSize(int width, int height) {
		this(width, height, new ArrayList<int[]>(), false);
	}

	boolean supportsFrameRate(double fps) {
		for (int[] f : this.fps_ranges) {
			if (f[0] <= fps && fps <= f[1])
				return true;
		}
		return false;
	}

	public int getMaxFramerate(){
		return fps_ranges.get(0)[1];
	}

	@Override
	public boolean equals(Object o) {
		if( !(o instanceof Size) )
			return false;
		VideoSize that = (VideoSize)o;
		return this.width == that.width && this.height == that.height;
	}

	@Override
	public int hashCode() {
		// must override this, as we override equals()
		// can't use:
		//return Objects.hash(width, height);
		// as this requires API level 19
		// so use this from http://stackoverflow.com/questions/11742593/what-is-the-hashcode-for-a-custom-class-having-just-two-int-properties
		return width*31 + height;
	}

	public static class RangeSorter implements Comparator<int[]>, Serializable {
		private static final long serialVersionUID = 5802214721073728212L;
		@Override
		public int compare(int[] o1, int[] o2) {
			if (o1[0] == o2[0]) return o1[1] - o2[1];
			return o1[0] - o2[0];
		}
	}

	public static class SizeSorter implements Comparator<VideoSize>, Serializable {
		private static final long serialVersionUID = 5802214721073718212L;

		@Override
		public int compare(final VideoSize a, final VideoSize b) {
			return b.width * b.height - a.width * a.height;
		}

	}
}
