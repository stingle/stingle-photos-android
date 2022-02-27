package org.stingle.photos.Editor.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {
	public static Bitmap getScaledBitmap(Bitmap image, int maxSize) {
		float scale = Math.max(1f, Math.max((float) image.getWidth() / maxSize, (float) image.getHeight() / maxSize));
		int newWidth = (int) (image.getWidth() / scale);
		int newHeight = (int) (image.getHeight() / scale);

		return Bitmap.createScaledBitmap(image, newWidth, newHeight, true);
	}

	public static void scaleRect(RectF rect, float px, float py, float s) {
		rect.left = px + (rect.left - px) * s;
		rect.top = py + (rect.top - py) * s;
		rect.right = px + (rect.right - px) * s;
		rect.bottom = py + (rect.bottom - py) * s;
	}

	public static String readAsset(Context context, String assetName) {
		try (InputStream inputStream = context.getAssets().open(assetName)) {
			return readStream(inputStream);
		} catch (IOException e) {
			e.printStackTrace();

			return "";
		}
	}

	public static String readStream(InputStream inputStream) {
		StringBuilder stringBuilder = new StringBuilder();

		char[] buffer = new char[1024];
		int charactersRead = 0;

		try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
			while ((charactersRead = inputStreamReader.read(buffer)) > 0) {
				stringBuilder.append(buffer, 0, charactersRead);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return stringBuilder.toString();
	}
}
