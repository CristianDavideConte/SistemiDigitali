package com.example.sistemidigitali.model;

import android.content.Context;

import com.example.sistemidigitali.enums.CustomObjectDetectorType;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CustomObjectDetector {
    private final String TEST_MODEL_FILE = "ssd_mobilenet_v1_1_metadata_1.tflite";
    private final String MODEL_FILE_F16 = "float_16_model_light.tflite";
    private final String MODEL_FILE_IO8 = "int_8_model_light.tflite";

    private Context context;
    private ObjectDetector detector;
    private CustomObjectDetectorType type;

    public CustomObjectDetector(Context context, CustomObjectDetectorType type) throws IOException {
        this.context = context;
        this.type = type;

        // Initialization
        ObjectDetectorOptions options =
                ObjectDetectorOptions.builder()
                        .setBaseOptions(BaseOptions.builder().useGpu().build()) //<uses-native-library> tag is required in the AndroidManifest.xml to use the GPU
                        //.setBaseOptions(BaseOptions.builder().useNnapi().build()) //used for testing on the Android Studio's emulator
                        .setScoreThreshold(0.3f) //30% sicurezza sulla predizione
                        .setMaxResults(10)
                        .build();

        if(this.type == CustomObjectDetectorType.HIGH_ACCURACY) this.detector = ObjectDetector.createFromFileAndOptions(this.context, MODEL_FILE_F16, options);
        else this.detector = ObjectDetector.createFromFileAndOptions(this.context, MODEL_FILE_IO8, options);
    }

    /**
     * Given a Tensor image returns a list of all the object detected in it.
     * @param image The image to analyze.
     * @return A list of Detection containing all informations about every detected object.
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
}
