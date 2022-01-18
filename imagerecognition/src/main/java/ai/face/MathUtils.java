package ai.face;

class MathUtils {
	/**
	 * @return The similarity coefficient between two equal size vectors.
	 * Returned value is within [0..1] range. Values closer to 0 means better similarity.
	 * */
	public static float computeCosineSimilarity(float[] vector1, float[] vector2) {
		if (vector1.length != vector2.length) {
			throw new IllegalArgumentException("Vectors have different sizes");
		}

		if (vector1.length == 0) {
			throw new IllegalArgumentException("Vectors have 0 size");
		}

		float dotProduct = 0f;
		float vector1LengthSquared = 0f;
		float vector2LengthSquared = 0f;

		for (int i = 0; i < vector1.length; i++) {
			dotProduct += vector1[i] * vector2[i];
			vector1LengthSquared += vector1[i] * vector1[i];
			vector2LengthSquared += vector2[i] * vector2[i];
		}

		return (float) (1f - dotProduct / Math.sqrt(vector1LengthSquared * vector2LengthSquared));
	}

	public static float euclideanDistance(float[] vector1, float[] vector2) {
		if (vector1.length != vector2.length) {
			throw new IllegalArgumentException("Vectors have different sizes");
		}

		if (vector1.length == 0) {
			throw new IllegalArgumentException("Vectors have 0 size");
		}

		float sum = 0f;

		for (int i = 0; i < vector1.length; i++) {
			float diff = vector1[i] - vector2[i];

			sum += diff * diff;
		}

		return (float) Math.sqrt(sum);
	}

	public static float sigmoid(float a) {
		return (float) (1f / (1f + Math.pow(Math.E, -a)));
	}

	/**
	 * Linearly interpolates between two vectors of same size using f as a fraction.
	 *
	 * @param src1 A vector of n size
	 * @param src2 A vector of n size
	 *
	 * @param f A fraction value between 0 and 1
	 * */
	public static float[] blend(float[] src1, float[] src2, float f) {
		if (src1.length != src2.length) {
			throw new IllegalArgumentException("Provided arrays must have equal lengths");
		}

		float[] result = new float[src1.length];

		for (int i = 0; i < src1.length; i++) {
			result[i] = src1[i] * f + src2[i] * (1 - f);
		}

		return result;
	}
}
