package org.stingle.photos.Editor.opengl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;

import org.stingle.photos.Editor.views.EditorView;

public class EditorPerspectiveController extends EditorController {
	public EditorPerspectiveController(EditorView editorView) {
		super(editorView);
	}

	@Override
	public void onDraw(Canvas canvas, Bitmap image) {

	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return false;
	}
}
