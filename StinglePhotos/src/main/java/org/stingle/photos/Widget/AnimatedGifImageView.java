package org.stingle.photos.Widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class AnimatedGifImageView extends androidx.appcompat.widget.AppCompatImageView {

    public static enum TYPE {
        FIT_CENTER, STRETCH_TO_FIT, AS_IS
    };

    public AnimatedGifImageView(Context context) {
        super(context);
    }


    boolean animatedGifImage = false;
    private InputStream is = null;
    private Movie mMovie = null;
    private long mMovieStart = 0;
    private TYPE mType = TYPE.FIT_CENTER;

    public void setAnimatedGif(int rawResourceId, TYPE streachType) {
        setImageBitmap(null);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mType = streachType;
        animatedGifImage = true;
        is = getContext().getResources().openRawResource(rawResourceId);
        try {
            mMovie = Movie.decodeStream(is);
        } catch (Exception e) {
            e.printStackTrace();
            byte[] array = streamToBytes(is);
            mMovie = Movie.decodeByteArray(array, 0, array.length);
        }
        p = new Paint();
    }

    public void setAnimatedGif(String filePath, TYPE streachType) throws FileNotFoundException {
        setImageBitmap(null);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mType = streachType;
        animatedGifImage = true;
        InputStream is;
        try {
            mMovie = Movie.decodeFile(filePath);
        } catch (Exception e) {
            e.printStackTrace();
            is = new FileInputStream(filePath);
            byte[] array = streamToBytes(is);
            mMovie = Movie.decodeByteArray(array, 0, array.length);
        }
        p = new Paint();
    }

    public void setAnimatedGif(byte[] byteArray, TYPE streachType){
        setImageBitmap(null);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mType = streachType;
        animatedGifImage = true;

        try {
            mMovie = Movie.decodeByteArray(byteArray, 0, byteArray.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
        p = new Paint();
    }


    @Override
    public void setImageResource(int resId) {
        animatedGifImage = false;
        super.setImageResource(resId);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        animatedGifImage = false;
        super.setImageBitmap(bm);
    }

    @Override
    public void setImageURI(Uri uri) {
        animatedGifImage = false;
        super.setImageURI(uri);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        animatedGifImage = false;
        super.setImageDrawable(drawable);
    }

    Paint p;
    private float mScaleH = 1f, mScaleW = 1f;
    private int mMeasuredMovieWidth;
    private int mMeasuredMovieHeight;
    private float mLeft;
    private float mTop;

    private static byte[] streamToBytes(InputStream is) {
        ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = is.read(buffer)) >= 0) {
                os.write(buffer, 0, len);
            }
        } catch (java.io.IOException e) {
        }
        return os.toByteArray();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mMovie != null) {
            int movieWidth = mMovie.width();
            int movieHeight = mMovie.height();

            int measureModeWidth = MeasureSpec.getMode(widthMeasureSpec);
            float scaleW = 1f, scaleH = 1f;
            if (measureModeWidth != MeasureSpec.UNSPECIFIED) {
                int maximumWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (movieWidth > maximumWidth) {
                    scaleW = (float) movieWidth / (float) maximumWidth;
                } else {
                    scaleW = (float) maximumWidth / (float) movieWidth;
                }
            }

            int measureModeHeight = MeasureSpec.getMode(heightMeasureSpec);

            if (measureModeHeight != MeasureSpec.UNSPECIFIED) {
                int maximumHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (movieHeight > maximumHeight) {
                    scaleH = (float) movieHeight / (float) maximumHeight;
                } else {
                    scaleH = (float) maximumHeight / (float) movieHeight;
                }
            }

            switch (mType) {
                case FIT_CENTER:
                    mScaleH = mScaleW = Math.min(scaleH, scaleW);
                    break;
                case AS_IS:
                    mScaleH = mScaleW = 1f;
                    break;
                case STRETCH_TO_FIT:
                    mScaleH = scaleH;
                    mScaleW = scaleW;
                    break;
            }

            mMeasuredMovieWidth = (int) (movieWidth * mScaleW);
            mMeasuredMovieHeight = (int) (movieHeight * mScaleH);

            setMeasuredDimension(mMeasuredMovieWidth, mMeasuredMovieHeight);

        } else {
            setMeasuredDimension(getSuggestedMinimumWidth(),
                    getSuggestedMinimumHeight());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mLeft = (getWidth() - mMeasuredMovieWidth) / 2f;
        mTop = (getHeight() - mMeasuredMovieHeight) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (animatedGifImage) {
            long now = android.os.SystemClock.uptimeMillis();
            if (mMovieStart == 0) { // first time
                mMovieStart = now;
            }
            if (mMovie != null) {
                p.setAntiAlias(true);
                int dur = mMovie.duration();
                if (dur == 0) {
                    dur = 1000;
                }
                int relTime = (int) ((now - mMovieStart) % dur);
                mMovie.setTime(relTime);
                canvas.save();
                canvas.scale(mScaleW, mScaleH);
                mMovie.draw(canvas, mLeft / mScaleW, mTop / mScaleH);
                canvas.restore();
                invalidate();
            }
        }

    }

}
