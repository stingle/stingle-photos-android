package org.stingle.photos.Editor.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.math.Vector2;

public class ImageSaver {
	private static Vector2 p = new Vector2();

	public static Bitmap saveImage(Image image) {
		Bitmap resultBitmap = Bitmap.createBitmap((int) image.getCropWidth(), (int) image.getCropHeight(), image.getBitmap().getConfig());

		Vector2 p = new Vector2();

		Vec3 temperatureVector = colorTemperatureToRGB(mix(1000.0, 40000.0, (image.getWarmthValueNormalized() + 1.0) / 2.0));

		for (int i = 0; i < resultBitmap.getHeight(); i++) {
			for (int j = 0; j < resultBitmap.getWidth(); j++) {
				int pixel = getPixel(image, j, i);

				Vec3 color = getColorVec(pixel);
				color = processPixel(image, color, j, i, temperatureVector);

				resultBitmap.setPixel(j, i, getColorInt(Color.alpha(pixel), color));
			}
		}

		return resultBitmap;
	}

	private static int getPixel(Image image, double x, double y) {
		p.set((float) x, (float) y);
		p.translate(-image.getCropWidth() / 2f, -image.getCropHeight() / 2f);
		p.rotate(image.getCropRotationRadians());
		p.translate(image.getCropCenter().x, image.getCropCenter().y);

		return image.getBitmap().getPixel(clamp((int) p.getX(), 0, image.getWidth() - 1), clamp((int) p.getY(), 0, image.getHeight() - 1));
	}

	private static int getColorInt(int a, Vec3 v) {
		return Color.argb(a, (int) (v.r * 255), (int) (v.g * 255), (int) (v.b * 255));
	}

	private static Vec3 getColorVec(int color) {
		return new Vec3(Color.red(color) / 255.0,
				Color.green(color) / 255.0, Color.blue(color) / 255.0);
	}

	private static Vec3 getColorVec(Image image, double x, double y) {
		int colorInt = getPixel(image, x, y);

		return getColorVec(colorInt);
	}

	static Vec3 applyFilters(Image image, Vec3 inColor, Vec3 temperatureVector) {
		// Brightness
		inColor.add(image.getBrightnessValueNormalized());

		// Contrast
		inColor.add(-0.5f);
		inColor.mul(image.getContrastValueNormalized());
		inColor.add(0.5f);

		// Highlights and Shadows

		double luminance = inColor.dot(0.3f);

		double highlight = clamp((1.0 - (Math.pow(1.0 - luminance, 1.0f / (2.0 - image.getHighlightsValueNormalized())) +
				-0.8 * Math.pow(1.0 - luminance, 2.0 / (2.0 - image.getHighlightsValueNormalized())))) - luminance, -1.0, 0.0);

		double shadow = clamp((Math.pow(luminance, 1.0 / (image.getShadowsValueNormalized() + 1.0)) + -0.76 * Math.pow(luminance,
				2.0 / (image.getShadowsValueNormalized() + 1.0))) - luminance, 0.0, 1.0);

		inColor.mul(1.0 / luminance);
		inColor.mul(luminance + shadow + highlight);

		// Saturation

		luminance = inColor.dot(0.2125, 0.7154, 0.0721);

		Vec3 greyScaleColor = new Vec3(luminance);

		inColor = mix(greyScaleColor, inColor, image.getSaturationValueNormalized());

		// Warmth/Temperature
		inColor.mul(temperatureVector);

		return inColor;
	}

