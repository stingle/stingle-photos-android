package org.stingle.photos.Gallery.Helpers;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.imageview.ShapeableImageView;

/**
 * A ShapeableImageView for fixed-size gallery thumbnails that does NOT propagate layout
 * requests when its image changes.
 *
 * Gallery cells are a fixed square (the column width), set once via LayoutParams in the
 * adapter. ImageView.setImageDrawable() still calls requestLayout() whenever the new
 * drawable's intrinsic size differs from the old one (e.g. when a decrypted thumbnail
 * replaces the null placeholder). In a RecyclerView that propagates up to the parent and
 * triggers a full relayout; after a fast scrollbar jump near the end of a very large list
 * that relayout drifts the scroll anchor and rebinds rows, kicking off more thumbnail loads
 * and thus more requestLayout() calls — a self-feeding loop that makes the grid scroll by
 * itself and starves the decrypt callbacks so thumbnails never paint until the user touches.
 *
 * Since the cell size is fixed, a thumbnail load only ever needs a redraw, never a re-measure.
 * RecyclerView measures item children explicitly during its own layout pass, so suppressing
 * this view's self-initiated requestLayout() is safe — the view is still sized correctly by
 * its parent; we just stop the spurious relayout storm.
 */
public class FixedShapeableImageView extends ShapeableImageView {

	public FixedShapeableImageView(Context context) {
		super(context);
	}

	public FixedShapeableImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public FixedShapeableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public void requestLayout() {
		// Intentionally do not propagate layout requests: the cell is a fixed square, so an
		// image change needs only invalidate()/redraw (which setImageDrawable also triggers),
		// not a re-measure. This breaks the image-load -> requestLayout -> relayout feedback
		// loop that caused the gallery to auto-scroll after a fast scrollbar jump.
	}
}
