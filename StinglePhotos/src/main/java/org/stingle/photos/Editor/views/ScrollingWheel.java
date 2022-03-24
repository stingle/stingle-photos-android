package org.stingle.photos.Editor.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;

import androidx.annotation.Nullable;

import org.stingle.photos.R;

public class ScrollingWheel extends View {
	private static final float MAX_RESISTANCE = 2f;
	private static final float SNAPPING_VALUE = 0f;

	private int minValue;
	private int maxValue;

	private int intValue;
	private float value;

	private float resolution;

	private float resistance;
	private boolean snappedAtValue;

	private float lineHeight;
	private float centerLineHeight;

	private int lineColor;
	private int centerLineColor;
	private int selectedLineColor;

	private Paint linePaint;
	private TextPaint textPaint;

	private float lastTouchX;
	private int touchStartValue;

	private Rect textBounds;

	private Listener listener;

	private VelocityTracker velocityTracker;
	private Scroller scroller;

	private ValueFormatter valueFormatter;

	private boolean disabled;

	private Runnable computeScroll = new Runnable() {
		@Override
		public void run() {
			if (scroller.computeScrollOffset()) {
				value = Math.min(maxValue, Math.max(minValue, scroller.getCurrX()));

				invalidate();

				post(this);
			}
		}
	};

	public ScrollingWheel(Context context) {
		super(context);

		init();
	}

	public ScrollingWheel(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);

		init();
	}

	public ScrollingWheel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		init();
	}

	public ScrollingWheel(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		init();
	}

	private void init() {
		Resources resources = getResources();

		lineColor = resources.getColor(R.color.grey2);
		selectedLineColor = resources.getColor(R.color.blue1);
		centerLineColor = resources.getColor(R.color.grey1);

		linePaint = new Paint();
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeWidth(resources.getDimension(R.dimen.line_thickness1));

		textPaint = new TextPaint();
		textPaint.setTextSize(resources.getDimension(R.dimen.wheel_text_size));

		value = 0f;

		minValue = -100;
		maxValue = 100;

		resolution = resources.getDimension(R.dimen.value_resolution);
		lineHeight = resources.getDimension(R.dimen.wheel_line_height);
		centerLineHeight = resources.getDimension(R.dimen.wheel_center_line_height);

		textBounds = new Rect();

		scroller = new Scroller(getContext());

		velocityTracker = VelocityTracker.obtain();

		valueFormatter = String::valueOf;
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
	}

	public void setValueFormatter(ValueFormatter valueFormatter) {
		this.valueFormatter = valueFormatter;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void setLimits(int minValue, int maxValue) {
		this.minValue = minValue;
		this.maxValue = maxValue;

		invalidate();
	}

	public void setValue(int value) {
		this.value = value;
		this.intValue = value;

		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		canvas.save();
		canvas.translate(0f, -getPaddingBottom());

		float width = getWidth();

		boolean enabled = isEnabled();

		for (int i = minValue; i <= maxValue; i++) {
			if (i % 5 == 0) {
				float x = valueToScreenX(i);

				boolean selected = false;

				if (value < 0 && i >= value && i <= 0) {
					selected = true;
				} else if (value > 0 && i >= 0 && i <= value) {
					selected = true;
				}

				linePaint.setColor(selected ? selectedLineColor : lineColor);

				int alpha = i % 50 == 0 ? 255 : 128;

				float f = Math.min(1f, Math.min(x, width - x) / (width / 4f)) * (enabled ? 1f : 0.4f);

				alpha *= f;

				linePaint.setAlpha(alpha);

				canvas.drawLine(x, getHeight() - lineHeight, x, getHeight(), linePaint);
			}
		}

		linePaint.setColor(value == 0 ? lineColor : selectedLineColor);
		linePaint.setAlpha(enabled ? 255 : 102);

		canvas.drawLine(width / 2f, getHeight() - centerLineHeight, width / 2f, getHeight(), linePaint);

		textPaint.setColor(value == 0 ? lineColor : selectedLineColor);
		textPaint.setAlpha(enabled ? 255 : 102);

		String text = valueFormatter.format((int) value);

		textPaint.getTextBounds(text, 0, text.length(), textBounds);

		canvas.restore();
		canvas.drawText(text, width / 2f - textBounds.left - textBounds.width() / 2f, getPaddingTop() - textBounds.top, textPaint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) {
			return false;
		}

		float x = event.getX();
		float y = event.getY();

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				scroller.forceFinished(true);

				velocityTracker.clear();
				velocityTracker.addMovement(event);

				lastTouchX = x;
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				float oldValue = value;

				float dx = (x - lastTouchX) / resolution;

				if (snappedAtValue) {
					resistance += dx;

					if (Math.abs(resistance) >= MAX_RESISTANCE) {
						dx = (Math.abs(resistance) - MAX_RESISTANCE) * Math.signum(dx);
						snappedAtValue = false;
					}
				}

				if (!snappedAtValue) {
					value -= dx;

					if ((oldValue < SNAPPING_VALUE && value > SNAPPING_VALUE) || (oldValue > SNAPPING_VALUE && value < SNAPPING_VALUE)) {
						if (Math.abs(value - oldValue) <= MAX_RESISTANCE) {
							value = SNAPPING_VALUE;
							snappedAtValue = true;
							resistance = 0f;
						}
					}
				}

				if (value > maxValue) {
					value = maxValue;
				} else if (value < minValue) {
					value = minValue;
				}

				int intValue = (int) value;
				if (intValue != this.intValue && listener != null) {
					this.intValue = intValue;
					listener.onValueChanged(intValue);
				}
				velocityTracker.addMovement(event);
				break;
			}

			case MotionEvent.ACTION_UP: {
				velocityTracker.addMovement(event);
				velocityTracker.computeCurrentVelocity(1);

				scroller.forceFinished(true);
				scroller.startScroll((int) value, 0, (int) (-60f * velocityTracker.getXVelocity() / resolution), 0);

				post(computeScroll);
				break;
			}
		}


		lastTouchX = x;

		invalidate();

		return true;
	}

	private float valueToScreenX(int x) {
		// value of 0 is in screen center

		float centerX = getWidth() / 2f;

		return centerX + (x - value) * resolution;
	}

	private int screenToValueX(float x) {
		return (int) (x / resolution);
	}

	public interface Listener {
		void onValueChanged(int value);
	}

	public interface ValueFormatter {
		String format(int value);
	}
}