	static Vec3 processPixel(Image image, Vec3 inColor, double x, double y, Vec3 temperatureVector) {
		applyFilters(image, inColor, temperatureVector);

		// Sharpness
		Vec3 leftTextureColor = applyFilters(image, getColorVec(image, x - 1.0, y), temperatureVector);
		Vec3 rightTextureColor = applyFilters(image, getColorVec(image, x + 1.0, y), temperatureVector);
		Vec3 topTextureColor = applyFilters(image, getColorVec(image, x, y - 1.0), temperatureVector);
		Vec3 bottomTextureColor = applyFilters(image, getColorVec(image, x, y + 1.0), temperatureVector);

		double centerMultiplier = 1.0 + 4.0 * image.getSharpnessValueNormalized();
		double edgeMultiplier = image.getSharpnessValueNormalized();

		inColor.mul(centerMultiplier);

		leftTextureColor.mul(edgeMultiplier);
		rightTextureColor.mul(edgeMultiplier);
		topTextureColor.mul(edgeMultiplier);
		bottomTextureColor.mul(edgeMultiplier);

		inColor.sub(leftTextureColor);
		inColor.sub(rightTextureColor);
		inColor.sub(topTextureColor);
		inColor.sub(bottomTextureColor);

		double radius = Math.max(image.getCropWidth(), image.getCropHeight()) / 2.0;

		double dx = Math.abs(x - image.getCropWidth() / 2f);
		double dy = Math.abs(y - image.getCropHeight() / 2f);

		// Vignette
		double distance = Math.hypot(dx, dy);
		double radius1 = radius * 0.4;
		double radius2 = radius * 1.1;

		double t = smoothstep(radius1, radius2, distance);

		Vec3 vignettedColor = new Vec3(inColor);
		vignettedColor.mul(1.0 - t);

		inColor = mix(inColor, vignettedColor, image.getVignetteValueNormalized());

		return inColor;
	}

	static Vec3 colorTemperatureToRGB(double temperature) {
//     Values from: http://blenderartists.org/forum/showthread.php?270332-OSL-Goodness&p=2268693&viewfull=1#post2268693

		Vec3 m0 = (temperature <= 6500.0) ? new Vec3(0.0, -2902.1955373783176, -8257.7997278925690) :
				new Vec3(1745.0425298314172, 1216.6168361476490, -8257.7997278925690);

		Vec3 m1 = (temperature <= 6500.0) ? new Vec3(0.0, 1669.5803561666639, 2575.2827530017594) :
				new Vec3(-2666.3474220535695, -2173.1012343082230, 2575.2827530017594);

		Vec3 m2 = (temperature <= 6500.0) ? new Vec3(1.0, 1.3302673723350029, 1.8993753891711275) :
				new Vec3(0.55995389139931482, 0.70381203140554553, 1.8993753891711275);

		double clampedTemperature = clamp(temperature, 1000.0, 40000.0);

		m1.add(clampedTemperature);
		m0.div(m1);
		m0.add(m2);

		return mix(m0, new Vec3(1.0), smoothstep(1000.0, 0.0, temperature));
	}

	private static double clamp(double f, double min, double max) {
		return Math.max(min, Math.min(f, max));
	}

	private static int clamp(int f, int min, int max) {
		return Math.max(min, Math.min(f, max));
	}

	private static double mix(double a, double b, double f) {
		return a + (b - a) * f;
	}

	private static Vec3 mix(Vec3 a, Vec3 b, double f) {
		return new Vec3(a.r + (b.r - a.r) * f,
				a.g + (b.g - a.g) * f,
				a.b + (b.b - a.b) * f);
	}

	private static double smoothstep(double a, double b, double x) {
		x = clamp((x - a) / (b - a), 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	private static class Vec3 {
		double r;
		double g;
		double b;

		public Vec3(Vec3 v) {
			this.r = v.r;
			this.g = v.g;
			this.b = v.b;
		}

		public Vec3(double r, double g, double b) {
			this.r = r;
			this.g = g;
			this.b = b;
		}

		public Vec3(double f) {
			r = f;
			g = f;
			b = f;
		}

		public double dot(Vec3 v) {
			return r * v.r + g * v.g + b * v.b;
		}

		public double dot(double f) {
			return r * f + g * f + b * f;
		}

		public double dot(double r, double g, double b) {
			return this.r * r + this.g * g + this.b * b;
		}

		public void add(Vec3 v) {
			r += v.r;
			g += v.g;
			b += v.b;

			ensureLimits();
		}

		public void sub(Vec3 v) {
			r -= v.r;
			g -= v.g;
			b -= v.b;

			ensureLimits();
		}

		public void add(double f) {
			r += f;
			g += f;
			b += f;

			ensureLimits();
		}

		public void mul(double f) {
			r *= f;
			g *= f;
			b *= f;

			ensureLimits();
		}

		public void mul(Vec3 v) {
			r *= v.r;
			g *= v.g;
			b *= v.b;

			ensureLimits();
		}

		public void div(Vec3 v) {
			r /= v.r;
			g /= v.g;
			b /= v.b;

			ensureLimits();
		}

		private void ensureLimits() {
			r = clamp(r, 0f, 1f);
			g = clamp(g, 0f, 1f);
			b = clamp(b, 0f, 1f);
		}
	}
}
