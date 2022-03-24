package org.stingle.photos.Editor.util;

import android.graphics.PointF;
import android.opengl.Matrix;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;

import org.stingle.photos.Editor.core.CropRatio;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.math.Line;
import org.stingle.photos.Editor.math.Point;
import org.stingle.photos.Editor.math.Vector;
import org.stingle.photos.Editor.math.Vector2;
import org.stingle.photos.Editor.opengl.Camera;
import org.stingle.photos.Editor.views.GLImageView;

public class CropGestureController {
	private static final int CROP_HANDLE_TOP_LEFT = 0;
	private static final int CROP_HANDLE_TOP = 1;
	private static final int CROP_HANDLE_TOP_RIGHT = 2;
	private static final int CROP_HANDLE_RIGHT = 3;
	private static final int CROP_HANDLE_BOTTOM_RIGHT = 4;
	private static final int CROP_HANDLE_BOTTOM = 5;
	private static final int CROP_HANDLE_BOTTOM_LEFT = 6;
	private static final int CROP_HANDLE_LEFT = 7;
	private static final int CROP_HANDLE_IMAGE = 8;
	private static final int CROP_HANDLE_NONE = 9;

	private int touchStartPointerId;

	private int touchPointerId0;
	private int touchPointerId1;

	private Callback callback;

	private Image image;

	private Vector2 previousScreenPointer1;
	private Vector2 previousScreenPointer2;

	private Vector2 previousImagePointer1;
	private Vector2 previousImagePointer2;

	private Vector2 screenPointer1;
	private Vector2 screenPointer2;

	private Vector2 imagePointer1;
	private Vector2 imagePointer2;

	private Vector2 vector0;
	private Vector2 vector1;
	private Vector2 vector2;
	private Vector2 vector3;

	private Vector tmpVector1;
	private Vector tmpVector2;

	private float touchRadius;

	private int currentCropHandle;

	private Camera camera;

	private GLImageView editorView;

	public CropGestureController(GLImageView editorView) {
		this.editorView = editorView;

		previousScreenPointer1 = new Vector2();
		previousScreenPointer2 = new Vector2();

		previousImagePointer1 = new Vector2();
		previousImagePointer2 = new Vector2();

		screenPointer1 = new Vector2();
		screenPointer2 = new Vector2();

		imagePointer1 = new Vector2();
		imagePointer2 = new Vector2();

		vector0 = new Vector2();
		vector1 = new Vector2();
		vector2 = new Vector2();
		vector3 = new Vector2();

		tmpVector1 = new Vector();
		tmpVector2 = new Vector();

		touchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, editorView.getResources().getDisplayMetrics());
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public void setImage(Image image) {
		this.image = image;
	}

	public void onTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				touchPointerId0 = event.getPointerId(0);

				screenPointer1.set(event.getX(), event.getY());

				Point cropCenter = image.getCropCenter();

				vector0.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
				vector0.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

				vector1.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
				vector1.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

				vector2.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
				vector2.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

				vector3.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
				vector3.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

				Matrix.multiplyMV(vector0.getValues(), 0, camera.getViewMatrix(), 0, vector0.getValues(), 0);
				Matrix.multiplyMV(vector1.getValues(), 0, camera.getViewMatrix(), 0, vector1.getValues(), 0);
				Matrix.multiplyMV(vector2.getValues(), 0, camera.getViewMatrix(), 0, vector2.getValues(), 0);
				Matrix.multiplyMV(vector3.getValues(), 0, camera.getViewMatrix(), 0, vector3.getValues(), 0);

				float left = Math.min(vector0.getX(), Math.min(vector1.getX(), Math.min(vector2.getX(), vector3.getX())));
				float right = Math.max(vector0.getX(), Math.max(vector1.getX(), Math.max(vector2.getX(), vector3.getX())));
				float top = Math.min(vector0.getY(), Math.min(vector1.getY(), Math.min(vector2.getY(), vector3.getY())));
				float bottom = Math.max(vector0.getY(), Math.max(vector1.getY(), Math.max(vector2.getY(), vector3.getY())));

