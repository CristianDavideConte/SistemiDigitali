package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

import com.example.sistemidigitali.enums.CustomObjectDetectorType;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CustomObjectDetector {
    private static final String TEST_MODEL_FILE = "ssd_mobilenet_v1_1_metadata_1.tflite";
    //private static final String MODEL_FILE_F16 = "float_16_model_light.tflite";
    private static final String MODEL_FILE_F16 = "float_16_model_heavy.tflite";
    private static final String MODEL_FILE_IO8 = "int_8_model_light.tflite";
    //private static final String MODEL_FILE_IO8 = "int_8_model_heavy.tflite";

    private ObjectDetector detector;
    private final FaceDetector faceDetector;

    public CustomObjectDetector(Context context, CustomObjectDetectorType type) {
        final ObjectDetectorOptions.Builder defaultOptionsBuilder = ObjectDetectorOptions.builder()
                                                                                         .setBaseOptions(BaseOptions.builder().setNumThreads(4).build())
                                                                                         .setScoreThreshold(0.23f); //23% prediction minimum accuracy

        final ObjectDetectorOptions.Builder unsupportedOpOptionsBuilder = ObjectDetectorOptions.builder()
                                                                                               .setBaseOptions(BaseOptions.builder().useNnapi().build()) //used for testing on the Android Studio's emulator
                                                                                               .setScoreThreshold(0.23f); //23% prediction minimum accuracy

        final FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();

        try {
            if (type == CustomObjectDetectorType.HIGH_ACCURACY)
                this.detector = ObjectDetector.createFromFileAndOptions(context, MODEL_FILE_F16, defaultOptionsBuilder.setMaxResults(10).build());
            else
                this.detector = ObjectDetector.createFromFileAndOptions(context, MODEL_FILE_IO8, defaultOptionsBuilder.setMaxResults(1).build());
        } catch (Exception e) {
            try {
                if (type == CustomObjectDetectorType.HIGH_ACCURACY)
                    this.detector = ObjectDetector.createFromFileAndOptions(context, MODEL_FILE_F16, unsupportedOpOptionsBuilder.setMaxResults(10).build());
                else
                    this.detector = ObjectDetector.createFromFileAndOptions(context, MODEL_FILE_IO8, unsupportedOpOptionsBuilder.setMaxResults(1).build());
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        this.faceDetector = FaceDetection.getClient(options);
    }

    /**
     * A lightweight object-detection method.
     * Given a Tensor image returns a list of all the faces (wearing or not a mask) detected in it.
     * @param image The image to analyze.
     * @return A list of Detection containing all mask-related informations about every detected face.
     */
    public List<Detection> detect(TensorImage image) {
        List<Detection> detections = new ArrayList<>();
        try {
            detections = this.detector.detect(image);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return detections;
    }

    /**
     * An heavyweight object-detection method.
     * Given a Bitmap image perform a face detection first to get all the faces in the image,
     * then all the faces are passed to the face-mask-recognition object-detector which returns
     * a list of all the faces (wearing or not a mask) detected.
     * @param image The image to analyze.
     * @return A list of Detection containing all mask-related informations about every detected face.
     */
    public List<Detection> detect(Bitmap image) {
        final List<Detection> detections = new ArrayList<>();
        try {
            Tasks.await(
                this.faceDetector
                    .process(InputImage.fromBitmap(image, 0)) //Get the faces in the image
                    .addOnSuccessListener(faces -> {
                            for(Face face : faces) {
                                //Crop the original image to show only the current face
                                Rect faceBoundingBox = face.getBoundingBox();
                                Bitmap croppedImageToFace = Bitmap.createBitmap(
                                        image,
                                        faceBoundingBox.left,
                                        faceBoundingBox.top,
                                        faceBoundingBox.width(),
                                        faceBoundingBox.height()
                                );
                                //Detect if the current face is correctly wearing a mask
                                List<Detection> detection = this.detector.detect(TensorImage.fromBitmap(croppedImageToFace));
                                if (detection.size() > 0) {
                                    detections.add(Detection.create(new RectF(faceBoundingBox), detection.get(0).getCategories()));
                                }
                            }
                        }
                    ).addOnFailureListener(Throwable::printStackTrace)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return detections;
    }
}
