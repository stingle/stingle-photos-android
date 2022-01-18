package ai.face;

/**
 * A class holding an arbitrary sized embedding vector of a single human face.
 * */
public class FaceFeature {
	public final float[] data;
	public final int iteration;

	public FaceFeature(float[] data, int iteration) {
		this.data = data;
		this.iteration = iteration;
	}

	public float getSimilarity(FaceFeature faceFeature) {
		return MathUtils.computeCosineSimilarity(data, faceFeature.data);
	}
}
