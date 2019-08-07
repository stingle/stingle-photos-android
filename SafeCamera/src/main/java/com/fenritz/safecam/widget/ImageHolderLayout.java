package com.fenritz.safecam.Widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.fenritz.safecam.Crypto.Crypto;

public class ImageHolderLayout extends LinearLayout {

	private boolean inteceptAllowed = true;
	private OnTouchListener listener = null;
	private int fileType = Crypto.FILE_TYPE_PHOTO;
	
	public ImageHolderLayout(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public ImageHolderLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	@SuppressLint("NewApi")
	public ImageHolderLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(listener != null){
			listener.onTouch(this, event);
		}
		return super.onTouchEvent(event);
	}
	
	@Override
	public void setOnTouchListener(OnTouchListener l) {
		listener = l;
		super.setOnTouchListener(l);
		
	}
	
	@Override
	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		inteceptAllowed = !disallowIntercept;
		super.requestDisallowInterceptTouchEvent(disallowIntercept);
	}
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if(inteceptAllowed){
			onTouchEvent(ev);
		}
		return super.onInterceptTouchEvent(ev);
	}


	public void setFileType(int type){
		fileType = type;
	}

	public int getFileType(){
		return fileType;
	}
}
