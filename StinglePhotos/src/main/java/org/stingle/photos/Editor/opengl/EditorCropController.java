package org.stingle.photos.Editor.opengl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;

import org.stingle.photos.Editor.core.CropRatio;
import org.stingle.photos.Editor.views.EditorView;

public class EditorCropController extends EditorController {
	private static final int CROP_HANDLE_NONE = 0;
	private static final int CROP_HANDLE_TOP = 1;
	private static final int CROP_HANDLE_RIGHT = 2;
	private static final int CROP_HANDLE_BOTTOM = 3;
	private static final int CROP_HANDLE_LEFT = 4;
	private static final int CROP_HANDLE_TOP_LEFT = 5;
	private static final int CROP_HANDLE_TOP_RIGHT = 6;
	private static final int CROP_HANDLE_BOTTOM_RIGHT = 7;
	private static final int CROP_HANDLE_BOTTOM_LEFT = 8;
	private static final int CROP_HANDLE_IMAGE = 9;

	private RectF cropRect;
	private int activeCropHandle;
	private float cropHandleTouchRadius;
	private CropRatio cropRatio;

	private float[] pointArray;

	private Paint clearPaint;
	private Paint dimPaint;
	private Paint cropStrokePaint;

	private float previousX;
	private float previousY;

	private int rotation;

	public EditorCropController(EditorView editorView) {
		super(editorView);

		pointArray = new float[4];

		cropRect = new RectF();
		cropHandleTouchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, getContext().getResources().getDisplayMetrics());

		clearPaint = new Paint();
		clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

		dimPaint = new Paint();
		dimPaint.setColor(0x88999999);
		dimPaint.setStyle(Paint.Style.FILL);

