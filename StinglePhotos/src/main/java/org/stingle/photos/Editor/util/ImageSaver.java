package org.stingle.photos.Editor.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.math.Vector2;

public class ImageSaver {
	static {
		System.loadLibrary("editor");
	}

	public static Bitmap saveImage(Image image) {
		float scaleDown = (float) image.getResizedWidth() / image.getWidth();

		float cropWidth = scaleDown * (image.getOrientation() % 2 == 0 ? image.getCropWidth() : image.getCropHeight());
		float cropHeight = scaleDown * (image.getOrientation() % 2 == 0 ? image.getCropHeight() : image.getCropWidth());

		Bitmap resultBitmap = Bitmap.createBitmap((int) cropWidth, (int) cropHeight, Bitmap.Config.ARGB_8888);

		Canvas resultCanvas = new Canvas(resultBitmap);
		resultCanvas.translate(cropWidth / 2f, cropHeight / 2f);
		resultCanvas.rotate((float) -image.getCropRotation() + image.getOrientation() * 90);
		resultCanvas.scale(scaleDown, scaleDown);
		resultCanvas.translate(-image.getCropCenter().x, -image.getCropCenter().y);
		resultCanvas.drawBitmap(image.getBitmap(), 0f, 0f, null);

		processImage(resultBitmap, image.getBrightnessValueNormalized(), image.getContrastValueNormalized(),
				image.getWhitePointValueNormalized(), image.getHighlightsValueNormalized(), image.getShadowsValueNormalized(),
				image.getBlackPointValueNormalized(), image.getSaturationValueNormalized(), image.getWarmthValueNormalized(),
				image.getTintValueNormalized(), image.getSharpnessValueNormalized(), image.getDenoiseValueNormalized(), image.getVignetteValueNormalized());

		return resultBitmap;
	}

	private static native void processImage(Bitmap outBitmap, float brightness, float contrast, float whitePoint,
									   float highlights, float shadows, float blackPoint, float saturation, float warmth, float tint,
									   float sharpness, float denoise, float vignette);
}
