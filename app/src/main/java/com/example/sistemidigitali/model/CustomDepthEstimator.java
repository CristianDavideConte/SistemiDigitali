package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class CustomDepthEstimator {
    private static final String DEPTH_ESTIMATOR_FILE = "midas_small_2_1.tflite";

    private static final float SFR_C_AVG = 194.29F; //Standard Average Depth in SFR area
    private static final float SFR_D = 1.0F;        //Standard Distance phone-person (in Meters)

    private final Context context;
    private Interpreter depthEstimator;
    private final TensorBuffer outputProbabilityBuffer;
    private final TensorProcessor outputTensorProcessor;

    public CustomDepthEstimator(Context context) {
        this.context = context;

        try {
            Interpreter.Options depthEstimatorOptions = new Interpreter.Options();
            depthEstimatorOptions.addDelegate(new GpuDelegate());
            depthEstimatorOptions.setNumThreads(4);

            MappedByteBuffer modelBuffer = loadModelFile();
            this.depthEstimator = new Interpreter(modelBuffer, depthEstimatorOptions);

            // Image shape is in the format of {1, height, width, 3}
            int test = 0;
            for (int i: new MetadataExtractor(modelBuffer).getInputTensorShape(0)) {
                println(test++, " -> " ,i);
            }
        } catch (Exception e) {
            try {
                println("Depth Estimator: GPU NOT COMPATIBLE -> LOADING CPU");

                Interpreter.Options depthEstimatorOptions = new Interpreter.Options();
                depthEstimatorOptions.setNumThreads(4);

                MappedByteBuffer modelBuffer = loadModelFile();
                this.depthEstimator = new Interpreter(modelBuffer, depthEstimatorOptions);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        this.outputProbabilityBuffer = TensorBuffer.createFixedSize(this.depthEstimator.getOutputTensor(0).shape(), DataType.FLOAT32);
        this.outputTensorProcessor = new TensorProcessor.Builder().add(new MinMaxScalingOp()).build();
    }

    /**
     * Sources:
     * https://github.com/shubham0204/Realtime_MiDaS_Depth_Estimation_Android/blob/65cd321b029fafee3d5b9ae4783fabd512951719/app/src/main/java/com/shubham0204/ml/depthestimation/MiDASModel.kt#L59
     * https://github.com/shubham0204/Realtime_MiDaS_Depth_Estimation_Android/blob/65cd321b029fafee3d5b9ae4783fabd512951719/app/src/main/java/com/shubham0204/ml/depthestimation/MiDASModel.kt#L89
     * https://github.com/isl-org/MiDaS/blob/b7fbf07a5d687653ec053757152f8f87efe49b4d/mobile/android/lib_support/src/main/java/org/tensorflow/lite/examples/classification/tflite/Classifier.java#L252
     * P = D * scale + shift
     * P = physical distance (meters)
     * D = inverse depth (with respect to the furthest point)
     * the returned float[] contains the D
     */
    public float[] getDepthMap(ByteBuffer input) {
        try {
            this.depthEstimator.run(input, outputProbabilityBuffer.getBuffer().rewind());
            this.outputTensorProcessor.process(outputProbabilityBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return outputProbabilityBuffer.getFloatArray(); //The output is a float[] containing the inverse (relative) depths between the observer and the pixel[i,j]
    }

    public float getDistancePhonePerson(float[] depthMap, float depthMapWidth, float depthMapHeight, float left, float width, float top, float height) {
        //C(i,j) = depthMap[i][j]
        //C(avg) = average depth in detection
        //SFR = STANDARD_DETECTION_RECT
        //distancePhonePerson = SFR.C(avg) * detection.C(avg) / SFR.C(avg)
        final float averageDepthInDetection = this.getAverageDepthInDetection(depthMap, depthMapWidth, depthMapHeight, left, width, top, height); //C(avg)
        println("AVERAGE DEPTH", averageDepthInDetection);
        return SFR_D * averageDepthInDetection / SFR_C_AVG;
    }

    /**
     * Given a depth map, calculate the average value (depth) within the given boundaries.
     * @param depthMap The depth map containing the depth values.
     * @param width The width of the detection.
     * @param height The height of the detection.
     * @return The average value (depth) within the given boundaries.
     */
    private float getAverageDepthInDetection(float[] depthMap, float depthMapWidth, float depthMapHeight, float left, float width, float top, float height) {
        left = left < 0 ? 0 : Math.min(left, depthMapWidth - 1);
        top  = top  < 0 ? 0 : Math.min(top, depthMapHeight - 1);
        int availableWidth  = (int) Math.min(depthMapWidth  - 1, left + width);
        int availableHeight = (int) Math.min(depthMapHeight - 1, top + height);

        /*
        println("DET LEFT", left);
        println("DET WIDTH", width);
        println("DET TOP", top);
        println("DET HEIGHT", height);
        println("AVAIL WIDTH", availableWidth);
        println("AVAIL HEIGHT", availableHeight);
        println("ARRAY LENGTH", depthMap.length);
        */

        float averageDepth = 0.0F;
        for (int j = (int) top; j < availableHeight; j++) {
            for (int i = (int) left; i < availableWidth; i++) {
                averageDepth += depthMap[(int)(i + j * depthMapWidth)];
            }
        }
        return averageDepth / (width * height);
    }

    /**
     * Return a MappedByteBuffer of a tflite model.
     * @return a MappedByteBuffer of the tflite file
     * @throws IOException if the file cannot be opened
     */
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.context.getAssets().openFd(CustomDepthEstimator.DEPTH_ESTIMATOR_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }
}
