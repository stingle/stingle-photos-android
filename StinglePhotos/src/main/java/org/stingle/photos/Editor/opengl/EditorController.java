package org.stingle.photos.Editor.opengl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;

import org.stingle.photos.Editor.views.EditorView;

public abstract class EditorController {
	private EditorView editorView;

	public EditorController(EditorView editorView) {
		this.editorView = editorView;
	}

	protected Context getContext() {
		return editorView.getContext();
	}

	protected void notifyRedrawNeeded() {
		editorView.invalidate();
	}

	public abstract void onDraw(Canvas canvas, Bitmap image);
	public abstract boolean onTouchEvent(MotionEvent event);
}
