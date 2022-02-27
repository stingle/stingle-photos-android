package org.stingle.photos.Editor.views;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.stingle.photos.Editor.core.CropRatio;
import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.opengl.Camera;
import org.stingle.photos.Editor.opengl.EditorRenderer;
import org.stingle.photos.Editor.util.Callback;
import org.stingle.photos.Editor.util.CameraGestureController;
import org.stingle.photos.Editor.util.CropGestureController;

public class GLImageView extends GLSurfaceView implements CameraGestureController.Callback, Image.ImageChangeListener {
	public static final int EDITOR_MODE_NORMAL = 0;
	public static final int EDITOR_MODE_CROP = 1;

	private int editorMode;

	private EditorRenderer editorRenderer;

	private Camera camera;

	private Image image;

	private CropGestureController cropGestureController;
	private CameraGestureController cameraGestureController;

	public GLImageView(Context context) {
		super(context);

		init();
	}

	public GLImageView(Context context, AttributeSet attrs) {
		super(context, attrs);

		init();
	}

	public void setEditorMode(int editorMode) {
		queueEvent(() -> {
			this.editorMode = editorMode;

			editorRenderer.setEditorMode(editorMode);

			requestRender();
		});
	}

	public void setViewportPadding(float left, float top, float right, float bottom) {
		queueEvent(() -> {
			camera.setPadding(left, top, right, bottom);

			if (image != null) {
				camera.lookAtImage(image);
			}

			requestRender();
		});
	}

	public void setImage(Image image) {
		queueEvent(() -> {
			this.image = image;
			this.image.setEditorChangeListener(GLImageView.this);

			if (editorRenderer != null) {
				editorRenderer.setImage(image);
			}

			cropGestureController.setImage(image);

			requestRender();
		});
	}

	public void getImage(Callback<Image> imageCallback) {
		queueEvent(() -> {
			imageCallback.call(image);
			requestRender();
		});
	}

	public void getCamera(Callback<Camera> cameraCallback) {
		queueEvent(() -> {
			cameraCallback.call(camera);
			requestRender();
		});
	}

	private void init() {
		cameraGestureController = new CameraGestureController();
		cameraGestureController.setCallback(this);

		cropGestureController = new CropGestureController(this);

		editorRenderer = new EditorRenderer(this);

		setEGLContextClientVersion(2);
		setEGLConfigChooser(8, 8, 8, 8, 0, 0);
		getHolder().setFormat(PixelFormat.RGBA_8888);
		setRenderer(editorRenderer);
		setRenderMode(RENDERMODE_WHEN_DIRTY);
		requestRender();

		setEditorMode(EDITOR_MODE_NORMAL);

		queueEvent(() -> {
			this.camera = new Camera(this);

			cropGestureController.setCamera(camera);
			editorRenderer.setCamera(camera);
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		MotionEvent eventCopy = MotionEvent.obtain(event);

		queueEvent(() -> {
			if (editorMode == EDITOR_MODE_NORMAL) {
				cameraGestureController.onTouchEvent(eventCopy);
			} else {
				cropGestureController.onTouchEvent(eventCopy);
			}
			eventCopy.recycle();
		});

		return true;
	}

	@Override
	public void onPan(float dx, float dy) {
		queueEvent(() -> {
			editorRenderer.getCamera().pan(dx, dy);
			requestRender();
		});
	}

	@Override
	public void onScale(float s, float px, float py) {
		queueEvent(() -> {
			editorRenderer.getCamera().scale(s, px, py);
			requestRender();
		});
	}

	@Override
	public void onImageChanged(Image image) {
		queueEvent(() -> {
//			editorRenderer.getBitmapImageRenderer().setImageConfiguration(imageConfiguration);
			requestRender();
		});
	}
}
