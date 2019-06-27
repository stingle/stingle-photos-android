package com.fenritz.safecam.Widget;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.fenritz.safecam.R;

public class SafeCameraButton extends Button {

	Paint paint = new Paint();
	RectF circle = null;
	int arcAddDegress = 0;
	int shrinkDirection = 0;
	boolean autoReset = true;
	
	public SafeCameraButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SafeCameraButton, 0, 0);
		try {
			shrinkDirection = ta.getInteger(R.styleable.SafeCameraButton_shrink_direction, 0);
			autoReset = ta.getBoolean(R.styleable.SafeCameraButton_auto_reset, true);
		} 
		finally {
			ta.recycle();
		}

		paint.setAntiAlias(true);
	    paint.setColor(getResources().getColor(R.color.sec_color));
	    paint.setStyle(Style.STROKE);
	}
	

	public SafeCameraButton(Context context) {
        this(context, null);
    }

	@Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		
		
		int sideSize;
		if(widthMeasureSpec >= heightMeasureSpec){
			if(shrinkDirection == 0){
				sideSize = widthMeasureSpec;
			}
			else{
				sideSize = heightMeasureSpec;
			}
		} 
		else{
			if(shrinkDirection == 0){
				sideSize = heightMeasureSpec;
			}
			else{
				sideSize = widthMeasureSpec;
			}
		}
        super.onMeasure(sideSize, sideSize);
    }
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		float fact = (float) (w/512.0);
	    
	    float left = 7 * fact;
	    float top = 7 * fact;
	    float right = 505 * fact;
	    float bottom = 505 * fact;
	    float strokeSize = 13 * fact;
	    

	    circle = new RectF(left, top, right, bottom);
	    paint.setStrokeWidth(strokeSize);
	}
	
	@Override
	public void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(state);
		
		resetAnimation();
	}
	
	@SuppressLint("NewApi")
	public void animateSCButton(final OnClickListener onClickListener, final View view){
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
			ValueAnimator animation = ValueAnimator.ofInt(0, 180);
		    animation.setDuration(500);
	
		    animation.addUpdateListener(new AnimatorUpdateListener() {
	
		        public void onAnimationUpdate(ValueAnimator animation) {
		            int value = (Integer) animation.getAnimatedValue();
		            arcAddDegress = value;
		            invalidate();
		        }
		    });
		    animation.addListener(new AnimatorListener() {
	
		        public void onAnimationStart(Animator animation) {
		        }
	
		        public void onAnimationRepeat(Animator animation) {
	
		        }
	
		        public void onAnimationEnd(Animator animation) {
		            onClickListener.onClick(view);
		            
		            if(autoReset){
			            Handler handler = new Handler();
			            handler.postDelayed(new Runnable(){
			                  public void run(){
			                    resetAnimation();
			               }
			            }, 1500);
		            }
		        }
	
		        public void onAnimationCancel(Animator animation) {
	
		        }
		    });
		    animation.start();
		}
	}
	
	public void resetAnimation(){
		arcAddDegress = 0;
		invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		if(circle != null){
			canvas.drawArc(circle, -180, 180+arcAddDegress, false, paint);
		}
	}
	
	@Override
    public void setOnClickListener(final OnClickListener onClickListener) {

        super.setOnClickListener(new OnClickListener() {

            public void onClick(View view) {
            	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
            		animateSCButton(onClickListener, view);
            	}
            	else{
            		onClickListener.onClick(view);
            	}
            }
        });
    }
	
}
