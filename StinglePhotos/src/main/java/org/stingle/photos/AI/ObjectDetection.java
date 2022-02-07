package org.stingle.photos.AI;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import org.stingle.photos.Files.FileManager;
import org.stingle.photos.StinglePhotosApplication;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ai.face.FaceRecogniser;
import ai.face.Person;
import ai.image.StingleImageRecognition;

public class ObjectDetection {

    private static final String TAG = ObjectDetection.class.getSimpleName();
    private static final Object object = new Object();

    private static volatile ObjectDetection instance = null;
    private final StingleImageRecognition objectDetector;
    private final FaceRecogniser faceRecogniser;

    private ObjectDetection() {
        objectDetector = new StingleImageRecognition.Builder(StinglePhotosApplication.getAppContext())
                .maxResults(5)
                .modelPath("model.tflite")
                .scoreThreshold(0.3f)
                .build();
        faceRecogniser = new FaceRecogniser();
        faceRecogniser.init(StinglePhotosApplication.getAppContext(), "facenet.tflite");
    }

    public static ObjectDetection getInstance() {
        if (instance != null) {
            return instance;
        }

        synchronized (object) {
            if (instance == null) {
                instance = new ObjectDetection();
            }
            return instance;
        }
    }

    public Set<StingleImageRecognition.DetectionResult> detectVideo(Uri videoUri) {
        long videoDuration = FileManager.getVideoDurationFromUri(
                StinglePhotosApplication.getAppContext(), videoUri);
        float factor;
        if (videoDuration > 10 && videoDuration < 100) {
            factor = 0.5f;
        } else if (videoDuration >= 100) {
            factor = 0.1f;
        } else {
            factor = 10f/videoDuration;
        }
        try {
            return objectDetector.runVideoObjectDetection(videoUri, factor);
        } catch (Exception e) {
            Log.d(TAG, "Failed to detect video");
        }
        return null;
    }

    public List<StingleImageRecognition.DetectionResult> detectImage(Bitmap bitmap) {
        try {
            return objectDetector.runObjectDetection(bitmap);
        } catch (Exception e) {
            Log.d(TAG, "Failed to detect image");
        }
        return null;
    }

    public List<StingleImageRecognition.DetectionResult> detectImage(Uri imageUri) {
        try {
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            Bitmap bitmap = BitmapFactory.decodeFile(imageUri.getPath(), bmOptions);
            return objectDetector.runObjectDetection(bitmap);
        } catch (Exception e) {
            Log.d(TAG, "Failed to detect image");
        }
        return null;
    }

    public void detectGif(Uri gifPath, StingleImageRecognition.OnDetectionResultsListener callback) {
        try {
            objectDetector.runGifObjectDetection(gifPath, 1f, callback);
        } catch (Exception e) {
            Log.d(TAG, "Failed to detect gif");
        }
    }

    public  FaceRecogniser.Result detectFace(Uri imageUri) {
        try {
            HashMap<UUID, Person > personNameMap = new HashMap<>();
            FaceRecogniser.Result results = faceRecogniser.recognise(
                    AIImageUtils.loadImage(StinglePhotosApplication.getAppContext(), imageUri),
                    personNameMap.values()).get();
            // TODO - save result.personList int database
            // MockDatabase.getInstance().addPersonList(result.personList);
            // TODO - save result.identifiedRectangleMap with imageUri in database
            // MockDatabase.getInstance().addImage(new Image(imageUri, result.identifiedRectangleMap));

            return results;
        } catch (Exception e) {
            return null;
        }
    }
}
