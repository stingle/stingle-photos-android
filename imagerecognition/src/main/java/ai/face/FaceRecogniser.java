package ai.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A class used for detecting and recognising faces.
 * */
public class FaceRecogniser {
	private static final float SIMILARITY_THRESHOLD = 0.4f;

	private FaceDetector faceDetector;
	private Interpreter modelInterpreter;
	private ImageProcessor imageProcessor;

	private final ExecutorService backgroundExecutorService;

	private int inputImageWidth;
	private int inputImageHeight;

	private int outputArraySize;

	public FaceRecogniser() {
		backgroundExecutorService = Executors.newSingleThreadExecutor();
	}

	/**
	 * Initializes inner components of this class asynchronously.
	 *
	 * @param context an Android context
	 * @param modelPath a path to tensorflow model in assets
	 * */
	public void init(Context context, String modelPath) {
		backgroundExecutorService.execute(() -> {
			FaceDetectorOptions options =
					new FaceDetectorOptions.Builder()
							.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
							.setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
							.setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
							.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
							.build();

			faceDetector = FaceDetection.getClient(options);

			try {
				MappedByteBuffer model = FileUtil.loadMappedFile(context, modelPath);

				CompatibilityList compatibilityList = new CompatibilityList();

				Interpreter.Options interpreterOptions = new Interpreter.Options();
				if (compatibilityList.isDelegateSupportedOnThisDevice()) {
					interpreterOptions.addDelegate(new GpuDelegate(compatibilityList.getBestOptionsForThisDevice()));
				} else {
					interpreterOptions.setNumThreads(4);
				}

				modelInterpreter = new Interpreter(model, interpreterOptions);

				Tensor inputTensor = modelInterpreter.getInputTensor(0);
				inputImageWidth = inputTensor.shape()[2];
				inputImageHeight = inputTensor.shape()[1];

				Tensor outputTensor = modelInterpreter.getOutputTensor(0);
				outputArraySize = outputTensor.shape()[1];

				imageProcessor =
						new ImageProcessor.Builder()
								.add(new NormalizeOp(0f, 255f))
								.build();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Asynchronously finds any face in the provided org.stingle.ai.image, identifies it comparing to existing faces and returns a future holding the result.
	 *
	 * @param image The org.stingle.ai.image to detect faces in
	 * @param previousIdentifiedFaces The collection of previously identified faces
	 *
	 * @return A future of Results.
	 * */
	public Future<Result> recognise(Bitmap image, Collection<Person> previousIdentifiedFaces) {
		return backgroundExecutorService.submit(new Callable<Result>() {
			@Override
			public Result call() throws Exception {
				List<Face> faceList = Tasks.await(faceDetector.process(InputImage.fromBitmap(image, 0)));
				List<Rect> faceRectangleList = getFaceRectangles(faceList);
				List<FaceFeature> faceFeatureList = getFaceFeatures(image, faceRectangleList);
				List<Person> identifiedFaceList = identifyFaceFeatures(faceFeatureList, previousIdentifiedFaces);

				Map<UUID, Rect> rectangleMap = new HashMap<>();

				for (int i = 0; i < identifiedFaceList.size(); i++) {
					rectangleMap.put(identifiedFaceList.get(i).id, faceRectangleList.get(i));
				}

				return new Result(identifiedFaceList, rectangleMap);
			}
		});
	}

	private List<Person> identifyFaceFeatures(List<FaceFeature> faceFeatureList,
											  Collection<Person> identifiedFaceCollection) {

		List<Person> identifiedFaceList = new ArrayList<>();

		for (FaceFeature faceFeature : faceFeatureList) {
			Person identifiedFace = findClosest(faceFeature, identifiedFaceCollection);
			Person updatedIdentifiedFace;

			if (identifiedFace != null) {
				float[] updatedData = MathUtils.blend(identifiedFace.feature.data,
						faceFeature.data, 1f / (identifiedFace.feature.iteration + 1));

				FaceFeature updatedFaceFeature = new FaceFeature(updatedData, identifiedFace.feature.iteration + 1);

				updatedIdentifiedFace = new Person(identifiedFace.id, updatedFaceFeature);
			} else {
				updatedIdentifiedFace = new Person(UUID.randomUUID(), faceFeature);
			}

			identifiedFaceList.add(updatedIdentifiedFace);
		}

		return identifiedFaceList;
	}

	private Person findClosest(FaceFeature faceFeature, Collection<Person> identifiedFaceCollection) {
		Person closestFace = null;
		float closestSimilarity = Float.MAX_VALUE;

		for (Person identifiedFace : identifiedFaceCollection) {
			float similarity = faceFeature.getSimilarity(identifiedFace.feature);

			if (similarity <= SIMILARITY_THRESHOLD && similarity < closestSimilarity) {
				closestFace = identifiedFace;
				closestSimilarity = similarity;
			}
		}

		return closestFace;
	}

	private List<FaceFeature> getFaceFeatures(Bitmap image, List<Rect> faceRectangles) {
		List<FaceFeature> featureList = new ArrayList<>();

		for (Rect faceRectangle : faceRectangles) {
			TensorImage tensorImage = new TensorImage(DataType.UINT8);
			tensorImage.load(getCroppedFace(image, faceRectangle));

			TensorImage processedTensorImage = imageProcessor.process(tensorImage);

			Object[] input = new Object[] {processedTensorImage.getBuffer()};
			Map<Integer, Object> output = new HashMap<>();

			float[][] embeddings = new float[1][outputArraySize];
			output.put(0, embeddings);

			modelInterpreter.runForMultipleInputsOutputs(input, output);

			featureList.add(new FaceFeature(embeddings[0], 1));
		}

		return featureList;
	}

	private Bitmap getCroppedFace(Bitmap image, Rect faceRect) {
		if (faceRect.width() > 0 && faceRect.height() > 0) {
			Bitmap face = Bitmap.createBitmap(inputImageWidth, inputImageHeight, Bitmap.Config.ARGB_8888);

			Matrix transform = new Matrix();
			transform.setRectToRect(new RectF(faceRect),
					new RectF(0f, 0f, inputImageWidth, inputImageHeight), Matrix.ScaleToFit.CENTER);

			Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

			Canvas canvas = new Canvas(face);
			canvas.drawBitmap(image, transform, paint);

			return face;
		} else {
			return null;
		}
	}

	private List<Rect> getFaceRectangles(List<Face> faceList) {
		List<Rect> faceRectangleList = new ArrayList<>();

		for (Face face : faceList) {
			faceRectangleList.add(face.getBoundingBox());
		}

		return faceRectangleList;
	}

	/**
	 * A container holding two different values.
	 *
	 * personList - list of identified persons which should be kept in permanent storage
	 * identifiedRectangleMap - A map holding person's id to a rectangle for a single picture
	 * */
	public static class Result {
		public final List<Person> personList;
		public final Map<UUID, Rect> identifiedRectangleMap;

		public Result(List<Person> personList, Map<UUID, Rect> identifiedRectangleMap) {
			this.personList = personList;
			this.identifiedRectangleMap = identifiedRectangleMap;
		}
	}
}
