package org.stingle.photos.Editor.util;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.util.Log;

public class GLUtils {
	public static int loadTexture(Bitmap image) {
		int[] textures = new int[1];

		GLES20.glGenTextures(1, textures, 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

		android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, image, 0);

		return textures[0];
	}

	public static int loadShader(final String shader, final int iType) {
		int[] compiled = new int[1];
		int shaderId = GLES20.glCreateShader(iType);
		GLES20.glShaderSource(shaderId, shader);
		GLES20.glCompileShader(shaderId);
		GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0) {
			Log.d("Load Shader Failed", "Compilation\n" + GLES20.glGetShaderInfoLog(shaderId));
			return 0;
		}

		return shaderId;
	}

	public static int loadProgram(final String vertexShader, final String fragmentShader) {
		int[] link = new int[1];
		int vertexShaderId = loadShader(vertexShader, GLES20.GL_VERTEX_SHADER);
		if (vertexShaderId == 0) {
			Log.d("Load Program", "Vertex Shader Failed");
			return 0;
		}
		int fragmentShaderId = loadShader(fragmentShader, GLES20.GL_FRAGMENT_SHADER);
		if (fragmentShaderId == 0) {
			Log.d("Load Program", "Fragment Shader Failed");
			return 0;
		}

		int programId = GLES20.glCreateProgram();

		GLES20.glAttachShader(programId, vertexShaderId);
		GLES20.glAttachShader(programId, fragmentShaderId);

		GLES20.glLinkProgram(programId);

		GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, link, 0);
		if (link[0] <= 0) {
			Log.d("Load Program", "Linking Failed");
			return 0;
		}
		GLES20.glDeleteShader(vertexShaderId);
		GLES20.glDeleteShader(fragmentShaderId);

		return programId;
	}
}
