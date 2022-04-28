package org.stingle.photos.Editor.core;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.stingle.photos.Editor.math.Point;
import org.stingle.photos.Editor.math.Vector;
import org.stingle.photos.Editor.math.Vector2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Image {
	public static final float DIM_FULL = 0.0f;
	public static final float DIM_MID = 0.2f;
	public static final float DIM_LOW = 0.6f;

	public static final int ORIENTATION_0 = 0;
	public static final int ORIENTATION_90 = 1;
	public static final int ORIENTATION_180 = 2;
	public static final int ORIENTATION_270 = 3;

	private ImageChangeListener imageChangeListener;

	private Bitmap bitmap;

	private final List<Property> propertyList;

	private Property brightnessProperty;
	private Property contrastProperty;
	private Property whitePointProperty;
	private Property highlightsProperty;
	private Property shadowsProperty;
	private Property blackPointProperty;
	private Property saturationProperty;
	private Property warmthProperty;
	private Property tintProperty;
	private Property sharpnessProperty;
	private Property denoiseProperty;
	private Property vignetteProperty;

	private int orientation;
	private int resizedWidth;
	private int resizedHeight;
	private Point cropCenter;
	private float cropWidth;
	private float cropHeight;
	private int cropRotation;
	private float rotationScale;

	private float dimAmount;

	private Vector2 vector0;
	private Vector2 vector1;
	private Vector2 vector2;
	private Vector2 vector3;

	private Point[] cropVertices;

	private CropRatio cropRatio;

	public Image(Bitmap bitmap) {
		this.bitmap = bitmap;

		propertyList = new ArrayList<>();

		cropCenter = new Point(bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
		cropWidth = bitmap.getWidth();
		cropHeight = bitmap.getHeight();
		cropRotation = 0;
		rotationScale = 1f;
		cropRatio = CropRatio.FREE;

		resizedWidth = bitmap.getWidth();
		resizedHeight = bitmap.getHeight();

		dimAmount = DIM_FULL;

		cropVertices = new Point[4];
		cropVertices[0] = new Point();
		cropVertices[1] = new Point();
		cropVertices[2] = new Point();
		cropVertices[3] = new Point();

		vector0 = new Vector2();
		vector1 = new Vector2();
		vector2 = new Vector2();
		vector3 = new Vector2();
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public int getWidth() {
		return bitmap.getWidth();
	}

	public int getHeight() {
		return bitmap.getHeight();
	}

	public float getCropRotationRadians() {
		return (float) Math.toRadians(cropRotation);
	}

	public int getCropRotation() {
		return cropRotation;
	}

	public void setCropRotation(int cropRotation) {
		this.cropRotation = cropRotation;

		double radians = Math.toRadians(-Math.abs(cropRotation));

		rotationScale = 1f / (float) (Math.cos(radians) - Math.max(cropWidth, cropHeight) / Math.min(cropWidth, cropHeight) * Math.sin(radians));

		correctCropPosition();
	}

	public CropRatio getCropRatio() {
		return cropRatio;
	}

	public void setCropRatio(CropRatio cropRatio) {
		this.cropRatio = cropRatio;

		float cropArea = cropWidth * cropHeight;

		cropHeight = (float) Math.sqrt(cropArea / cropRatio.getRatio());
		cropWidth = cropRatio.getWidth(cropHeight);

		correctCropPosition();
	}

	public int getResizedWidth() {
		return resizedWidth;
	}

	public void setResizedSize(int resizedWidth, int resizedHeight) {
		if (orientation % 2 == 0) {
			this.resizedWidth = resizedWidth;
			this.resizedHeight = resizedHeight;
		} else {
			this.resizedWidth = resizedHeight;
			this.resizedHeight = resizedWidth;
		}
	}

	public int getOrientation() {
		return orientation;
	}

	public void setOrientation(int orientation) {
		this.orientation = orientation % 4;
	}

	public Point getCropCenter() {
		return cropCenter;
	}

	public void setCropCenter(float x, float y) {
		cropCenter.set(x, y);
	}

	public float getCropWidth() {
		return cropWidth * rotationScale;
	}

	public void setCropWidth(float cropWidth) {
		this.cropWidth = cropWidth / rotationScale;
	}

	public float getCropHeight() {
		return cropHeight * rotationScale;
	}

	public void setCropHeight(float cropHeight) {
		this.cropHeight = cropHeight / rotationScale;
	}

	public void reset() {
		orientation = ORIENTATION_0;
		cropCenter.set(bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
		cropWidth = bitmap.getWidth();
		cropHeight = bitmap.getHeight();
		cropRotation = 0;
		rotationScale = 1f;
		cropRatio = CropRatio.FREE;
	}

	public float getDimAmount() {
		return dimAmount;
	}

	public void setDimAmount(float dimAmount) {
		this.dimAmount = dimAmount;
	}

	public void setEditorChangeListener(ImageChangeListener imageChangeListener) {
		this.imageChangeListener = imageChangeListener;
	}

	public Property findPropertyWithName(String name) {
		for (Property property : propertyList) {
			if (Objects.equals(property.name, name)) {
				return property;
			}
		}

		return null;
	}

	public void init() {
		brightnessProperty = new Property(AdjustOption.BRIGHTNESS.name, -100, 100, -0.3f, 0f, 0.3f);
		contrastProperty = new Property(AdjustOption.CONTRAST.name, -100, 100, 0.6f, 1f, 2f);
		whitePointProperty = new Property(AdjustOption.WHITE_POINT.name, -100, 100, -1f, 0f, 1f);
		highlightsProperty = new Property(AdjustOption.HIGHLIGHTS.name, -100, 100, -1f, 0f, 1f);
		shadowsProperty = new Property(AdjustOption.SHADOWS.name, -100, 100, -1f, 0f, 1f);
		blackPointProperty = new Property(AdjustOption.BLACK_POINT.name, -100, 100, -1f, 0f, 1f);
		saturationProperty = new Property(AdjustOption.SATURATION.name, -100, 100, 0f, 1f, 2f);
		warmthProperty = new Property(AdjustOption.WARMTH.name, -100, 100, -1f, 0f, 1f);
		tintProperty = new Property(AdjustOption.TINT.name, -100, 100, -1f, 0f, 1f);
		sharpnessProperty = new Property(AdjustOption.SHARPEN.name, 0, 100, 0, 0f, 2f);
		denoiseProperty = new Property(AdjustOption.DENOISE.name, 0, 100, 0, 0f, 2f);
		vignetteProperty = new Property(AdjustOption.VIGNETTE.name, -100, 100, -1f, 0f, 1f);

		propertyList.add(brightnessProperty);
		propertyList.add(contrastProperty);
		propertyList.add(whitePointProperty);
		propertyList.add(highlightsProperty);
		propertyList.add(shadowsProperty);
		propertyList.add(blackPointProperty);
		propertyList.add(saturationProperty);
		propertyList.add(warmthProperty);
		propertyList.add(tintProperty);
		propertyList.add(sharpnessProperty);
		propertyList.add(denoiseProperty);
		propertyList.add(vignetteProperty);
	}

	public float getBrightnessValueNormalized() {
		return brightnessProperty.getNormalizedValue();
	}

	public float getContrastValueNormalized() {
		return contrastProperty.getNormalizedValue();
	}

	public float getWhitePointValueNormalized() {
		return whitePointProperty.getNormalizedValue();
	}

	public float getHighlightsValueNormalized() {
		return highlightsProperty.getNormalizedValue();
	}

	public float getShadowsValueNormalized() {
		return shadowsProperty.getNormalizedValue();
	}

	public float getBlackPointValueNormalized() {
		return blackPointProperty.getNormalizedValue();
	}

	public float getSaturationValueNormalized() {
		return saturationProperty.getNormalizedValue();
	}

	public float getWarmthValueNormalized() {
		return warmthProperty.getNormalizedValue();
	}

	public float getTintValueNormalized() {
		return tintProperty.getNormalizedValue();
	}

	public float getSharpnessValueNormalized() {
		return sharpnessProperty.getNormalizedValue();
	}

	public float getDenoiseValueNormalized() {
		return denoiseProperty.getNormalizedValue();
	}

	public float getVignetteValueNormalized() {
		return vignetteProperty.getNormalizedValue();
	}

	public interface ImageChangeListener {
		void onImageChanged(Image image);
	}

	public Point[] getCropVertices() {
		cropVertices[0].set(cropCenter.x - getCropWidth() / 2f, cropCenter.y - getCropHeight() / 2f);
		cropVertices[0].rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		cropVertices[1].set(cropCenter.x + getCropWidth() / 2f, cropCenter.y - getCropHeight() / 2f);
		cropVertices[1].rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		cropVertices[2].set(cropCenter.x - getCropWidth() / 2f, cropCenter.y + getCropHeight() / 2f);
		cropVertices[2].rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		cropVertices[3].set(cropCenter.x + getCropWidth() / 2f, cropCenter.y + getCropHeight() / 2f);
		cropVertices[3].rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		return cropVertices;
	}

	private void correctCropPosition() {
		vector0.set(cropCenter.x - getCropWidth() / 2f, cropCenter.y - getCropHeight() / 2f);
		vector0.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		vector1.set(cropCenter.x + getCropWidth() / 2f, cropCenter.y - getCropHeight() / 2f);
		vector1.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		vector2.set(cropCenter.x - getCropWidth() / 2f, cropCenter.y + getCropHeight() / 2f);
		vector2.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		vector3.set(cropCenter.x + getCropWidth() / 2f, cropCenter.y + getCropHeight() / 2f);
		vector3.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		float left = Math.min(vector0.getX(), Math.min(vector1.getX(), Math.min(vector2.getX(), vector3.getX())));
		float right = Math.max(vector0.getX(), Math.max(vector1.getX(), Math.max(vector2.getX(), vector3.getX())));
		float top = Math.min(vector0.getY(), Math.min(vector1.getY(), Math.min(vector2.getY(), vector3.getY())));
		float bottom = Math.max(vector0.getY(), Math.max(vector1.getY(), Math.max(vector2.getY(), vector3.getY())));

		float width = right - left;
		float height = bottom - top;

		float scale = Math.min(1f, bitmap.getWidth() / width);
		scale = Math.min(scale, bitmap.getHeight() / height);

		cropWidth *= scale;
		cropHeight *= scale;

		vector0.set(cropCenter.x - getCropWidth() / 2f, cropCenter.y - getCropHeight() / 2f);
		vector0.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		vector1.set(cropCenter.x + getCropWidth() / 2f, cropCenter.y - getCropHeight() / 2f);
		vector1.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		vector2.set(cropCenter.x - getCropWidth() / 2f, cropCenter.y + getCropHeight() / 2f);
		vector2.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		vector3.set(cropCenter.x + getCropWidth() / 2f, cropCenter.y + getCropHeight() / 2f);
		vector3.rotate(cropCenter.x, cropCenter.y, getCropRotationRadians());

		float dx = 0f;
		float dy = 0f;

		if (vector0.getX() < 0) {
			dx = vector0.getX();
		} else if (vector0.getX() > bitmap.getWidth()) {
			dx = vector0.getX() - bitmap.getWidth();
		}

		if (vector0.getY() < 0) {
			dy = vector0.getY();
		} else if (vector0.getY() > bitmap.getHeight()) {
			dy = vector0.getY() - bitmap.getHeight();
		}

		if (vector1.getX() < 0) {
			dx = vector1.getX();
		} else if (vector1.getX() > bitmap.getWidth()) {
			dx = vector1.getX() - bitmap.getWidth();
		}

		if (vector1.getY() < 0) {
			dy = vector1.getY();
		} else if (vector1.getY() > bitmap.getHeight()) {
			dy = vector1.getY() - bitmap.getHeight();
		}

		if (vector2.getX() < 0) {
			dx = vector2.getX();
		} else if (vector2.getX() > bitmap.getWidth()) {
			dx = vector2.getX() - bitmap.getWidth();
		}

		if (vector2.getY() < 0) {
			dy = vector2.getY();
		} else if (vector2.getY() > bitmap.getHeight()) {
			dy = vector2.getY() - bitmap.getHeight();
		}

		if (vector3.getX() < 0) {
			dx = vector3.getX();
		} else if (vector3.getX() > bitmap.getWidth()) {
			dx = vector3.getX() - bitmap.getWidth();
		}

		if (vector3.getY() < 0) {
			dy = vector3.getY();
		} else if (vector3.getY() > bitmap.getHeight()) {
			dy = vector3.getY() - bitmap.getHeight();
		}

		cropCenter.set(cropCenter.x - dx, cropCenter.y - dy);
	}
}
