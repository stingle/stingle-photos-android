package com.fenritz.safecam.Widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.ViewGroup;

public class FocusCircleView extends ViewGroup {

	private float CIRCLE_RADIUS = 100f;
	private long CIRCLE_DURATION = 1200L;

	private boolean mDrawCircle = false;
	private Handler mHandler;
	private Paint mPaint;
	private float mLastCenterX = 0f;
	private float mLastCenterY = 0f;
	private Context context;

	public FocusCircleView(Context context) {
		this(context, null, 0);
	}

	public FocusCircleView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public FocusCircleView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		this.context = context;
		init();
	}

	public void init(){
		setWillNotDraw(false);
		mHandler = new Handler();
		mPaint = new Paint();
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setColor(Color.WHITE);
		mPaint.setStrokeWidth(4f);
	}

	public void setStrokeColor(int color) {
		mPaint.setColor(color);
	}

	public void drawFocusCircle(float x, float y) {
		mLastCenterX = x;
		mLastCenterY = y;
		toggleCircle(true);

		mHandler.removeCallbacksAndMessages(null);
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				toggleCircle(false);
			}
		}, CIRCLE_DURATION);
	}

	private void toggleCircle(boolean show) {
		mDrawCircle = show;
		invalidate();
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {

	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (mDrawCircle) {
			canvas.drawCircle(mLastCenterX, mLastCenterY, CIRCLE_RADIUS, mPaint);
		}
	}


}
