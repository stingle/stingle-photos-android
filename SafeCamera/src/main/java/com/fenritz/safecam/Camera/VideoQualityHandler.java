package com.fenritz.safecam.Camera;

import android.media.CamcorderProfile;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VideoQualityHandler {
	private static final String TAG = "Stingle_Error";

	public static class Dimension2D {
		final int width;
		final int height;

		public Dimension2D(int width, int height) {
			this.width = width;
			this.height = height;
		}
	}

	// video_quality can either be:
	// - an int, in which case it refers to a CamcorderProfile
	// - of the form [CamcorderProfile]_r[width]x[height] - we use the CamcorderProfile as a base, and override the video resolution - this is needed to support resolutions which don't have corresponding camcorder profiles
	private List<String> video_quality;
	private int current_video_quality = -1; // this is an index into the video_quality array, or -1 if not found (though this shouldn't happen?)
	private List<VideoSize> video_sizes;
	private List<VideoSize> video_sizes_high_speed; // may be null if high speed not supported

	void resetCurrentQuality() {
		video_quality = null;
		current_video_quality = -1;
	}


	public void initialiseVideoQualityFromProfiles(List<Integer> profiles, List<Dimension2D> dimensions) {
		video_quality = new ArrayList<>();
		boolean done_video_size[] = null;
		if( video_sizes != null ) {
			done_video_size = new boolean[video_sizes.size()];
			for(int i=0;i<video_sizes.size();i++)
				done_video_size[i] = false;
		}
		if( profiles.size() != dimensions.size() ) {
			Log.e(TAG, "profiles and dimensions have unequal sizes");
			throw new RuntimeException(); // this is a programming error
		}
		for(int i=0;i<profiles.size();i++) {
			Dimension2D dim = dimensions.get(i);
			addVideoResolutions(done_video_size, profiles.get(i), dim.width, dim.height);
		}
	}

	// Android docs and FindBugs recommend that Comparators also be Serializable
	private static class SortVideoSizesComparator implements Comparator<VideoSize>, Serializable {
		private static final long serialVersionUID = 5802214721033718212L;

		@Override
		public int compare(final VideoSize a, final VideoSize b) {
			return b.width * b.height - a.width * a.height;
		}
	}

	public void sortVideoSizes() {
		Collections.sort(this.video_sizes, new SortVideoSizesComparator());
	}

	private void addVideoResolutions(boolean done_video_size[], int base_profile, int min_resolution_w, int min_resolution_h) {
		if( video_sizes == null ) {
			return;
		}

		for(int i=0;i<video_sizes.size();i++) {
			if( done_video_size[i] )
				continue;
			VideoSize size = video_sizes.get(i);
			if( size.width == min_resolution_w && size.height == min_resolution_h ) {
				String str = "" + base_profile;
				video_quality.add(str);
				done_video_size[i] = true;
			}
			else if( base_profile == CamcorderProfile.QUALITY_LOW || size.width * size.height >= min_resolution_w*min_resolution_h ) {
				String str = "" + base_profile + "_r" + size.width + "x" + size.height;
				video_quality.add(str);
				done_video_size[i] = true;
			}
		}
	}

	public List<String> getSupportedVideoQuality() {
		return this.video_quality;
	}

	int getCurrentVideoQualityIndex() {
		return this.current_video_quality;
	}

	void setCurrentVideoQualityIndex(int current_video_quality) {
		this.current_video_quality = current_video_quality;
	}

	public String getCurrentVideoQuality() {
		if( current_video_quality == -1 )
			return null;
		return video_quality.get(current_video_quality);
	}

	public List<VideoSize> getSupportedVideoSizes() {
		return this.video_sizes;
	}

	public List<VideoSize> getSupportedVideoSizesHighSpeed() {
		return this.video_sizes_high_speed;
	}

	/** Whether the requested fps is supported, without relying on high-speed mode.
	 *  Typically caller should first check videoSupportsFrameRateHighSpeed().
	 */
	/*public boolean videoSupportsFrameRate(int fps) {
		//return true;
		return CameraController.CameraFeatures.supportsFrameRate(this.video_sizes, fps);
	}*/

	/** Whether the requested fps is supported as a high-speed mode.
	 */
	/*public boolean videoSupportsFrameRateHighSpeed(int fps) {
		//return true;
		return CameraController.CameraFeatures.supportsFrameRate(this.video_sizes_high_speed, fps);
	}*/

	/*VideoSize findVideoSizeForFrameRate(int width, int height, double fps) {
		VideoSize requested_size = new VideoSize(width, height);
		VideoSize best_video_size = CameraController.CameraFeatures.findSize(this.getSupportedVideoSizes(), requested_size, fps, false);
		if( best_video_size == null && this.getSupportedVideoSizesHighSpeed() != null ) {
			best_video_size = CameraController.CameraFeatures.findSize(this.getSupportedVideoSizesHighSpeed(), requested_size, fps, false);
		}
		return best_video_size;
	}*/

	private static VideoSize getMaxVideoSize(List<VideoSize> sizes) {
		int max_width = -1, max_height = -1;
		for(VideoSize size : sizes) {
			if( max_width == -1 || size.width*size.height > max_width*max_height ) {
				max_width = size.width;
				max_height = size.height;
			}
		}
		return new VideoSize(max_width, max_height);
	}

	/** Returns the maximum supported (non-high-speed) video size.
	 */
	VideoSize getMaxSupportedVideoSize() {
		return getMaxVideoSize(video_sizes);
	}

	/** Returns the maximum supported high speed video size.
	 */
	VideoSize getMaxSupportedVideoSizeHighSpeed() {
		return getMaxVideoSize(video_sizes_high_speed);
	}

	public void setVideoSizes(List<VideoSize> video_sizes) {
		this.video_sizes = video_sizes;
		this.sortVideoSizes();
	}

	public void setVideoSizesHighSpeed(List<VideoSize> video_sizes_high_speed) {
		this.video_sizes_high_speed = video_sizes_high_speed;
	}
}
