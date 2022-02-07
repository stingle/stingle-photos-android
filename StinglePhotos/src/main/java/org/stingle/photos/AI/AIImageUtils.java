package org.stingle.photos.AI;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public class AIImageUtils {

    private AIImageUtils() {
        throw new UnsupportedOperationException();
    }

    public static Bitmap loadImageAndCrop(Context context, Uri imageUri, Rect cropRect) {
        Bitmap image = loadImage(context, imageUri);
        if (image != null) {
            return cropImage(image, cropRect);
        }

        return null;
    }

    public static Bitmap loadImage(Context context, Uri imageUri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            e.printStackTrace();

            return null;
        }
    }

    public static Bitmap cropImage(Bitmap image, Rect cropRect) {
        if (cropRect.width() > 0 && cropRect.height() > 0) {
            Bitmap face = Bitmap.createBitmap(cropRect.width(), cropRect.height(), Bitmap.Config.ARGB_8888);

            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

            Canvas canvas = new Canvas(face);
            canvas.drawBitmap(image, -cropRect.left, -cropRect.top, paint);

            return face;
        } else {
            return null;
        }
    }
}
