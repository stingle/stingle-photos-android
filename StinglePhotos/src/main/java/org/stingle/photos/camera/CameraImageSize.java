package org.stingle.photos.camera;

import android.content.Context;
import android.util.Rational;
import android.util.Size;

import org.stingle.photos.R;

import java.io.Serializable;
import java.util.Comparator;

public class CameraImageSize {
    public final int width;
    public final int height;
    public final float megapixel;
    public final String aspectRatio;
    private final String quality;
    protected Context context;

    public CameraImageSize(Context context, int width, int height) {
        this.context = context;
        this.width = width;
        this.height = height;
        this.megapixel = (float)Math.round((float)width*(float)height / 100000) /10;
        this.aspectRatio = getAspectRatio(width,height);
        this.quality = getResolutionName(height);
    }
    public String getQuality() {
        return quality;
    }

    private static String getAspectRatio(int width, int height) {
        return (new Rational(width, height)).toString().replace("/",":");
    }

    @Override
    public String toString() {
        return "(" + this.aspectRatio + ") " + this.megapixel + " " + context.getString(R.string.megapixels);
    }

    public String getResolutionName(int height){
        switch (height){
            case 2160:
                return "UHD";
            case 1080 :
                return "Full HD";
            case 720 :
                return "HD";
            case 480 :
                return "SD";
        }
        return "";
    }

    public String getPhotoString(){
        return this.toString();
    }

    public Size getSize(){
        return new Size(width, height);
    }

    public Size getRevertedSize(){
        return new Size(height, width);
    }

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof Size) )
            return false;
        CameraImageSize that = (CameraImageSize)o;
        return this.width == that.width && this.height == that.height;
    }

    public static class SizeSorter implements Comparator<CameraImageSize>, Serializable {
        private static final long serialVersionUID = 5802214721073718212L;

        @Override
        public int compare(final CameraImageSize a, final CameraImageSize b) {
            return b.width * b.height - a.width * a.height;
        }

    }
}