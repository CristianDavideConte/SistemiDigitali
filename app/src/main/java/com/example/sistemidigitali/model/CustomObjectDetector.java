package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.TensorFlowLite;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomObjectDetector {
    private final String TEST_MODEL_FILE = "ssd_mobilenet_v1_1_metadata_1.tflite";
    private final String MODEL_FILE_F16 = "float_16_model_light.tflite";
    private final String MODEL_FILE_IO8 = "int_8_model_light.tflite";

    private ObjectDetector detector;
    private Context context;

    public CustomObjectDetector(Context context) throws IOException {
        this.context = context;

        // Initialization
        ObjectDetectorOptions options =
                ObjectDetectorOptions.builder()
                        .setBaseOptions(BaseOptions.builder().useNnapi().build())
                        .setScoreThreshold(0.4f) //40% sicurezza sulla predizione
                        .setMaxResults(10)
                        .build();

        long init = System.currentTimeMillis();
        this.detector = ObjectDetector.createFromFileAndOptions(this.context, MODEL_FILE_IO8, options);
        println("DEC:", System.currentTimeMillis() - init);
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
