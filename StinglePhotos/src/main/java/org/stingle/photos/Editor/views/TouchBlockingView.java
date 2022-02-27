package org.stingle.photos.Editor.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TouchBlockingView extends View {
	public TouchBlockingView(Context context) {
		super(context);
	}

	public TouchBlockingView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TouchBlockingView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public TouchBlockingView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@SuppressLint("MissingSuperCall")
	@Override
	public void draw(Canvas canvas) {
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return true;
	}
}
