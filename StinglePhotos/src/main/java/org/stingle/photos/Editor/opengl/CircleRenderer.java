package org.stingle.photos.Editor.opengl;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import org.stingle.photos.Editor.util.GLUtils;
import org.stingle.photos.Editor.util.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class CircleRenderer {
	private String vertexShader;
	private String fragmentShader;

	private int programId;

	private int vertexAttribute;
	private int textureCoordinateAttribute;

	private int viewProjectionMatrixLocation;

	private FloatBuffer textureCoordinateBuffer;
	private FloatBuffer vertexBuffer;

	private ShortBuffer indexBuffer;

	public CircleRenderer(Context context) {
		vertexShader = Utils.readAsset(context, "shaders/circle_vertex_shader.glsl");
		fragmentShader = Utils.readAsset(context, "shaders/circle_fragment_shader.glsl");

		vertexBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

		textureCoordinateBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		textureCoordinateBuffer.put(new float[]{
				0f, 0f,
				0f, 1f,
				1f, 0f,
				1f, 1f
		});
		textureCoordinateBuffer.position(0);

		indexBuffer = ByteBuffer.allocateDirect(4 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
		indexBuffer.put(new short[]{
				0, 1, 2, 3
		});
		indexBuffer.position(0);
	}

	public void setCenterAndRadius(float cx, float cy, float radius) {
		vertexBuffer.put(new float[]{
				cx - radius, cy - radius,
				cx - radius, cy + radius,
				cx + radius, cy - radius,
				cx + radius, cy + radius
		});
		vertexBuffer.position(0);
	}

	public void createProgram() {
		programId = GLUtils.loadProgram(vertexShader, fragmentShader);

		viewProjectionMatrixLocation = GLES20.glGetUniformLocation(programId, "viewProjectionMatrix");

		vertexAttribute = GLES20.glGetAttribLocation(programId, "position");
		textureCoordinateAttribute = GLES20.glGetAttribLocation(programId, "inputTextureCoordinate");
	}

	public void draw(float[] viewProjectionMatrix) {
		int error = GLES20.glGetError();
		if (error != GLES20.GL_NO_ERROR) {
			Log.d("GLRenderer", "Error == " + error);
		}

		GLES20.glUseProgram(programId);

		GLES20.glEnableVertexAttribArray(vertexAttribute);
		GLES20.glVertexAttribPointer(vertexAttribute, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

		GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
		GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, true, 8, textureCoordinateBuffer);

		GLES20.glUniformMatrix4fv(viewProjectionMatrixLocation, 1, false, viewProjectionMatrix, 0);

		GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 4, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

		GLES20.glDisableVertexAttribArray(vertexAttribute);
		GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
	}
}
