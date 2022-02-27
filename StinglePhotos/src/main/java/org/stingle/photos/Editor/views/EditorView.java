package org.stingle.photos.Editor.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.stingle.photos.Editor.opengl.EditorController;
import org.stingle.photos.Editor.opengl.EditorCropController;
import org.stingle.photos.Editor.opengl.EditorPerspectiveController;
import org.stingle.photos.Editor.util.Utils;

public class EditorView extends View {
	private Bitmap image;
	private Bitmap previewImage;

	private Canvas previewImageCanvas;

	private Paint imagePaint;
	private Matrix cameraTransform;
	private Matrix inverseCameraTransform;

	private int imageRotation;
	private int imageScaleX;
	private int imageScaleY;

	private boolean cropEnabled;

	private EditorCropController cropController;
	private EditorPerspectiveController perspectiveController;

	private EditorController currentController;

	public EditorView(Context context) {
		super(context);

		init();
	}

	public EditorView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);

		init();
	}

	public EditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		init();
	}

	public EditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		init();
	}

	private void init() {
		imagePaint = new Paint();
		imagePaint.setFilterBitmap(true);

		imageRotation = 0;
		imageScaleX = 1;
		imageScaleY = 1;

		cameraTransform = new Matrix();
		inverseCameraTransform = new Matrix();

		cropController = new EditorCropController(this);
		perspectiveController = new EditorPerspectiveController(this);
	}

	public EditorCropController getCropController() {
		return cropController;
	}

	public void setImage(Bitmap image) {
		this.image = image;
		this.previewImage = Utils.getScaledBitmap(image, 1024);

		previewImageCanvas = new Canvas(previewImage);

		fitImageInScreen();

		invalidate();
	}

	public Bitmap getImage() {
		return image;
	}

	public Bitmap getPreviewImage() {
		return previewImage;
	}

	public void setPreviewImage(Bitmap previewImage) {
		previewImageCanvas.drawColor(Color.WHITE, PorterDuff.Mode.CLEAR);
		previewImageCanvas.drawBitmap(previewImage, 0, 0, null);

		invalidate();
	}

	public int getImageWidth() {
		return image.getWidth();
	}

	public int getImageHeight() {
		return image.getHeight();
	}

	public void setColorFilter(ColorFilter colorFilter) {
		imagePaint.setColorFilter(colorFilter);

		invalidate();
	}

	public int getImageRotation() {
		return imageRotation;
	}

	public void setImageRotation(int imageRotation) {
		this.imageRotation = (imageRotation % 360 + 360) % 360;

		fitImageInScreen();
	}

	public int getImageScaleX() {
		return imageScaleX;
	}

	public void setImageScaleX(int imageScaleX) {
		this.imageScaleX = imageScaleX;

		invalidate();
	}

	public int getImageScaleY() {
		return imageScaleY;
	}

	public void setImageScaleY(int imageScaleY) {
		this.imageScaleY = imageScaleY;

		invalidate();
	}

	public void applyCurrentState() {
		Bitmap updatedBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(updatedBitmap);
		canvas.drawBitmap(image, 0f, 0f, imagePaint);

		imagePaint.setColorFilter(null);
		image = updatedBitmap;
	}

	private void fitImageInScreen() {
		if (getWidth() > 0 && getHeight() > 0 && previewImage != null) {
			boolean rotated = imageRotation == 90 || imageRotation == 270;

			float width = rotated ? previewImage.getHeight() : previewImage.getWidth();
			float height = rotated ? previewImage.getWidth() : previewImage.getHeight();

			float scale = Math.min((getWidth() - getPaddingLeft() - getPaddingRight()) / width,
					(getHeight() - getPaddingTop() - getPaddingBottom()) / height);

			cameraTransform.reset();
			cameraTransform.postScale(scale, scale);
			cameraTransform.postTranslate(getWidth() / 2f, getHeight() / 2f);

			cameraTransform.invert(inverseCameraTransform);

			invalidate();
		}
	}

//	public void setCropEnabled(boolean cropEnabled) {
//		if (this.cropEnabled != cropEnabled) {
//			this.cropEnabled = cropEnabled;
//
//			if (cropEnabled) {
//				cropRect.set(0f, 0f, previewImage.getWidth(), previewImage.getHeight());
//			}
//
//			invalidate();
//		}
//	}

	public boolean isRotated() {
		return false;
	}

//	public Bitmap getCroppedImage() {
//		float scale = (float) image.getWidth() / previewImage.getWidth();
//
//		RectF scaledCropRect = new RectF(cropRect);
//		scaledCropRect.left *= scale;
//		scaledCropRect.top *= scale;
//		scaledCropRect.right *= scale;
//		scaledCropRect.bottom *= scale;
//
//
//		Bitmap croppedImage = Bitmap.createBitmap((int) scaledCropRect.width(), (int) scaledCropRect.height(), Bitmap.Config.ARGB_8888);
//		Canvas canvas = new Canvas(croppedImage);
//		canvas.drawBitmap(image, -scaledCropRect.left, -scaledCropRect.top, null);
//
//		return croppedImage;
//	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		fitImageInScreen();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (previewImage != null) {
			if (currentController != null) {
				currentController.onDraw(canvas, previewImage);
			} else {
				canvas.save();
				canvas.concat(cameraTransform);
				canvas.rotate(imageRotation);
				canvas.scale(imageScaleX, imageScaleY);
				canvas.translate(-previewImage.getWidth() / 2f, -previewImage.getHeight() / 2f);
				canvas.drawBitmap(previewImage, 0f, 0f, imagePaint);
				canvas.restore();
			}
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return currentController != null && currentController.onTouchEvent(event);
	}
}
