package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.print;
import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.RectF;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class CustomDepthEstimator {
    private final String DEPTH_ESTIMATOR_FILE = "midas_small_2_1.tflite"; //"model_opt.tflite";
    //private final String DEPTH_ESTIMATOR_FILE = "dense_depth.tflite"; //https://github.com/jojo13572001/DenseDepth
    //private final String DEPTH_ESTIMATOR_FILE = "pydnet.tflite"; //https://github.com/FilippoAleotti/mobilePydnet

    private static final float SFR_C_AVG = 170.0F;
    private static final float SFR_D = 10.0F;


    private Context context;
    private Interpreter depthEstimator;
    private TensorBuffer outputProbabilityBuffer;
    private int imageSizeX, imageSizeY;

    public CustomDepthEstimator(Context context) {
        this.context = context;

        try {
            Interpreter.Options depthEstimatorOptions = new Interpreter.Options();
            depthEstimatorOptions.addDelegate(new GpuDelegate());
            depthEstimatorOptions.setNumThreads(4);

            MappedByteBuffer modelBuffer = loadModelFile(DEPTH_ESTIMATOR_FILE);
            this.depthEstimator = new Interpreter(modelBuffer, depthEstimatorOptions);
            outputProbabilityBuffer = TensorBuffer.createFixedSize(this.depthEstimator.getOutputTensor(0).shape(), DataType.FLOAT32);
            //outputProbabilityBuffer = TensorBuffer.createFixedSize(new int[]{1, 1500, 2000, 3}, DataType.FLOAT32);

            MetadataExtractor metadataExtractor = new MetadataExtractor(modelBuffer);
            // Image shape is in the format of {1, height, width, 3}.
            int[] imageShape = metadataExtractor.getInputTensorShape(/*inputIndex=*/ 0);
            imageSizeY = imageShape[1];
            imageSizeX = imageShape[2];

            int test = 0;
            for (int i: imageShape) {
                println(test++, " -> " ,i);
            }
        } catch (Exception e) {
            println("Depth Estimator: GPU NOT COMPATIBLE -> LOADING CPU");
            try {
                Interpreter.Options depthEstimatorOptions = new Interpreter.Options();
                depthEstimatorOptions.setNumThreads(4);

                this.depthEstimator = new Interpreter(loadModelFile(DEPTH_ESTIMATOR_FILE), depthEstimatorOptions);
                outputProbabilityBuffer = TensorBuffer.createFixedSize(this.depthEstimator.getOutputTensor(0).shape(), DataType.FLOAT32);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    //https://github.com/isl-org/MiDaS/blob/b7fbf07a5d687653ec053757152f8f87efe49b4d/mobile/android/lib_support/src/main/java/org/tensorflow/lite/examples/classification/tflite/Classifier.java#L252
    //P = D * scale + shift
    //P = physical distance (meters)
    //D = inverse depth (with respect to the furthest point)
    //the returned float[] contains the D
    //Sapendo la dimensione dimensione di un volto ad una distanza x scelta ed il suo valore di depth map (punto centrale del rettangolo o la media),
    //si può calcolare la distanza tra due volti (rettangoli e loro punti centrali) utilizzando la loro depth e i valori calcolati precedentemente (forse anche conversione px->cm).
    public float[] getDepthMap(ByteBuffer input) {
        try {
            this.depthEstimator.run(input, outputProbabilityBuffer.getBuffer().rewind());
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*int test = 0;
        print("[");
        for (float i: outputProbabilityBuffer.getFloatArray()) {
            if(test != 0 && test % 256 == 0) print(",\n");
            print(test % 256 == 0 ? "[ " + i : i);
            test++;
            print(test % 256 == 0 ? " ]" : ", ");
            //if(test > 1000) break;
        }
        println("]");
        println();*/

        return outputProbabilityBuffer.getFloatArray(); //The outputs are relative depths between pixels
    }

    public float getDistancePhonePerson(float[] depthMap, float depthMapWidth, float depthMapHeight, float left, float width, float top, float height) {
        //C(i,j) = depthMap[i][j]
        //C(avg) = average depth in detection
        //SFR = STANDARD_DETECTION_RECT
        //distancePhonePerson = SFR.C(avg) * detection.C(avg) / SFR.C(avg)
        float cAvg = this.getAverageDepthInDetection(depthMap, depthMapWidth, depthMapHeight, left, width, top, height);
        return SFR_D * cAvg / SFR_C_AVG;
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

        println("DET LEFT", left);
        println("DET WIDTH", width);
        println("DET TOP", top);
        println("DET HEIGHT", height);
        println("AVAIL WIDTH", availableWidth);
        println("AVAIL HEIGHT", availableHeight);
        println("ARRAY LENGTH", depthMap.length);


        float averageDepth = 0.0F;
        for (int i = (int) left; i < availableWidth; i++) {
            for (int j = (int) top; j < availableHeight; j++) {
                averageDepth += depthMap[(int)(i + j * depthMapWidth)];
            }
        }
        return averageDepth / (width * height);
    }

    public int getImageSizeX() {
        return imageSizeX;
    }

    public int getImageSizeY() {
        return imageSizeY;
    }


    /**
     * Return a MappedByteBuffer of a tflite model.
     * @param path tflite model path
     * @return a MappedByteBuffer of the tflite file
     * @throws IOException if the file cannot be opened
     */
    private MappedByteBuffer loadModelFile(String path) throws IOException {
        AssetFileDescriptor fileDescriptor = this.context.getAssets().openFd(path);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }
}
