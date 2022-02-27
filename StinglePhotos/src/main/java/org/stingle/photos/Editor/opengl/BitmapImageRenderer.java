package org.stingle.photos.Editor.opengl;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import org.stingle.photos.Editor.core.Image;
import org.stingle.photos.Editor.util.GLUtils;
import org.stingle.photos.Editor.util.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class BitmapImageRenderer {
	private String vertexShader;
	private String fragmentShader;

	private int programId;

	private int vertexAttribute;
	private int textureCoordinateAttribute;

	private int viewProjectionMatrixLocation;

	private int textureLocation;

	private int textureWidthLocation;
	private int textureHeightLocation;

	private int brightnessLocation;
	private int contrastLocation;
	private int highlightsLocation;
	private int shadowsLocation;
	private int saturationLocation;
	private int warmthLocation;
	private int sharpnessLocation;
	private int vignetteLocation;

	private int cropCenterLocation;
	private int cropRotationLocation;
	private int cropWidthLocation;
	private int cropHeightLocation;

	private int dimAmountLocation;

	private int textureId;

	private FloatBuffer textureCoordinateBuffer;
	private FloatBuffer vertexBuffer;

	private ShortBuffer indexBuffer;

	private float[] modelMatrix;
	private float[] modelViewProjectionMatrix;

	private Image image;

	public BitmapImageRenderer(Context context) {
		vertexShader = Utils.readAsset(context, "shaders/almighty_vertex_shader.glsl");
		fragmentShader = Utils.readAsset(context, "shaders/almighty_fragment_shader.glsl");

		vertexBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

		textureCoordinateBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		textureCoordinateBuffer.put(new float[] {
				0f, 0f,
				0f, 1f,
				1f, 0f,
				1f, 1f
		});
		textureCoordinateBuffer.position(0);

		indexBuffer = ByteBuffer.allocateDirect(4 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
		indexBuffer.put(new short[] {
			0, 1, 2, 3
		});
		indexBuffer.position(0);

		modelMatrix = new float[16];
		Matrix.setIdentityM(modelMatrix, 0);
		modelViewProjectionMatrix = new float[16];
	}

	public void setImage(Image image) {
		this.image = image;

		vertexBuffer.put(new float[] {
				0f, 0f,
				0f, image.getHeight(),
				image.getWidth(), 0f,
				image.getWidth(), image.getHeight()
		});
		vertexBuffer.position(0);

		createTexture();
	}

	public void createProgram() {
		programId = GLUtils.loadProgram(vertexShader, fragmentShader);

		viewProjectionMatrixLocation = GLES20.glGetUniformLocation(programId, "viewProjectionMatrix");

		vertexAttribute = GLES20.glGetAttribLocation(programId, "position");
		textureCoordinateAttribute = GLES20.glGetAttribLocation(programId, "inputTextureCoordinate");

		textureWidthLocation = GLES20.glGetUniformLocation(programId, "textureWidth");
		textureHeightLocation = GLES20.glGetUniformLocation(programId, "textureHeight");

		textureLocation = GLES20.glGetUniformLocation(programId, "inputImageTexture");
		brightnessLocation = GLES20.glGetUniformLocation(programId, "brightness");
		contrastLocation = GLES20.glGetUniformLocation(programId, "contrast");
		highlightsLocation = GLES20.glGetUniformLocation(programId, "highlights");
		shadowsLocation = GLES20.glGetUniformLocation(programId, "shadows");
		saturationLocation = GLES20.glGetUniformLocation(programId, "saturation");
		warmthLocation = GLES20.glGetUniformLocation(programId, "warmth");
		sharpnessLocation = GLES20.glGetUniformLocation(programId, "sharpness");
		vignetteLocation = GLES20.glGetUniformLocation(programId, "vignette");

		cropCenterLocation = GLES20.glGetUniformLocation(programId, "cropCenter");
		cropRotationLocation = GLES20.glGetUniformLocation(programId, "cropRotation");
		cropWidthLocation = GLES20.glGetUniformLocation(programId, "cropWidth");
		cropHeightLocation = GLES20.glGetUniformLocation(programId, "cropHeight");

		dimAmountLocation = GLES20.glGetUniformLocation(programId, "dimAmount");
	}

	public void createTexture() {
		if (!GLES20.glIsTexture(textureId) && image != null && image.getBitmap() != null) {
			textureId = GLUtils.loadTexture(image.getBitmap());
		}
	}

	public void draw(float[] viewProjectionMatrix) {
		if (image != null) {
			Matrix.multiplyMM(modelViewProjectionMatrix, 0, viewProjectionMatrix,0, modelMatrix, 0);

			int error = GLES20.glGetError();
			if (error != GLES20.GL_NO_ERROR) {
				Log.d("GLRenderer", "Error == " + error);
			}

			GLES20.glUseProgram(programId);

			GLES20.glEnableVertexAttribArray(vertexAttribute);
			GLES20.glVertexAttribPointer(vertexAttribute, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

			GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
			GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, true, 8, textureCoordinateBuffer);

			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

			if (image != null) {
				GLES20.glUniform1f(textureWidthLocation, image.getWidth());
				GLES20.glUniform1f(textureHeightLocation, image.getHeight());

				GLES20.glUniform1f(brightnessLocation, image.getBrightnessValueNormalized());
				GLES20.glUniform1f(contrastLocation, image.getContrastValueNormalized());
				GLES20.glUniform1f(highlightsLocation, image.getHighlightsValueNormalized());
				GLES20.glUniform1f(shadowsLocation, image.getShadowsValueNormalized());
				GLES20.glUniform1f(saturationLocation, image.getSaturationValueNormalized());
				GLES20.glUniform1f(warmthLocation, image.getWarmthValueNormalized());
				GLES20.glUniform1f(sharpnessLocation, image.getSharpnessValueNormalized());
				GLES20.glUniform1f(vignetteLocation, image.getVignetteValueNormalized());

				GLES20.glUniform2f(cropCenterLocation, image.getCropCenter().x, image.getCropCenter().y);
				GLES20.glUniform1f(cropRotationLocation, -image.getCropRotationRadians());
				GLES20.glUniform1f(cropWidthLocation, image.getCropWidth());
				GLES20.glUniform1f(cropHeightLocation, image.getCropHeight());

				GLES20.glUniform1f(dimAmountLocation, image.getDimAmount());
			}

			GLES20.glUniformMatrix4fv(viewProjectionMatrixLocation, 1, false, modelViewProjectionMatrix, 0);

			GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, 4, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

			GLES20.glDisableVertexAttribArray(vertexAttribute);
			GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
		}
	}
}
