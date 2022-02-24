package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.print;
import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

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
    private final String DEPTH_ESTIMATOR_FILE = "midas_small_2_1.tflite";//"model_opt.tflite"; //

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
        //3x512x512
        this.depthEstimator.run(input, outputProbabilityBuffer.getBuffer().rewind());


        int test = 0;
        print("[");
        for (float i: outputProbabilityBuffer.getFloatArray()) {
            if(test != 0 && test % 256 == 0) print(",\n");
            print(test % 256 == 0 ? "[ " + i : i);
            test++;
            print(test % 256 == 0 ? " ]" : ", ");
            //if(test > 1000) break;
        }
        println("]");
        println();

        return outputProbabilityBuffer.getFloatArray(); //The outputs are relative depths between pixels
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
