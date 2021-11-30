package org.stingle.photos.camera.helpers;

import static androidx.camera.video.QualitySelector.QUALITY_FHD;
import static androidx.camera.video.QualitySelector.QUALITY_HD;
import static androidx.camera.video.QualitySelector.QUALITY_SD;
import static androidx.camera.video.QualitySelector.QUALITY_UHD;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;

import androidx.camera.core.CameraSelector;

import org.stingle.photos.camera.models.CameraImageSize;

import java.util.ArrayList;
import java.util.Collections;

public class CameraImageSizeHelper {

    private final Context context;
    private final SharedPreferences settings;
    private int quality = QUALITY_HD;

    public CameraImageSizeHelper(Context context, SharedPreferences settings) {
        this.context = context;
        this.settings = settings;
    }

    public int getQuality() {
        return quality;
    }

    public CameraImageSize calculateCameraImageSize(int lensFacing,
                                                    boolean isVideoCapture) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics != null) {
                    if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != lensFacing) {
                        continue;
                    }
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    String sizeIndex = "0";
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        if (isVideoCapture) {
                            sizeIndex = settings.getString("front_video_res", "0");
                        } else {
                            sizeIndex = settings.getString("front_photo_res", "0");
                        }
                    } else if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        if (isVideoCapture) {
                            sizeIndex = settings.getString("back_video_res", "0");
                        } else {

                            sizeIndex = settings.getString("back_photo_res", "0");
                        }
                    }

                    if (isVideoCapture) {
                        ArrayList<String> videoOutputs = parseVideoOutputs(context, map);
                        if (videoOutputs.size() > 0) {
                            String qualityString = videoOutputs.get(Integer.parseInt(sizeIndex));
                            switch (qualityString) {
                                case "UHD":
                                    quality = QUALITY_UHD;
                                    return new CameraImageSize(context, 3840, 2160);
                                case "Full HD":
                                    quality = QUALITY_FHD;
                                    return new CameraImageSize(context, 1920, 1080);
                                case "HD":
                                    quality = QUALITY_HD;
                                    return new CameraImageSize(context, 1280, 720);
                                case "SD":
                                    quality = QUALITY_SD;
                                    return new CameraImageSize(context, 720, 480);
                            }
                        }
                    } else {
                        ArrayList<CameraImageSize> photoOutputs = parsePhotoOutputs(context, map);
                        if (photoOutputs.size() > 0) {
                            return photoOutputs.get(Integer.parseInt(sizeIndex));
                        }
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static ArrayList<String> parseVideoOutputs(Context context, StreamConfigurationMap map) {
        ArrayList<String> videoSizes = new ArrayList<>();

        android.util.Size[] cameraVideoSizes = map.getOutputSizes(MediaRecorder.class);
        for (android.util.Size size : cameraVideoSizes) {
			/*if( size.getWidth() > 4096 || size.getHeight() > 2160 || size.getHeight() < 480) {
				continue; // Nexus 6 returns these, even though not supported?!
			}*/
            String quality = new CameraImageSize(context, size.getWidth(), size.getHeight()).getQuality();
            if (!quality.isEmpty() && !videoSizes.contains(quality)) {
                videoSizes.add(quality);
            }
        }
        return videoSizes;
    }

    public static ArrayList<CameraImageSize> parsePhotoOutputs(Context context, StreamConfigurationMap map) {
        ArrayList<CameraImageSize> photoSizes = new ArrayList<>();

        android.util.Size[] cameraPhotoSizes = map.getOutputSizes(ImageFormat.JPEG);
        for (android.util.Size size : cameraPhotoSizes) {
            CameraImageSize photoSize = new CameraImageSize(context, size.getWidth(), size.getHeight());
            if (photoSize.megapixel < 0.9) {
                continue;
            }
            photoSizes.add(photoSize);
        }
        Collections.sort(photoSizes, new CameraImageSize.SizeSorter());

        return photoSizes;
    }
}