				if (Math.hypot(screenPointer1.getX() - vector0.getX(), screenPointer1.getY() - vector0.getY()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_TOP_LEFT;
				} else if (Math.hypot(screenPointer1.getX() - vector1.getX(), screenPointer1.getY() - vector1.getY()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_TOP_RIGHT;
				} else if (Math.hypot(screenPointer1.getX() - vector2.getX(), screenPointer1.getY() - vector2.getY()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_BOTTOM_LEFT;
				} else if (Math.hypot(screenPointer1.getX() - vector3.getX(), screenPointer1.getY() - vector3.getY()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_BOTTOM_RIGHT;
				} else if (screenPointer1.getX() >= vector0.getX() && screenPointer1.getX() <= vector1.getX() &&
						Math.abs(screenPointer1.getY() - vector0.getY()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_TOP;
				} else if (screenPointer1.getX() >= vector0.getX() && screenPointer1.getX() <= vector1.getX() &&
						Math.abs(screenPointer1.getY() - vector3.getY()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_BOTTOM;
				} else if (screenPointer1.getY() >= vector0.getY() && screenPointer1.getY() <= vector3.getY() &&
						Math.abs(screenPointer1.getX() - vector0.getX()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_LEFT;
				} else if (screenPointer1.getY() >= vector0.getY() && screenPointer1.getY() <= vector3.getY() &&
						Math.abs(screenPointer1.getX() - vector1.getX()) <= touchRadius) {
					currentCropHandle = CROP_HANDLE_RIGHT;
				} else if (screenPointer1.getX() >= left && screenPointer1.getX() <= right &&
						screenPointer1.getY() >= top && screenPointer1.getY() <= bottom) {
					currentCropHandle = CROP_HANDLE_IMAGE;
				} else {
					currentCropHandle = CROP_HANDLE_NONE;
				}

				image.setDimAmount(currentCropHandle == CROP_HANDLE_NONE ? Image.DIM_MID : Image.DIM_LOW);

				Matrix.multiplyMV(imagePointer1.getValues(), 0, camera.getInverseViewMatrix(), 0, screenPointer1.getValues(), 0);
//				imagePointer1.translate(-cropCenter.x, -cropCenter.y);
//				imagePointer1.rotate(image.getCropRotationRadians());

				previousScreenPointer1.set(screenPointer1);
				previousImagePointer1.set(imagePointer1);
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				int pointerIndex0 = event.findPointerIndex(touchPointerId0);
				int pointerIndex1 = event.findPointerIndex(touchPointerId1);

				float currentTouchX0 = event.getX(pointerIndex0);
				float currentTouchY0 = event.getY(pointerIndex0);

				Point cropCenter = image.getCropCenter();

				screenPointer1.set(currentTouchX0, currentTouchY0);
				Matrix.multiplyMV(imagePointer1.getValues(), 0, camera.getInverseViewMatrix(), 0, screenPointer1.getValues(), 0);
				Matrix.multiplyMV(previousImagePointer1.getValues(), 0, camera.getInverseViewMatrix(), 0, previousScreenPointer1.getValues(), 0);

				float imageDx = imagePointer1.getX() - previousImagePointer1.getX();
				float imageDy = imagePointer1.getY() - previousImagePointer1.getY();

				imagePointer1.translate(-cropCenter.x, -cropCenter.y);
				imagePointer1.rotate(-image.getCropRotationRadians());

				previousImagePointer1.translate(-cropCenter.x, -cropCenter.y);
				previousImagePointer1.rotate(-image.getCropRotationRadians());

				float dx = imagePointer1.getX() - previousImagePointer1.getX();
				float dy = imagePointer1.getY() - previousImagePointer1.getY();

				float minWidth = 50f;
				float minHeight = 50f;

				float maxOffsetX = Math.max(image.getCropWidth() - minWidth, 0f);
				float maxOffsetY = Math.max(image.getCropHeight() - minHeight, 0f);

				CropRatio cropRatio = image.getCropRatio();

				double diagonalAngle = Math.atan2(image.getCropHeight(), image.getCropWidth());
				double diagonalCos = Math.cos(diagonalAngle);
				double diagonalSin = Math.sin(diagonalAngle);

				Point pivotPoint = new Point();

				Point[] cropVertices = image.getCropVertices();

				tmpVector1.set(dx, dy);

				switch (currentCropHandle) {
					case CROP_HANDLE_TOP_LEFT: {
						if (cropRatio.isFree()) {
							insetTop(-Math.min(maxOffsetY, tmpVector1.y));
							float overflowScale = computeOverflow(Vector.UP);
							insetTop(image.getCropHeight() * (overflowScale - 1f));

							insetLeft(-Math.min(maxOffsetX, tmpVector1.x));
							overflowScale = computeOverflow(Vector.RIGHT);
							insetLeft(image.getCropHeight() * (overflowScale - 1f));
						} else {
							tmpVector2.set(-image.getCropWidth(), -image.getCropHeight());
							tmpVector1.project(tmpVector2);

							maxOffsetY = Math.min(maxOffsetY, maxOffsetX / cropRatio.getRatio());
							float offsetY = Math.min(maxOffsetY, tmpVector1.y);
							float offsetX = offsetY * cropRatio.getRatio();

							insetTop(-offsetY);
							insetLeft(-offsetX);

							rescale(cropVertices[3].x, cropVertices[3].y);
						}
						break;
					}

					case CROP_HANDLE_TOP: {
						if (cropRatio.isFree()) {
							insetTop(-Math.min(maxOffsetY, tmpVector1.y));
							float overflowScale = computeOverflow(Vector.UP);
							insetTop(image.getCropHeight() * (overflowScale - 1f));
						} else {
							maxOffsetY = Math.min(maxOffsetY, maxOffsetX / cropRatio.getRatio());

							float offsetY = Math.min(maxOffsetY, tmpVector1.y);

							insetTop(-offsetY);
							insetLeft(-offsetY * cropRatio.getRatio() / 2f);
							insetRight(-offsetY * cropRatio.getRatio() / 2f);

							rescale((cropVertices[2].x + cropVertices[3].x) / 2f,
									(cropVertices[2].y + cropVertices[3].y) / 2f);
						}
						break;
					}

					case CROP_HANDLE_TOP_RIGHT: {
						if (cropRatio.isFree()) {
							insetTop(-Math.min(maxOffsetY, tmpVector1.y));
							float scale = computeOverflow(Vector.UP);
							insetTop(image.getCropHeight() * (scale - 1f));

							insetRight(-Math.min(maxOffsetX, -tmpVector1.x));
							scale = computeOverflow(Vector.RIGHT);
							insetRight(image.getCropHeight() * (scale - 1f));
						} else {
							tmpVector2.set(cropRatio.w, -cropRatio.h);
							tmpVector1.project(tmpVector2);

							maxOffsetY = Math.min(maxOffsetY, maxOffsetX / cropRatio.getRatio());
							float offsetY = -Math.min(maxOffsetY, tmpVector1.y);
							float offsetX = offsetY * cropRatio.getRatio();

							insetRight(offsetX);
							insetTop(offsetY);

							rescale(cropVertices[2].x, cropVertices[2].y);
						}
						break;
					}

					case CROP_HANDLE_RIGHT: {
						if (cropRatio.isFree()) {
							insetRight(-Math.min(maxOffsetX, -tmpVector1.x));
							float scale = computeOverflow(Vector.RIGHT);
							insetRight(image.getCropHeight() * (scale - 1f));
						} else {
							maxOffsetX = Math.min(maxOffsetX, maxOffsetY * cropRatio.getRatio());

							float offsetX = -Math.min(maxOffsetX, -tmpVector1.x);

							insetRight(offsetX);
							insetTop(offsetX / cropRatio.getRatio() / 2f);
							insetBottom(offsetX / cropRatio.getRatio() / 2f);

							rescale((cropVertices[0].x + cropVertices[2].x) / 2f,
									(cropVertices[0].y + cropVertices[2].y) / 2f);
						}
						break;
					}

					case CROP_HANDLE_BOTTOM_RIGHT: {
						if (cropRatio.isFree()) {
							insetBottom(-Math.min(maxOffsetY, -tmpVector1.y));
							float scale = computeOverflow(Vector.UP);
							insetBottom(image.getCropHeight() * (scale - 1f));

							insetRight(-Math.min(maxOffsetX, -tmpVector1.x));
							scale = computeOverflow(Vector.RIGHT);
							insetRight(image.getCropHeight() * (scale - 1f));
						} else {
							tmpVector2.set(cropRatio.w, cropRatio.h);
							tmpVector1.project(tmpVector2);

							maxOffsetY = Math.min(maxOffsetY, maxOffsetX / cropRatio.getRatio());
							float offsetY = -Math.min(maxOffsetY, -tmpVector1.y);
							float offsetX = offsetY * cropRatio.getRatio();

							insetRight(offsetX);
							insetBottom(offsetY);

							rescale(cropVertices[0].x, cropVertices[0].y);
						}
						break;
					}

					case CROP_HANDLE_BOTTOM: {
						if (cropRatio.isFree()) {
							insetBottom(-Math.min(maxOffsetY, -tmpVector1.y));
							float scale = computeOverflow(Vector.UP);
							insetBottom(image.getCropHeight() * (scale - 1f));
						} else {
							maxOffsetY = Math.min(maxOffsetY, maxOffsetX / cropRatio.getRatio());

							float offsetY = Math.min(maxOffsetY, -tmpVector1.y);

							insetBottom(-offsetY);
							insetLeft(-offsetY * cropRatio.getRatio() / 2f);
							insetRight(-offsetY * cropRatio.getRatio() / 2f);

							rescale((cropVertices[0].x + cropVertices[1].x) / 2f,
									(cropVertices[0].y + cropVertices[1].y) / 2f);
						}
						break;
					}

					case CROP_HANDLE_BOTTOM_LEFT: {
						if (cropRatio.isFree()) {
							insetBottom(-Math.min(maxOffsetY, -tmpVector1.y));
							float scale = computeOverflow(Vector.UP);
							insetBottom(image.getCropHeight() * (scale - 1f));

							insetLeft(-Math.min(maxOffsetX, tmpVector1.x));
							scale = computeOverflow(Vector.RIGHT);
							insetLeft(image.getCropHeight() * (scale - 1f));
						} else {
							tmpVector2.set(-cropRatio.w, cropRatio.h);
							tmpVector1.project(tmpVector2);

							maxOffsetY = Math.min(maxOffsetY, maxOffsetX / cropRatio.getRatio());
							float offsetY = -Math.min(maxOffsetY, -tmpVector1.y);
							float offsetX = offsetY * cropRatio.getRatio();

							insetLeft(offsetX);
							insetBottom(offsetY);

							rescale(cropVertices[1].x, cropVertices[1].y);
						}
						break;
					}

					case CROP_HANDLE_LEFT: {
						if (cropRatio.isFree()) {
							insetLeft(-Math.min(maxOffsetX, tmpVector1.x));
							float scale = computeOverflow(Vector.RIGHT);
							insetLeft(image.getCropHeight() * (scale - 1f));
						} else {
							maxOffsetX = Math.min(maxOffsetX, maxOffsetY * cropRatio.getRatio());

							float offsetX = Math.min(maxOffsetX, tmpVector1.x);

							insetLeft(-offsetX);
							insetTop(-offsetX / cropRatio.getRatio() / 2f);
							insetBottom(-offsetX / cropRatio.getRatio() / 2f);

							rescale((cropVertices[1].x + cropVertices[3].x) / 2f,
									(cropVertices[1].y + cropVertices[3].y) / 2f);
						}
						break;
					}

					case CROP_HANDLE_IMAGE: {
						move(imageDx, imageDy);
						camera.lookAtImage(image);
						break;
					}
				}

				previousScreenPointer1.set(screenPointer1);

//				if (event.getPointerCount() == 1) {
//					float dx = event.getX() - previousTouchX0;
//					float dy = event.getY() - previousTouchX0;
//
//					notifyPan(dx, dy);
//				} else {
//					int pointerIndex0 = event.findPointerIndex(touchPointerId0);
//					int pointerIndex1 = event.findPointerIndex(touchPointerId1);
//
//					float currentTouchX0 = event.getX(pointerIndex0);
//					float currentTouchY0 = event.getY(pointerIndex0);
//
//					float currentTouchX1 = event.getX(pointerIndex1);
//					float currentTouchY1 = event.getY(pointerIndex1);
//
//					float previousTouchMidX = (previousTouchX0 + previousTouchX1) / 2f;
//					float previousTouchMidY = (previousTouchY0 + previousTouchY1) / 2f;
//
//					float currentTouchMidX = (currentTouchX0 + currentTouchX1) / 2f;
//					float currentTouchMidY = (currentTouchY0 + currentTouchY1) / 2f;
//
//					float scale = (float) Math.hypot(currentTouchX0 - currentTouchX1, currentTouchY0 - currentTouchY1) /
//							Math.max(1f, (float) Math.hypot(previousTouchX0 - previousTouchX1, previousTouchY0 - previousTouchY1));
//
//					notifyPan(currentTouchMidX - previousTouchMidX, currentTouchMidY - previousTouchMidY);
//					notifyScale(scale);
//				}
				break;
			}

			case MotionEvent.ACTION_POINTER_DOWN: {
				if (event.getPointerCount() == 2) {
					int pointerIndex = event.getActionIndex();

					touchPointerId1 = event.getPointerId(pointerIndex);

					previousScreenPointer2.set(event.getX(pointerIndex), event.getY(pointerIndex));
				}
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				int removedPointerIndex = event.getActionIndex();
				int removedPointerId = event.getPointerId(removedPointerIndex);
				boolean pointerRemoved = touchPointerId0 == removedPointerId || touchPointerId1 == removedPointerId;
				if (pointerRemoved) {
					if (event.getPointerCount() > 2) {
						for (int i = 0; i < event.getPointerCount(); ++i) {
							int pointerId = event.getPointerId(i);
							if (pointerId != touchPointerId0 && pointerId != touchPointerId1) {
								if (touchPointerId0 == removedPointerId) {
									touchPointerId0 = pointerId;

									previousScreenPointer1.set(event.getX(i), event.getY(i));
								} else {
									touchPointerId1 = pointerId;

									previousScreenPointer2.set(event.getX(i), event.getY(i));
								}
								break;
							}
						}
					} else {
						if (touchPointerId0 == removedPointerId) {
							touchPointerId0 = touchPointerId1;
							touchPointerId1 = MotionEvent.INVALID_POINTER_ID;

							int validPointerIndex = event.findPointerIndex(touchPointerId0);

							previousScreenPointer1.set(event.getX(validPointerIndex), event.getY(validPointerIndex));
						}
					}
				}
				break;
			}

			case MotionEvent.ACTION_OUTSIDE:
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				camera.lookAtImage(image);
				image.setDimAmount(Image.DIM_MID);
				break;
			}
		}

		editorView.requestRender();
	}

	private void insetTop(float dy) {
		Point cropCenter = image.getCropCenter();

		image.setCropHeight(image.getCropHeight() + dy);
		
		Vector v = new Vector(Vector.UP);
		v.rotate(image.getCropRotationRadians());
		v.unit().scale(-dy / 2f);
		
		cropCenter.set(cropCenter.x + v.x, cropCenter.y + v.y);
		
//		image.setCropCenter(cropCenter.x + (float) Math.sin(-image.getCropRotationRadians()) * dy / 2f,
//				cropCenter.y + (float) Math.cos(-image.getCropRotationRadians()) * dy / 2f);
//
//		vector0.set(cropCenter.x, cropCenter.y - sign * image.getCropHeight() / 2f);
//		vector0.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());
//
//		float overflowScale = computeOverflow(vector0.getX(), vector0.getY());
//
//		Log.d("Crop", "Overflow scale (v) = " + overflowScale);
//
//		dy = image.getCropHeight() * (1f - overflowScale);
//
//		image.setCropHeight(image.getCropHeight() - dy);
//		image.setCropCenter(cropCenter.x - sign * (float) Math.sin(-image.getCropRotationRadians()) * dy / 2f,
//				cropCenter.y - sign * (float) Math.cos(-image.getCropRotationRadians()) * dy / 2f);
	}

	private void insetBottom(float dy) {
		Point cropCenter = image.getCropCenter();

		image.setCropHeight(image.getCropHeight() + dy);

		Vector v = new Vector(Vector.DOWN);
		v.rotate(image.getCropRotationRadians());
		v.unit().scale(-dy / 2f);

		cropCenter.set(cropCenter.x + v.x, cropCenter.y + v.y);
	}

	private void insetLeft(float dx) {
		Point cropCenter = image.getCropCenter();

		image.setCropWidth(image.getCropWidth() + dx);

		Vector v = new Vector(Vector.RIGHT);
		v.rotate(image.getCropRotationRadians());
		v.unit().scale(-dx / 2f);

		cropCenter.set(cropCenter.x + v.x, cropCenter.y + v.y);

//		Point cropCenter = image.getCropCenter();
//
//		image.setCropWidth(image.getCropWidth() + sign * dx);
//		image.setCropCenter(cropCenter.x + (float) Math.cos(image.getCropRotationRadians()) * dx / 2f,
//				cropCenter.y + (float) Math.sin(image.getCropRotationRadians()) * dx / 2f);

//		vector0.set(cropCenter.x - sign * image.getCropWidth() / 2f, cropCenter.y);
//		vector0.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());
//
//		float overflowScale = computeOverflow(vector0.getX(), vector0.getY());
//
//		Log.d("Crop", "Overflow scale (h) = " + overflowScale);
//
//		dx = image.getCropWidth() * (1f - overflowScale);
//
//		image.setCropWidth(image.getCropWidth() - dx);
//		image.setCropCenter(cropCenter.x - sign * (float) Math.cos(image.getCropRotationRadians()) * dx / 2f,
//				cropCenter.y - sign * (float) Math.sin(image.getCropRotationRadians()) * dx / 2f);
	}

	private void insetRight(float dx) {
		Point cropCenter = image.getCropCenter();

		image.setCropWidth(image.getCropWidth() + dx);

		Vector v = new Vector(Vector.LEFT);
		v.rotate(image.getCropRotationRadians());
		v.unit().scale(-dx / 2f);

		cropCenter.set(cropCenter.x + v.x, cropCenter.y + v.y);
	}

	private void scaleTop(float scale) {
		insetTop(image.getCropHeight() * (scale - 1f));

//		Point cropCenter = image.getCropCenter();
//
//		image.setCropHeight(image.getCropHeight() * scale);
//
//		insetTop();
//
//		cropCenter.scale(pivotPoint.x, pivotPoint.y, scale);
//		float dy = image.getCropHeight() * (1f - scale);
//
//		image.setCropHeight(image.getCropHeight() - dy);
//		image.setCropCenter(cropCenter.x, cropCenter.y - (float) Math.cos(-image.getCropRotationRadians()) * dy / 2f);
	}

	private void scaleHorizontal(float scale, Point pivotPoint) {
//		Point cropCenter = image.getCropCenter();
//		
//		float dx = image.getCropWidth() * (1f - scale);
//
//		image.setCropWidth(image.getCropWidth() - dx);
//		image.setCropCenter(cropCenter.x - sign * (float) Math.cos(image.getCropRotationRadians()) * dx / 2f,
//				cropCenter.y - sign * (float) Math.sin(image.getCropRotationRadians()) * dx / 2f);

		Point cropCenter = image.getCropCenter();

		image.setCropWidth(image.getCropWidth() * scale);

		cropCenter.scale(pivotPoint.x, pivotPoint.y, scale);
	}
	
	private void rescale(float px, float py) {
		float scale = computeOverflow2(px, py);
		
		Point cropCenter = image.getCropCenter();

		image.setCropWidth(image.getCropWidth() * scale);
		image.setCropHeight(image.getCropHeight() * scale);

		cropCenter.scale(px, py, scale);
	}

	private void move(float dx, float dy) {
		Point cropCenter = image.getCropCenter();

		image.setCropCenter(cropCenter.x - dx, cropCenter.y - dy);

		vector0.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
		vector0.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

		vector1.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
		vector1.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

		vector2.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
		vector2.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

		vector3.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
		vector3.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

		dx = 0f;
		dy = 0f;

		if (vector0.getX() < 0) {
			dx = vector0.getX();
		} else if (vector0.getX() > image.getWidth()) {
			dx = vector0.getX() - image.getWidth();
		}

		if (vector0.getY() < 0) {
			dy = vector0.getY();
		} else if (vector0.getY() > image.getHeight()) {
			dy = vector0.getY() - image.getHeight();
		}

		if (vector1.getX() < 0) {
			dx = vector1.getX();
		} else if (vector1.getX() > image.getWidth()) {
			dx = vector1.getX() - image.getWidth();
		}

		if (vector1.getY() < 0) {
			dy = vector1.getY();
		} else if (vector1.getY() > image.getHeight()) {
			dy = vector1.getY() - image.getHeight();
		}

		if (vector2.getX() < 0) {
			dx = vector2.getX();
		} else if (vector2.getX() > image.getWidth()) {
			dx = vector2.getX() - image.getWidth();
		}

		if (vector2.getY() < 0) {
			dy = vector2.getY();
		} else if (vector2.getY() > image.getHeight()) {
			dy = vector2.getY() - image.getHeight();
		}

		if (vector3.getX() < 0) {
			dx = vector3.getX();
		} else if (vector3.getX() > image.getWidth()) {
			dx = vector3.getX() - image.getWidth();
		}

		if (vector3.getY() < 0) {
			dy = vector3.getY();
		} else if (vector3.getY() > image.getHeight()) {
			dy = vector3.getY() - image.getHeight();
		}

		image.setCropCenter(cropCenter.x - dx, cropCenter.y - dy);
	}

	private float computeOverflow(Vector direction) {
//		float overflowScale = 1f;
//
//		PointF cropCenter = image.getCropCenter();
//
//		vector0.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
//		vector0.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());
//
//		vector1.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
//		vector1.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());
//
//		vector2.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
//		vector2.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());
//
//		vector3.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
//		vector3.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());
//
//		float left = -0.001f;
//		float top = -0.001f;
//		float right = image.getWidth() + 0.001f;
//		float bottom = image.getHeight() + 0.001f;
//
//		if (vector0.getX() < left) {
//			overflowScale = Math.min(overflowScale, cx / (cx - vector0.getX()));
//		} else if (vector0.getX() > right) {
//			overflowScale = Math.min(overflowScale, (image.getWidth() - cx) / (vector0.getX() - cx));
//		}
//
//		if (vector0.getY() < top) {
//			overflowScale = Math.min(overflowScale, cy / (cy - vector0.getY()));
//		} else if (vector0.getY() > bottom) {
//			overflowScale = Math.min(overflowScale, (image.getHeight() - cy) / (vector0.getY() - cy));
//		}
//
//		if (vector1.getX() < left) {
//			overflowScale = Math.min(overflowScale, cx / (cx - vector1.getX()));
//		} else if (vector1.getX() > right) {
//			overflowScale = Math.min(overflowScale, (image.getWidth() - cx) / (vector1.getX() - cx));
//		}
//
//		if (vector1.getY() < top) {
//			overflowScale = Math.min(overflowScale, cy / (cy - vector1.getY()));
//		} else if (vector1.getY() > bottom) {
//			overflowScale = Math.min(overflowScale, (image.getHeight() - cy) / (vector1.getY() - cy));
//		}
//
//		if (vector2.getX() < left) {
//			overflowScale = Math.min(overflowScale, cx / (cx - vector2.getX()));
//		} else if (vector2.getX() > right) {
//			overflowScale = Math.min(overflowScale, (image.getWidth() - cx) / (vector2.getX() - cx));
//		}
//
//		if (vector2.getY() < top) {
//			overflowScale = Math.min(overflowScale, cy / (cy - vector2.getY()));
//		} else if (vector2.getY() > bottom) {
//			overflowScale = Math.min(overflowScale, (image.getHeight() - cy) / (vector2.getY() - cy));
//		}
//
//		if (vector3.getX() < left) {
//			overflowScale = Math.min(overflowScale, cx / (cx - vector3.getX()));
//		} else if (vector3.getX() > right) {
//			overflowScale = Math.min(overflowScale, (image.getWidth() - cx) / (vector3.getX() - cx));
//		}
//
//		if (vector3.getY() < top) {
//			overflowScale = Math.min(overflowScale, cy / (cy - vector3.getY()));
//		} else if (vector3.getY() > bottom) {
//			overflowScale = Math.min(overflowScale, (image.getHeight() - cy) / (vector3.getY() - cy));
//		}
//
//		return overflowScale;

		Vector directionVector = new Vector(direction);
		directionVector.rotate(image.getCropRotationRadians());

		Vector edgeVector = new Vector();

		float scale = 1f;

		Line edge1 = new Line();
		Line edge2 = new Line();

		Line topLine = new Line(0f, 0f, image.getWidth(), 0f);
		Line bottomLine = new Line(0f, image.getHeight(), image.getWidth(), image.getHeight());
		Line leftLine = new Line(0f, 0f, 0f, image.getHeight());
		Line rightLine = new Line(image.getWidth(), 0f, image.getWidth(), image.getHeight());

		Point[] cropVertices = image.getCropVertices();

		for (int i = 0; i < cropVertices.length; i++) {
			Point vertex = cropVertices[i];
			if (vertex.x > image.getWidth() || vertex.x < 0f || vertex.y > image.getHeight() || vertex.y < 0f) {
				edge1.set(cropVertices[(i + 3) % cropVertices.length], vertex);
				edge2.set(cropVertices[(i + 1) % cropVertices.length], vertex);

				edgeVector.set(edge1.p0, edge1.p1);

				Line correctEdge;

				if (edgeVector.rotationCosine(directionVector) > 0.5f) {
					correctEdge = edge1;
				} else {
					correctEdge = edge2;
				}

				float intersectionFraction = Float.NaN;

				if (vertex.x > image.getWidth()) {
					intersectionFraction = correctEdge.intersectFraction(rightLine);
				} else if (vertex.x < 0f) {
					intersectionFraction = correctEdge.intersectFraction(leftLine);
				}

				if (!Float.isNaN(intersectionFraction)) {
					scale = Math.min(scale, intersectionFraction);
				}

				intersectionFraction = Float.NaN;

				if (vertex.y > image.getHeight()) {
					intersectionFraction = correctEdge.intersectFraction(bottomLine);
				} else if (vertex.y < 0f) {
					intersectionFraction = correctEdge.intersectFraction(topLine);
				}

				if (!Float.isNaN(intersectionFraction)) {
					scale = Math.min(scale, intersectionFraction);
				}
			}
		}

		return scale;
	}

	private float computeOverflow2(float px, float py) {
		Point[] cropVertices = image.getCropVertices();

		float scale = 1f;

		Line topLine = new Line(0f, 0f, image.getWidth(), 0f);
		Line bottomLine = new Line(0f, image.getHeight(), image.getWidth(), image.getHeight());
		Line leftLine = new Line(0f, 0f, 0f, image.getHeight());
		Line rightLine = new Line(image.getWidth(), 0f, image.getWidth(), image.getHeight());

		Line scalingLine = new Line();
		
		Point pivotPoint = new Point(px, py);

		for (int i = 0; i < 4; i++) {
			Point vertex = cropVertices[i];

			scalingLine.set(pivotPoint, vertex);

			float intersectionFraction = Float.NaN;

			if (vertex.x > image.getWidth()) {
				intersectionFraction = scalingLine.intersectFraction(rightLine);
			} else if (vertex.x < 0f) {
				intersectionFraction = scalingLine.intersectFraction(leftLine);
			}

			if (!Float.isNaN(intersectionFraction)) {
				scale = Math.min(scale, intersectionFraction);
			}

			intersectionFraction = Float.NaN;

			if (vertex.y > image.getHeight()) {
				intersectionFraction = scalingLine.intersectFraction(bottomLine);
			} else if (vertex.y < 0f) {
				intersectionFraction = scalingLine.intersectFraction(topLine);
			}

			if (!Float.isNaN(intersectionFraction)) {
				scale = Math.min(scale, intersectionFraction);
			}
		}

		return scale;
	}

	private void notifyPan(float dx, float dy) {
		if (callback != null) {
			callback.onPan(dx, dy);
		}
	}

	private void notifyScale(float s) {
		if (callback != null) {
			callback.onScale(s);
		}
	}

	public interface Callback {
		void onPan(float dx, float dy);

		void onScale(float s);
	}
}
