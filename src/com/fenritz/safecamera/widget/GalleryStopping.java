package com.fenritz.safecamera.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Gallery;

public class GalleryStopping extends Gallery {

	public GalleryStopping(Context context) {
		super(context);
	}

	public GalleryStopping(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public GalleryStopping(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		int kEvent;
		
		if (e2.getX() > e1.getX()) { // Check if scrolling left
			kEvent = KeyEvent.KEYCODE_DPAD_LEFT;
		} 
		else { // Otherwise scrolling right
			kEvent = KeyEvent.KEYCODE_DPAD_RIGHT;
		}
		onKeyDown(kEvent, null);
		return true;
	}

}
