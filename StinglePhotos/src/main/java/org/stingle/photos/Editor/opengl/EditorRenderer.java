package org.stingle.photos.Editor.opengl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.TypedValue;

import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.math.Point;
import org.stingle.photos.Editor.math.Vector2;
import org.stingle.photos.Editor.views.GLImageView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class EditorRenderer implements GLSurfaceView.Renderer {

	private BitmapImageRenderer bitmapImageRenderer;
	private CircleRenderer circleRenderer;

	private Camera camera;

	private Image image;

	private GLImageView editorView;

	private int editorMode;

	private Vector2 vector;
	private float cropHandleRadius;

	public EditorRenderer(GLImageView editorView) {
		this.editorView = editorView;

		bitmapImageRenderer = new BitmapImageRenderer(editorView.getContext());
		if (image != null) {
			bitmapImageRenderer.setImage(image);
		}

		circleRenderer = new CircleRenderer(editorView.getContext());
		circleRenderer.setCenterAndRadius(720, 1200, 300);

		vector = new Vector2();

		cropHandleRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, editorView.getResources().getDisplayMetrics());

		editorMode = GLImageView.EDITOR_MODE_NORMAL;
	}

	public void setEditorMode(int editorMode) {
		this.editorMode = editorMode;

		if (image != null) {
			if (editorMode == GLImageView.EDITOR_MODE_NORMAL) {
				image.setDimAmount(Image.DIM_FULL);
			} else {
				image.setDimAmount(Image.DIM_MID);
			}
		}
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}

	public void setImage(Image image) {
		this.image = image;

		if (bitmapImageRenderer != null) {
			bitmapImageRenderer.setImage(image);
		}

		camera.lookAtImage(image);
	}

	public Camera getCamera() {
		return camera;
	}

	public BitmapImageRenderer getBitmapImageRenderer() {
		return bitmapImageRenderer;
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);

		bitmapImageRenderer.createProgram();
		bitmapImageRenderer.createTexture();

		circleRenderer.createProgram();

//		editorView.postDelayed(new Runnable() {
//			@Override
//			public void run() {
//				editorView.queueEvent(() -> {
//					camera.rotateALittle();
//					editorView.requestRender();
//				});
//
//				editorView.postDelayed(this, 100);
//			}
//		}, 100);
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		GLES20.glViewport(0, 0, width, height);

		camera.setViewport(width, height);
		if (image != null) {
			camera.lookAtImage(image);
		}
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		GLES20.glClearColor(0f, 0f, 0f, 1f);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

		bitmapImageRenderer.draw(camera.getViewProjectionMatrix());

		if (editorMode == GLImageView.EDITOR_MODE_CROP) {
			Point cropCenter = image.getCropCenter();

			vector.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
			vector.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

			Matrix.multiplyMV(vector.getValues(), 0, camera.getViewMatrix(), 0, vector.getValues(), 0);

			circleRenderer.setCenterAndRadius(vector.getX(), vector.getY(), cropHandleRadius);
			circleRenderer.draw(camera.getProjectionMatrix());

			vector.set(cropCenter.x - image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
			vector.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

			Matrix.multiplyMV(vector.getValues(), 0, camera.getViewMatrix(), 0, vector.getValues(), 0);

			circleRenderer.setCenterAndRadius(vector.getX(), vector.getY(), cropHandleRadius);
			circleRenderer.draw(camera.getProjectionMatrix());

			vector.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y - image.getCropHeight() / 2f);
			vector.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

			Matrix.multiplyMV(vector.getValues(), 0, camera.getViewMatrix(), 0, vector.getValues(), 0);

			circleRenderer.setCenterAndRadius(vector.getX(), vector.getY(), cropHandleRadius);
			circleRenderer.draw(camera.getProjectionMatrix());

			vector.set(cropCenter.x + image.getCropWidth() / 2f, cropCenter.y + image.getCropHeight() / 2f);
			vector.rotate(cropCenter.x, cropCenter.y, image.getCropRotationRadians());

			Matrix.multiplyMV(vector.getValues(), 0, camera.getViewMatrix(), 0, vector.getValues(), 0);

			circleRenderer.setCenterAndRadius(vector.getX(), vector.getY(), cropHandleRadius);
			circleRenderer.draw(camera.getProjectionMatrix());
		}
	}
}
