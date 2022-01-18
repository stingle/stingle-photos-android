package ai.image;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import android.media.ExifInterface;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.gifdecoder.StandardGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.stingle.ai.BuildConfig;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class StingleImageRecognition {

    private static final String TAG = "Stingle - ODT";
    private static final float MAX_FONT_SIZE = 96F;

    // best practice sizes for tesnorflow org.stingle.ai.image classification
    public static final int IMG_SIZE_WIDTH = 256;
    public static final int IMG_SIZE_HEIGHT = 256;

    private final Context context;
    private final String modelPath;
    private final int maxResults;
    private final float scoreThreshold;
    private final int numThreads;

    private ObjectDetector detector;

    private StingleImageRecognition(final Context context,
                                    final String modelPath,
                                    final int maxResults,
                                    final float scoreThreshold,
                                    final int numThreads) {
        this.context = context;
        this.modelPath = modelPath;
        this.maxResults = maxResults;
        this.scoreThreshold = scoreThreshold;
        this.numThreads = numThreads;

        setupDetection();
    }

    private void setupDetection() {
        try {
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(maxResults)
                    .setScoreThreshold(scoreThreshold)
                    .setNumThreads(numThreads)
                    .build();

            detector = ObjectDetector.createFromFileAndOptions(
                    context,
                    modelPath,
                    options
            );
        } catch (Exception e) {
            Log.d(TAG, "failed to create TF detection object");
        }
    }

    public static class Builder {

        private final Context context;
        private String modelPath = "model.tflite";
        private int maxResults = 5;
        private float scoreThreshold = 0.5f;
        private int numThreads = -1;

        public Builder(final Context context) {
            this.context = context;
        }

        public Builder modelPath(final String modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder maxResults(final int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder scoreThreshold(final float scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
            return this;
        }

        public Builder numThreads(final int numThreads) {
            this.numThreads = numThreads;
            return this;
        }

        public StingleImageRecognition build() {
            return new StingleImageRecognition(context, modelPath, maxResults, scoreThreshold, numThreads);
        }
    }

    /**
     * Runs object detection on bitmap and returns detected objects list
     *
     * @param bitmap bitmap org.stingle.ai.image to run object detection on
     * @return detected objects list
     * @throws IOException when accessing corrupted or TF model file or not finding the model file path
     */
    public List<DetectionResult> runObjectDetection(Bitmap bitmap) throws IOException {
        TensorImage image = TensorImage.fromBitmap(bitmap);

        if (detector == null) {
            throw new IOException("failed to access TF model file");
        }

        List<Detection> results = detector.detect(image);
        List<DetectionResult> finalResults = new ArrayList<>(results.size());
        for (Detection detection : results) {
            Category category = detection.getCategories().get(0);
            finalResults.add(new DetectionResult(category.getLabel(), category.getScore()));
        }
        if (BuildConfig.DEBUG) {
            debugPrint(results);
        }
        return finalResults;
    }

    /**
     * Runs object detection on bitmap and returns detected objects list
     *
     * @param bitmap   bitmap org.stingle.ai.image to run object detection on
     * @param listener callback with detected results
     * @throws IOException when accessing corrupted or TF model file or not finding the model file path
     */
    public void runObjectDetection(Bitmap bitmap, OnDetectionResultsListener listener) throws IOException {
        TensorImage image = TensorImage.fromBitmap(bitmap);

        if (detector == null) {
            throw new IOException("failed to access TF model file");
        }

        List<Detection> results = detector.detect(image);
        List<DetectionResult> finalResults = new ArrayList<>(results.size());
        for (Detection detection : results) {
            Category category = detection.getCategories().get(0);
            finalResults.add(new DetectionResult(category.getLabel(), category.getScore()));
        }
        if (BuildConfig.DEBUG) {
            debugPrint(results);
        }
        listener.onDetected(new HashSet<>(finalResults));
    }

    /**
     * Runs object detection on bitmap and draw the output on provided org.stingle.ai.image view
     *
     * @param bitmap         bitmap org.stingle.ai.image to run object detection on
     * @param inputImageView imageview reference to draw detected objects bounding boxes into it
     * @return detected objects list
     * @throws IOException when accessing corrupted or TF model file or not finding the model file path
     */
    public List<DetectionResult> runObjectDetection(Bitmap bitmap, ImageView inputImageView) throws IOException {
        TensorImage image = TensorImage.fromBitmap(bitmap);

        if (detector == null) {
            throw new IOException("failed to access TF model file");
        }
        // Step 3: Feed given org.stingle.ai.image to the detector
        List<Detection> results = detector.detect(image);

        // Step 4: Parse the detection result and show it

        List<DetectionResultBox> finalResultsToDisplay = new ArrayList<>(results.size());
        List<DetectionResult> finalResults = new ArrayList<>(results.size());

        for (Detection detection : results) {
            // Get the top-1 category and craft the display text
            Category category = detection.getCategories().get(0);
            String text = category.getLabel() + " " + (int) (category.getScore() * 100);

            // Create a data object to display the detection result
            finalResultsToDisplay.add(new DetectionResultBox(detection.getBoundingBox(), text));

            finalResults.add(new DetectionResult(category.getLabel(), category.getScore()));
        }

        // Draw the detection result on the bitmap and show it.
        Bitmap imgWithResult = drawDetectionResult(bitmap, finalResultsToDisplay);
        inputImageView.setImageBitmap(imgWithResult);

        return finalResults;
    }

    /**
     * Runs video detection on assets video file and returns the detected objects UNIQUE list
     *
     * @param afd            assets video file descriptor
     * @param duration       the original video duration in millis
     * @param skipFrameDelay skip frames delay in millis (example: when skipFrameDelay is passed 5_000L,
     *                       then the video corresponding frames should be processed by video detector:
     *                       0, 5000, 10000, etc... until duration).
     *                       Note: OPTION_CLOSEST returns KEY FRAME!
     * @return the detected UNIQUE objects
     * @throws Exception when assets file is corrupted or media retriever can't open it
     */
    public Set<DetectionResult> runVideoObjectDetection(AssetFileDescriptor afd,
                                                        long duration,
                                                        long skipFrameDelay) throws Exception {
        if (detector == null) {
            throw new IOException("failed to access TF model file");
        }
        if (skipFrameDelay >= duration) {
            throw new Exception("skip delay need to be lower number than duration.");
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

        List<DetectionResult> finalResults = new ArrayList<>();
        for (long i = 0; i < duration; i = i + skipFrameDelay) {
            try {
                Bitmap bitmap = retriever.getFrameAtTime(i,
                        MediaMetadataRetriever.OPTION_CLOSEST).copy(Bitmap.Config.ARGB_8888, true);
                if (bitmap != null) {
                    finalResults.addAll(runObjectDetection(bitmap));
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }

        retriever.release();

        // removing duplicates
        return new HashSet<>(finalResults);
    }

    /**
     * Runs video detection on passed video file absolute path and returns the detected objects UNIQUE list
     *
     * @param videoPath      any video file absolute path
     * @param skipFrameDelay skip frames delay in millis (example: when skipFrameDelay is passed 5_000L,
     *                       then the video corresponding frames should be processed by video detector:
     *                       0, 5000, 10000, etc... until duration).
     *                       Note: OPTION_CLOSEST returns KEY FRAME!
     * @return the detected UNIQUE objects
     * @throws Exception when passed video file is corrupted, not found, permission denied or media retriever can't open it
     */
    public Set<DetectionResult> runVideoObjectDetection(String videoPath,
                                                        long skipFrameDelay) throws Exception {
        if (detector == null) {
            throw new IOException("failed to access TF model file");
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);

        long duration = getVideoDuration(retriever);
        if (skipFrameDelay >= duration) {
            throw new Exception("skip delay need to be lower number than duration.");
        }

        List<DetectionResult> finalResults = new ArrayList<>();
        for (long i = 0; i < duration; i = i + skipFrameDelay) {
            try {
                Bitmap bitmap = retriever.getFrameAtTime(i,
                        MediaMetadataRetriever.OPTION_CLOSEST).copy(Bitmap.Config.ARGB_8888, true);
                if (bitmap != null) {
                    finalResults.addAll(runObjectDetection(bitmap));
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }

        retriever.release();

        // removing duplicates
        return new HashSet<>(finalResults);
    }

    /**
     * Runs video detection on passed video file uri and returns the detected objects UNIQUE list
     *
     * @param videoUri       any video file absolute path
     * @param skipFrameDelay skip frames delay in millis (example: when skipFrameDelay is passed 5_000L,
     *                       then the video corresponding frames should be processed by video detector:
     *                       0, 5000, 10000, etc... until duration).
     *                       Note: OPTION_CLOSEST returns KEY FRAME!
     * @return the detected UNIQUE objects
     * @throws Exception when passed video file uri is corrupted, not found, permission denied or media retriever can't open it
     */
    public Set<DetectionResult> runVideoObjectDetection(Uri videoUri,
                                                        long skipFrameDelay) throws Exception {
        if (detector == null) {
            throw new IOException("failed to access TF model file");
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, videoUri);

        long duration = getVideoDuration(retriever);
        if (skipFrameDelay >= duration) {
            throw new Exception("skip delay need to be lower number than duration.");
        }

        List<DetectionResult> finalResults = new ArrayList<>();
        for (long i = 0; i < duration; i = i + skipFrameDelay) {
            try {
                Bitmap bitmap = retriever.getFrameAtTime(i,
                        MediaMetadataRetriever.OPTION_CLOSEST).copy(Bitmap.Config.ARGB_8888, true);
                if (bitmap != null) {
                    finalResults.addAll(runObjectDetection(bitmap));
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }

        retriever.release();

        // removing duplicates
        return new HashSet<>(finalResults);
    }

    /**
     * Runs video detection on passed video file uri and returns the detected objects UNIQUE list
     *
     * @param videoUri any video file absolute path
     * @param factor   the factor of video duration to be processed (example: when video duration
     *                 is 10 seconds and factor passed to 0.5f then the 5 seconds of the full 10
     *                 seconds video will be processed (frames in the whole video)
     *                 Note: OPTION_CLOSEST returns KEY FRAME!
     * @return the detected UNIQUE objects
     * @throws Exception when passed video file uri is corrupted, not found, permission denied or media retriever can't open it
     */
    public Set<DetectionResult> runVideoObjectDetection(Uri videoUri,
                                                        float factor) throws Exception {
        if (detector == null) {
            throw new IOException("failed to access TF model file");
        }
        if (factor <= 0f || factor > 1.0f) {
            throw new Exception("factor parameter must be grater then 0 and lower then 1");
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, videoUri);

        long duration = getVideoDuration(retriever);
        if (duration == 0) {
            throw new Exception("failed to retrieve duration from video");
        }

        List<DetectionResult> finalResults = new ArrayList<>();
        for (long i = 0; i < duration; i = i + (int) (1 / factor) * 1000L) {
            try {
                Bitmap bitmap = retriever.getFrameAtTime(i,
                        MediaMetadataRetriever.OPTION_CLOSEST).copy(Bitmap.Config.ARGB_8888, true);
                if (bitmap != null) {
                    finalResults.addAll(runObjectDetection(bitmap));
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }

        retriever.release();

        // removing duplicates
        return new HashSet<>(finalResults);
    }

    /**
     * Runs gif detection on the passed file uri and returns the detected objects result with result callback.
     *
     * @param factor   the factor of gif frames to be processed (when passed to 1.0f -> all frames will be processed,
     *                 when passed to 0.5 -> the each second frame will be skipped, etc..). It's useful when the gif size is huge
     * @param gifPath  file media uri to detect objects from
     * @param callback detection results callback
     */
    public void runGifObjectDetection(Uri gifPath, float factor, OnDetectionResultsListener callback) throws Exception {
        if (factor <= 0 || factor > 1.0f) {
            throw new Exception("factor parameter must be grater then 0 and lower then 1");
        }
        Glide.with(context)
                .asGif()
                .load(gifPath)
                .into(new CustomTarget<GifDrawable>() {

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        callback.onDetected(null);
                    }

                    @Override
                    public void onResourceReady(@NonNull GifDrawable resource, @Nullable Transition<? super GifDrawable> transition) {
                        try {
                            Object GifState = resource.getConstantState();
                            Field frameLoader = GifState.getClass().getDeclaredField("frameLoader");
                            frameLoader.setAccessible(true);
                            Object gifFrameLoader = frameLoader.get(GifState);

                            Field gifDecoder = gifFrameLoader.getClass().getDeclaredField("gifDecoder");
                            gifDecoder.setAccessible(true);
                            ArrayList<Bitmap> bitmaps = new ArrayList<>();
                            StandardGifDecoder standardGifDecoder = (StandardGifDecoder) gifDecoder.get(gifFrameLoader);
                            int framesCount = standardGifDecoder.getFrameCount();
                            for (int i = 0; i < framesCount; i++) {
                                standardGifDecoder.advance();
                                bitmaps.add(standardGifDecoder.getNextFrame());
                            }
                            List<DetectionResult> results = new ArrayList<>();
                            int step = 1;
                            if (bitmaps.size() > 5) {
                                step = (int) (1 / factor);
                            }
                            for (int i = 0; i < bitmaps.size(); i = i + step) {
                                try {
                                    results.addAll(runObjectDetection(bitmaps.get(i)));
                                } catch (Exception e) {
                                    Log.d(TAG, e.getMessage());
                                }
                            }
                            callback.onDetected(new HashSet<>(results));
                        } catch (Exception ex) {
                            Log.d(TAG, ex.getMessage());
                            callback.onDetected(null);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        callback.onDetected(null);
                    }
                });
    }

    /**
     * @param imagePath      org.stingle.ai.image file absolute path
     * @param inputImageView imageview for the retrieving width and height
     * @return scaled and rotated bitmap
     */
    public Bitmap prepareBitmap(String imagePath, ImageView inputImageView) {
        // Get the dimensions of the View
        int targetW = inputImageView.getWidth();
        int targetH = inputImageView.getWidth();

        return scaleAndRotateBitmap(imagePath, targetW, targetH);
    }

    /**
     * @param imagePath org.stingle.ai.image file absolute path
     * @param width     to apply bitmap options for specified width
     * @param height    to apply bitmap options for specified height
     * @return scaled and rotated bitmap
     */
    public Bitmap prepareBitmap(String imagePath, int width, int height) {
        return scaleAndRotateBitmap(imagePath, width, height);
    }

    /**
     * @param imagePath org.stingle.ai.image file absolute path
     * @return scaled and rotated bitmap with best practice sizes by TF
     */
    public Bitmap prepareBitmap(String imagePath) {
        return scaleAndRotateBitmap(imagePath, IMG_SIZE_WIDTH, IMG_SIZE_HEIGHT);
    }

    /* Helper Methods */

    private void debugPrint(List<Detection> results) {
        for (int i = 0; i < results.size(); ++i) {
            RectF box = results.get(i).getBoundingBox();

            Log.d(TAG, "Detected object: " + i);
            Log.d(TAG, String.format("  boundingBox: (%.2f, %.2f) - (%.2f, %.2f)",
                    box.left, box.top, box.right, box.bottom));

            for (int j = 0; j < results.get(i).getCategories().size(); ++j) {
                Category category = results.get(i).getCategories().get(j);
                Log.d(TAG, String.format("    Label %d: %s", j, category.getLabel()));
                int confidence = (int) (category.getScore() * 100);
                Log.d(TAG, String.format("    Confidence: %d percentage", confidence));
            }
        }
    }

    private Bitmap drawDetectionResult(
            Bitmap bitmap,
            List<DetectionResultBox> detectionResults) {
        Bitmap outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(outputBitmap);
        Paint pen = new Paint();
        pen.setTextAlign(Paint.Align.LEFT);

        for (DetectionResultBox r : detectionResults) {
            // draw bounding box
            pen.setColor(Color.RED);
            pen.setStrokeWidth(8F);
            pen.setStyle(Paint.Style.STROKE);
            RectF box = r.boundingBox;
            canvas.drawRect(box, pen);

            // calculate the right font size
            Rect tagSize = new Rect(0, 0, 0, 0);
            pen.setStyle(Paint.Style.FILL_AND_STROKE);
            pen.setColor(Color.YELLOW);
            pen.setStrokeWidth(2F);

            pen.setTextSize(MAX_FONT_SIZE);
            pen.getTextBounds(r.text, 0, r.text.length(), tagSize);
            float fontSize = pen.getTextSize() * box.width() / tagSize.width();

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.getTextSize()) pen.setTextSize(fontSize);

            float margin = (box.width() - tagSize.width()) / 2.0F;
            if (margin < 0F) margin = 0F;
            canvas.drawText(
                    r.text, box.left + margin,
                    box.top + tagSize.height() * 1F, pen
            );
        }

        return outputBitmap;
    }

    private Bitmap scaleAndRotateBitmap(String imagePath, int width, int height) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        // Get the dimensions of the bitmap
        bmOptions.inJustDecodeBounds = true;

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the org.stingle.ai.image
        int scaleFactor = Math.max(1, Math.min(photoW / width, photoH / height));

        // Decode the org.stingle.ai.image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inMutable = true;
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
            );
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }

        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, bmOptions);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: {
                return rotateImage(bitmap, 90f);
            }
            case ExifInterface.ORIENTATION_ROTATE_180: {
                return rotateImage(bitmap, 180f);
            }
            case ExifInterface.ORIENTATION_ROTATE_270: {
                return rotateImage(bitmap, 270f);
            }
            default: {
                return bitmap;
            }
        }
    }

    private Bitmap rotateImage(Bitmap source, Float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(
                source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true
        );
    }

    private long getVideoDuration(MediaMetadataRetriever retriever) {
        long duration;
        try {
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = Long.parseLong(time);

        } catch (RuntimeException e){
            duration = 0;
        }

        return duration;
    }

    public static class DetectionResult implements Comparable<StingleImageRecognition.DetectionResult> {
        private final String label;
        private final float score;

        public DetectionResult(final String label, final float score) {
            this.label = label;
            this.score = score;
        }

        public String getLabel() {
            return label;
        }

        public float getScore() {
            return score;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DetectionResult that = (DetectionResult) o;
            return label.equals(that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label);
        }

        @Override
        public int compareTo(DetectionResult other) {
            if (other.label.equals(this.label)) {
                return 0;
            }
            return this.label.compareTo(other.label);
        }
    }

    private static class DetectionResultBox {

        final RectF boundingBox;
        final String text;

        DetectionResultBox(final RectF boundingBox, final String text) {
            this.boundingBox = boundingBox;
            this.text = text;
        }
    }

    public interface OnDetectionResultsListener {
        void onDetected(Set<DetectionResult> results);
    }
}
