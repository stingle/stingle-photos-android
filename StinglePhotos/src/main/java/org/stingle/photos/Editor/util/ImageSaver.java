package org.stingle.photos.Editor.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.math.Vector2;

public class ImageSaver {
	private static final Vec3 hotTemperature = colorTemperatureToRGB(40000.0);
	private static final Vec3 coldTemperature = colorTemperatureToRGB(2500.0);
	private static final Vec3 luminanceWeighting = new Vec3(0.2125, 0.7154, 0.0721);

	private static Vector2 p = new Vector2();

	public static Bitmap saveImage(Image image) {
		Bitmap resultBitmap = Bitmap.createBitmap((int) image.getCropWidth(), (int) image.getCropHeight(), image.getBitmap().getConfig());

		for (int i = 0; i < resultBitmap.getHeight(); i++) {
			for (int j = 0; j < resultBitmap.getWidth(); j++) {
				int pixel = getPixel(image, j, i);

				Vec3 color = processPixel(image, getColorVec(pixel), j, i);

				resultBitmap.setPixel(j, i, getColorInt(Color.alpha(pixel), color));
			}
		}

		return resultBitmap;
	}

	private static int getPixel(Image image, double x, double y) {
		p.set((float) x, (float) y);
		p.translate(-image.getCropWidth() / 2f, -image.getCropHeight() / 2f);
		p.rotate((float) Math.toRadians(image.getCropRotation() - image.getOrientation() * 90));
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

	static Vec3 applyFilters(Image image, Vec3 inColor) {
		// Brightness
		inColor.add(image.getBrightnessValueNormalized());
		inColor.ensureLimits();

		// Contrast
		inColor.add(-0.5f);
		inColor.mul(image.getContrastValueNormalized());
		inColor.add(0.5f);
		inColor.ensureLimits();

		// Highlights and Shadows

		double blackPointDistance = Math.abs(dot(inColor, luminanceWeighting) - 0.05);
		double blackPointWeight = Math.pow(smoothstep(0.05, 0.5, blackPointDistance), 8.0);

		double blackPointValue = image.getBlackPointValueNormalized() < 0.0 ? image.getBlackPointValueNormalized() * 0.2 : image.getBlackPointValueNormalized() * 0.1;

		inColor = mix(new Vec3(inColor.r + blackPointValue, inColor.g + blackPointValue, inColor.b + blackPointValue), inColor, blackPointWeight);
		inColor.ensureLimits();

		// Shadow
		double shadowDistance = Math.abs(dot(inColor, luminanceWeighting) - 0.25);
		double shadowWeight = Math.pow(smoothstep(0.15, 0.5, shadowDistance), 4.0);

		double shadowValue = mix(-0.1, 0.1, (image.getShadowsValueNormalized() + 1.0) / 2.0);

		inColor = mix(new Vec3(inColor.r + shadowValue, inColor.g + shadowValue, inColor.b + shadowValue), inColor, shadowWeight);
		inColor.ensureLimits();

		// Highlight
		double highlightDistance = Math.abs(dot(inColor, luminanceWeighting) - 0.75);
		double highlightWeight = Math.pow(smoothstep(0.15, 0.5, highlightDistance), 4.0);

		double highlightValue = mix(-0.1, 0.1, (image.getHighlightsValueNormalized() + 1.0) / 2.0);

		inColor = mix(new Vec3(inColor.r + highlightValue, inColor.g + highlightValue, inColor.b + highlightValue), inColor, highlightWeight);
		inColor.ensureLimits();

		// White point
		double whitePointDistance = Math.abs(dot(inColor, luminanceWeighting) - 0.95);
		double whitePointWeight = Math.pow(smoothstep(0.05, 0.5, whitePointDistance), 8.0);

		double whitePointValue = image.getWhitePointValueNormalized() * 0.2;

		inColor = mix(new Vec3(inColor.r + whitePointValue, inColor.g + whitePointValue, inColor.b + whitePointValue), inColor, whitePointWeight);
		inColor.ensureLimits();

		// Saturation

		inColor = mix(new Vec3(inColor.dot(luminanceWeighting)), inColor, image.getSaturationValueNormalized());
		inColor.ensureLimits();

		// Warmth/Temperature
		Vec3 selectedTemperature = image.getWarmthValueNormalized() < 0.0 ? mix(hotTemperature, new Vec3(1.0), image.getWarmthValueNormalized() + 1.0) : mix(new Vec3(1.0), coldTemperature, image.getWarmthValueNormalized());

		double oldLuminance = dot(inColor, luminanceWeighting);

		inColor.mul(selectedTemperature);

		double newLuminance = dot(inColor, luminanceWeighting);

		inColor.mul(oldLuminance / Math.max(newLuminance, 1e-5));
		inColor.ensureLimits();

		// Tint
		double greenTint = -clamp(image.getTintValueNormalized(), -1.0, 0.0);
		double purpleTint = clamp(image.getTintValueNormalized(), 0.0, 1.0);

		inColor.g = mix(inColor.g, 1.0, greenTint * 0.1);

		inColor.r = mix(inColor.r, 1.0, purpleTint * 0.1);
		inColor.b = mix(inColor.b, 1.0, purpleTint * 0.2);

		inColor.ensureLimits();

		return inColor;
	}

	static Vec3 processPixel(Image image, Vec3 inColor, double x, double y) {
		inColor = applyFilters(image, inColor);

		// Sharpness
		if (image.getSharpnessValueNormalized() > 0.0) {
			Vec3 leftTextureColor = applyFilters(image, getColorVec(image, x - 1.0, y));
			Vec3 rightTextureColor = applyFilters(image, getColorVec(image, x + 1.0, y));
			Vec3 topTextureColor = applyFilters(image, getColorVec(image, x, y - 1.0));
			Vec3 bottomTextureColor = applyFilters(image, getColorVec(image, x, y + 1.0));

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
			inColor.ensureLimits();
		}

		double dx = 0.0;
		double dy = 0.0;

		// Denoise
		if (image.getDenoiseValueNormalized() > 0.0) {
			for (int i = 0; i < 9; i++) {
				dx = (float) (i % 3 - 1);
				dy = (float) (i / 3 - 1);

				Vec3 pixelColor = applyFilters(image, getColorVec(image, x + dx, y + dy));
				pixelColor.mul(image.getDenoiseValueNormalized());

				inColor.add(pixelColor);
			}

			inColor.mul(1.0 / (1.0 + image.getDenoiseValueNormalized() * 9.0));
			inColor.ensureLimits();
		}

		double radius = Math.max(image.getCropWidth(), image.getCropHeight()) / 2.0;

		dx = Math.abs(x - image.getCropWidth() / 2f);
		dy = Math.abs(y - image.getCropHeight() / 2f);

		// Vignette
		double distance = Math.hypot(dx, dy);
		double radius1 = radius * 0.4;
		double radius2 = radius * 1.1;

		double t = smoothstep(radius1, radius2, distance);

		Vec3 vignetteColor = image.getVignetteValueNormalized() > 0.0 ? new Vec3(0.0, 0.0, 0.0) : new Vec3(1.0, 1.0, 1.0);
		Vec3 vignettedColor = mix(inColor, vignetteColor, t);

		inColor = mix(inColor, vignettedColor, Math.abs(image.getVignetteValueNormalized()));

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
	
	private static double dot(Vec3 a, Vec3 b) {
		return a.dot(b);
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
		}

		public void sub(Vec3 v) {
			r -= v.r;
			g -= v.g;
			b -= v.b;
		}

		public void add(double f) {
			r += f;
			g += f;
			b += f;
		}

		public void mul(double f) {
			r *= f;
			g *= f;
			b *= f;
		}

		public void mul(Vec3 v) {
			r *= v.r;
			g *= v.g;
			b *= v.b;
		}

		public void div(Vec3 v) {
			r /= v.r;
			g /= v.g;
			b /= v.b;
		}

		private void ensureLimits() {
			r = clamp(r, 0.0, 1.0);
			g = clamp(g, 0.0, 1.0);
			b = clamp(b, 0.0, 1.0);
		}
	}
}
