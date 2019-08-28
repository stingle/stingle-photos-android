package org.stingle.photos.Camera;

import android.content.Context;
import android.util.Range;
import android.util.Size;

import org.stingle.photos.R;

import java.io.Serializable;
import java.util.Comparator;

public class CameraImageSize {
	public final int width;
	public final int height;
	public final Range<Integer> fpsRange;
	public final int maxFps;
	public final float megapixel;
	public final String aspectRatio;
	protected Context context;

	public CameraImageSize(Context context, int width, int height, Range<Integer> fpsRange, int maxFps) {
		this.context = context;
		this.width = width;
		this.height = height;
		this.fpsRange = fpsRange;
		this.maxFps = maxFps;;
		this.megapixel = (float)Math.round((float)width*(float)height / 100000) /10;
		this.aspectRatio = getAspectRatio(width,height);
	}

	private static String getAspectRatio(int width, int height) {
		int gcf = greatestCommonFactor(width, height);
		if( gcf > 0 ) {
			// had a Google Play crash due to gcf being 0!? Implies width must be zero
			width /= gcf;
			height /= gcf;
		}
		return width + ":" + height;
	}
	private static int greatestCommonFactor(int a, int b) {
		while( b > 0 ) {
			int temp = b;
			b = a % b;
			a = temp;
		}
		return a;
	}

	@Override
	public String toString() {
		return "(" + this.aspectRatio + ") " + String.valueOf(this.megapixel) + " " + context.getString(R.string.megapixels);
	}

	public String getResolutionName(int width, int height){
		switch (height){
			case 2160:
				return " (UHD)";
			case 1080 :
				return " (Full HD)";
			case 720 :
				return " (HD)";
			case 480 :
				return " (SD)";
		}
		return "";
	}

	public String getPhotoString(){
		return this.toString();
	}

	public String getVideoString(){
		return "(" + this.aspectRatio + ") " + String.valueOf(width) + "x" + String.valueOf(height) + " " + String.valueOf(this.maxFps) + " " + context.getString(R.string.fps) + getResolutionName(width, height);
	}

	public Size getSize(){
		return new Size(width, height);
	}

	@Override
	public boolean equals(Object o) {
		if( !(o instanceof Size) )
			return false;
		CameraImageSize that = (CameraImageSize)o;
		return this.width == that.width && this.height == that.height;
	}

	public static class RangeSorter implements Comparator<int[]>, Serializable {
		private static final long serialVersionUID = 5802214721073728212L;
		@Override
		public int compare(int[] o1, int[] o2) {
			if (o1[0] == o2[0]) return o1[1] - o2[1];
			return o1[0] - o2[0];
		}
	}

	public static class SizeSorter implements Comparator<CameraImageSize>, Serializable {
		private static final long serialVersionUID = 5802214721073718212L;

		@Override
		public int compare(final CameraImageSize a, final CameraImageSize b) {
			return b.width * b.height - a.width * a.height;
		}

	}
}
