package org.stingle.photos.Editor.opengl;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;

import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.math.Point;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Editor.util.MatrixEvaluator;
import org.stingle.photos.Editor.views.GLImageView;

public class Camera {
	private float[] projectionMatrix;
	private float[] viewMatrix;
	private float[] inverseViewMatrix;

	private float[] viewProjectionMatrix;

	private float[] tmpMatrix = new float[16];

	private GLImageView imageView;

	private int viewportWidth;
	private int viewportHeight;

	private float paddingLeft;
	private float paddingTop;
	private float paddingRight;
	private float paddingBottom;

	private boolean matrixDirty;

	private Handler mainThreadHandler;

	public Camera(GLImageView imageView) {
		this.imageView = imageView;

		mainThreadHandler = new Handler(Looper.getMainLooper());

		projectionMatrix = new float[16];

		viewMatrix = new float[16];
		Matrix.setIdentityM(viewMatrix, 0);

		viewProjectionMatrix = new float[16];

		inverseViewMatrix = new float[16];

		matrixDirty = true;
	}

	public void setPadding(float left, float top, float right, float bottom) {
		paddingLeft = left;
		paddingTop = top;
		paddingRight = right;
		paddingBottom = bottom;
	}

	public void setViewport(int width, int height) {
		viewportWidth = width;
		viewportHeight = height;

		Matrix.orthoM(projectionMatrix, 0, 0f, width, height, 0f, 0f, 1f);

		matrixDirty = true;
	}

	public void lookAtImage(Image image) {
		if (viewportWidth != 0 && viewportHeight != 0) {
			Point cropCenter = image.getCropCenter();

			float scale = Math.min((viewportWidth - paddingLeft - paddingRight) / image.getCropWidth(),
					(viewportHeight - paddingTop - paddingBottom) / image.getCropHeight());

			Matrix.setIdentityM(viewMatrix, 0);
			Matrix.translateM(viewMatrix, 0, paddingLeft + (viewportWidth - paddingLeft - paddingRight) / 2f,
					paddingTop + (viewportHeight - paddingTop - paddingBottom) / 2f, 0f);
			Matrix.scaleM(viewMatrix, 0, scale, scale, 1f);
			Matrix.rotateM(viewMatrix, 0, -image.getCropRotation() + image.getOrientation() * 90, 0f, 0f, 1f);
			Matrix.translateM(viewMatrix, 0, -cropCenter.x, -cropCenter.y, 0f);
//

			Matrix.invertM(inverseViewMatrix, 0, viewMatrix, 0);

			matrixDirty = true;
		}
	}

	public void pan(float dx, float dy) {
		Matrix.setIdentityM(tmpMatrix, 0);
		Matrix.translateM(tmpMatrix, 0, dx, dy, 0f);

		Matrix.multiplyMM(viewMatrix, 0, tmpMatrix, 0, viewMatrix, 0);

		Matrix.invertM(inverseViewMatrix, 0, viewMatrix, 0);

		matrixDirty = true;
	}

	public void scale(float s, float px, float py) {
		Matrix.setIdentityM(tmpMatrix, 0);
		Matrix.translateM(tmpMatrix, 0, px, py, 0f);
		Matrix.scaleM(tmpMatrix, 0, s, s, 1f);
		Matrix.translateM(tmpMatrix, 0, -px, -py, 0f);

		Matrix.multiplyMM(viewMatrix, 0, tmpMatrix, 0, viewMatrix, 0);

		Matrix.invertM(inverseViewMatrix, 0, viewMatrix, 0);

		matrixDirty = true;
	}

	public void animateRotate(float degrees, float px, float py) {
		float[] oldViewMatrix = new float[16];
		float[] newViewMatrix = new float[16];
		System.arraycopy(viewMatrix, 0, oldViewMatrix, 0, viewMatrix.length);

		Matrix.setIdentityM(tmpMatrix, 0);
		Matrix.translateM(tmpMatrix, 0, px, py, 0f);
		Matrix.rotateM(tmpMatrix, 0, degrees, 0f, 0f, 1f);
		Matrix.translateM(tmpMatrix, 0, -px, -py, 0f);

		Matrix.multiplyMM(newViewMatrix, 0, viewMatrix, 0, tmpMatrix, 0);

		mainThreadHandler.post(() -> {
			ValueAnimator valueAnimator = ValueAnimator.ofObject(new MatrixEvaluator(), oldViewMatrix, newViewMatrix);
			valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					imageView.queueEvent(() -> {
						System.arraycopy((float[]) animation.getAnimatedValue(), 0, viewMatrix, 0, viewMatrix.length);

						Matrix.invertM(inverseViewMatrix, 0, viewMatrix, 0);

						matrixDirty = true;

						imageView.requestRender();
					});
				}
			});
			valueAnimator.addListener(new Animator.AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {

				}

				@Override
				public void onAnimationEnd(Animator animation) {
					imageView.getImage(new Callback<Image>() {
						@Override
						public void call(Image image) {
							image.setOrientation(image.getOrientation() + 1);
							float cropWidth = image.getCropWidth();
							float cropHeight = image.getCropHeight();

//							image.setCropWidth(cropHeight);
//							image.setCropHeight(cropWidth);

							lookAtImage(image);
						}
					});
				}

				@Override
				public void onAnimationCancel(Animator animation) {

				}

				@Override
				public void onAnimationRepeat(Animator animation) {

				}
			});
			valueAnimator.setDuration(200);
			valueAnimator.start();
		});
	}

	public float[] getInverseViewMatrix() {
		if (matrixDirty) {
			computeMatrices();
			matrixDirty = false;
		}

		return inverseViewMatrix;
	}

	public float[] getViewMatrix() {
		if (matrixDirty) {
			computeMatrices();
			matrixDirty = false;
		}

		return viewMatrix;
	}

	public float[] getViewProjectionMatrix() {
		if (matrixDirty) {
			computeMatrices();
			matrixDirty = false;
		}

		return viewProjectionMatrix;
	}

	public float[] getProjectionMatrix() {
		return projectionMatrix;
	}

	private void computeMatrices() {
//		Matrix.setIdentityM(viewMatrix, 0);
////		Matrix.translateM(viewMatrix, 0, (float) (px * Math.cos(Math.toRadians(rotation))), (float) (py * Math.sin(Math.toRadians(rotation))), 0f);
//		Matrix.translateM(viewMatrix, 0, px, py, 0f);
//		Matrix.rotateM(viewMatrix, 0, rotation, 0f, 0f, 1f);
//		Matrix.scaleM(viewMatrix, 0, scale, scale, 1f);

		Matrix.invertM(inverseViewMatrix, 0, viewMatrix, 0);

		Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
	}
}
