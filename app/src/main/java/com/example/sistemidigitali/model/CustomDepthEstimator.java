package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.print;
import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.checkerframework.checker.units.qual.C;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
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

    public CustomDepthEstimator(Context context) {
        this.context = context;

        try {
            Interpreter.Options depthEstimatorOptions = new Interpreter.Options();
            depthEstimatorOptions.addDelegate(new GpuDelegate());
            depthEstimatorOptions.setNumThreads(4);

            this.depthEstimator = new Interpreter(loadModelFile(DEPTH_ESTIMATOR_FILE), depthEstimatorOptions);
            outputProbabilityBuffer = TensorBuffer.createFixedSize(this.depthEstimator.getOutputTensor(0).shape(), DataType.FLOAT32);

            //Iterator<Integer> it = Arrays.stream(this.depthEstimator.getOutputTensor(0).shape()).boxed().iterator();
            //println(it.next(), it.next(), it.next(), it.next()); //1 256 256 1
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

        return outputProbabilityBuffer.getFloatArray();
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
