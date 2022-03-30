package org.stingle.photos.AI;

import android.content.Context;
import android.net.Uri;

import org.stingle.photos.Db.Objects.StingleFileSearch;
import org.stingle.photos.Db.Query.FileSearchDb;

import java.util.Set;

import ai.image.StingleImageRecognition;

public class AIHelper {

    public static void detectImageAndSaveInDb(Context context, String fileName, Uri uri) {
        FileSearchDb searchDb = new FileSearchDb(context);
        try {
            ObjectDetection detection = ObjectDetection.getInstance();
            Set<StingleImageRecognition.DetectionResult> detectedResults = detection.detectImage(context, uri);
            for (StingleImageRecognition.DetectionResult obj : detectedResults) {
                searchDb.insert(new StingleFileSearch(fileName, "obj", obj.getLabel(), "1"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            searchDb.close();
        }
    }
}