		cropStrokePaint = new Paint();
		cropStrokePaint.setStyle(Paint.Style.STROKE);
		cropStrokePaint.setColor(Color.BLUE);
		cropStrokePaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getContext().getResources().getDisplayMetrics()));

	}

	public void setRotation(int degrees) {
		this.rotation = degrees;

		notifyRedrawNeeded();
	}

	@Override
	public void onDraw(Canvas canvas, Bitmap image) {
//		pointArray[0] = cropRect.left - previewImage.getWidth() / 2f;
//		pointArray[1] = cropRect.top - previewImage.getHeight() / 2f;
//		pointArray[2] = cropRect.right - previewImage.getWidth() / 2f;
//		pointArray[3] = cropRect.bottom - previewImage.getHeight() / 2f;
//
//		cameraTransform.mapPoints(pointArray);
//
//		canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
//		canvas.save();
//		canvas.concat(cameraTransform);
//		canvas.drawRect(-previewImage.getWidth() / 2f, -previewImage.getHeight() / 2f, previewImage.getWidth() / 2f, previewImage.getHeight() / 2f, dimPaint);
//		canvas.restore();
//		canvas.drawRect(pointArray[0], pointArray[1], pointArray[2], pointArray[3], clearPaint);
//		canvas.drawRect(pointArray[0], pointArray[1], pointArray[2], pointArray[3], cropStrokePaint);
//		canvas.restore();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
//		switch (event.getActionMasked()) {
//			case MotionEvent.ACTION_DOWN: {
//				pointArray[0] = event.getX();
//				pointArray[1] = event.getY();
//
//				inverseCameraTransform.mapPoints(pointArray);
//
//				pointArray[0] += previewImage.getWidth() / 2f;
//				pointArray[1] += previewImage.getHeight() / 2f;
//
//				float cropHandleTouchRadiusInImage = inverseCameraTransform.mapRadius(cropHandleTouchRadius);
//
//				if (Math.hypot(pointArray[0] - cropRect.left, pointArray[1] - cropRect.top) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_TOP_LEFT;
//				} else if (Math.hypot(pointArray[0] - cropRect.right, pointArray[1] - cropRect.top) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_TOP_RIGHT;
//				} else if (Math.hypot(pointArray[0] - cropRect.right, pointArray[1] - cropRect.bottom) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_BOTTOM_RIGHT;
//				} else if (Math.hypot(pointArray[0] - cropRect.left, pointArray[1] - cropRect.bottom) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_BOTTOM_LEFT;
//				} else if (Math.abs(pointArray[0] - cropRect.left) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_LEFT;
//				} else if (Math.abs(pointArray[0] - cropRect.right) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_RIGHT;
//				} else if (Math.abs(pointArray[1] - cropRect.top) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_TOP;
//				} else if (Math.abs(pointArray[1] - cropRect.bottom) <= cropHandleTouchRadiusInImage) {
//					activeCropHandle = CROP_HANDLE_BOTTOM;
//				} else if (cropRect.contains(pointArray[0], pointArray[1])) {
//					activeCropHandle = CROP_HANDLE_IMAGE;
//				} else {
//					activeCropHandle = CROP_HANDLE_NONE;
//				}
//
//				previousX = pointArray[0];
//				previousY = pointArray[1];
//
//				break;
//			}
//
//			case MotionEvent.ACTION_MOVE: {
//				pointArray[0] = event.getX();
//				pointArray[1] = event.getY();
//
//				inverseCameraTransform.mapPoints(pointArray);
//
//				pointArray[0] += previewImage.getWidth() / 2f;
//				pointArray[1] += previewImage.getHeight() / 2f;
//
//				switch (activeCropHandle) {
//					case CROP_HANDLE_IMAGE: {
//						cropRect.offset(pointArray[0] - previousX, pointArray[1] - previousY);
//						correctCropRectPosition();
//						break;
//					}
//
//					case CROP_HANDLE_TOP_LEFT: {
//						float dx = Math.min(previousX - pointArray[0], cropRect.left);
//						float dy = Math.min(previousY - pointArray[1], cropRect.top);
//
//						if (cropRatio != null && !cropRatio.isFree()) {
//							Utils.scaleRect(cropRect, cropRect.right, cropRect.bottom, getUniformScale(dx, dy));
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.right, cropRect.bottom);
//						} else {
//							cropRect.left -= dx;
//							cropRect.top -= dy;
//						}
//						break;
//					}
//
//					case CROP_HANDLE_TOP_RIGHT: {
//						float dx = Math.min(pointArray[0] - previousX, previewImage.getWidth() - cropRect.right);
//						float dy = Math.min(previousY - pointArray[1], cropRect.top);
//
//						if (cropRatio != null && cropRatio.isActive()) {
//							Utils.scaleRect(cropRect, cropRect.left, cropRect.bottom, getUniformScale(dx, dy));
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.left, cropRect.bottom);
//						} else {
//							cropRect.right += dx;
//							cropRect.top -= dy;
//						}
//						break;
//					}
//
//					case CROP_HANDLE_BOTTOM_RIGHT: {
//						float dx = Math.min(pointArray[0] - previousX, previewImage.getWidth() - cropRect.right);
//						float dy = Math.min(pointArray[1] - previousY, previewImage.getHeight() - cropRect.bottom);
//
//						if (cropRatio != null && cropRatio.isActive()) {
//							Utils.scaleRect(cropRect, cropRect.left, cropRect.top, getUniformScale(dx, dy));
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.left, cropRect.top);
//						} else {
//							cropRect.right += dx;
//							cropRect.bottom += dy;
//						}
//						break;
//					}
//
//					case CROP_HANDLE_BOTTOM_LEFT: {
//						float dx = Math.min(previousX - pointArray[0], cropRect.left);
//						float dy = Math.min(pointArray[1] - previousY, previewImage.getHeight() - cropRect.bottom);
//
//						if (cropRatio != null && cropRatio.isActive()) {
//							Utils.scaleRect(cropRect, cropRect.right, cropRect.top, getUniformScale(dx, dy));
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.right, cropRect.top);
//						} else {
//							cropRect.left -= dx;
//							cropRect.bottom += dy;
//						}
//						break;
//					}
//
//					case CROP_HANDLE_TOP: {
//						float dy = Math.min(previousY - pointArray[1], cropRect.top);
//
//						cropRect.top -= dy;
//
//						if (cropRatio != null && cropRatio.isActive()) {
//							float dx = dy * cropRatio.w / cropRatio.h;
//							cropRect.left -= dx / 2;
//							cropRect.right += dx / 2;
//
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.centerX(), cropRect.bottom);
//						}
//						break;
//					}
//
//					case CROP_HANDLE_BOTTOM: {
//						float dy = Math.min(pointArray[1] - previousY, previewImage.getHeight() - cropRect.bottom);
//
//						cropRect.bottom += dy;
//
//						if (cropRatio != null && cropRatio.isActive()) {
//							float dx = dy * cropRatio.w / cropRatio.h;
//							cropRect.left -= dx / 2;
//							cropRect.right += dx / 2;
//
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.centerX(), cropRect.top);
//						}
//						break;
//					}
//
//					case CROP_HANDLE_LEFT: {
//						float dx = Math.min(previousX - pointArray[0], cropRect.left);
//
//						cropRect.left -= dx;
//
//						if (cropRatio != null && cropRatio.isActive()) {
//							float dy = dx * cropRatio.h / cropRatio.w;
//							cropRect.top -= dy / 2;
//							cropRect.bottom += dy / 2;
//
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.right, cropRect.centerY());
//						}
//						break;
//					}
//
//					case CROP_HANDLE_RIGHT: {
//						float dx = Math.min(pointArray[0] - previousX, previewImage.getWidth() - cropRect.right);
//
//						cropRect.right += dx;
//
//						if (cropRatio != null && cropRatio.isActive()) {
//							float dy = dx * cropRatio.h / cropRatio.w;
//							cropRect.top -= dy / 2;
//							cropRect.bottom += dy / 2;
//
//							correctCropRectPosition();
//							correctCropRectScale(cropRect.left, cropRect.centerY());
//						}
//
//						break;
//					}
//				}
//
//				previousX = pointArray[0];
//				previousY = pointArray[1];
//
//				invalidate();
//
//				break;
//			}
//		}
		return true;
	}

	public CropRatio getCropRatio() {
		return cropRatio;
	}

	public void setCropRatio(CropRatio cropRatio) {
//		if (!Objects.equals(this.cropRatio, cropRatio)) {
//			this.cropRatio = cropRatio;
//
//			if (!cropRatio.isFree()) {
//				float maxSize = Math.max(cropRect.width(), cropRect.height());
//
//				float scale = Math.min(maxSize / cropRatio.w, maxSize / cropRatio.h);
//				float cx = cropRect.centerX();
//				float cy = cropRect.centerY();
//
//				float width = scale * cropRatio.w;
//				float height = scale * cropRatio.h;
//
//				cropRect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
//				correctCropRectPosition();
//				correctCropRectScale(cropRect.centerX(), cropRect.centerY());
//
//				invalidate();
//			}
//		}
	}

	public void reset() {
//		imageRotation = 0;
//		imageScaleX = 1;
//		imageScaleY = 1;
//
//		imagePaint.setColorFilter(null);
	}

	private void correctCropRect() {
//		cropRect.left = Math.min(Math.max(cropRect.left, 0f), cropRect.right);
//		cropRect.top = Math.min(Math.max(cropRect.top, 0f), cropRect.top);
//		cropRect.right = Math.max(Math.min(cropRect.right, previewImage.getWidth()), cropRect.left);
//		cropRect.bottom = Math.max(Math.min(cropRect.bottom, previewImage.getHeight()), cropRect.top);
	}

	private void correctCropRectScale(float px, float py) {
//		float scale = 1f;
//
//		if (cropRect.top < 0) {
//			scale = Math.min(scale, py / (py - cropRect.top));
//		}
//
//		if (cropRect.bottom > previewImage.getHeight()) {
//			scale = Math.min(scale, (previewImage.getHeight() - py) / (cropRect.bottom - py));
//		}
//
//		if (cropRect.left < 0) {
//			scale = Math.min(scale, px / (px - cropRect.left));
//		}
//
//		if (cropRect.right > previewImage.getWidth()) {
//			scale = Math.min(scale, (previewImage.getWidth() - px) / (cropRect.right - px));
//		}
//
//		Utils.scaleRect(cropRect, px, py, scale);
	}

	private void correctCropRectPosition() {
//		float dx = 0f;
//		float dy = 0f;
//
//		if (cropRect.width() <= previewImage.getWidth() && cropRect.height() <= previewImage.getHeight()) {
//			if (cropRect.left < 0) {
//				dx = -cropRect.left;
//			} else if (cropRect.right > previewImage.getWidth()) {
//				dx = previewImage.getWidth() - cropRect.right;
//			}
//
//			if (cropRect.top < 0) {
//				dy = -cropRect.top;
//			} else if (cropRect.bottom > previewImage.getHeight()) {
//				dy = previewImage.getHeight() - cropRect.bottom;
//			}
//
//			cropRect.offset(dx, dy);
//		}
	}

	private float getUniformScale(float dx, float dy) {
		float angle = (float) Math.atan2(cropRect.height(), cropRect.width());

		float projectedDx = (float) (Math.cos(angle) * dx);
		float projectedDy = (float) (Math.sin(angle) * dy);

		float a = cropRect.width() + projectedDx;
		float b = cropRect.height() + projectedDy;

		float len = (float) Math.hypot(a, b);

		return (float) (len / Math.hypot(cropRect.width(), cropRect.height()));
	}
}
