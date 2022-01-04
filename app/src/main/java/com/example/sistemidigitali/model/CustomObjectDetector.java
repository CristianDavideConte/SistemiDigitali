package com.example.sistemidigitali.model;

import android.content.Context;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.io.IOException;
import java.util.List;

public class CustomObjectDetector {

    private final String MODEL_FILE ="lite-model_ssd_mobilenet_v1_1_metadata_2.tflite";
    private final String METADATA_FILE = "model.tflite";

    private ObjectDetector detector;
    private Context context;

    public CustomObjectDetector(Context context) throws IOException {
        this.context = context;

        // Initialization
        ObjectDetectorOptions options =
                ObjectDetectorOptions.builder()
                        .setBaseOptions(BaseOptions.builder().useNnapi().build())
                        //.setScoreThreshold(0.01f) //1% sicurezza sulla predizione
                        //.setMaxResults(10)
                        .build();

        //this.detector = ObjectDetector.createFromFileAndOptions(this.context, MODEL_FILE, options);
        this.detector = ObjectDetector.createFromFileAndOptions(this.context, METADATA_FILE, options);
    }

    /**
     * Given a Tensor image returns a list of all the object detected in it.
     * @param image The image to analyze.
     * @return A list of Detection containing all informations about every detected object.
     */
    public List<Detection> detect(TensorImage image) {
        return this.detector.detect(image);
    }
}
