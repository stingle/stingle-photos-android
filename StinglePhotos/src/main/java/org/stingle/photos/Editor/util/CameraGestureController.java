package org.stingle.photos.Editor.util;

import android.view.MotionEvent;

public class CameraGestureController {
	private int touchStartPointerId;

	private int touchPointerId0;
	private int touchPointerId1;

	private float previousTouchX0;
	private float previousTouchY0;

	private float previousTouchX1;
	private float previousTouchY1;

	private Callback callback;

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public void onTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				previousTouchX0 = event.getX();
				previousTouchY0 = event.getY();

				touchPointerId0 = event.getPointerId(0);
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				float currentTouchX0;
				float currentTouchY0;
				float currentTouchX1 = 0f;
				float currentTouchY1 = 0f;

				if (event.getPointerCount() == 1) {
					currentTouchX0 = event.getX();
					currentTouchY0 = event.getY();

					float dx = currentTouchX0 - previousTouchX0;
					float dy = currentTouchY0 - previousTouchY0;

					notifyPan(dx, dy);
				} else {
					int pointerIndex0 = event.findPointerIndex(touchPointerId0);
					int pointerIndex1 = event.findPointerIndex(touchPointerId1);

					currentTouchX0 = event.getX(pointerIndex0);
					currentTouchY0 = event.getY(pointerIndex0);

					currentTouchX1 = event.getX(pointerIndex1);
					currentTouchY1 = event.getY(pointerIndex1);

					float previousTouchMidX = (previousTouchX0 + previousTouchX1) / 2f;
					float previousTouchMidY = (previousTouchY0 + previousTouchY1) / 2f;

					float currentTouchMidX = (currentTouchX0 + currentTouchX1) / 2f;
					float currentTouchMidY = (currentTouchY0 + currentTouchY1) / 2f;

					float scale = (float) Math.hypot(currentTouchX0 - currentTouchX1, currentTouchY0 - currentTouchY1) /
							Math.max(1f, (float) Math.hypot(previousTouchX0 - previousTouchX1, previousTouchY0 - previousTouchY1));

					notifyPan(currentTouchMidX - previousTouchMidX, currentTouchMidY - previousTouchMidY);
					notifyScale(scale, currentTouchMidX, currentTouchMidY);
				}

				previousTouchX0 = currentTouchX0;
				previousTouchY0 = currentTouchY0;

				previousTouchX1 = currentTouchX1;
				previousTouchY1 = currentTouchY1;
				break;
			}

			case MotionEvent.ACTION_POINTER_DOWN: {
				if (event.getPointerCount() == 2) {
					int pointerIndex = event.getActionIndex();

					touchPointerId1 = event.getPointerId(pointerIndex);

					previousTouchX1 = event.getX(pointerIndex);
					previousTouchY1 = event.getY(pointerIndex);
				}
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				int removedPointerIndex = event.getActionIndex();
				int removedPointerId = event.getPointerId(removedPointerIndex);
				boolean pointerRemoved = touchPointerId0 == removedPointerId || touchPointerId1 == removedPointerId;
				if (pointerRemoved) {
					if (event.getPointerCount() < 3) {
						touchPointerId0 = MotionEvent.INVALID_POINTER_ID;
						touchPointerId1 = MotionEvent.INVALID_POINTER_ID;

						for (int i = 0; i < event.getPointerCount(); ++i) {
							int pointerId = event.getPointerId(i);
							if (pointerId != removedPointerId) {
								touchPointerId0 = pointerId;

								previousTouchX0 = event.getX(i);
								previousTouchY0 = event.getY(i);
							}
						}
					} else {
						if (touchPointerId0 == removedPointerId) {
							touchPointerId0 = MotionEvent.INVALID_POINTER_ID;
						} else {
							touchPointerId1 = MotionEvent.INVALID_POINTER_ID;
						}

						for (int i = 0; i < event.getPointerCount(); ++i) {
							int pointerId = event.getPointerId(i);
							if (pointerId != removedPointerId && pointerId != touchPointerId0 && pointerId != touchPointerId1) {
								if (touchPointerId0 == MotionEvent.INVALID_POINTER_ID) {
									touchPointerId0 = pointerId;

									previousTouchX0 = event.getX(i);
									previousTouchY0 = event.getY(i);
								} else if (touchPointerId1 == MotionEvent.INVALID_POINTER_ID) {
									touchPointerId1 = pointerId;

									previousTouchX1 = event.getX(i);
									previousTouchY1 = event.getY(i);
								}
							}
						}
					}
				}
				break;
			}

			case MotionEvent.ACTION_OUTSIDE:
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				break;
			}
		}
	}

	private void notifyPan(float dx, float dy) {
		if (callback != null) {
			callback.onPan(dx, dy);
		}
	}

	private void notifyScale(float s, float px, float py) {
		if (callback != null) {
			callback.onScale(s, px, py);
		}
	}

	public interface Callback {
		void onPan(float dx, float dy);

		void onScale(float s, float px, float py);
	}
}
