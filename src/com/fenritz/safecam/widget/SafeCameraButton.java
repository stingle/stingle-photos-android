package com.fenritz.safecam.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;

import com.fenritz.safecam.R;

public class SafeCameraButton extends ImageButton {

	Paint paint = new Paint();
	RectF circle = null;
	Bitmap mutableBitmap = null;
	
	public SafeCameraButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		

		BitmapFactory.Options myOptions = new BitmapFactory.Options();
	    myOptions.inDither = true;
	    myOptions.inScaled = false;
	    myOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;// important
	    myOptions.inPurgeable = true;

	    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.camera_bare,myOptions);
	    
	    paint.setAntiAlias(true);
	    paint.setColor(getResources().getColor(R.color.sec_color));
	    paint.setStyle(Style.STROKE);
	    

	    Bitmap workingBitmap = Bitmap.createBitmap(bitmap);
	    mutableBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);
	    

	    /*final int left = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics());
	    final int top = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics());
	    final int right = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 253, getResources().getDisplayMetrics());
	    final int bottom = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 255, getResources().getDisplayMetrics());*/
	    
	    int width = mutableBitmap.getWidth();
	    int height = mutableBitmap.getHeight();
	    
	    float fact = (float) (width/512.0);
	    
	    float left = 7 * fact;
	    float top = 7 * fact;
	    float right = 505 * fact;
	    float bottom = 505 * fact;
	    float strokeSize = 13 * fact;
	    
	    Log.d("qaq", String.valueOf(width) + "x" + String.valueOf(height));
	    Log.d("qaq", "fact = " + String.valueOf(fact));
	    
	    //circle = new RectF(7, 7, 506, 505);
	   
	    circle = new RectF(left, top, right, bottom);
	    paint.setStrokeWidth(strokeSize);
	    updateImage();
	}
	

	public SafeCameraButton(Context context) {
        this(context, null);
    }

	protected void updateImage(){
		Canvas canvas = new Canvas(mutableBitmap);
	    
	    canvas.drawArc(circle, -180, 180, false, paint);

	    setAdjustViewBounds(true);
	    setImageBitmap(mutableBitmap);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
	    

	    //setAdjustViewBounds(true);
	    //setImageBitmap(mutableBitmap);
	}
	
}
